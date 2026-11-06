package dev.error404.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import dev.error404.client.ClientState;

/**
 * The slow, non-jumpscare half of the horror: the screen getting worse the longer you survive.
 *
 * <p>Nothing here is a scare on its own. Together they are a constant, low-grade wrongness that
 * the player cannot point at and cannot turn off, which is what "disturbing" means in practice:</p>
 *
 * <ul>
 *   <li><b>Tunnel vision</b> - at low integrity the edges of the screen darken and close in, with
 *       the player's own heartbeat (proximity to 422 tightens it further).</li>
 *   <li><b>Desaturation</b> - a grey wash that creeps in over a session, so the world slowly
 *       stops having colour without a single event marking the change.</li>
 *   <li><b>The intruder</b> - very rarely, a dark shape crosses the bottom corner of the screen in
 *       under a second. It is not an entity: it is drawn locally, it has no hitbox, and it is
 *       gone before the player can be sure it was there.</li>
 *   <li><b>Breathing vignette</b> - the vignette pulses at walking pace when the player has not
 *       slept, which reads as "your own body is the thing that is wrong".</li>
 * </ul>
 */
public final class DreadOverlay {
	private DreadOverlay() {
	}

	private static int intruderTicks;
	private static int intruderSide;
	private static int nextIntruder = 20 * 60 * 3;
	private static final RandomSource RANDOM = RandomSource.create();

	public static void tick(Minecraft client) {
		if (client.player == null || client.level == null || ClientState.photosensitive()) {
			return;
		}

		if (intruderTicks > 0) {
			intruderTicks--;
			return;
		}

		// only once the world is properly falling apart, and only outside the anchor zone
		if (ClientState.phase() < 3 || ClientState.integrity() > 55) {
			return;
		}

		if (--nextIntruder > 0) {
			return;
		}

		nextIntruder = 20 * (120 + RANDOM.nextInt(180));
		intruderTicks = 12;
		intruderSide = RANDOM.nextBoolean() ? 0 : 1;
	}

	/** 0 when the world is intact, 1 when it is nearly gone. Used by every effect here. */
	private static float decay() {
		return Mth.clamp(1.0f - ClientState.integrity() / 100.0f, 0.0f, 1.0f);
	}

	public static void render(GuiGraphics graphics, int width, int height) {
		if (ClientState.ended()) {
			return;
		}

		float decay = decay();

		if (decay <= 0.01f || ClientState.photosensitive()) {
			// still draw nothing: a photosensitive player gets a clean screen, not a dimmer one
			return;
		}

		// --- desaturation: a grey wash that scales with decay --------------------------
		int grey = (int) (decay * 46);

		if (grey > 0) {
			graphics.fill(0, 0, width, height, (Math.min(70, grey) << 24) | 0x40484A);
		}

		// --- tunnel vision -------------------------------------------------------------
		// four gradients instead of an ellipse: cheaper, and it produces a harsher edge
		int tunnel = (int) (40 + decay * 130);
		graphics.fillGradient(0, 0, width, height / 3, (tunnel << 24) | 0x05060A, 0x0005060A);
		graphics.fillGradient(0, height - height / 3, width, height, 0x0005060A, (tunnel << 24) | 0x05060A);
		graphics.fillGradient(0, 0, width / 4, height, (tunnel << 24) | 0x05060A, 0x0005060A);
		graphics.fillGradient(width - width / 4, 0, width, height, 0x0005060A, (tunnel << 24) | 0x05060A);

		// --- the intruder --------------------------------------------------------------
		if (intruderTicks > 0) {
			renderIntruder(graphics, width, height);
		}
	}

	/**
	 * A silhouette crossing the bottom of the screen.
	 *
	 * <p>Drawn as a stack of black rectangles with a single unlit edge, sliding from off-screen to
	 * off-screen in twelve ticks. No face, no detail, no sound: the whole effect depends on the
	 * player being unable to stop it and unable to look at it properly.</p>
	 */
	private static void renderIntruder(GuiGraphics graphics, int width, int height) {
		float progress = 1.0f - (intruderTicks / 12.0f);
		int shapeW = Math.max(26, width / 26);
		int shapeH = Math.max(70, height / 5);

		int travel = width + shapeW * 3;
		int x = intruderSide == 0
				? (int) (progress * travel) - shapeW * 2
				: width + shapeW * 2 - (int) (progress * travel);
		int y = height - shapeH - height / 12;

		// body: a tall dark column, slightly wider at the bottom
		graphics.fill(x, y, x + shapeW, y + shapeH, 0xE8000000);
		graphics.fill(x - shapeW / 5, y + shapeH / 5, x + shapeW + shapeW / 5, y + shapeH, 0xE8000000);

		// head, too small for the body
		int headW = shapeW * 2 / 3;
		graphics.fill(x + shapeW / 6, y - headW, x + shapeW / 6 + headW, y, 0xE8000000);

		// one edge of rim light so it separates from the world behind it
		int rim = intruderSide == 0 ? x + shapeW - 1 : x;

		for (int i = 0; i < shapeH; i += 2) {
			graphics.fill(rim, y + i, rim + 1, y + i + 2, 0x2E8A9A96);
		}
	}
}
