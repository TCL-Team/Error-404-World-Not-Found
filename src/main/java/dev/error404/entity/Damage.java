package dev.error404.entity;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import dev.error404.Error404;

/**
 * The mod's own damage types, so deaths read correctly in the log and on the death screen
 * ("X was not processed") instead of borrowing vanilla's "magic" line.
 */
public final class Damage {
	public static final ResourceKey<DamageType> UNPROCESSABLE =
			ResourceKey.create(Registries.DAMAGE_TYPE, Error404.id("unprocessable"));
	public static final ResourceKey<DamageType> WORLD_DELETED =
			ResourceKey.create(Registries.DAMAGE_TYPE, Error404.id("world_deleted"));

	private Damage() {
	}

	/** 422's stare / contact damage, credited to the entity. */
	public static DamageSource unprocessable(Level level, Entity source) {
		return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
				.getOrThrow(UNPROCESSABLE), source);
	}

	/** The world itself eating a player (deletion front, cascade floor). */
	public static DamageSource worldDeleted(Level level) {
		return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
				.getOrThrow(WORLD_DELETED));
	}
}
