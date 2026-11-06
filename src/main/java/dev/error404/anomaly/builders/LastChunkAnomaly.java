package dev.error404.anomaly.builders;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;

import dev.error404.ModBlocks;
import dev.error404.anomaly.Anomaly;
import dev.error404.anomaly.AnomalyType;

/**
 * THE_LAST_CHUNK - one island of intact grass in the middle of nothing, with a stone slab
 * exactly in the centre. This is where the SALVAGE ending happens: place an Anchor Core on
 * the slab and hold the world together.
 */
public class LastChunkAnomaly implements Anomaly {
	public static final int RADIUS = 6;

	@Override
	public AnomalyType type() {
		return AnomalyType.THE_LAST_CHUNK;
	}

	@Override
	public int footprint() {
		return 10;
	}

	@Override
	public void build(ServerLevel level, BlockPos centre, RandomSource random) {
		int ox = centre.getX();
		int oz = centre.getZ();
		int y = StructureKit.ground(level, ox, oz);

		// carve the island out of the void
		for (int dx = -RADIUS - 3; dx <= RADIUS + 3; dx++) {
			for (int dz = -RADIUS - 3; dz <= RADIUS + 3; dz++) {
				double dist = Math.sqrt(dx * dx + dz * dz);

				for (int dy = -6; dy <= 0; dy++) {
					BlockPos pos = new BlockPos(ox + dx, y + dy, oz + dz);

					if (dist <= RADIUS) {
						level.setBlock(pos, dy == 0 ? Blocks.GRASS_BLOCK.defaultBlockState()
								: (dy > -3 ? Blocks.DIRT.defaultBlockState() : Blocks.STONE.defaultBlockState()), 3);
					} else {
						level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
					}
				}

				if (dist > RADIUS - 1.5 && dist <= RADIUS) {
					level.setBlock(new BlockPos(ox + dx, y - 4, oz + dz), ModBlocks.VOID_STONE.defaultBlockState(), 3);
				}
			}
		}

		// a single dead tree and the slab
		for (int i = 0; i < 4; i++) {
			StructureKit.set(level, ox + 3, y + 1 + i, oz - 2, Blocks.OAK_LOG.defaultBlockState());
		}

		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (Math.abs(dx) + Math.abs(dz) <= 3) {
					StructureKit.set(level, ox + 3 + dx, y + 5, oz - 2 + dz, Blocks.OAK_LEAVES.defaultBlockState());
				}
			}
		}

		StructureKit.set(level, ox, y + 1, oz, Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
		StructureKit.sign(level, ox + 1, y + 1, oz + 1,
				"ERR_404_K", "the last chunk", "place the anchor", "and hold");
	}

	@Override
	public List<String> discoveryLines() {
		return List.of("ERR_404_K: last intact region found", "everything else has already been processed");
	}

	@Override
	public boolean canRevoke() {
		return false;
	}
}
