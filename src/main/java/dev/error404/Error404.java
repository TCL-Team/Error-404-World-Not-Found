package dev.error404;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;

import dev.error404.entity.ModEntities;
import dev.error404.event.ServerEvents;
import dev.error404.integrity.Error404Director;
import dev.error404.network.ModNetworking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ERROR 404: WORLD NOT FOUND — main (common/server) entrypoint.
 *
 * <p>Registration order matters: registries first (blocks, items, sounds, entities), then
 * content that references them (creative tab), then the runtime systems (networking,
 * director, events, commands).</p>
 */
public class Error404 implements ModInitializer {
	public static final String MOD_ID = "error404";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		Error404Config.load();

		// 1. registries
		ModSounds.initialize();
		ModBlocks.initialize();
		ModItems.initialize();
		ModEntities.initialize();
		Error404Tabs.initialize();

		// 2. networking
		ModNetworking.initialize();
		registerTitlePack();

		// 3. runtime systems
		Error404Commands.register();
		Error404Director.register();
		ServerEvents.register();

		LOGGER.info("[error404] loaded - world integrity {}", Error404Config.get().enabled ? "armed" : "disabled by config");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
