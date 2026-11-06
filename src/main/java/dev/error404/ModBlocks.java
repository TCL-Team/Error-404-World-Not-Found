package dev.error404;

import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import dev.error404.block.AnchorCoreBlock;
import dev.error404.block.NullTorchBlock;
import dev.error404.block.VoidStoneBlock;

public final class ModBlocks {
	private ModBlocks() {
	}

	/** The magenta/black checker from the missing-texture atlas. Decorative + crafting. */
	public static final Block MISSING_TEXTURE = register("missing_texture", Block::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_MAGENTA)
					.strength(1.2f, 6.0f)
					.sound(SoundType.STONE)
					.lightLevel(state -> 3));

	/** What the deletion front leaves behind. Silent to walk on. */
	public static final Block VOID_STONE = register("void_stone", VoidStoneBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_BLACK)
					.strength(2.5f, 1200.0f)
					.sound(SoundType.STONE));

	/** Player-built stability zone (32 block radius). */
	public static final Block ANCHOR_CORE = register("anchor_core", AnchorCoreBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_GRAY)
					.strength(4.0f, 1200.0f)
					.sound(SoundType.METAL)
					.lightLevel(state -> 12)
					.noOcclusion());

	/** Light that 422 refuses to acknowledge. */
	public static final Block NULL_TORCH = register("null_torch", NullTorchBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_BLACK)
					.strength(0.0f)
					.sound(SoundType.WOOD)
					.lightLevel(state -> 15)
					.noCollission()
					.noOcclusion(),
			false);

	private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		return register(name, factory, properties, true);
	}

	private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties, boolean withItem) {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, Error404.id(name));
		Block block = factory.apply(properties.setId(blockKey));

		if (withItem) {
			ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Error404.id(name));
			Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		}

		return Registry.register(BuiltInRegistries.BLOCK, blockKey, block);
	}

	public static void initialize() {
		// static init is enough; the method exists so call order is explicit in Error404
	}
}
