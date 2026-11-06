package dev.error404;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;

import dev.error404.integrity.Error404Director;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ERROR 404: WORLD NOT FOUND — main (common/server) entrypoint.
 *
 * <p>v0.1 scope: configuration, the world-integrity state machine and the {@code /error404}
 * command surface. Chunk deletion, anomalies and the 422 entity land in later milestones
 * (see {@code docs/ROADMAP.md}).</p>
 */
public class Error404 implements ModInitializer {
	public static final String MOD_ID = "error404";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		Error404Config.load();
		Error404Commands.register();
		Error404Director.register();

		LOGGER.info("[error404] initialized - integrity {}", Error404Config.get().enabled ? "armed" : "disabled by config");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
