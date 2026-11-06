package dev.error404;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;

import dev.error404.entity.ModEntities;

/** One creative tab for everything the mod adds. */
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
					output.accept(ModItems.DATA_FRAGMENT);
					output.accept(ModItems.CORRUPT_SHARD);
					output.accept(ModItems.VOID_PEARL);
					output.accept(ModItems.ERROR_LOG);
					output.accept(ModBlocks.ANCHOR_CORE);
					output.accept(ModBlocks.MISSING_TEXTURE);
					output.accept(ModBlocks.VOID_STONE);
					output.accept(ModBlocks.NULL_TORCH);
					output.accept(ModEntities.SPAWN_EGG_422);
				})
				.build());
	}
}
