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
import dev.error404.deletion.DeletionFront;

/**
 * LOADBAR_WALL - a 40 block wide concrete wall with a black progress bar in it.
 * Every tick the bar fills a little. When it reaches 100% the world deletes a chunk.
 */
public class LoadBarWallAnomaly implements Anomaly {
	public static final int WIDTH = 40;
	public static final int HEIGHT = 14;
	private static final int BAR_LENGTH = 32;

	@Override
	public AnomalyType type() {
		return AnomalyType.LOADBAR_WALL;
	}

	@Override
	public int footprint() {
		return 24;
	}

	@Override
	public void build(ServerLevel level, BlockPos centre, RandomSource random) {
		int ox = centre.getX();
		int oz = centre.getZ();
		int y = StructureKit.ground(level, ox, oz);
		StructureKit.clearArea(level, ox, y + 2, oz, 22, 20, 1);

		BlockState concrete = Blocks.WHITE_CONCRETE.defaultBlockState();
		BlockState black = Blocks.BLACK_CONCRETE.defaultBlockState();

		for (int dx = -WIDTH / 2; dx <= WIDTH / 2; dx++) {
			int x = ox + dx;

			for (int dy = 0; dy < HEIGHT; dy++) {
				StructureKit.set(level, x, y + dy, oz, concrete);
				StructureKit.set(level, x, y + dy, oz - 1, concrete);
			}
		}

		// the bar frame
		for (int dx = -BAR_LENGTH / 2 - 1; dx <= BAR_LENGTH / 2 + 1; dx++) {
			StructureKit.set(level, ox + dx, y + 6, oz - 2, black);
			StructureKit.set(level, ox + dx, y + 8, oz - 2, black);
		}

		for (int dx = -BAR_LENGTH / 2; dx <= BAR_LENGTH / 2; dx++) {
			StructureKit.set(level, ox + dx, y + 7, oz - 2, black);
		}

		StructureKit.sign(level, ox - 10, y + 10, oz - 2, "DELETION", "progress bar", "do not wait", "");
		StructureKit.chest(level, ox + 12, y, oz - 3, Direction.SOUTH, List.of(
				StructureKit.fragment(),
				StructureKit.shard()
		));
	}

	@Override
	public void tick(ServerLevel level, BlockPos centre, RandomSource random, int ageTicks) {
		// Progress is a pure function of age, so it is the same for every player and
		// survives a reload without any extra state.
		int progress = (ageTicks / 20) % 100;
		int filled = (int) ((progress / 100.0) * BAR_LENGTH);
		int y = StructureKit.ground(level, centre.getX(), centre.getZ());
		int oz = centre.getZ() - 2;

		for (int dx = -BAR_LENGTH / 2; dx <= BAR_LENGTH / 2; dx++) {
			boolean on = dx + BAR_LENGTH / 2 < filled;
			BlockPos pos = new BlockPos(centre.getX() + dx, y + 7, oz);
			BlockState wanted = on ? Blocks.REDSTONE_BLOCK.defaultBlockState() : Blocks.BLACK_CONCRETE.defaultBlockState();

			if (!level.getBlockState(pos).is(wanted.getBlock())) {
				level.setBlock(pos, wanted, 3);
			}
		}

		if (progress == 99) {
			DeletionFront.tick(level.getServer());
		}
	}

	@Override
	public List<String> discoveryLines() {
		return List.of("ERR_404_E: deletion progress bar located", "when it fills, the region goes");
	}

	@Override
	public boolean canRevoke() {
		return false;
	}
}
