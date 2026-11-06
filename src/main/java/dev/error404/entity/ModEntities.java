package dev.error404.entity;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import dev.error404.Error404;

public final class ModEntities {
	private ModEntities() {
	}

	public static final EntityType<Error422Entity> ERROR_422 = register(
			"error_422",
			EntityType.Builder.<Error422Entity>of(Error422Entity::new, MobCategory.MONSTER)
					.sized(0.7f, 2.9f)
					.clientTrackingRange(128)
					.build(ResourceKey.create(Registries.ENTITY_TYPE, Error404.id("error_422")))
	);

	/**
	 * A command/staging tool only - 422 never needs an egg to appear, the anomaly manager
	 * spawns it. It exists so map makers can pin one in place.
	 */
	public static final Item SPAWN_EGG_422 = registerEgg("error_422");

	private static <T extends Entity> EntityType<T> register(String name, EntityType<T> type) {
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, Error404.id(name), type);
	}

	private static Item registerEgg(String name) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Error404.id(name + "_spawn_egg"));
		return Registry.register(BuiltInRegistries.ITEM, key,
				new SpawnEggItem(new Item.Properties().spawnEgg(ERROR_422).setId(key)));
	}

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(ERROR_422, Error422Entity.createAttributes());
	}
}
