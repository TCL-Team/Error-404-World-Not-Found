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
 * THE_SINKHOLE - a perfect circle cut down to bedrock. The rim is glass-smooth, and the
 * walls are not stone: they are void stone, so the hole makes no sound at all.
 */
public class SinkholeAnomaly implements Anomaly {
	private static final int RADIUS = 7;

	@Override
	public AnomalyType type() {
		return AnomalyType.THE_SINKHOLE;
	}

	@Override
	public int footprint() {
		return 12;
	}

	@Override
	public void build(ServerLevel level, BlockPos centre, RandomSource random) {
		int ox = centre.getX();
		int oz = centre.getZ();
		int surface = StructureKit.ground(level, ox, oz);
		int bottom = level.getMinY() + 2;

		for (int dx = -RADIUS; dx <= RADIUS; dx++) {
			for (int dz = -RADIUS; dz <= RADIUS; dz++) {
				double dist = Math.sqrt(dx * dx + dz * dz);

				if (dist > RADIUS) {
					continue;
				}

				for (int y = surface + 2; y > bottom; y--) {
					BlockPos pos = new BlockPos(ox + dx, y, oz + dz);
					level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
				}

				if (dist > RADIUS - 1.2) {
					for (int y = bottom; y <= surface + 1; y++) {
						level.setBlock(new BlockPos(ox + dx, y, oz + dz),
								dev.error404.ModBlocks.VOID_STONE.defaultBlockState(), 3);
					}
				}
			}
		}

		// a floor nobody wants to reach
		for (int dx = -RADIUS; dx <= RADIUS; dx++) {
			for (int dz = -RADIUS; dz <= RADIUS; dz++) {
				if (dx * dx + dz * dz <= RADIUS * RADIUS) {
					level.setBlock(new BlockPos(ox + dx, bottom, oz + dz), Blocks.BLACKSTONE.defaultBlockState(), 3);
				}
			}
		}

		StructureKit.chest(level, ox, bottom + 1, oz, Direction.SOUTH, List.of(
				StructureKit.shard(),
				StructureKit.shard(),
				StructureKit.writtenBook("the pit", List.of(
						"this pit is perfect.",
						"nothing natural is perfect.",
						"so something made it.",
						"and it left something down there."))
		));

		StructureKit.sign(level, ox + RADIUS + 1, surface + 1, oz, "ERR_404_G", "perfect circle", "no debris", "");
	}

	@Override
	public List<String> discoveryLines() {
		return List.of("ERR_404_G: impossible terrain - no debris field");
	}
}
