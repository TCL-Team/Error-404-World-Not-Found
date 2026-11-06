package dev.error404.client;

import net.fabricmc.api.ClientModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entrypoint. v0.1 does nothing visually yet - the void sky, HUD glitches and the
 * 422 renderer arrive in milestones v0.2 - v0.4.
 */
public class Error404Client implements ClientModInitializer {
	private static final Logger LOGGER = LoggerFactory.getLogger("error404/client");

	@Override
	public void onInitializeClient() {
		LOGGER.info("[error404] client hooks loaded");
	}
}
