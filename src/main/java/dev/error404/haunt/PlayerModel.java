package dev.error404.haunt;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * A tiny model of how the player behaves, learned in-session.
 *
 * <p>ERROR 422's entity is scary because it feels like it knows you. It cannot actually know
 * you - but it can watch four things and act on them, and that reads as knowledge:</p>
 *
 * <ul>
 *   <li><b>stillness</b> - how long you stay in one place (it likes arriving then),</li>
 *   <li><b>depth</b> - whether you hide underground (it prefers to meet you at the surface),</li>
 *   <li><b>door use</b> - whether you close doors behind you (it opens them, or does not bother),</li>
 *   <li><b>light</b> - whether you carry a torch (it kills light, so it likes the dark).</li>
 * </ul>
 *
 * <p>Only three counters per player, all in memory, nothing persisted, nothing that survives
 * a relog. Its whole point is that the behaviour change is noticeable within one session.</p>
 */
public final class PlayerModel {
	public static final class Habit {
		public long longestStillTicks;
		public long currentStillTicks;
		public Vec3 lastPos;
		public int surfaceReturns;
		public int undergroundTicks;
		public int torchSightings;
		public int interactions;

		public boolean hidesUnderground() {
			return undergroundTicks > 20 * 60 * 2;
		}

		public boolean carriesLight() {
			return torchSightings > 3;
		}

		public boolean staysStill() {
			return longestStillTicks > 20 * 10;
		}
	}

	private static final Map<UUID, Habit> HABITS = new HashMap<>();

	private PlayerModel() {
	}

	public static Habit of(ServerPlayer player) {
		return HABITS.computeIfAbsent(player.getUUID(), uuid -> new Habit());
	}

	/** Called once a second per player. */
	public static void observe(ServerPlayer player) {
		Habit habit = of(player);
		Vec3 pos = player.position();

		if (habit.lastPos != null && habit.lastPos.distanceToSqr(pos) < 0.25) {
			habit.currentStillTicks++;
			habit.longestStillTicks = Math.max(habit.longestStillTicks, habit.currentStillTicks);
		} else {
			habit.currentStillTicks = 0;
		}

		habit.lastPos = pos;

		if (player.getY() < 45.0) {
			habit.undergroundTicks++;
		}

		if (player.getMainHandItem().is(net.minecraft.world.item.Items.TORCH)
				|| player.getMainHandItem().is(net.minecraft.world.item.Items.LANTERN)) {
			habit.torchSightings++;
		}
	}

	/** Where it should appear for this player, given what it has learned about them. */
	public static BlockPos preferredAmbushSpot(ServerPlayer player) {
		Habit habit = of(player);
		BlockPos base = player.blockPosition();

		// A player who hides underground gets met at the surface when they come up.
		if (habit.hidesUnderground() && player.getY() < 50.0) {
			base = player.blockPosition().above(24);
		}

		// A player who stands still gets approached from directly behind, not from the side.
		if (habit.staysStill()) {
			Vec3 back = player.position().subtract(player.getViewVector(1.0f).scale(14.0));
			return BlockPos.containing(back.x, back.y, back.z);
		}

		return base;
	}

	public static void clear() {
		HABITS.clear();
	}

	/** Disconnect cleanup: habits are rebuilt in a couple of minutes and must not leak. */
	public static void forget(UUID uuid) {
		HABITS.remove(uuid);
	}
}
