package dev.error404.client.render;

import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import dev.error404.client.ClientState;

/**
 * The crash screen that is not a crash.
 *
 * <p>A death screen can be understood - you died, the game told you. This cannot: the game
 * appears to have thrown an exception, the client is still rendering, and nothing in the world
 * has changed. Four and a half seconds later it wipes itself and the game carries on as if
 * nothing happened, which is worse than if it had actually crashed.</p>
 *
 * <p>It is honest about being fake in the only way that matters: it is recoverable, it never
 * blocks input for longer than it is on screen, and the photosensitivity switch disables it.
 * The fake stack trace names the mod because a crash report that names the thing haunting you
 * is the joke.</p>
 */
public final class FakeCrashOverlay {
	private FakeCrashOverlay() {
	}

	public static final int DURATION_TICKS = 90; // 4.5 s

	private static final String[] TRACE = {
			"java.lang.NullPointerException: Cannot invoke \"World.getChunk(int, int)\" because \"this.world\" is null",
			"",
			"\tat net.minecraft.server.level.ServerLevel.getChunk(ServerLevel.java:1841)",
			"\tat dev.error404.deletion.ChunkCollapse.collapse(ChunkCollapse.java:64)",
			"\tat dev.error404.deletion.DeletionFront.tick(DeletionFront.java:38)",
			"\tat dev.error404.event.ServerEvents.onServerTick(ServerEvents.java:61)",
			"",
			"-- Chunk being deleted --",
			"\tStatus: MISSING",
			"\tOwner: <not found>",
			"",
			"An unexpected error has occurred. The world may not be in a valid state.",
			"Saving is disabled. Do not turn off your computer.",
	};

	private static int ticks;
	private static String[] lines = TRACE;
	private static long seed;

	public static void show() {
		ticks = DURATION_TICKS;
		seed = System.nanoTime();
		lines = TRACE;
	}

	public static boolean active() {
		return ticks > 0;
	}

	public static void tick() {
		if (ticks > 0) {
			ticks--;
		}
	}

	public static void render(GuiGraphics graphics, int width, int height) {
		if (ticks <= 0 || ClientState.photosensitive()) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		Random random = new Random(seed);

		// the world behind the report is still there, just very dark
		graphics.fill(0, 0, width, height, 0xF20B0D0E);

		// a few displaced rows, like a GPU that gave up
		for (int i = 0; i < 14; i++) {
			int y = random.nextInt(height);
			int h = 2 + random.nextInt(6);
			graphics.fill(random.nextInt(width / 2), y, width / 2 + random.nextInt(width / 2), y + h, 0x14FFFFFF);
		}

		graphics.drawCenteredString(client.font, "Minecraft has crashed!", width / 2, 24, 0xFFFFFFFF);
		graphics.drawCenteredString(client.font,
				"An unexpected error has occurred. The game has crashed and will close...",
				width / 2, 40, 0xFFB0B0B0);

		int y = 64;

		for (String line : lines) {
			graphics.drawString(client.font, line, 22, y, 0xFF9AA0A0, false);
			y += 10;

			if (y > height - 40) {
				break;
			}
		}

		// the tell: a crash screen does not have a countdown to being fine again
		int left = (int) Math.ceil(ticks / 20.0);

		graphics.drawString(client.font, "resuming in " + left + "...", 22, height - 24,
				0xFF30A040, false);
	}
}
