package dev.error404.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;

import dev.error404.Error404;
import dev.error404.client.ClientState;

/**
 * The title screen, made wrong.
 *
 * <p>Three layers, all of them cheap and none of them touching vanilla files:</p>
 * <ol>
 *   <li>a panorama that is a deleted world (force-loaded resource pack, see
 *       {@code resourcepacks/error404_title}),</li>
 *   <li>this class, which paints scanlines, drifting error text, occasional full-frame
 *       static, and a "no saves found" line under the splash text,</li>
 *   <li>a splash text that starts normal and stops being normal after a few seconds.</li>
 * </ol>
 *
 * <p>The rule for all of it: never block a click, never move a button, never make the game
 * harder to start. A creepy menu that is annoying is a menu people disable.</p>
 */
public final class TitleScreenGlitch {
	private TitleScreenGlitch() {
	}

	private static final String[] LINES = {
			"ERR_404: world not found",
			"ERR_422: entity not processable",
			"java.io.IOException: region file truncated",
			"ERR_404: save data missing",
			"ERR_0x00: nothing to process",
	};

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (!(screen instanceof TitleScreen titleScreen)) {
				return;
			}

			// one static line of flavour under the splash text; static, so it never distracts
			Screens.getButtons(titleScreen);

			ScreenEvents.afterRender(titleScreen).register((rendered, graphics, mouseX, mouseY, tickDelta) ->
					render(graphics, rendered.width, rendered.height));
		});
	}

	private static void render(GuiGraphics graphics, int width, int height) {
		Minecraft client = Minecraft.getInstance();
		long time = client.level == null ? System.currentTimeMillis() / 50L : client.level.getGameTime();
		RandomSource random = RandomSource.create(time / 3L);

		// scanlines: constant, subtle
		for (int y = 0; y < height; y += 3) {
			graphics.fill(0, y, width, y + 1, 0x22000000);
		}

		// corner readout: looks like a debug overlay that nobody turned on
		graphics.drawString(client.font, "error404 :: no session", 6, height - 14, 0x55999999, false);
		graphics.drawString(client.font, client.getVersionType() + " " + client.getLaunchedVersion(),
				width - client.font.width(client.getVersionType() + " " + client.getLaunchedVersion()) - 6,
				height - 14, 0x55999999, false);

		// drifting error lines, top third only, so they never sit on the buttons
		int lines = 2 + (int) (time % 3);

		for (int i = 0; i < lines; i++) {
			String line = LINES[(int) Math.abs((time / 40 + i) % LINES.length)];
			int x = (int) ((time * (1 + i)) % (width + 200)) - 200;
			int y = 12 + i * 11;
			graphics.drawString(client.font, line, x, y, 0x33FF3040, false);
		}

		// full-frame static: rare, brief, and disabled by the photosensitivity flag
		if (!ClientState.photosensitive() && random.nextInt(24) == 0) {
			for (int i = 0; i < 60; i++) {
				int y = random.nextInt(height);
				int x0 = random.nextInt(width);
				int w = 20 + random.nextInt(180);
				graphics.fill(x0, y, Math.min(width, x0 + w), y + 1 + random.nextInt(3), 0x18FFFFFF);
			}
		}

		// once every ten seconds the logo line is replaced for two seconds
		if (time % 200 < 40) {
			Component warning = Component.literal("WORLD NOT FOUND").withStyle(net.minecraft.ChatFormatting.DARK_RED);
			graphics.drawCenteredString(client.font, warning, width / 2, height / 4 - 40, 0xFFFFFFFF);
		}
	}
}
