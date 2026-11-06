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
 * DOOR_TO_ROOM - a single oak door standing in open ground. Behind it: a 5x5 stone room
 * with no exit and no light, holding one chest and one very patient sound.
 */
public class DoorToRoomAnomaly implements Anomaly {
	@Override
	public AnomalyType type() {
		return AnomalyType.DOOR_TO_ROOM;
	}

	@Override
	public int footprint() {
		return 9;
	}

	@Override
	public void build(ServerLevel level, BlockPos centre, RandomSource random) {
		int ox = centre.getX();
		int oz = centre.getZ();
		int y = StructureKit.ground(level, ox, oz);
		StructureKit.clearArea(level, ox, y + 2, oz + 3, 5, 8, 2);

		// the room sits behind the door, carved into nothing
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = 0; dz <= 4; dz++) {
				for (int dy = -1; dy <= 3; dy++) {
					boolean shell = Math.abs(dx) == 2 || dz == 0 || dz == 4 || dy == -1 || dy == 3;
					BlockPos pos = new BlockPos(ox + dx, y + dy, oz + dz);

					if (shell) {
						level.setBlock(pos, Blocks.STONE_BRICKS.defaultBlockState(), 3);
					} else {
						level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
					}
				}
			}
		}

		// carve the doorway
		StructureKit.door(level, ox, y, oz, Direction.SOUTH, false);
		level.setBlock(new BlockPos(ox, y + 2, oz), Blocks.STONE_BRICKS.defaultBlockState(), 3);

		StructureKit.chest(level, ox, y, oz + 3, Direction.NORTH, List.of(
				StructureKit.fragment(),
				StructureKit.shard(),
				StructureKit.writtenBook("kamra", List.of(
						"kamre ka koi exit nahi hai.",
						"lekin main andar aa gaya.",
						"matlab exit tha.",
						"ab nahi hai."))
		));

		StructureKit.sign(level, ox + 3, y + 1, oz + 2, "ERR_404_F", "room without", "a door inside it", "");
	}

	@Override
	public void tick(ServerLevel level, BlockPos centre, RandomSource random, int ageTicks) {
		if (ageTicks % 200 == 0) {
			level.playSound(null, centre, dev.error404.ModSounds.WHISPER, net.minecraft.sounds.SoundSource.AMBIENT, 0.4f, 0.7f);
		}
	}

	@Override
	public List<String> discoveryLines() {
		return List.of("ERR_404_F: interior volume exceeds exterior");
	}
}
