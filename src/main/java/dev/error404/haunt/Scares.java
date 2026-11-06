package dev.error404.haunt;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

import dev.error404.Difficulty;
import dev.error404.ModSounds;
import dev.error404.integrity.IntegrityState;
import dev.error404.integrity.Phase;
import dev.error404.network.ModNetworking;

/**
 * Jumpscares, and when they are allowed to happen.
 *
 * <p>The rule that separates a scare from an annoyance is <b>justification</b>: a jumpscare is
 * only fired by something in the world, never on a timer. There are exactly five triggers, and
 * every one of them is a moment the player is already tense:</p>
 *
 * <ol>
 *   <li><b>422 turns to look at you</b> - it has been behind you and you turned around.</li>
 *   <li><b>a chunk begins to collapse under your feet</b> - the personal collapse warning.</li>
 *   <li><b>you take a hit</b> that leaves you at a quarter health.</li>
 *   <li><b>a structure appears</b> inside fifteen blocks of you.</li>
 *   <li><b>you walk into 422 while it is HUNTing</b> - contact in the final phase.</li>
 * </ol>
 *
 * <p>On top of that: one scare per player per {@link #COOLDOWN_TICKS} (two minutes), never in an
 * anchor zone, never below DELETION phase, never on `chill`, and never in the first minute of a
 * session - a player who is still reading the "you may lose your world" screen is not ready.</p>
 */
public final class Scares {
	private Scares() {
	}

	/** Two minutes between scares for one player. Long enough that each one lands. */
	public static final int COOLDOWN_TICKS = 20 * 120;
	/** Extra 18 s after a scare where nothing else scary fires for that player. */
	public static final int AFTERSHOCK_TICKS = 20 * 18;

	private static final java.util.Map<java.util.UUID, Long> LAST_SCARE = new java.util.HashMap<>();
	private static final java.util.Map<java.util.UUID, Long> AFTERSHOCK = new java.util.HashMap<>();

	/** Cheap: only expires stale entries, so it is safe to call for every dimension. */
	public static void tick(ServerLevel level) {
		if (!level.players().isEmpty() && level.getGameTime() % 600 == 0) {
			long now = level.getGameTime();
			AFTERSHOCK.entrySet().removeIf(entry -> entry.getValue() < now - 20 * 60);
		}
	}

	/** Drops one player's cooldown entries on disconnect. */
	public static void forget(java.util.UUID uuid) {
		LAST_SCARE.remove(uuid);
		AFTERSHOCK.remove(uuid);
	}

	public static void reset() {
		LAST_SCARE.clear();
		AFTERSHOCK.clear();
	}

	/**
	 * Fires a jumpscare at one player if the rules allow it.
	 *
	 * @param style 0 = breath then sting, 1 = hard sting, 2 = a word that stops
	 * @return true if it actually fired
	 */
	public static boolean fire(ServerLevel level, ServerPlayer player, int style) {
		MinecraftServer server = level.getServer();

		if (server == null || !allowed(level, player)) {
			return false;
		}

		// The early cue never shows the face. A face in the first hour is a party trick; a
		// whisper that stops mid word is a thing the player will think about at work.
		if (IntegrityState.get(server).phase().ordinal() < Phase.DELETION.ordinal()) {
			style = 2;
		}

		ModNetworking.sendEffect(player, ModNetworking.EFFECT_SCARE, Integer.toString(style), 9);

		long now = level.getGameTime();
		LAST_SCARE.put(player.getUUID(), now);
		AFTERSHOCK.put(player.getUUID(), now + AFTERSHOCK_TICKS);
		return true;
	}

	/** True when a scare would be allowed right now. */
	public static boolean allowed(ServerLevel level, ServerPlayer player) {
		IntegrityState state = IntegrityState.get(level.getServer());

		if (!dev.error404.Error404Config.get().enabled || state.ended() || !state.active()) {
			return false;
		}

		// Below DELETION there is no jumpscare, but there is something worse: a tick where the
		// mod does nothing at all. DESYNC gets the cheap version - the static burst and the
		// whisper that arrives half a second too late - so a player who never lets their world
		// rot still finds out, in the first hour, what they installed.
		if (state.phase().ordinal() < Phase.DELETION.ordinal()) {
			return state.phase().ordinal() >= Phase.DESYNC.ordinal() && cheapOk(level, player);
		}

		if (player.isCreative() || player.isSpectator() || player.isDeadOrDying()) {
			return false;
		}

		Difficulty difficulty = Difficulty.current();

		if (difficulty == Difficulty.CHILL) {
			return false;
		}

		// never in the opening minute: the player is still learning the rules
		if (state.ageTicks() < 20 * 60) {
			return false;
		}

		if (insideAnchor(state, player)) {
			return false;
		}

		long now = level.getGameTime();
		Long last = LAST_SCARE.get(player.getUUID());

		if (last != null && now - last < COOLDOWN_TICKS) {
			return false;
		}

		Long aftershock = AFTERSHOCK.get(player.getUUID());

		return aftershock == null || now > aftershock;
	}

	/** The restrained early-phase scare: no face, no flash, just the noise of the world slipping. */
	private static boolean cheapOk(ServerLevel level, ServerPlayer player) {
		long now = level.getGameTime();
		Long last = LAST_SCARE.get(player.getUUID());
		Long aftershock = AFTERSHOCK.get(player.getUUID());

		if (last != null || aftershock != null) {
			return false; // the early cue happens once per session, and only after the cooldown
		}

		return now > 0;
	}

	private static boolean insideAnchor(IntegrityState state, ServerPlayer player) {
		// the player's own dimension: a Nether anchor must not mute a scare in the overworld
		net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension =
				player.level().dimension();

		for (var anchor : state.anchorPositions(dimension)) {
			if (dev.error404.block.AnchorCoreBlock.protects(anchor, player.blockPosition())) {
				return true;
			}
		}

		return false;
	}

	/**
	 * 422 has been watched long enough that it turns to face the player. Style 0: the breath
	 * arrives first, so by the time the face does the player has already turned around.
	 */
	public static void onWatched(ServerLevel level, ServerPlayer player) {
		if (fire(level, player, 0)) {
			level.playSound(null, player.blockPosition(), ModSounds.WHISPER, SoundSource.HOSTILE, 1.4f, 0.6f);
		}
	}

	/** A chunk is about to go under the player. The face arrives with the countdown. */
	public static void onCollapseWarning(ServerLevel level, ServerPlayer player) {
		fire(level, player, 1);
	}

	/** The player dropped to a quarter health. Style 2: it is never the same twice. */
	public static void onNearDeath(ServerLevel level, ServerPlayer player) {
		fire(level, player, 2);
	}

	/** 422 reached the player in the final phase. */
	public static void onContact(ServerLevel level, ServerPlayer player) {
		fire(level, player, 1);
	}

	/** A structure appeared inside fifteen blocks. */
	public static void onStructure(ServerLevel level, ServerPlayer player) {
		fire(level, player, 0);
	}
}
