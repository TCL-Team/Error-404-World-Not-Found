package dev.error404.anomaly;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

import dev.error404.Error404;
import dev.error404.ModSounds;
import dev.error404.entity.Error422Entity;
import dev.error404.integrity.IntegrityState;
import dev.error404.integrity.Phase;

/**
 * Decides when and where the world grows a new impossible structure, keeps the existing
 * ones alive (their {@link Anomaly#tick}), and dissolves them again when they "revoke".
 */
public final class AnomalyManager {
	private static final int MAX_ACTIVE = 3;
	private static final int PLACE_INTERVAL_TICKS = 200; // every 10s attempt one placement
	private static final int DISCOVERY_RADIUS = 26;

	/** Runtime list; the authoritative copy is mirrored into {@link IntegrityState}. */
	private static final List<Instance> ACTIVE = new ArrayList<>();
	/** Set by the SALVAGE ending: no new structures, no revocations. */
	private static boolean frozen = false;
	private static final int SPAWN_CHECK_TICKS = 100;
	/**
	 * Nothing makes a session stop being scary faster than the entity appearing and doing
	 * nothing for five minutes. In the final phase it presses in; otherwise it keeps its
	 * distance and plays dead. The cooldown is per-session, so it is not a new timer.
	 */
	private static final int PRESSURE_INTERVAL = 20 * 45;
	private static final int SPAWN_COOLDOWN_TICKS = 20 * 45;
	private static long lastSpawnTick = 0L;

	private AnomalyManager() {
	}

	public record Instance(AnomalyType type, BlockPos pos, long bornTick, boolean revoking, int razedBlocks) {
	}

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
		long now = state.ageTicks();

		if (!frozen && phase.ordinal() >= Phase.DELETION.ordinal() && now % PLACE_INTERVAL_TICKS == 0) {
			tryPlace(level, state, phase);
		}

		if (!frozen) {
			maybeSpawn422(level, phase, now);
		}

		tickInstances(level, now);
		checkDiscovery(level, state);
	}

	// ------------------------------------------------------------------ placement

	private static void tryPlace(ServerLevel level, IntegrityState state, Phase phase) {
		int alive = 0;
		for (Instance instance : ACTIVE) {
			if (!instance.revoking()) {
				alive++;
			}
		}

		if (alive >= MAX_ACTIVE) {
			return;
		}

		List<AnomalyType> allowed = new ArrayList<>();
		for (AnomalyType type : AnomalyType.values()) {
			if (type.minimumPhase() <= phase.ordinal()) {
				allowed.add(type);
			}
		}

		if (allowed.isEmpty()) {
			return;
		}

		ServerPlayer player = level.players().get(level.random.nextInt(level.players().size()));
		RandomSource random = level.random;
		AnomalyType type = allowed.get(random.nextInt(allowed.size()));

		int minDistance = phase.ordinal() >= Phase.ANNEXATION.ordinal() ? 48 : 80;
		int maxDistance = phase.ordinal() >= Phase.ANNEXATION.ordinal() ? 90 : 140;

		for (int attempt = 0; attempt < 6; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double distance = minDistance + random.nextDouble() * (maxDistance - minDistance);
			int x = player.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
			int z = player.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);

			if (tooCloseToExisting(x, z)) {
				continue;
			}

			level.getChunk(x >> 4, z >> 4); // make sure the region exists before we build in it
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos pos = new BlockPos(x, y, z);

			if (level.getBlockState(pos.below()).isAir()) {
				continue;
			}

			Anomaly anomaly = type.create();

			try {
				anomaly.build(level, pos, random);
			} catch (RuntimeException e) {
				Error404.LOGGER.error("[error404] anomaly {} failed to build at {}", type, pos, e);
				continue;
			}

			// If it appeared close enough that the player can see it, that is a scare moment:
			// a building that was not there a second ago, inside fifteen blocks.
			if (player.blockPosition().distSqr(pos) < 15.0 * 15.0) {
				dev.error404.haunt.Scares.onStructure(level, player);
			}

			ACTIVE.add(new Instance(type, pos, state.ageTicks(), false, 0));
			state.registerAnomaly(type.name(), pos);
			level.playSound(null, pos, ModSounds.STRUCTURE_APPEAR, SoundSource.AMBIENT, 1.0f, 1.0f);
			Error404.LOGGER.info("[error404] anomaly {} placed at {} ({} blocks from {})",
					type, pos, (int) Math.sqrt(player.blockPosition().distSqr(pos)), player.getName().getString());
			return;
		}
	}

	private static boolean tooCloseToExisting(int x, int z) {
		for (Instance instance : ACTIVE) {
			if (instance.pos().distSqr(new BlockPos(x, instance.pos().getY(), z)) < 60.0 * 60.0) {
				return true;
			}
		}

		return false;
	}

	// ------------------------------------------------------------------ runtime

	private static void tickInstances(ServerLevel level, long now) {
		Iterator<Instance> iterator = ACTIVE.iterator();

		while (iterator.hasNext()) {
			Instance instance = iterator.next();
			AnomalyType type = instance.type();
			long age = now - instance.bornTick();
			RandomSource random = level.random;

			if (!instance.revoking()) {
				try {
					type.create().tick(level, instance.pos(), random, (int) Math.min(Integer.MAX_VALUE, age));
				} catch (RuntimeException e) {
					Error404.LOGGER.warn("[error404] anomaly {} tick failed", type, e);
				}

				// After 10 minutes an anomaly may start deleting itself, block by block.
				if (age > 20L * 600 && type.create().canRevoke() && random.nextInt(240) == 0) {
					ACTIVE.set(ACTIVE.indexOf(instance), new Instance(type, instance.pos(), instance.bornTick(), true, 0));
					level.playSound(null, instance.pos(), ModSounds.STRUCTURE_REVOKE, SoundSource.AMBIENT, 1.0f, 1.0f);
					broadcastNear(level, instance.pos(), "ERR_404: structure revoked");
				}

				continue;
			}

			int razed = raze(level, instance);
			ACTIVE.set(ACTIVE.indexOf(instance), new Instance(type, instance.pos(), instance.bornTick(), true, razed));

			if (razed <= 0) {
				iterator.remove();
				IntegrityState.get(level.getServer()).unregisterAnomaly(type.name());
			}
		}
	}

	/** Removes a batch of blocks from a revoking structure, top-down. */
	private static int raze(ServerLevel level, Instance instance) {
		int budget = 90;
		int removed = 0;
		BlockPos centre = instance.pos();
		int radius = instance.type().create().footprint();

		for (int dy = 12; dy >= -6 && budget > 0; dy--) {
			for (int dx = -radius; dx <= radius && budget > 0; dx++) {
				for (int dz = -radius; dz <= radius && budget > 0; dz++) {
					BlockPos pos = centre.offset(dx, dy, dz);
					var state = level.getBlockState(pos);

					if (!state.isAir() && !state.is(Blocks.BEDROCK) && !state.hasBlockEntity()) {
						level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
						removed++;
						budget--;
					}
				}
			}
		}

		return removed;
	}

	// ------------------------------------------------------------------ discovery

	private static void checkDiscovery(ServerLevel level, IntegrityState state) {
		for (Instance instance : ACTIVE) {
			if (state.hasFoundAnomaly(instance.type().name())) {
				continue;
			}

			for (ServerPlayer player : level.players()) {
				if (player.blockPosition().distSqr(instance.pos()) > (double) DISCOVERY_RADIUS * DISCOVERY_RADIUS) {
					continue;
				}

				state.markAnomalyFound(instance.type().name());
				player.sendSystemMessage(Component.literal("[Server] " + instance.type().code() + " logged").withStyle(ChatFormatting.RED));

				for (String line : instance.type().create().discoveryLines()) {
					player.sendSystemMessage(Component.literal("  " + line).withStyle(ChatFormatting.DARK_GRAY));
				}

				level.playSound(null, player.blockPosition(), ModSounds.ERROR_TONE, SoundSource.AMBIENT, 0.7f, 1.0f);
				break;
			}
		}
	}

	/**
	 * 422 is not a mob that spawns - it is already there. From DESYNC onwards the world
	 * occasionally "forgets to remove" one near a player who is not looking that way, so it
	 * only ever appears behind you or in the corner of the screen.
	 *
	 * <p>Hard cap from the difficulty preset, a 45 second cooldown between spawns, and always
	 * 24 blocks behind the player so it can never materialise in someone's face.</p>
	 */
	private static void maybeSpawn422(ServerLevel level, Phase phase, long now) {
		// From the first phase on, it exists. Earlier versions waited for DESYNC, which meant
		// a player could spend twenty minutes in a quiet world wondering what the mod does.
		if (now % SPAWN_CHECK_TICKS != 0) {
			return;
		}

		List<Error422Entity> alive = level.getEntitiesOfClass(Error422Entity.class,
				new net.minecraft.world.phys.AABB(level.getRespawnData().globalPos().pos()).inflate(4096.0));

		// Pressure: an existing 422 gets moved into the player's blind spot on a slow timer,
		// so "it stopped appearing" never becomes "it is gone".
		if (phase.ordinal() >= Phase.CASCADE.ordinal() && now % PRESSURE_INTERVAL == 0 && !alive.isEmpty()) {
			Error422Entity entity = alive.get(level.random.nextInt(alive.size()));
			ServerPlayer victim = level.players().get(level.random.nextInt(level.players().size()));

			if (entity.distanceTo(victim) > 40.0) {
				entity.pressIn(victim);
			}
		}

		if (alive.size() >= Math.max(2, dev.error404.Difficulty.current().maxAlive + 1)) {
			return;
		}

		if (lastSpawnTick != 0 && now < lastSpawnTick + SPAWN_COOLDOWN_TICKS) {
			return;
		}

		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) {
				continue;
			}

			// behind the player: opposite to where they are looking
			net.minecraft.world.phys.Vec3 behind = player.position().subtract(player.getViewVector(1.0f).scale(24.0));
			int x = (int) behind.x;
			int z = (int) behind.z;
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);

			if (level.getBlockState(new BlockPos(x, y, z)).isAir()
					&& level.getBlockState(new BlockPos(x, y + 1, z)).isAir()) {
				Error422Entity entity = dev.error404.entity.ModEntities.ERROR_422.create(level,
						net.minecraft.world.entity.EntitySpawnReason.EVENT);

				if (entity != null) {
					entity.setPos(x + 0.5, y, z + 0.5);
					level.addFreshEntity(entity);
					level.playSound(null, player.blockPosition(), ModSounds.PHASE_SHIFT, SoundSource.AMBIENT, 0.35f, 0.7f);
					lastSpawnTick = now;
					Error404.LOGGER.debug("[error404] 422 materialised near {} at {},{},{}",
							player.getName().getString(), x, y, z);
					return;
				}
			}

		}

		// Nothing was placed this pass; try again on the next check instead of hammering it.
	}

	// ------------------------------------------------------------------ api

	public static List<Instance> active() {
		return List.copyOf(ACTIVE);
	}

	/** Used by /error404 anomaly & reset. */
	public static void spawnAt(ServerLevel level, AnomalyType type, BlockPos pos) {
		Anomaly anomaly = type.create();
		anomaly.build(level, pos, level.random);
		ACTIVE.add(new Instance(type, pos, IntegrityState.get(level.getServer()).ageTicks(), false, 0));
		IntegrityState.get(level.getServer()).registerAnomaly(type.name(), pos);
		level.playSound(null, pos, ModSounds.STRUCTURE_APPEAR, SoundSource.AMBIENT, 1.0f, 1.0f);
	}

	/**
	 * Called after a player death. Being killed by something invulnerable and having it
	 * respawn-camp the death site is not horror, it is a broken game. It withdraws.
	 */
	public static void withdrawFor(MinecraftServer server, long ticks) {
		lastSpawnTick = IntegrityState.get(server).ageTicks() + ticks;
	}

	public static void clear() {
		ACTIVE.clear();
		lastSpawnTick = 0L;
	}

	/**
	 * SALVAGE: the world stops growing new structures and stops revoking the ones it built.
	 * Nothing is removed - the anomalies are the player's proof they were here.
	 */
	public static void freeze() {
		frozen = true;
	}

	public static void forgetDiscovered(IntegrityState state) {
		state.clearAnomalies();
	}

	private static void broadcastNear(ServerLevel level, BlockPos pos, String text) {
		for (ServerPlayer player : level.players()) {
			if (player.blockPosition().distSqr(pos) < 96 * 96) {
				player.sendSystemMessage(Component.literal("[Server] " + text).withStyle(ChatFormatting.DARK_RED));
			}
		}
	}
}
