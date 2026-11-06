package dev.error404.anomaly.builders;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;

import dev.error404.anomaly.Anomaly;
import dev.error404.anomaly.AnomalyType;

/**
 * INVERTED_TREE - an oak growing downwards. Trunk starts under the grass, canopy hangs
 * deep underground, roots reach up into the sky. Digging is rewarded, but 422 notices.
 */
public class InvertedTreeAnomaly implements Anomaly {
	private static final int DEPTH = 22;

	@Override
	public AnomalyType type() {
		return AnomalyType.INVERTED_TREE;
	}

	@Override
	public int footprint() {
		return 10;
	}

	@Override
	public void build(ServerLevel level, BlockPos centre, RandomSource random) {
		int x = centre.getX();
		int z = centre.getZ();
		int surface = StructureKit.ground(level, x, z);

		// trunk going down
		for (int i = 0; i < DEPTH; i++) {
			StructureKit.set(level, x, surface - i, z, Blocks.OAK_LOG.defaultBlockState());
		}

		// canopy at the bottom
		int leafY = surface - DEPTH;
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				for (int dy = 0; dy <= 2; dy++) {
					double d = Math.sqrt(dx * dx + dz * dz + (dy - 1) * (dy - 1) * 1.6);

					if (d <= 3.2 && random.nextInt(5) != 0) {
						StructureKit.set(level, x + dx, leafY + dy, z + dz, Blocks.OAK_LEAVES.defaultBlockState());
					}
				}
			}
		}

		// roots reaching up
		for (int i = 1; i <= 3; i++) {
			StructureKit.set(level, x + i, surface + i, z, Blocks.OAK_LOG.defaultBlockState());
			StructureKit.set(level, x - i, surface + i, z + i, Blocks.OAK_LOG.defaultBlockState());
			StructureKit.set(level, x, surface + i, z - i, Blocks.OAK_LOG.defaultBlockState());
		}

		StructureKit.chest(level, x, leafY, z, net.minecraft.core.Direction.NORTH, List.of(
				StructureKit.fragment(),
				StructureKit.shard(),
				StructureKit.writtenBook("ped", List.of(
						"the tree is upside down.",
						"or am i?"
				))
		));

		StructureKit.sign(level, x + 5, surface + 1, z, "ERR_404_I", "gravity: undefined", "below grass", "");
	}

	@Override
	public List<String> discoveryLines() {
		return List.of("ERR_404_I: gravity applied to the wrong axis");
	}
}
