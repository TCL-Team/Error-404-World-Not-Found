package dev.error404.client.render;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

import dev.error404.client.ClientState;

/**
 * The title screen, made wrong.
 *
 * <p>Four layers, none of them touching a vanilla file:</p>
 * <ol>
 *   <li>a panorama of a world that has been deleted, shipped as a built-in resource pack
 *       ({@code resourcepacks/title}) that overrides the {@code minecraft} namespace,</li>
 *   <li>an original wordmark ({@code ERROR 404 / WORLD NOT FOUND}) and a "NULL EDITION" bar
 *       replacing the vanilla logo through the same pack,</li>
 *   <li>a splash text that reads like a stack trace instead of a joke,</li>
 *   <li>this class: scanlines, drifting error lines, and a rare burst of static.</li>
 * </ol>
 *
 * <p>The one hard rule: never block a click, never move a button, never slow down getting
 * into the game. A creepy menu that is annoying is a menu people disable.</p>
 */
public final class TitleScreenGlitch {
	private TitleScreenGlitch() {
	}

	/** Painted over vanilla's splash text, which the pack cannot reach. */
	private static Component splashLine = Component.literal("ERR_404: world not found");

	private static final String[] SPLASHES = {
			"ERR_404: world not found",
			"no save data",
			"0 chunks loaded",
			"region file truncated",
			"you were here before",
			"ERR_0x00: nothing to process",
			"this menu is a ghost",
			"world_nether.dat: missing",
			"1 player, 0 found",
			"nobody wrote this world down",
	};

	private static final String[] LOG_LINES = {
			"[error404] ERR_404: world not found",
			"[error404] ERR_422: entity not processable",
			"[error404] java.io.IOException: region file truncated",
			"[error404] WARN save data missing, continuing anyway",
			"[error404] ERR_0x00: nothing to process",
	};

	private static void render(GuiGraphics graphics, int width, int height) {
		Minecraft client = Minecraft.getInstance();
		long time = client.level == null ? System.currentTimeMillis() / 50L : client.level.getGameTime();
		RandomSource random = RandomSource.create(time / 3L);

		// scanlines: constant, subtle
		for (int y = 0; y < height; y += 3) {
			graphics.fill(0, y, width, y + 1, 0x22000000);
		}

		// corner readout: looks like a debug overlay nobody turned on
		graphics.drawString(client.font, "error404 :: no session", 6, height - 14, 0x55999999, false);

		// the splash, replaced: drawn where vanilla draws it, rotated the same way
		graphics.pose().pushMatrix();
		graphics.pose().translate(width / 2.0f + 123.0f, 69.0f);
		graphics.pose().rotate(-0.34906584f);
		int splashWidth = client.font.width(splashLine);
		float splashScale = 1.8F * 100.0F / (splashWidth + 48);
		graphics.pose().scale(splashScale, splashScale);
		graphics.drawCenteredString(client.font, splashLine, 0, -8, 0xFFFF4040);
		graphics.pose().popMatrix();

		// drifting error lines, top third only, so they never sit on the buttons
		int lines = 2 + (int) (time % 3);

		for (int i = 0; i < lines; i++) {
			String line = LOG_LINES[(int) Math.abs((time / 40 + i) % LOG_LINES.length)];
			int x = (int) ((time * (1 + i)) % (width + 220)) - 220;
			int y = 12 + i * 11;
			graphics.drawString(client.font, line, x, y, 0x33FF3040, false);
		}

		// occasional full-frame static, brief and rare; glasses-off players never see it
		if (!ClientState.photosensitive() && random.nextInt(28) == 0) {
			for (int i = 0; i < 60; i++) {
				int y = random.nextInt(height);
				int x0 = random.nextInt(width);
				int w = 20 + random.nextInt(180);
				graphics.fill(x0, y, Math.min(width, x0 + w), y + 1 + random.nextInt(3), 0x18FFFFFF);
			}
		}

		// once every ten seconds the logo area says the quiet part out loud
		if (time % 200 < 30) {
			graphics.drawCenteredString(client.font,
					Component.literal("WORLD NOT FOUND").withStyle(ChatFormatting.DARK_RED),
					width / 2, 6, 0xFFFFFFFF);
		}
	}
}
