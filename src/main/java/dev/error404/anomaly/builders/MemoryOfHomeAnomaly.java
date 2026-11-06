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
 * MEMORY_OF_HOME - a copy of a small house: same shape a player would build, same chest
 * layout, but every door opens inward, the windows look at solid stone, and the bed is
 * already warm. It exists to make the player doubt their own base.
 */
public class MemoryOfHomeAnomaly implements Anomaly {
	@Override
	public AnomalyType type() {
		return AnomalyType.MEMORY_OF_HOME;
	}

	@Override
	public int footprint() {
		return 12;
	}

	@Override
	public void build(ServerLevel level, BlockPos centre, RandomSource random) {
		int ox = centre.getX();
		int oz = centre.getZ();
		int y = StructureKit.ground(level, ox, oz);
		StructureKit.clearArea(level, ox, y + 2, oz, 9, 8, 2);

		int w = 4;
		int d = 3;
		int h = 4;

		for (int dx = -w; dx <= w; dx++) {
			for (int dz = -d; dz <= d; dz++) {
				StructureKit.set(level, ox + dx, y - 1, oz + dz, Blocks.OAK_PLANKS.defaultBlockState());

				for (int dy = 0; dy < h; dy++) {
					boolean wall = Math.abs(dx) == w || Math.abs(dz) == d;

					if (wall) {
						StructureKit.set(level, ox + dx, y + dy, oz + dz, Blocks.SPRUCE_PLANKS.defaultBlockState());
					}
				}
			}
		}

		// roof
		for (int dx = -w - 1; dx <= w + 1; dx++) {
			for (int dz = -d - 1; dz <= d + 1; dz++) {
				StructureKit.set(level, ox + dx, y + h, oz + dz, Blocks.SPRUCE_SLAB.defaultBlockState());
			}
		}

		// windows that look at stone
		for (int dx = -1; dx <= 1; dx++) {
			StructureKit.set(level, ox + dx, y + 2, oz - d, Blocks.GLASS_PANE.defaultBlockState());
		}

		for (int dx = -1; dx <= 1; dx++) {
			StructureKit.set(level, ox + dx, y + 2, oz - d - 1, Blocks.STONE.defaultBlockState());
		}

		// the door opens inward; a second door inside opens into a wall
		StructureKit.door(level, ox, y, oz + d, Direction.SOUTH, true);
		StructureKit.door(level, ox + 2, y, oz, Direction.NORTH, false);

		StructureKit.bed(level, ox - 2, y, oz - 1, Direction.NORTH);
		StructureKit.chest(level, ox + 2, y, oz - 2, Direction.SOUTH, List.of(
				StructureKit.fragment(),
				StructureKit.writtenBook("ghar", List.of(
						"ye tumhara ghar hai.",
						"main jaanta hoon.",
						"maine ise yaad rakha.",
						"lekin tum kahan the?")),
				new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD, 3)
		));

		StructureKit.sign(level, ox - 2, y + 1, oz + d, "ERR_404_J", "memory_of_home", "modified at load", "");
	}

	@Override
	public void tick(ServerLevel level, BlockPos centre, RandomSource random, int ageTicks) {
		// Occasionally a chest in here re-arranges itself when nobody is looking inside.
		if (ageTicks % 600 == 0) {
			if (level.getBlockEntity(centre.offset(2, 0, -2)) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest) {
				int slot = random.nextInt(chest.getContainerSize());

				if (!chest.getItem(slot).isEmpty() && random.nextInt(3) == 0) {
					chest.setItem(slot, net.minecraft.world.item.ItemStack.EMPTY);
					level.playSound(null, centre, dev.error404.ModSounds.STATIC_BURST, net.minecraft.sounds.SoundSource.BLOCKS, 0.4f, 1.2f);
				}
			}
		}
	}

	@Override
	public List<String> discoveryLines() {
		return List.of("ERR_404_J: structure matched your build history", "it was not placed by you");
	}

	@Override
	public boolean canRevoke() {
		return false;
	}
}
