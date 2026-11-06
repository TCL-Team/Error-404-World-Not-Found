package dev.error404.anomaly.builders;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;

import dev.error404.anomaly.Anomaly;
import dev.error404.anomaly.AnomalyType;

/**
 * STAIRCASE_TO_NOWHERE - stone brick stairs that climb out of the world and end in air.
 * Standing on the last step plays footsteps behind you (handled by the entity, not here).
 */
public class StaircaseAnomaly implements Anomaly {
	@Override
	public AnomalyType type() {
		return AnomalyType.STAIRCASE_TO_NOWHERE;
	}

	@Override
	public int footprint() {
		return 10;
	}

	@Override
	public void build(ServerLevel level, BlockPos centre, RandomSource random) {
		int x = centre.getX();
		int z = centre.getZ();
		int y = StructureKit.ground(level, x, z);

		int height = 26 + random.nextInt(18);

		for (int i = 0; i < height; i++) {
			BlockPos step = new BlockPos(x, y + i, z + i);
			StructureKit.set(level, step.getX(), step.getY(), step.getZ(),
					Blocks.STONE_BRICKS.defaultBlockState());
			StructureKit.set(level, step.getX(), step.getY(), step.getZ() - 1,
					Blocks.STONE_BRICKS.defaultBlockState());
		}

		// the last step is a stair facing nothing
		BlockPos end = new BlockPos(x, y + height, z + height);
		StructureKit.set(level, end.getX(), end.getY(), end.getZ(), Blocks.STONE_BRICK_STAIRS.defaultBlockState()
				.setValue(StairBlock.FACING, net.minecraft.core.Direction.NORTH)
				.setValue(StairBlock.HALF, Half.BOTTOM)
				.setValue(StairBlock.SHAPE, StairsShape.STRAIGHT));

		StructureKit.sign(level, end.getX(), end.getY() + 1, end.getZ(),
				"ERR_404_B", "climb", "there is nothing", "up here");
	}

	@Override
	public List<String> discoveryLines() {
		return List.of("ERR_404_B: vertical chunk data truncated");
	}
}
