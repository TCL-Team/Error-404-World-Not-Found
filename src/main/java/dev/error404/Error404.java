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

	/**
	 * Ships the creepy title panorama as a built-in resource pack (DEFAULT_ENABLED), not by
	 * overwriting vanilla files: a player who wants the normal menu can switch it off in
	 * Resource Packs, and a player who never opens that menu gets the horror version.
	 */
	private static void registerTitlePack() {
		var container = net.fabricmc.loader.api.FabricLoader.getInstance()
				.getModContainer(MOD_ID).orElse(null);

		if (container == null) {
			return;
		}

		boolean ok = net.fabricmc.fabric.api.resource.v1.ResourceLoader.registerBuiltinPack(
				id("title"),
				container,
				net.minecraft.network.chat.Component.translatable("pack.error404.title"),
				net.fabricmc.fabric.api.resource.v1.pack.PackActivationType.DEFAULT_ENABLED);

		if (!ok) {
			LOGGER.warn("[error404] could not register the title resource pack");
		}
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
