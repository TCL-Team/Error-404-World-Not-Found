package dev.error404.haunt;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import dev.error404.ModSounds;
import dev.error404.integrity.IntegrityState;
import dev.error404.integrity.Phase;

/**
 * The server-side haunting: chat messages, join/leave lines and sounds that come from the
 * game itself rather than from an entity.
 *
 * <p>The rule for everything in here is that it must look exactly like a real server message
 * at a glance. Vanilla line formats, vanilla colours, the player's own name. The player is
 * meant to read "you joined the game" while sitting alone in a single-player world and have
 * to think about it.</p>
 *
 * <p>Nothing here is random noise: each line fires once per session per player, in phase
 * order, so a player who talks about the mod never spoils the next one.</p>
 */
public final class PhantomServer {
	/** Ticks between two phantom events, at minimum. */
	private static final int MIN_GAP = 20 * 90;
	private static final int MAX_GAP = 20 * 220;

	private static long nextEventTick = 0L;
	private static int eventIndex = 0;

	private PhantomServer() {
	}

	/** Called from the server tick while a session is active. */
	public static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();

		if (level == null || level.players().isEmpty()) {
			return;
		}

		IntegrityState state = IntegrityState.get(server);

		if (!state.active() || state.ended()) {
			return;
		}

		Phase phase = state.phase();

		if (phase.ordinal() < Phase.DESYNC.ordinal()) {
			return;
		}

		long now = state.ageTicks();

		if (nextEventTick == 0L) {
			nextEventTick = now + MIN_GAP;
		}

		if (now < nextEventTick) {
			return;
		}

		nextEventTick = now + MIN_GAP + level.random.nextInt(MAX_GAP - MIN_GAP);

		List<ServerPlayer> players = level.players();
		ServerPlayer player = players.get(level.random.nextInt(players.size()));

		fire(server, level, player, phase, eventIndex++);
	}

	private static void fire(MinecraftServer server, ServerLevel level, ServerPlayer player, Phase phase, int index) {
		String name = player.getGameProfile().name();

		switch (index % 8) {
			// ---- leaving and rejoining a world you are already in
			case 0 -> {
				broadcast(server, Component.literal(name + " left the game").withStyle(ChatFormatting.YELLOW));
				behind(level, player, ModSounds.FOOTSTEP_ECHO, 0.9f, 0.85f);
			}
			case 1 -> {
				broadcast(server, Component.literal(name + " joined the game").withStyle(ChatFormatting.YELLOW));
				behind(level, player, ModSounds.WHISPER, 1.0f, 0.7f);
			}
			// ---- a chat line in your own name that you did not type
			case 2 -> {
				if (phase.ordinal() >= Phase.DELETION.ordinal()) {
					broadcast(server, Component.literal("<" + name + "> it is still in your base")
							.withStyle(ChatFormatting.WHITE));
					behind(level, player, ModSounds.ERROR_TONE, 0.5f, 0.6f);
				}
			}
			// ---- the server talking to a player who is alone
			case 3 -> {
				if (phase.ordinal() >= Phase.DELETION.ordinal()) {
					player.sendSystemMessage(Component.literal("[Server] " + name
							+ " was removed from the whitelist").withStyle(ChatFormatting.YELLOW));
				}
			}
			// ---- something is walking behind you
			case 4 -> behind(level, player, ModSounds.FOOTSTEP_ECHO, 1.0f, 0.75f);
			// ---- the world itself reports a failure
			case 5 -> {
				if (phase.ordinal() >= Phase.CASCADE.ordinal()) {
					broadcast(server, Component.literal("ERR_404: failed to save chunk ["
							+ (player.getBlockX() >> 4) + ", " + (player.getBlockZ() >> 4) + "]")
							.withStyle(ChatFormatting.DARK_RED));
					level.playSound(null, player.blockPosition(), ModSounds.CHUNK_DELETE,
							SoundSource.AMBIENT, 1.0f, 1.0f);
				}
			}
			// ---- someone counting
			case 6 -> {
				if (phase.ordinal() >= Phase.CASCADE.ordinal()) {
					broadcast(server, Component.literal("<" + name + "> " + (3 - (index % 3))).withStyle(ChatFormatting.WHITE));
				}
			}
			// ---- the last one: the only message that tells the truth
			case 7 -> {
				if (phase.ordinal() >= Phase.ANNEXATION.ordinal()) {
					broadcast(server, Component.literal("<" + name + "> don't look at it").withStyle(ChatFormatting.WHITE));
					behind(level, player, ModSounds.WHISPER, 1.0f, 0.5f);
				}
			}
			default -> {
			}
		}
	}

	private static void broadcast(MinecraftServer server, Component line) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			player.sendSystemMessage(line);
		}
	}

	/**
	 * Plays a sound *behind* the player instead of at the player. Positional audio is the
	 * whole trick: headphones do the rest.
	 */
	private static void behind(ServerLevel level, ServerPlayer player, net.minecraft.sounds.SoundEvent sound,
			float volume, float pitch) {
		Vec3 back = player.position().subtract(player.getViewVector(1.0f).scale(3.0));
		level.playSound(null, back.x, back.y, back.z, sound, SoundSource.AMBIENT, volume, pitch);
	}

	public static void reset() {
		nextEventTick = 0L;
		eventIndex = 0;
	}
}
