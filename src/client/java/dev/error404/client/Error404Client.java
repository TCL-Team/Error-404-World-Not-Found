package dev.error404.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.Minecraft;

import dev.error404.entity.ModEntities;
import dev.error404.client.render.Error422Renderer;
import dev.error404.client.render.HudGlitchRenderer;
import dev.error404.client.render.ModModelLayers;
import dev.error404.client.render.VoidAmbience;
import dev.error404.network.EffectPayload;
import dev.error404.network.StateSyncPayload;

/**
 * Client entrypoint: entity renderer, model layer, HUD overlay, ambience and the two
 * server payload receivers.
 */
public class Error404Client implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ModModelLayers.register();
		EntityRendererRegistry.register(ModEntities.ERROR_422, Error422Renderer::new);

		HudGlitchRenderer.register();
		dev.error404.client.render.TitleScreenGlitch.register();
		VoidAmbience.register();
		dev.error404.client.config.ConfigCommand.register();

		ClientPlayNetworking.registerGlobalReceiver(StateSyncPayload.ID, (payload, context) ->
				ClientState.apply(payload.integrity(), payload.phase(), payload.seed(), payload.fragments(), payload.ended(), payload.flags()));

		ClientPlayNetworking.registerGlobalReceiver(EffectPayload.ID, (payload, context) ->
				ClientState.trigger(payload.kind(), payload.message(), payload.durationTicks()));

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientState.apply(100, 0, 0L, 0, false, 0));

		// Cold open: the title-screen drone, only when the mod is present and enabled.
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.screen != null && client.level == null && client.getSoundManager() != null) {
				TitleSound.maybePlay(client);
			}
		});
	}

	/** Keeps the single title drone from stacking up. */
	static final class TitleSound {
		private static int ticks = -1;

		private TitleSound() {
		}

		static void maybePlay(Minecraft client) {
			if (!dev.error404.Error404Config.get().enabled) {
				return;
			}

			if (ticks < 0) {
				ticks = 0;
			}

			if (ticks++ == 40) {
				client.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
						dev.error404.ModSounds.TITLE_DRONE, 0.4f));
			}
		}
	}
}
