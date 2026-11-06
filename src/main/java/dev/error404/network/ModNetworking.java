package dev.error404.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Server -> client state sync.
 *
 * <p>The client never decides anything about the world: it receives integrity, phase,
 * the session seed (for deterministic visual noise) and one-shot effect triggers.</p>
 *
 * <p>Effects are encoded as a small enum: 0 = static burst, 1 = phase shift, 2 = subtitle
 * line, 3 = clear overlay, 4 = desaturate flash.</p>
 */
public final class ModNetworking {
	public static final int EFFECT_STATIC = 0;
	public static final int EFFECT_PHASE_SHIFT = 1;
	public static final int EFFECT_MESSAGE = 2;
	public static final int EFFECT_CLEAR = 3;
	public static final int EFFECT_DESATURATE = 4;
	/** The fake crash screen. Client-side only - nothing on the server actually fails. */
	public static final int EFFECT_CRASH = 5;
	/** Jumpscare. The payload's message is the style: "0" breath, "1" sting, "2" whisper. */
	public static final int EFFECT_SCARE = 6;

	private ModNetworking() {
	}

	public static void initialize() {
		PayloadTypeRegistry.playS2C().register(StateSyncPayload.ID, StateSyncPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(EffectPayload.ID, EffectPayload.CODEC);

		// Send the current state as soon as a player joins, so late joiners see the same world.
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendState(handler.getPlayer(), server));
	}

	/** Pushes the world state to one player. */
	public static void sendState(net.minecraft.server.level.ServerPlayer player, net.minecraft.server.MinecraftServer server) {
		var state = dev.error404.integrity.IntegrityState.get(server);
		var cfg = dev.error404.Error404Config.get();
		int flags = 0;

		if (cfg.photosensitivity) {
			flags |= StateSyncPayload.FLAG_PHOTOSENSITIVITY;
		}

		if (cfg.hud) {
			flags |= StateSyncPayload.FLAG_HUD;
		}

		ServerPlayNetworking.send(player, new StateSyncPayload(
				state.integrity(),
				state.phase().ordinal(),
				state.sessionSeed(),
				state.dataFragments(),
				state.ended(),
				flags
		));
	}

	/** Pushes the world state to everyone (used by the director when a phase changes). */
	public static void broadcastState(net.minecraft.server.MinecraftServer server) {
		for (net.minecraft.server.level.ServerPlayer player : server.getPlayerList().getPlayers()) {
			sendState(player, server);
		}
	}

	/** Sends a one-shot visual effect to one player. */
	public static void sendEffect(net.minecraft.server.level.ServerPlayer player, int kind, String message, int ticks) {
		ServerPlayNetworking.send(player, new EffectPayload(kind, message == null ? "" : message, ticks));
	}

	/** Sends a one-shot visual effect to everyone. */
	public static void broadcastEffect(net.minecraft.server.MinecraftServer server, int kind, String message, int ticks) {
		for (net.minecraft.server.level.ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerPlayNetworking.send(player, new EffectPayload(kind, message == null ? "" : message, ticks));
		}
	}
}
