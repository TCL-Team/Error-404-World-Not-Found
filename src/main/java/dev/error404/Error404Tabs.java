package dev.error404;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;

import dev.error404.entity.ModEntities;

/**
 * One creative tab for everything the mod adds.
 *
 * <p><b>Every entry goes through {@link #add}</b>, which refuses anything that is not a real
 * single-item stack. This is not defensive clutter: {@code CreativeModeTab.ItemDisplayBuilder}
 * throws {@link IllegalArgumentException} ("Stack size must be exactly 1") when it sees an empty
 * stack, and the tab is built from inside {@code CreativeModeInventoryScreen}'s constructor. One
 * bad entry therefore does not mean a missing item - it means <b>opening the creative inventory
 * crashes the game and saves the world on the way out</b>. A block with no item was exactly that
 * entry, and it is the reason v0.9.5 crashed on every creative inventory open.</p>
 */
public final class Error404Tabs {
	private Error404Tabs() {
	}

	public static final ResourceKey<CreativeModeTab> ERROR404 = ResourceKey.create(
			BuiltInRegistries.CREATIVE_MODE_TAB.key(), Error404.id("main"));

	public static void initialize() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, ERROR404, FabricItemGroup.builder()
				.icon(() -> new ItemStack(ModItems.CORRUPT_SHARD))
				.title(Component.translatable("itemGroup.error404"))
				.displayItems((params, output) -> {
					add(output, ModItems.DATA_FRAGMENT);
					add(output, ModItems.CORRUPT_SHARD);
					add(output, ModItems.VOID_PEARL);
					add(output, ModItems.ERROR_LOG);
					add(output, ModBlocks.ANCHOR_CORE);
					add(output, ModBlocks.MISSING_TEXTURE);
					add(output, ModBlocks.VOID_STONE);
					add(output, ModBlocks.NULL_TORCH);
					add(output, ModEntities.SPAWN_EGG_422);
				})
				.build());
	}

	/**
	 * Accepts an item only if it is a real item producing a legal creative-tab stack.
	 *
	 * <p>{@code Item.BLOCK_ITEMS} is how vanilla itself decides whether a block has an item; a
	 * block that is missing from it (or an item that resolved to {@code minecraft:air}) is skipped
	 * with a warning instead of taking the client down.</p>
	 */
	private static void add(CreativeModeTab.Output output, Item item) {
		if (item == null || item == net.minecraft.world.item.Items.AIR) {
			Error404.LOGGER.warn("[error404] creative tab entry is not a real item - skipped");
			return;
		}

		ItemStack stack = new ItemStack(item);

		if (stack.isEmpty() || stack.getCount() != 1) {
			Error404.LOGGER.warn("[error404] creative tab entry {} produced an illegal stack - skipped",
					BuiltInRegistries.ITEM.getKey(item));
			return;
		}

		output.accept(stack);
	}

	/** Same, for a block: only accepts it when the block actually has an item form. */
	private static void add(CreativeModeTab.Output output, Block block) {
		Item item = block == null ? null : block.asItem();

		if (item == null || item == net.minecraft.world.item.Items.AIR) {
			Error404.LOGGER.warn("[error404] block {} has no item and cannot be in the creative tab",
					block == null ? "null" : BuiltInRegistries.BLOCK.getKey(block));
			return;
		}

		add(output, item);
	}
}
