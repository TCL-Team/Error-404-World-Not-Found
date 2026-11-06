package dev.error404.anomaly.builders;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;

import dev.error404.ModBlocks;
import dev.error404.ModSounds;
import dev.error404.anomaly.Anomaly;
import dev.error404.anomaly.AnomalyType;

/**
 * ERR_404_X - THE HEARTBEAT VAULT.
 *
 * <p>A sealed room with no door that was not built by anything: a 9x9 obsidian shell, every
 * block of the inner floor a pressure plate, and in the middle a single block of void stone
 * with somebody's heartbeat coming out of it. The plates are wired to nothing, because there
 * is nothing left to trigger.</p>
 *
 * <p>It is the one anomaly that is deliberately <em>quiet</em> - no particles, no reveal sound
 * beyond the standard one - because the fear here is the reading: a room shaped like an
 * argument that ended, with a heartbeat in it, and no save data explaining who built it.</p>
 */
public class HeartbeatVaultAnomaly implements Anomaly {
	private static final int HALF = 4;     // 9x9 interior
	private static final int HEIGHT = 5;

	@Override
	public AnomalyType type() {
		return AnomalyType.HEARTBEAT_VAULT;
	}

	@Override
	public int footprint() {
		return 9;
	}

	@Override
	public void build(ServerLevel level, BlockPos centre, RandomSource random) {
		int ox = centre.getX();
		int oz = centre.getZ();
		int surface = StructureKit.ground(level, ox, oz);
		int floor = surface - 1;

		// the shell: obsidian walls, floor and ceiling, absolutely no opening
		for (int dx = -HALF - 1; dx <= HALF + 1; dx++) {
			for (int dz = -HALF - 1; dz <= HALF + 1; dz++) {
				boolean wall = Math.abs(dx) == HALF + 1 || Math.abs(dz) == HALF + 1;

				for (int dy = 0; dy <= HEIGHT + 1; dy++) {
					BlockPos pos = new BlockPos(ox + dx, floor + dy, oz + dz);

					if (wall || dy == 0 || dy == HEIGHT + 1) {
						StructureKit.set(level, pos.getX(), pos.getY(), pos.getZ(),
								Blocks.OBSIDIAN.defaultBlockState());
					} else {
						StructureKit.set(level, pos.getX(), pos.getY(), pos.getZ(),
								Blocks.AIR.defaultBlockState());
					}
				}
			}
		}

		// every interior floor block is a plate wired to nothing
		for (int dx = -HALF; dx <= HALF; dx++) {
			for (int dz = -HALF; dz <= HALF; dz++) {
				StructureKit.set(level, ox + dx, floor + 1, oz + dz,
						Blocks.STONE_PRESSURE_PLATE.defaultBlockState());
			}
		}

		// the thing in the middle, and the only light in the room
		StructureKit.set(level, ox, floor + 1, oz, ModBlocks.VOID_STONE.defaultBlockState());
		StructureKit.set(level, ox, floor + 2, oz, Blocks.OAK_SIGN.defaultBlockState()
				.setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,
						Direction.NORTH));

		if (level.getBlockEntity(new BlockPos(ox, floor + 2, oz))
				instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign) {
			var text = new net.minecraft.world.level.block.entity.SignText();

			String[] lines = {"ERR_404_X", "it is not", "a heart", "it is a timer"};
			for (int i = 0; i < 4; i++) {
				text = text.setMessage(i, net.minecraft.network.chat.Component.literal(lines[i]));
			}

			sign.setText(text, true);
		}

		// the loot you have to break a wall to reach: a fragment, a shard, and somebody's log
		StructureKit.chest(level, ox + 2, floor + 1, oz - 2, Direction.SOUTH, List.of(
				StructureKit.fragment(),
				StructureKit.shard(),
				StructureKit.writtenBook("the vault", List.of(
						"i built this with my hands and",
						"nobody helped me.",
						"the heartbeat was already inside",
						"when i closed the last wall.",
						"",
						"i did not open it again.",
						"i am writing this from outside."))
		));

		level.playSound(null, centre, ModSounds.HEARTBEAT, SoundSource.AMBIENT, 1.0f, 0.6f);
	}

	@Override
	public void tick(ServerLevel level, BlockPos centre, RandomSource random, int ageTicks) {
		// a slow, wrong heartbeat from inside a room nobody can get into
		if (ageTicks % 80 == 0) {
			level.playSound(null, centre, ModSounds.HEARTBEAT, SoundSource.AMBIENT, 0.8f, 0.55f);
		}

		// and once a minute the room acknowledges that you are reading it
		if (ageTicks % 1200 == 600) {
			for (var player : level.players()) {
				if (player.blockPosition().distSqr(centre) < 40 * 40) {
					player.displayClientMessage(
							net.minecraft.network.chat.Component.literal("the plates are not wired")
									.withStyle(net.minecraft.ChatFormatting.DARK_GRAY), true);
				}
			}
		}
	}

	@Override
	public List<String> discoveryLines() {
		return List.of(
				"ERR_404_X: sealed room, no entrance",
				"ERR_404_X: one heartbeat, source not in world data");
	}
}
