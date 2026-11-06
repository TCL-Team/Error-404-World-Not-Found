package dev.error404.anomaly.builders;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;

import dev.error404.anomaly.Anomaly;
import dev.error404.anomaly.AnomalyType;

/**
 * CHUNK_SEAM - two biomes stitched together in the same chunk. Snow on the left, sand on
 * the right, and a line of raw stone down the middle where the seam was made.
 */
public class ChunkSeamAnomaly implements Anomaly {
	@Override
	public AnomalyType type() {
		return AnomalyType.CHUNK_SEAM;
	}

	@Override
	public int footprint() {
		return 14;
	}

	@Override
	public void build(ServerLevel level, BlockPos centre, RandomSource random) {
		int ox = centre.getX();
		int oz = centre.getZ();
		int radius = 12;

		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				int x = ox + dx;
				int z = oz + dz;
				int y = StructureKit.ground(level, x, z) - 1;
				boolean cold = dx < 0;

				BlockPos surface = new BlockPos(x, y, z);
				StructureKit.set(level, x, y, z, (cold ? Blocks.SNOW_BLOCK : Blocks.SAND).defaultBlockState());

				if (Math.abs(dx) <= 1) {
					StructureKit.set(level, x, y, z, Blocks.DEAD_BUSH.defaultBlockState());
					StructureKit.set(level, x, y - 1, z, Blocks.GRAVEL.defaultBlockState());
				} else if (Math.abs(dz) % 5 == 0) {
					StructureKit.set(level, x, y + 1, z, (cold ? Blocks.SNOW.defaultBlockState()
							: Blocks.CACTUS.defaultBlockState()));
				} else if (Math.abs(dx) > 4 && Math.abs(dz) > 4 && (Math.abs(dx) + Math.abs(dz)) % 7 == 0) {
					StructureKit.set(level, x, y - 1, z, Blocks.STONE.defaultBlockState());
				}
			}
		}

		// the seam itself: a wall of raw blocks exactly one block wide
		for (int dz = -radius; dz <= radius; dz++) {
			int y = StructureKit.ground(level, ox, oz + dz);
			StructureKit.set(level, ox, y, oz + dz, Blocks.CRYING_OBSIDIAN.defaultBlockState());
		}

		StructureKit.chest(level, ox + 3, StructureKit.ground(level, ox + 3, oz) + 1, oz, Direction.WEST, List.of(
				StructureKit.shard(),
				StructureKit.shard(),
				StructureKit.writtenBook("seam", List.of(
						"two worlds were sewn into one chunk.",
						"neither of them belongs here."))
		));

		StructureKit.sign(level, ox, StructureKit.ground(level, ox, oz + radius + 1) + 1, oz + radius + 1,
				"ERR_404_C", "biome mismatch", "stitched at runtime", "");
	}

	@Override
	public List<String> discoveryLines() {
		return List.of("ERR_404_C: biome mismatch - two regions stitched");
	}
}
