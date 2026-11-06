package dev.error404;

import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

import dev.error404.item.CorruptShardItem;
import dev.error404.item.DataFragmentItem;
import dev.error404.item.ErrorLogItem;

public final class ModItems {
	private ModItems() {
	}

	public static final Item DATA_FRAGMENT = register("data_fragment", DataFragmentItem::new,
			new Item.Properties().stacksTo(8));

	public static final Item CORRUPT_SHARD = register("corrupt_shard", CorruptShardItem::new,
			new Item.Properties().stacksTo(16));

	public static final Item ERROR_LOG = register("error_log", ErrorLogItem::new,
			new Item.Properties().stacksTo(1));

	public static final Item VOID_PEARL = register("void_pearl", Item::new,
			new Item.Properties().stacksTo(16));

	private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Error404.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	public static void initialize() {
	}
}
