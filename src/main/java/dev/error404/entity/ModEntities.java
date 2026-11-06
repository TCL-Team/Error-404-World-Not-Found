package dev.error404.entity;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

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

	private static <T extends Entity> EntityType<T> register(String name, EntityType<T> type) {
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, Error404.id(name), type);
	}

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(ERROR_422, Error422Entity.createAttributes());
	}
}
