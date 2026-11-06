package dev.error404.client;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Client-side mirror of the server's integrity state. Everything visual is derived from
 * here, so the client stays a dumb renderer and both sides agree on the same numbers.
 */
public final class ClientState {
	private ClientState() {
	}

	private static int integrity = 100;
	private static int phase = 0;
	private static long seed = 0L;
	private static int fragments = 0;
	private static boolean ended = false;
	/** Mirrored from the server config: reduces flashing (1) and enables the HUD readout (2). */
	private static boolean photosensitive = false;
	private static boolean hudReadout = false;

	// one-shot effects
	private static boolean seeded = false;
	/** How long the dread overlay (tunnel vision, desaturation, intruder) stays up. */
	private static int dreadTicks = 0;
	private static int staticTicks = 0;
	private static int desaturateTicks = 0;
	private static int phaseFlashTicks = 0;
	private static String message = "";
	private static int messageTicks = 0;

	public static void apply(int newIntegrity, int newPhase, long newSeed, int newFragments, boolean newEnded, int flags) {
		integrity = newIntegrity;
		phase = Mth.clamp(newPhase, 0, 4);
		seed = newSeed;
		fragments = newFragments;
		ended = newEnded;
		photosensitive = (flags & 1) != 0;
		hudReadout = (flags & 2) != 0;

		// Only arm the dread clock on the first sync and when the world gets worse. Re-arming it
		// on every 5 s keep-alive packet would keep the tunnel vision up for the whole session,
		// which is the opposite of a scare.
		if (!seeded || newPhase > phase) {
			seeded = true;
			dreadTicks = Math.max(dreadTicks, 20 * 25);
		}
	}

	public static boolean dreadActive() {
		return dreadTicks > 0;
	}

	/** 0..1, fading out over the last 8 seconds. */
	public static float dreadStrength() {
		return dreadTicks <= 0 ? 0.0f : Math.min(1.0f, dreadTicks / (20.0f * 8.0f));
	}

	public static void addDread(int ticks) {
		dreadTicks = Math.max(dreadTicks, ticks);
	}

	public static boolean photosensitive() {
		return photosensitive;
	}

	public static boolean hudReadout() {
		return hudReadout;
	}

	public static void trigger(int kind, String text, int ticks) {
		switch (kind) {
			case 0 -> staticTicks = Math.max(staticTicks, Math.max(6, ticks));
			case 1 -> {
				phaseFlashTicks = Math.max(phaseFlashTicks, Math.max(20, ticks));
				staticTicks = Math.max(staticTicks, 10);
			}
			case 2 -> {
				message = text == null ? "" : text;
				messageTicks = Math.max(20, ticks);
			}
			case 3 -> {
				staticTicks = 0;
				desaturateTicks = 0;
				phaseFlashTicks = 0;
				message = "";
			}
			case 4 -> desaturateTicks = Math.max(desaturateTicks, Math.max(10, ticks));
			case 5 -> dev.error404.client.render.FakeCrashOverlay.show();
			case 6 -> dev.error404.client.render.JumpscareOverlay.trigger(
					text == null || text.isEmpty() ? 1 : Integer.parseInt(text.trim().substring(0, 1)),
					photosensitive);
			default -> {
			}
		}
	}

	public static void tick() {
		if (staticTicks > 0) {
			staticTicks--;
		}

		if (desaturateTicks > 0) {
			desaturateTicks--;
		}

		if (phaseFlashTicks > 0) {
			phaseFlashTicks--;
		}

		if (messageTicks > 0) {
			messageTicks--;
		}

		if (dreadTicks > 0) {
			dreadTicks--;
		}
	}

	/** A new world (or a disconnect) must not inherit the last world's overlays. */
	public static void reset() {
		integrity = 100;
		phase = 0;
		seed = 0L;
		fragments = 0;
		ended = false;
		seeded = false;
		dreadTicks = 0;
		staticTicks = 0;
		desaturateTicks = 0;
		phaseFlashTicks = 0;
		message = "";
		messageTicks = 0;
		dev.error404.client.render.JumpscareOverlay.reset();
		dev.error404.client.render.FakeCrashOverlay.reset();
	}

	public static int integrity() {
		return integrity;
	}

	public static int phase() {
		return phase;
	}

	public static long seed() {
		return seed;
	}

	public static int fragments() {
		return fragments;
	}

	public static boolean ended() {
		return ended;
	}

	public static boolean staticActive() {
		return staticTicks > 0;
	}

	public static float staticStrength() {
		return staticTicks <= 0 ? 0.0f : Math.min(1.0f, staticTicks / 12.0f);
	}

	public static boolean phaseFlashActive() {
		return phaseFlashTicks > 0;
	}

	public static float phaseFlashStrength() {
		return phaseFlashTicks <= 0 ? 0.0f : Math.min(1.0f, phaseFlashTicks / 30.0f);
	}

	public static float desaturateStrength() {
		return desaturateTicks <= 0 ? 0.0f : Math.min(1.0f, desaturateTicks / 20.0f);
	}

	public static String message() {
		return messageTicks > 0 ? message : "";
	}

	/** Deterministic per-session noise so overlays look identical on every client. */
	public static RandomSource seeded() {
		return RandomSource.create(seed ^ 0x404404404L);
	}

	public static String phaseName() {
		return switch (phase) {
			case 1 -> "DESYNC";
			case 2 -> "DELETION";
			case 3 -> "CASCADE";
			case 4 -> "ANNEXATION";
			default -> "SIGNAL";
		};
	}
}
