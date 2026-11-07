package dev.error404.integrity;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import dev.error404.Error404;
import dev.error404.Error404Config;

/**
 * The heart of the mod: a very small server-side loop that watches the world die.
 *
 * <p>Design rule: this tick handler stays O(1) per server tick. All the heavy work
 * (chunk deletion, anomaly placement, entity AI) lives in its own systems and only runs
 * when the phase says it should.</p>
 */
public final class Error404Director {
	/** Runtime-only pacing multiplier used by {@code /error404 demo}. */
	private static int demoMultiplier = 1;

	private Error404Director() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Error404Director::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(server, handler.getPlayer()));
	}

	public static void setDemoMultiplier(int multiplier) {
		demoMultiplier = Math.max(1, multiplier);
	}

	private static void onJoin(MinecraftServer server, ServerPlayer player) {
		Error404Config cfg = Error404Config.get();
		if (!cfg.enabled) {
			return;
		}

		IntegrityState state = IntegrityState.get(server);
		if (state.ended()) {
			// The ending already happened: show the closing card again instead of starting
			// a second session on top of a finished world.
			dev.error404.ending.EndingManager.reconcile(server);
			return;
		}

		dev.error404.Achievements.grant(player, dev.error404.Achievements.ROOT);

		if (!state.active()) {
			state.start(newSessionSeed(server), IntegrityState.MAX_INTEGRITY);

			if (cfg.announceOnJoin) {
				broadcast(server, "ERR_404: integrity monitoring armed. backups are recommended.", ChatFormatting.GRAY);
			}

			Error404.LOGGER.info("[error404] session started (seed {})", state.sessionSeed());

			// Operators get told how to test their own mod. Everyone else gets nothing: the whole
			// design depends on the player not knowing the mod is talking to them.
			if (player.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)) {
				player.sendSystemMessage(Component.literal("[error404] hidden commands are available: "
						+ "/error404 enable, then /error404 demo 40 to see deletion now")
						.withStyle(ChatFormatting.DARK_GRAY));
			}
		}
	}

	private static void tick(MinecraftServer server) {
		Error404Config cfg = Error404Config.get();
		if (!cfg.enabled) {
			return;
		}

		IntegrityState state = IntegrityState.get(server);
		if (!state.active() || state.ended()) {
			return;
		}

		// Check for zero before doing any work: integrity can also reach zero from the
		// exploration cost or from 422's presence, not just from the passive drain.
		if (state.integrity() <= 0) {
			dev.error404.ending.EndingManager.triggerDelete(server);
			return;
		}

		state.tickAge();

		long ticksPerPoint = Math.max(1L, (20L * cfg.secondsPerIntegrityPoint) / demoMultiplier);
		long accumulated = state.addTicks(1L);

		if (accumulated < ticksPerPoint) {
			return;
		}

		state.consumeTicks(ticksPerPoint);
		state.drain(1);

		if (state.shouldAdvancePhase()) {
			announcePhase(server, state.advancePhase());
		}

		if (state.integrity() <= 0) {
			dev.error404.ending.EndingManager.triggerDelete(server);
		}
	}

	private static void announcePhase(MinecraftServer server, Phase phase) {
		broadcast(server, phase.errorLine(), ChatFormatting.RED);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			player.displayClientMessage(Component.literal(phase.whisper()).withStyle(ChatFormatting.DARK_GRAY), true);
		}
	}

	private static void broadcast(MinecraftServer server, String line, ChatFormatting color) {
		server.getPlayerList().broadcastSystemMessage(
				Component.literal("[Server] " + line).withStyle(color),
				false
		);
	}

	private static long newSessionSeed(MinecraftServer server) {
		// Mixing a world value with nanoTime keeps two sessions in the same world distinct,
		// which the client-side visual effects rely on.
		return server.overworld().getSeed() * 31L ^ System.nanoTime();
	}
}
