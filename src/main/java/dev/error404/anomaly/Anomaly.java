package dev.error404.anomaly;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

/**
 * A structure the world builds when it stops making sense.
 *
 * <p>Builders run on the server thread but are written to be cheap: a few thousand
 * {@code setBlock} calls at most, all with {@code UPDATE_CLIENTS}. They must be
 * self-contained (no worldgen, no loot tables) so they can be placed anywhere, anytime.</p>
 */
public interface Anomaly {
	AnomalyType type();

	/** Radius of the area the builder wants to itself. */
	int footprint();

	/** Places the structure; the position is the centre of the footprint. */
	void build(ServerLevel level, BlockPos centre, RandomSource random);

	/**
	 * Called on a slow timer while the player is nearby. This is where anomalies get to be
	 * weird: blocks vanishing, chests appearing, villagers turning to face you.
	 */
	default void tick(ServerLevel level, BlockPos centre, RandomSource random, int ageTicks) {
	}

	/** Chat lines shown the first time a player gets close. */
	default List<String> discoveryLines() {
		return List.of();
	}

	/** Should this structure erase itself block by block? */
	default boolean canRevoke() {
		return true;
	}
}
