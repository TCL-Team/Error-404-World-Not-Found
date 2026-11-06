package dev.error404.anomaly.builders;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

import dev.error404.anomaly.Anomaly;
import dev.error404.anomaly.AnomalyType;
import dev.error404.integrity.IntegrityState;

/**
 * ARCHIVE - a stone corridor of signs. Every sign holds one line of the player's own
 * history: how long they have survived, how many chunks are gone, which error codes they
 * have triggered. No scares here - only the receipt.
 */
public class ArchiveAnomaly implements Anomaly {
	@Override
	public AnomalyType type() {
		return AnomalyType.ARCHIVE;
	}

	@Override
	public int footprint() {
		return 14;
	}

	@Override
	public void build(ServerLevel level, BlockPos centre, RandomSource random) {
		int ox = centre.getX();
		int oz = centre.getZ();
		int y = StructureKit.ground(level, ox, oz);

		IntegrityState state = IntegrityState.get(level.getServer());
		int seconds = (int) (state.ageTicks() / 20L);

		for (int i = 0; i < 14; i++) {
			int z = oz + i * 3;

			// floor + walls of a corridor
			for (int dz = 0; dz < 3; dz++) {
				for (int dx = -2; dx <= 2; dx++) {
					StructureKit.set(level, ox + dx, y - 1, z + dz, net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState());

					if (Math.abs(dx) == 2) {
						for (int h = 0; h < 4; h++) {
							StructureKit.set(level, ox + dx, y + h, z + dz, net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState());
						}
					}
				}
			}

			String[] lines = archiveLines(state, i, seconds);
			StructureKit.sign(level, ox + 1, y, z + 1, lines);
			StructureKit.sign(level, ox - 1, y, z + 1, lines[0], lines[1]);
		}

		StructureKit.chest(level, ox, y, oz + 42, net.minecraft.core.Direction.SOUTH, List.of(
				StructureKit.writtenBook("archive", List.of(
						"the world is being deleted,",
						"and it keeps a record.",
						"so do you."))
		));
	}

	private static String[] archiveLines(IntegrityState state, int index, int seconds) {
		return switch (index * 3 % 9) {
			case 0 -> new String[]{"ERR_404_H", "archive entry " + index, "integrity " + state.integrity() + "%", ""};
			case 1 -> new String[]{"uptime", seconds + " seconds", "", ""};
			case 2 -> new String[]{"deleted chunks", state.deletedChunks().size() + "", "", ""};
			case 3 -> new String[]{"phase", state.phase().name(), "", ""};
			case 4 -> new String[]{"fragments", state.dataFragments() + "/8", "", ""};
			case 5 -> new String[]{"error codes", String.join(",", state.foundAnomalies()), "", ""};
			case 6 -> new String[]{"session", Long.toHexString(state.sessionSeed()), "", ""};
			case 7 -> new String[]{"you are", "the last record", "", ""};
			default -> new String[]{"", "this sign was", "never written", ""};
		};
	}

	@Override
	public List<String> discoveryLines() {
		return List.of("ERR_404_H: archive opened - everything logged so far");
	}

	@Override
	public boolean canRevoke() {
		return false;
	}
}
