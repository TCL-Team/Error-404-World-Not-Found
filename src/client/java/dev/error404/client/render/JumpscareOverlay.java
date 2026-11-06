package dev.error404.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import dev.error404.ModSounds;
import dev.error404.client.ClientState;

/**
 * The jumpscare.
 *
 * <p>Design constraints, all of them deliberate:</p>
 * <ul>
 *   <li><b>Short.</b> Static plate, then the face, then a corrupted frame, then gone. 0.45 s in
 *       total. A jumpscare that lingers stops being a scare and becomes a screenshot.</li>
 *   <li><b>Silent to the server.</b> The server sends one packet; every frame is drawn locally,
 *       so a slow connection cannot smear it into a slideshow.</li>
 *   <li><b>Rare and never repeated.</b> At most one every {@value #MIN_GAP_TICKS} ticks, and the
 *       server only fires it from situations that justify it.</li>
 *   <li><b>Optional.</b> The photosensitivity switch removes the white static plate and the
 *       flash frames and keeps the face - still startling, no strobe.</li>
 *   <li><b>Never blocks input.</b> It is an overlay, not a screen: the player can keep running
 *       while it is on the screen, which is the whole point.</li>
 * </ul>
 *
 * <p>Two faces: {@code scare_face} (a face) and {@code scare_face_err} (the same face after the
 * decoder gave up), plus {@code scare_glitch}, a full-frame static plate.</p>
 */
public final class JumpscareOverlay {
	private JumpscareOverlay() {
	}

	private static final Identifier FACE = dev.error404.Error404.id("textures/gui/scare/scare_face.png");
	private static final Identifier FACE_ERR = dev.error404.Error404.id("textures/gui/scare/scare_face_err.png");
	private static final Identifier GLITCH = dev.error404.Error404.id("textures/gui/scare/scare_glitch.png");

	/** Total length of the whole scare. */
	public static final int DURATION_TICKS = 9;
	/** Minimum ticks between two scares, client-side: 90 s. */
	public static final int MIN_GAP_TICKS = 20 * 90;

	private static int ticks;
	private static int cooldown;
	/** 0 = breath-then-face, 1 = hard sting, 2 = whisper. Only changes the sound and timing. */
	private static int style;
	private static long seed;
	/** Whether the face is drawn centred and huge, or slightly off-centre (more unsettling). */
	private static boolean offCentre;

	public static void trigger(int style, boolean photosensitive) {
		if (cooldown > 0 || ticks > 0) {
			return;
		}

		ticks = DURATION_TICKS;
		cooldown = MIN_GAP_TICKS;
		JumpscareOverlay.style = Math.max(0, Math.min(2, style));
		seed = System.nanoTime();
		offCentre = RandomSource.create(seed).nextBoolean();

		play(photosensitive);
	}

	private static void play(boolean photosensitive) {
		Minecraft client = Minecraft.getInstance();

		if (client.level == null || client.player == null) {
			return;
		}

		var player = client.player;
		var level = client.level;

		switch (style) {
			case 0 -> {
				// breath first, then the hit: the silence between them is the scare
				level.playLocalSound(player.getX(), player.getY(), player.getZ(),
						ModSounds.SCARE_BREATH, SoundSource.AMBIENT, 1.6f, 1.0f, false);
				level.playLocalSound(player.getX(), player.getY(), player.getZ(),
						ModSounds.SCARE_STING, SoundSource.AMBIENT, 1.9f, 1.0f, false);
			}
			case 1 -> level.playLocalSound(player.getX(), player.getY(), player.getZ(),
					ModSounds.SCARE_STING, SoundSource.AMBIENT, 2.0f, 0.85f, false);
			default -> level.playLocalSound(player.getX(), player.getY(), player.getZ(),
					ModSounds.SCARE_WHISPER_NAME, SoundSource.AMBIENT, 1.8f, 0.9f, false);
		}

		if (!photosensitive) {
			level.playLocalSound(player.getX(), player.getY(), player.getZ(),
					ModSounds.STATIC_BURST, SoundSource.AMBIENT, 1.2f, 1.4f, false);
		}
	}

	public static void tick() {
		if (cooldown > 0) {
			cooldown--;
		}

		if (ticks > 0) {
			ticks--;
		}
	}

	public static boolean active() {
		return ticks > 0;
	}

	public static void render(GuiGraphics graphics, int width, int height) {
		if (ticks <= 0) {
			return;
		}

		boolean photo = ClientState.photosensitive();
		int elapsed = DURATION_TICKS - ticks;
		RandomSource random = RandomSource.create(seed + elapsed);

		// frame 0-1: static plate (skipped entirely for photosensitive players)
		if (!photo && elapsed < 2) {
			blitFull(graphics, GLITCH, width, height, 0xE0FFFFFF);
			return;
		}

		// frames 2-6: the face, growing, shaking by a pixel or two
		if (elapsed < 7) {
			graphics.fill(0, 0, width, height, photo ? 0xF0000000 : 0xE6040507);

			int size = Math.min(width, height);
			// it lunges: 62% of the screen on the first frame, 96% by the last
			float scale = 0.62f + (elapsed - 2) * 0.055f;
			int drawSize = (int) (size * Math.min(1.0f, scale));

			int jitterX = random.nextInt(7) - 3;
			int jitterY = random.nextInt(7) - 3;
			int x = (width - drawSize) / 2 + jitterX + (offCentre ? width / 16 : 0);
			int y = (height - drawSize) / 2 + jitterY - (offCentre ? height / 24 : 0);

			Identifier face = elapsed >= 5 ? FACE_ERR : FACE;
			graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, face,
					x, y, 0.0f, 0.0f, drawSize, drawSize, 512, 512);

			// the corrupted frame gets a red wash over it, once
			if (elapsed == 5 && !photo) {
				graphics.fill(0, 0, width, height, 0x22FF0000);
			}

			return;
		}

		// last frames: the screen goes black except a thin line of static, then clears
		graphics.fill(0, 0, width, height, 0xFF000000);

		if (!photo) {
			int y = height / 2;

			for (int x = 0; x < width; x += 3) {
				if (random.nextInt(3) == 0) {
					graphics.fill(x, y, x + 2, y + 1, 0x60FFFFFF);
				}
			}
		}
	}

	private static void blitFull(GuiGraphics graphics, Identifier texture, int width, int height, int tint) {
		graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, texture,
				0, 0, 0.0f, 0.0f, width, height, 512, 512);
	}
}
