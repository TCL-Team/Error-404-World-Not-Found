package dev.error404.anomaly.builders;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;

import dev.error404.ModBlocks;
import dev.error404.ModItems;

/**
 * Small helpers shared by the anomaly builders. Everything here is deliberately vanilla:
 * the horror comes from placement and context, not from exotic blocks.
 */
public final class StructureKit {
	private StructureKit() {
	}

	public static void set(ServerLevel level, int x, int y, int z, BlockState state) {
		level.setBlock(new BlockPos(x, y, z), state, 3);
	}

	public static int ground(ServerLevel level, int x, int z) {
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
	}

	public static void clearArea(ServerLevel level, int cx, int cy, int cz, int radiusXZ, int up, int down) {
		for (int x = cx - radiusXZ; x <= cx + radiusXZ; x++) {
			for (int z = cz - radiusXZ; z <= cz + radiusXZ; z++) {
				for (int y = cy - down; y <= cy + up; y++) {
					BlockState state = level.getBlockState(new BlockPos(x, y, z));

					if (!state.isAir() && state.getFluidState().isEmpty() && !state.is(ModBlocks.ANCHOR_CORE)) {
						set(level, x, y, z, Blocks.AIR.defaultBlockState());
					}
				}
			}
		}
	}

	public static ChestBlockEntity chest(ServerLevel level, int x, int y, int z, Direction facing, List<ItemStack> loot) {
		set(level, x, y, z, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing));

		if (level.getBlockEntity(new BlockPos(x, y, z)) instanceof ChestBlockEntity chest) {
			for (int i = 0; i < loot.size() && i < chest.getContainerSize(); i++) {
				chest.setItem(i, loot.get(i));
			}

			return chest;
		}

		return null;
	}

	public static void sign(ServerLevel level, int x, int y, int z, String... lines) {
		set(level, x, y, z, Blocks.OAK_SIGN.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));

		if (level.getBlockEntity(new BlockPos(x, y, z)) instanceof SignBlockEntity sign) {
			SignText text = new SignText();

			for (int i = 0; i < 4; i++) {
				String line = i < lines.length ? lines[i] : "";
				text = text.setMessage(i, Component.literal(line));
			}

			sign.setText(text, true);
		}
	}

	public static void bed(ServerLevel level, int x, int y, int z, Direction facing) {
		set(level, x, y, z, Blocks.RED_BED.defaultBlockState()
				.setValue(BedBlock.FACING, facing)
				.setValue(BedBlock.PART, BedPart.HEAD));
		set(level, x, y, z + 1, Blocks.RED_BED.defaultBlockState()
				.setValue(BedBlock.FACING, facing)
				.setValue(BedBlock.PART, BedPart.FOOT));
	}

	public static void door(ServerLevel level, int x, int y, int z, Direction facing, boolean open) {
		BlockState lower = Blocks.OAK_DOOR.defaultBlockState()
				.setValue(HorizontalDirectionalBlock.FACING, facing)
				.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER)
				.setValue(BlockStateProperties.OPEN, open);
		set(level, x, y, z, lower);
		set(level, x, y + 1, z, lower.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
	}

	public static ItemStack fragment() {
		return new ItemStack(ModItems.DATA_FRAGMENT);
	}

	public static ItemStack shard() {
		return new ItemStack(ModItems.CORRUPT_SHARD);
	}

	public static ItemStack writtenBook(String title, List<String> pages) {
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		net.minecraft.world.item.component.WrittenBookContent content = new net.minecraft.world.item.component.WrittenBookContent(
				net.minecraft.server.network.Filterable.passThrough(title),
				"error404",
				0,
				pages.stream().map(p -> net.minecraft.server.network.Filterable.passThrough(Component.literal(p))).toList(),
				true
		);
		book.set(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT, content);
		return book;
	}

	/** Random line from a hardcoded pool - keeps structures from repeating themselves. */
	public static String pick(RandomSource random, String... options) {
		return options[random.nextInt(options.length)];
	}
}
