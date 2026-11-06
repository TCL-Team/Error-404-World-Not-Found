package dev.error404.anomaly.builders;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import dev.error404.anomaly.Anomaly;
import dev.error404.anomaly.AnomalyType;

/**
 * NULL_HUT - a house with two walls, no roof, and a bed someone was sleeping in.
 * The other half of the house is gone, cleanly, as if it had never been written.
 */
public class HalfHouseAnomaly implements Anomaly {
	@Override
	public AnomalyType type() {
		return AnomalyType.NULL_HUT;
	}

	@Override
	public int footprint() {
		return 8;
	}

	@Override
	public void build(ServerLevel level, BlockPos centre, RandomSource random) {
		int ox = centre.getX();
		int oz = centre.getZ();
		int y = StructureKit.ground(level, ox, oz);
		StructureKit.clearArea(level, ox, y + 1, oz, 7, 6, 1);

		BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
		BlockState log = Blocks.OAK_LOG.defaultBlockState();

		// floor and two walls only
		for (int x = -3; x <= 3; x++) {
			for (int z = -3; z <= 3; z++) {
				StructureKit.set(level, ox + x, y - 1, oz + z, Blocks.OAK_PLANKS.defaultBlockState());
			}
		}

		for (int yOff = 0; yOff < 3; yOff++) {
			for (int x = -3; x <= 3; x++) {
				StructureKit.set(level, ox + x, y + yOff, oz - 3, planks);
			}

			for (int z = -3; z <= 3; z++) {
				StructureKit.set(level, ox - 3, y + yOff, oz + z, planks);
			}
		}

		for (int h = 0; h < 4; h++) {
			StructureKit.set(level, ox - 3, y + h, oz - 3, log);
			StructureKit.set(level, ox + 3, y + h, oz - 3, log);
		}

		// a window frame with nothing behind it
		StructureKit.set(level, ox, y + 1, oz - 3, Blocks.GLASS_PANE.defaultBlockState());
		StructureKit.set(level, ox, y + 2, oz - 3, Blocks.GLASS_PANE.defaultBlockState());

		// the bed is always made, and always occupied-looking
		StructureKit.bed(level, ox + 1, y, oz + 1, Direction.NORTH);

		StructureKit.chest(level, ox - 2, y, oz + 2, Direction.SOUTH, List.of(
				StructureKit.writtenBook("deewar", List.of(
						"maine teen deewarein banayi thi.",
						"ab do hain.",
						"main dekh raha tha. koi aaya nahi.",
						"lekin ek gayab ho gayi.")),
				StructureKit.fragment()
		));

		StructureKit.sign(level, ox + 2, y + 1, oz - 3, "#404", "half already", "was never here", "");
	}

	@Override
	public List<String> discoveryLines() {
		return List.of("ERR_404_A: structure failed to complete", "the missing half was never written");
	}
}
