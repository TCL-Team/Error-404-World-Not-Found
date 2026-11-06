package dev.error404.client.render;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import dev.error404.Error404;
import dev.error404.client.ClientState;

/**
 * Everything the player sees that is not the world: scanlines, drifting error text, the
 * integrity readout (opt-in), the phase flash and the static burst.
 *
 * <p>Rendered as one HUD layer just above the hotbar so it also covers the inventory
 * background when the pause screen dims the world.</p>
 */
public final class HudGlitchRenderer {
	private static final Identifier LAYER = Identifier.fromNamespaceAndPath(Error404.MOD_ID, "glitch");
	private static final String[] ERROR_LINES = {
			"ERR_404: chunk save failed",
			"ERR_422: entity not processable",
			"java.io.IOException: region file truncated",
			"WARN: world_integrity below threshold",
			"ERR_404: structure references missing block",
			"ERR_422: player record checksum mismatch",
			"ERR_404: requested world not found",
			"ERR_0x00: nothing to process",
	};
	private static final RandomSource RANDOM = RandomSource.create();

	private static final net.minecraft.resources.Identifier LAYER_CRASH =
			dev.error404.Error404.id("hud_fake_crash");
	private static final net.minecraft.resources.Identifier LAYER_DREAD =
			dev.error404.Error404.id("hud_dread");

	private HudGlitchRenderer() {
	}

	public static void register() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, LAYER, HudGlitchRenderer::render);

		// the fake crash sits above everything else, including chat and the hotbar, and ticks on
		// its own countdown so it disappears whether or not the player touches anything
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, LAYER_CRASH,
				(context, delta) -> {
					// clocks are advanced by ClientTickEvents; these only draw
					FakeCrashOverlay.render(context, context.guiWidth(), context.guiHeight());
					JumpscareOverlay.render(context, context.guiWidth(), context.guiHeight());
				});

		// the slow layer goes under the HUD, so chat and health stay readable
		HudElementRegistry.attachElementBefore(VanillaHudElements.HOTBAR, LAYER_DREAD,
				(context, delta) -> DreadOverlay.render(context, context.guiWidth(), context.guiHeight()));
	}

	/**
	 * 0 = nothing nearby, 1 = it is touching you.
	 *
	 * <p>Only reacts to an entity the server already synced, so there is nothing to exploit
	 * and nothing to desync: if the client can see it, the player can be scared by it.</p>
	 */
	private static float proximityDread(Minecraft client) {
		if (client.player == null || client.level == null) {
			return 0.0f;
		}

		double nearest = Double.MAX_VALUE;

		for (net.minecraft.world.entity.Entity entity : client.level.entitiesForRendering()) {
			if (entity instanceof dev.error404.entity.Error422Entity) {
				nearest = Math.min(nearest, entity.distanceTo(client.player));
			}
		}

		if (nearest > 24.0) {
			return 0.0f;
		}

		return (float) (1.0 - Math.min(1.0, nearest / 24.0));
	}

	/**
	 * The screen stops being reliable when it is close: a hard vignette that tightens, a
	 * chromatic red/cyan split on the edges, and, at maximum dread, four-pixel horizontal
	 * tears that do not move like anything the player's brain can parse.
	 */
	private static void renderProximityDread(GuiGraphics graphics, Minecraft client, int width, int height, float dread) {
		boolean calm = ClientState.photosensitive();
		float scale = calm ? 0.3f : 1.0f;

		// vignette: three nested bands, cheapest possible way to darken the edges
		int bands = 3;

		for (int i = 0; i < bands; i++) {
			int inset = (int) ((1.0f - dread) * scale * (width * 0.02f)) + i * (width / 40);
			int alpha = (int) (0x30 * dread * scale * (1.0f - i * 0.25f)) << 24;

			if (alpha == 0) {
				continue;
			}

			int colour = 0x00000000 | alpha;
			graphics.fill(0, 0, width, inset, colour);
			graphics.fill(0, height - inset, width, height, colour);
			graphics.fill(0, 0, inset, height, colour);
			graphics.fill(width - inset, 0, width, height, colour);
		}

		// chromatic split: thin red and cyan seams down the screen edges
		if (dread > 0.35f && !calm) {
			int edge = Math.max(2, (int) (6 * dread));
			graphics.fill(0, 0, edge, height, 0x33FF2030);
			graphics.fill(width - edge, 0, width, height, 0x3300E0FF);
		}

		// tearing: a few rows that are simply the wrong brightness
		if (dread > 0.6f && !calm && client.player.tickCount % 7 < 3) {
			RandomSource random = ClientState.seeded();
			int tears = (int) (3 + dread * 5);

			for (int i = 0; i < tears; i++) {
				int y = random.nextInt(Math.max(1, height));
				int thickness = 1 + random.nextInt(4);
				int x0 = random.nextInt(Math.max(1, width / 2));
				int x1 = x0 + random.nextInt(Math.max(1, width / 3));
				graphics.fill(x0, y, Math.min(width, x1), Math.min(height, y + thickness), 0x1AFFFFFF);
			}
		}

		// at maximum dread the hotbar itself flickers out of existence for a frame
		if (dread > 0.9f && client.player.tickCount % 11 == 0) {
			graphics.fill(0, height - 26, width, height, 0xCC000000);
		}
	}

	private static void render(GuiGraphics graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();

		if (client.player == null || client.options.hideGui) {
			return;
		}

		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		int phase = ClientState.phase();
		float intensity = phaseIntensity(phase);

		// Is it looking at me, and how close is it?
		float dread = proximityDread(client);

		if (dread > 0.0f) {
			renderProximityDread(graphics, client, width, height, dread);
			// Proximity makes the whole overlay read worse, without ever flashing.
			intensity = Math.min(1.0f, intensity + dread * 0.35f);
		}

		// --- scanlines: always there once the world starts desyncing
		if (intensity > 0.0f) {
			int alpha = (int) (0x22 * intensity) << 24;

			for (int y = 0; y < height; y += 3) {
				graphics.fill(0, y, width, y + 1, alpha);
			}
		}

		// --- drifting terminal text
		if (intensity > 0.05f) {
			int lineCount = 1 + (int) (intensity * 4);
			int tick = client.gui.getGuiTicks();

			for (int i = 0; i < lineCount; i++) {
				String text = ERROR_LINES[(tick / 20 + i * 3) % ERROR_LINES.length];
				int y = (int) ((tick * 0.35f + i * 47) % (height + 20)) - 10;
				int x = 6 + (i % 2) * 8;
				graphics.drawString(client.font, text, x, y, 0xFF8B1020 | ((int) (0x60 * intensity) << 24), false);
			}
		}

		// --- integrity readout (opt-in via config, off by default)
		if (ClientState.hudReadout()) {
			String readout = "integrity " + ClientState.integrity() + "%  " + ClientState.phaseName()
					+ "  fragments " + ClientState.fragments() + "/8";
			graphics.drawString(client.font, readout, 6, 6, 0xFF9E9E9E, false);
		}

		// --- action-bar-ish message in the middle
		String message = ClientState.message();

		if (!message.isEmpty()) {
			int x = (width - client.font.width(message)) / 2;
			graphics.drawString(client.font, message, x, height / 2 + 40, 0xFFB0182C, false);
		}

		// --- phase flash
		if (ClientState.phaseFlashActive()) {
			float strength = ClientState.phaseFlashStrength() * (ClientState.photosensitive() ? 0.3f : 1.0f);
			graphics.fill(0, 0, width, height, ((int) (0x55 * strength) << 24) | 0x00101010);
			String title = "ERR_404 :: " + ClientState.phaseName();
			int x = (width - client.font.width(title)) / 2;
			graphics.drawString(client.font, title, x, height / 2 - 30, 0xFFC81830, true);
		}

		// --- static burst (halved for photosensitive players)
		if (ClientState.staticActive()) {
			float strength = ClientState.staticStrength() * (ClientState.photosensitive() ? 0.35f : 1.0f);
			int lines = (int) (height * 0.35f * strength);

			for (int i = 0; i < lines; i++) {
				int y = RANDOM.nextInt(height);
				int thickness = 1 + RANDOM.nextInt(3);
				int shade = 0x20 + RANDOM.nextInt(0x60);
				int color = ((int) (0xAA * strength) << 24) | (shade << 16) | (shade << 8) | shade;
				graphics.fill(0, y, width, y + thickness, color);
			}

			graphics.fill(0, 0, width, height, ((int) (0x33 * strength) << 24) | 0x00101010);
		}

		// --- desaturation veil (used when 422 is in PRESENCE range)
		float desat = ClientState.desaturateStrength();

		if (desat > 0.0f) {
			graphics.fill(0, 0, width, height, ((int) (0x88 * desat) << 24) | 0x00141414);
		}

		// --- endgame vignette: the world is closing in
		if (phase >= 4) {
			for (int i = 0; i < 18; i++) {
				int inset = i * 4;
				int alpha = (int) (0x08 * (1.0f - i / 18.0f)) << 24;
				graphics.fill(inset, inset, width - inset, inset + 2, alpha);
				graphics.fill(inset, height - inset - 2, width - inset, height - inset, alpha);
			}
		}

		String subtitle = ClientState.phase() > 0 ? subtitleFor(ClientState.phase()) : "";

		if (!subtitle.isEmpty()) {
			int x = (width - client.font.width(subtitle)) / 2;
			graphics.drawString(client.font, subtitle, x, height - 46, 0x66B0182C, false);
		}
	}

	private static float phaseIntensity(int phase) {
		return Mth.clamp(phase / 4.0f, 0.0f, 1.0f) * 0.9f;
	}

	private static String subtitleFor(int phase) {
		return switch (phase) {
			case 1 -> "several regions stopped saving";
			case 2 -> "deletion in progress";
			case 3 -> "cascade failure: the world is removing itself";
			case 4 -> "world not found";
			default -> "";
		};
	}

	/** Used by the client entrypoint for the tab-list style debug line. */
	public static Component statusLine() {
		return Component.literal("ERR_404 " + ClientState.integrity() + "%");
	}
}
