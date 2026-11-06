package dev.error404.haunt;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Remembers where every player has been, so 422 can walk it back.
 *
 * <p>This is the one thing in the mod that is genuinely unsettling in playtesting: from
 * CASCADE onwards, 422 does not pathfind towards you - it replays your own route from sixty
 * seconds ago, block for block, at walking speed. If you doubled back, it doubles back. If
 * you stopped to look at something, it stops there too.</p>
 *
 * <p>Bounded and cheap: one sample per second, sixty samples per player, so at most a few
 * kilobytes and one {@code addLast} per second per player.</p>
 */
public final class PathMemory {
	private static final int SAMPLES = 60;
	/**
	 * Dimension-aware on purpose: without this a 422 in the Nether would replay overworld
	 * coordinates and either fall into the void or teleport across the world.
	 */
	private static final Map<String, Deque<Vec3>> PATHS = new HashMap<>();

	private static String key(ServerPlayer player) {
		return player.level().dimension().location() + "/" + player.getUUID();
	}

	private PathMemory() {
	}

	public static void sample(ServerLevel level) {
		for (ServerPlayer player : level.players()) {
			Deque<Vec3> path = PATHS.computeIfAbsent(key(player), k -> new ArrayDeque<>(SAMPLES));

			if (path.size() >= SAMPLES) {
				path.pollFirst();
			}

			path.addLast(player.position());
		}
	}

	/** The oldest remembered position: exactly one minute behind the player. */
	public static Vec3 oldest(ServerPlayer player) {
		Deque<Vec3> path = PATHS.get(key(player));
		return path == null || path.isEmpty() ? null : path.peekFirst();
	}

	/** Where the player was {@code secondsAgo} seconds ago, if we remember that far. */
	public static Vec3 at(ServerPlayer player, int secondsAgo) {
		Deque<Vec3> path = PATHS.get(key(player));

		if (path == null || path.isEmpty()) {
			return null;
		}

		int index = Math.max(0, path.size() - 1 - secondsAgo);
		int i = 0;

		for (Vec3 position : path) {
			if (i++ == index) {
				return position;
			}
		}

		return null;
	}

	/**
	 * Where the player is heading, extrapolated from their current velocity. Used by the
	 * "approach" behaviour so it does not walk to where you were, but to where you will be.
	 */
	public static BlockPos predict(ServerPlayer player) {
		Vec3 velocity = player.getDeltaMovement();
		Vec3 target = player.position().add(velocity.scale(40.0)); // ~2 seconds ahead
		return BlockPos.containing(target.x, player.getY(), target.z);
	}

	public static void clear() {
		PATHS.clear();
	}

	public static void forget(UUID uuid) {
		PATHS.remove(uuid);
	}
}
