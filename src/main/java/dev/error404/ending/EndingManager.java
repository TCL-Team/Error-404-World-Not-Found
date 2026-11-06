package dev.error404.ending;

import java.nio.file.Path;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;

import dev.error404.Error404;
import dev.error404.ModEntities;
import dev.error404.ModSounds;
import dev.error404.anomaly.AnomalyManager;
import dev.error404.deletion.WorldBackup;
import dev.error404.event.ServerEvents;
import dev.error404.integrity.IntegrityState;
import dev.error404.network.ModNetworking;

/**
 * The end of a session. Every ending is explicit, reversible (except the rename, which the
 * player can undo in the file system) and always announced - no silent world loss.
 *
 * <p>Endings are driven from {@link IntegrityState#ending()} so a restart in the middle of
 * one cannot skip it: the server sees {@code ended == true} on boot and re-applies the
 * cosmetic part while the world itself is already saved/renamed.</p>
 */
public final class EndingManager {
	/** How long the closing title stays up, in ticks. */
	private static final int FINAL_TITLE_TICKS = 160;
	/** Stare-attack counter for the 0x00 ending: three full stares. */
	public static final int NULL_ENDING_STARES = 3;

	private static int stareCount = 0;

	private EndingManager() {
	}

	/** Called by the director the moment integrity reaches zero. */
	public static void triggerDelete(MinecraftServer server) {
		IntegrityState state = IntegrityState.get(server);

		if (state.ended()) {
			return;
		}

		boolean nullEnding = stareCount >= NULL_ENDING_STARES;
		EndingType type = nullEnding ? EndingType.NULL : EndingType.DELETE;

		state.end(type.code());
		AnomalyManager.clear();
		playFinale(server, type);

		if (type == EndingType.DELETE) {
			archiveWorld(server);
		}

		Error404.LOGGER.warn("[error404] session ended with {} (stares: {})", type.name(), stareCount);
	}

	/** Called when the player completes the salvage ritual on the last chunk. */
	public static void triggerSalvage(MinecraftServer server, ServerPlayer player) {
		IntegrityState state = IntegrityState.get(server);

		if (state.ended()) {
			return;
		}

		state.setIntegrity(IntegrityState.MAX_INTEGRITY);
		state.end(EndingType.SALVAGE.code());
		ServerEvents.clearAnomaliesAfterSalvage(server);
		playFinale(server, EndingType.SALVAGE);

		// The one thing that survives: a dormant 422, forty blocks behind the base.
		ServerLevel level = server.overworld();

		if (level != null && player != null) {
			Vec3 behind = player.position().subtract(player.getViewVector(1.0f).scale(40.0));
			BlockPos spawn = BlockPos.containing(behind.x, level.getHeight(
					net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
					(int) behind.x, (int) behind.z), behind.z);

			ModEntities.ERROR_422.spawn(level, spawn, EntitySpawnReason.EVENT);
		}

		Error404.LOGGER.info("[error404] world salvaged by {}", player == null ? "console" : player.getName().getString());
	}

	/** A player stared at 422 for the full limit; three of those and the world goes quiet. */
	public static void registerStare(ServerPlayer player) {
		stareCount++;
		player.sendSystemMessage(Component.literal("ERR_422: look count " + stareCount + "/" + NULL_ENDING_STARES)
				.withStyle(ChatFormatting.DARK_GRAY));
	}

	public static int stareCount() {
		return stareCount;
	}

	public static void resetStareCount() {
		stareCount = 0;
	}

	/**
	 * Big screen moment for every player: title, subtitle, sound, particles. Deliberately
	 * the only place in the mod that uses the title packet.
	 */
	private static void playFinale(MinecraftServer server, EndingType type) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			player.connection.send(new ClientboundSetTitlesAnimationPacket(10, FINAL_TITLE_TICKS, 30));
			player.connection.send(new ClientboundSetTitleTextPacket(
					Component.literal("ERROR 404").withStyle(type == EndingType.SALVAGE ? ChatFormatting.AQUA : ChatFormatting.DARK_RED)));
			player.connection.send(new ClientboundSetSubtitleTextPacket(
					Component.literal(type.title() + " - " + type.line()).withStyle(ChatFormatting.GRAY)));

			player.sendSystemMessage(Component.literal("").withStyle(ChatFormatting.DARK_GRAY));
			player.sendSystemMessage(Component.literal("[Server] ERR_404: " + type.line()).withStyle(ChatFormatting.DARK_RED));
			player.sendSystemMessage(Component.literal("[Server] /error404 restore puts the world back").withStyle(ChatFormatting.GRAY));

			// Master-volume tone: the title card is silent otherwise, and a sound of our own
			// is the one reliable way to reach a player whose sound sliders are all over.
			player.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(
					net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ModSounds.ERROR_TONE),
					SoundSource.MASTER, player.getX(), player.getY(), player.getZ(), 1.0f, 0.6f, player.getRandom().nextLong()));
			ServerEvents.flashGlitch(server, type.title());
			ModNetworking.broadcastEffect(server, ModNetworking.EFFECT_DESATURATE, "", 200);
		}

		ServerLevel level = server.overworld();

		if (level != null) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 1.0, player.getZ(),
						120, 2.5, 1.5, 2.5, 0.02);
			}
		}
	}

	/**
	 * Non-destructive DELETE ending: the save directory is renamed to
	 * {@code <world>/error404_world_not_found/} after a fresh backup zip is written.
	 * Only with {@code destructiveEnding = true} does the rename happen at all; otherwise
	 * this is a log line and nothing else.
	 */
	private static void archiveWorld(MinecraftServer server) {
		if (!dev.error404.Error404Config.get().destructiveEnding) {
			Error404.LOGGER.info("[error404] destructiveEnding is off - world left exactly as it is");
			return;
		}

		Path backup = WorldBackup.backup(server, true);

		if (backup == null) {
			Error404.LOGGER.warn("[error404] could not create a backup, refusing to archive the world");
			return;
		}

		Path root = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
		Path target = root.resolve("error404_world_not_found");

		try {
			java.nio.file.Files.move(root, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			Error404.LOGGER.warn("[error404] world archived to {}", target);
		} catch (java.io.IOException e) {
			Error404.LOGGER.error("[error404] could not archive the world (backup is at {})", backup, e);
		}
	}

	/**
	 * Re-application of an ending after a restart: the counter is restored, the world stays
	 * stopped, and a sleeping/dead player is simply left alone.
	 */
	public static void reconcile(MinecraftServer server) {
		IntegrityState state = IntegrityState.get(server);

		if (!state.ended()) {
			return;
		}

		EndingType type = EndingType.byCode(state.ending());

		if (type == null) {
			return;
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			player.sendSystemMessage(Component.literal("[Server] ERR_404: this world ended with " + type.title())
					.withStyle(ChatFormatting.DARK_RED));
		}
	}

	/** Used by {@code /error404 ending <salvage|delete|null>} to preview an ending safely. */
	public static void force(MinecraftServer server, EndingType type) {
		switch (type) {
			case SALVAGE -> {
				List<ServerPlayer> players = server.getPlayerList().getPlayers();

				if (!players.isEmpty()) {
					triggerSalvage(server, players.getFirst());
				}
			}
			case DELETE -> triggerDelete(server);
			case NULL -> {
				stareCount = NULL_ENDING_STARES;
				triggerDelete(server);
			}
		}
	}

	/** Kept for the secret ending: a player who never looked should not be able to get it. */
	public static void applyStarePunishment(ServerPlayer player) {
		player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0), player);
		player.level().playSound(null, player.blockPosition(), ModSounds.SUB_HIT, SoundSource.HOSTILE, 1.0f, 0.5f);
	}
}
