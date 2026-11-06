package dev.error404.haunt;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import dev.error404.Error404;
import dev.error404.Error404Config;
import dev.error404.ModSounds;
import dev.error404.deletion.DeletionJournal;
import dev.error404.integrity.IntegrityState;
import dev.error404.integrity.Phase;

/**
 * The "anything can happen" layer.
 *
 * <p>ERROR 422's reputation comes from doing things a game is not supposed to do at random.
 * This class is that, written deliberately: a pool of named events, each one <em>small</em>,
 * each one recoverable, and each one gated so it cannot fire in the first five minutes of a
 * session or while the player is inside an anchor zone. Chaos that is not bounded is just a
 * broken mod; chaos with a menu and a cooldown reads as intent.</p>
 *
 * <p>Every world change goes through {@link DeletionJournal}, so {@code /error404 restore}
 * undoes the whole thing, including the chunks nobody saw vanish.</p>
 */
public final class Chaos {
	/** Nothing fires before the world is already visibly broken. */
	private static final int MIN_PHASE = Phase.DELETION.ordinal();
	/** First event is at least 90 s in, then a gap of 60-180 s. */
	private static final int FIRST_DELAY = 20 * 90;
	private static final int MIN_GAP = 20 * 60;
	private static final int MAX_GAP = 20 * 180;

	private static long nextTick = 0L;
	private static int fired = 0;
	private static final List<String> LOG = new java.util.ArrayList<>();

	private Chaos() {
	}

	/** Every event this session, in order. Used by the error log item and the ending card. */
	public static List<String> sessionLog() {
		return List.copyOf(LOG);
	}

	public static void tick(MinecraftServer server) {
		Error404Config cfg = Error404Config.get();

		if (!cfg.enabled || !cfg.chaos) {
			return;
		}

		ServerLevel level = server.overworld();

		if (level == null || level.players().isEmpty()) {
			return;
		}

		IntegrityState state = IntegrityState.get(server);

		if (!state.active() || state.ended() || state.phase().ordinal() < MIN_PHASE) {
			return;
		}

		long now = state.ageTicks();

		if (nextTick == 0L) {
			nextTick = now + FIRST_DELAY;
		}

		if (now < nextTick) {
			return;
		}

		nextTick = now + MIN_GAP + level.random.nextInt(MAX_GAP - MIN_GAP);

		List<ServerPlayer> players = level.players();
		ServerPlayer player = players.get(level.random.nextInt(players.size()));

		// The anchor zone is the one promise the mod makes. Chaos respects it.
		if (insideAnchor(state, player)) {
			return;
		}

		fire(server, level, player, state);
	}

	/**
	 * Fires one named event right now, regardless of cooldowns. Used by
	 * {@code /error404 chaos fire <event>} so a player can see what the mod is capable of
	 * before it decides to show them.
	 *
	 * @return the event name, or null if the name is unknown
	 */
	public static String forceFire(MinecraftServer server, ServerLevel level, ServerPlayer player, String event) {
		IntegrityState state = IntegrityState.get(server);

		switch (event) {
			case "chunk" -> eventChunkGone(level, player, state);
			case "inventory" -> eventInventoryScramble(level, player);
			case "death" -> eventSuddenDeath(level, player, state);
			case "doors" -> eventDoors(level, player);
			case "sound" -> eventSound(level, player);
			case "lightning" -> eventLightning(level, player);
			case "mobs" -> eventMobs(level, player);
			case "teleport" -> eventTeleport(level, player);
			case "message" -> eventMessage(server, level, player, state);
			case "night" -> eventNight(level, player);
			case "hunger" -> eventHunger(level, player);
			case "silence" -> eventSilence(level, player);
			default -> {
				return null;
			}
		}

		fired++;
		return event;
	}

	public static java.util.List<String> events() {
		return java.util.List.of("chunk", "inventory", "death", "doors", "sound", "lightning", "mobs",
				"teleport", "message", "night", "hunger", "silence");
	}

	private static void fire(MinecraftServer server, ServerLevel level, ServerPlayer player, IntegrityState state) {
		int roll = level.random.nextInt(100);

		if (roll < 20) {
			eventChunkGone(level, player, state);
		} else if (roll < 31) {
			eventInventoryScramble(level, player);
		} else if (roll < 42) {
			eventSuddenDeath(level, player, state);
		} else if (roll < 51) {
			eventDoors(level, player);
		} else if (roll < 60) {
			eventSound(level, player);
		} else if (roll < 69) {
			eventLightning(level, player);
		} else if (roll < 78) {
			eventMobs(level, player);
		} else if (roll < 85) {
			eventTeleport(level, player);
		} else if (roll < 90) {
			eventNight(level, player);
		} else if (roll < 95) {
			eventHunger(level, player);
		} else if (roll < 98) {
			eventSilence(level, player);
		} else {
			eventMessage(server, level, player, state);
		}

		fired++;
	}

	// ---------------------------------------------------------------- events

	/**
	 * A chunk somewhere near the player is simply gone: every block above the void floor is
	 * removed at once, the way an unloaded chunk looks, and it is journaled like everything
	 * else. This is the single most ERROR-422 thing the mod does.
	 */
	private static void eventChunkGone(ServerLevel level, ServerPlayer player, IntegrityState state) {
		int radius = 2 + level.random.nextInt(4);
		int cx = (player.getBlockX() >> 4) + (level.random.nextBoolean() ? radius : -radius);
		int cz = (player.getBlockZ() >> 4) + (level.random.nextInt(radius * 2 + 1) - radius);

		if (dev.error404.deletion.DeletionRules.isChunkProtected(level, cx, cz)
				|| state.hasDeletedChunk(cx, cz)) {
			return;
		}

		// 1024 blocks is exactly what DeletionJournal remembers per chunk: the hole is always
		// fully recoverable, and it is capped so a single event cannot spike the server tick.
		int removed = 0;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		int minY = level.getMinY() + 1;
		int budget = DeletionJournal.PER_CHUNK;

		for (int x = cx << 4; x < (cx << 4) + 16 && budget > 0; x++) {
			for (int z = cz << 4; z < (cz << 4) + 16 && budget > 0; z++) {
				int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);

				for (int y = top; y >= minY && budget > 0; y--) {
					cursor.set(x, y, z);
					BlockState blockState = level.getBlockState(cursor);

					if (blockState.isAir()) {
						continue;
					}

					if (dev.error404.deletion.DeletionRules.isProtected(level, cursor)) {
						continue;
					}

					DeletionJournal.record(cx, cz, cursor, blockState);
					level.setBlock(cursor, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					removed++;
					budget--;
				}
			}
		}

		state.markDeletedChunk(cx, cz);
		state.drain(2);
		LOG.add("chunk_gone @" + cx + "," + cz + " (" + removed + " blocks)");

		level.playSound(null, (cx << 4) + 8, level.getSeaLevel(), (cz << 4) + 8,
				ModSounds.CHUNK_DELETE, SoundSource.AMBIENT, 1.6f, 0.6f);
		level.sendParticles(ParticleTypes.PORTAL, (cx << 4) + 8, player.getY() + 2, (cz << 4) + 8,
				120, 8.0, 4.0, 8.0, 0.2);

		if (level.random.nextBoolean()) {
			player.sendSystemMessage(Component.literal("WARN: chunk [" + cx + ", " + cz
					+ "] failed to load and was discarded").withStyle(ChatFormatting.RED));
		}
	}

	/** Hotbar contents reshuffled. Nothing deleted - it comes back, just not where it was. */
	private static void eventInventoryScramble(ServerLevel level, ServerPlayer player) {
		var inventory = player.getInventory();
		int size = inventory.getContainerSize();

		for (int i = 0; i < 12; i++) {
			int a = level.random.nextInt(size);
			int b = level.random.nextInt(size);

			if (a == b) {
				continue;
			}

			var stackA = inventory.getItem(a).copy();
			var stackB = inventory.getItem(b).copy();
			inventory.setItem(a, stackB);
			inventory.setItem(b, stackA);
		}

		player.containerMenu.broadcastChanges();
		player.playSound(ModSounds.STATIC_BURST, 1.0f, 0.6f);
		player.displayClientMessage(Component.literal("ERR_404: your inventory was processed out of order")
				.withStyle(ChatFormatting.DARK_RED), true);
		LOG.add("inventory_scramble");
	}

	/**
	 * The sudden death: a real lightning bolt, no storm, no warning, on the player's exact
	 * block. It is lethal if you are at full health only at NIGHTMARE; on STANDARD it takes
	 * half your health and leaves you with a message that explains nothing.
	 */
	private static void eventSuddenDeath(ServerLevel level, ServerPlayer player, IntegrityState state) {
		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.EVENT);

		if (bolt == null) {
			return;
		}

		bolt.setPos(player.getX(), player.getY(), player.getZ());
		bolt.setVisualOnly(dev.error404.Difficulty.current() == dev.error404.Difficulty.CHILL);
		level.addFreshEntity(bolt);

		player.hurtServer(level, dev.error404.entity.Damage.worldDeleted(level), 5.0f);

		state.drain(3);
		LOG.add("sudden_death");

		player.sendSystemMessage(Component.literal("").withStyle(ChatFormatting.RESET));
		player.displayClientMessage(Component.literal("ERR_FATAL: this player is not in the save file")
				.withStyle(ChatFormatting.DARK_RED), false);
		level.playSound(null, player.blockPosition(), ModSounds.ERROR_TONE, SoundSource.AMBIENT, 1.2f, 0.5f);
		level.playSound(null, player.blockPosition(), ModSounds.SUB_HIT, SoundSource.AMBIENT, 1.4f, 0.6f);
	}

	/** Every door and trapdoor near the player opens at once. Nothing else moves. */
	private static void eventDoors(ServerLevel level, ServerPlayer player) {
		int changed = 0;

		for (BlockPos pos : BlockPos.betweenClosed(player.blockPosition().offset(-20, -8, -20),
				player.blockPosition().offset(20, 8, 20))) {
			BlockState blockState = level.getBlockState(pos);

			if (blockState.getBlock() instanceof DoorBlock
					&& blockState.hasProperty(BlockStateProperties.OPEN)) {
				level.setBlock(pos, blockState.cycle(BlockStateProperties.OPEN), Block.UPDATE_CLIENTS);
				changed++;
			}
		}

		if (changed == 0) {
			return;
		}

		level.playSound(null, player.blockPosition(), ModSounds.ENTITY_ATTACK, SoundSource.AMBIENT, 0.8f, 0.5f);
		player.displayClientMessage(Component.literal("something came in").withStyle(ChatFormatting.GRAY), true);
		LOG.add("doors_open (" + changed + ")");
	}

	/** A sound directly behind the player, at full volume, with nothing there when they turn. */
	private static void eventSound(ServerLevel level, ServerPlayer player) {
		var back = player.position().subtract(player.getViewVector(1.0f).scale(2.5));
		var sound = switch (level.random.nextInt(4)) {
			case 0 -> ModSounds.ENTITY_AMBIENT;
			case 1 -> ModSounds.WHISPER;
			case 2 -> ModSounds.FOOTSTEP_ECHO;
			default -> ModSounds.DATA_CHIRP;
		};

		level.playSound(null, back.x, back.y, back.z, sound, SoundSource.AMBIENT, 1.5f,
				0.5f + level.random.nextFloat() * 0.3f);
		LOG.add("behind_you");
	}

	/**
	 * Midday becomes midnight in one tick.
	 *
	 * <p>Not a gradual dusk - the light just goes, the sky snaps to the night gradient, and
	 * whatever was standing twenty blocks away is now only visible when it moves. The client
	 * has to be told explicitly: the level's day time is authoritative on the server, but a
	 * client that is not sent {@code ClientboundSetTimePacket} keeps rendering the old sky
	 * until the next natural transition, which is exactly the "the sun did not move, the world
	 * did" feeling this is after.</p>
	 */
	private static void eventNight(ServerLevel level, ServerPlayer player) {
		long current = level.getDayTime();
		long today = current - Math.floorMod(current, 24000L);

		// 18000 is midnight; if it is already night, jump to the *next* night instead
		long midnight = today + 18000L;

		while (midnight <= current) {
			midnight += 24000L;
		}

		level.setDayTime(midnight);
		level.getServer().getPlayerList().broadcastAll(
				new net.minecraft.network.protocol.game.ClientboundSetTimePacket(
						level.getGameTime(), midnight, true));

		level.playSound(null, player.blockPosition(), ModSounds.PHASE_SHIFT, SoundSource.AMBIENT, 1.2f, 0.4f);
		player.displayClientMessage(net.minecraft.network.chat.Component
				.literal("ERR_404: chunk lighting recalculated").withStyle(net.minecraft.ChatFormatting.DARK_GRAY), true);
		LOG.add("night");
	}

	/**
	 * Hunger, instantly.
	 *
	 * <p>Nothing on screen explains it, and the player has no way to tell whether they simply
	 * forgot to eat. Applied as raw exhaustion rather than a hunger effect so it cannot be
	 * cancelled, and capped so it never takes the last two points of food: starving to death
	 * is not scary, it is tedious.</p>
	 */
	private static void eventHunger(ServerLevel level, ServerPlayer player) {
		var food = player.getFoodData();
		float target = Math.max(2.0f, food.getFoodLevel() - (4 + level.random.nextInt(6)));

		if (food.getFoodLevel() > target) {
			food.setFoodLevel((int) target);
		}

		food.setSaturation(0.0f);
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.HUNGER, 20 * 20, 2));

		level.playSound(null, player.blockPosition(), ModSounds.SUB_HIT, SoundSource.PLAYERS, 0.9f, 0.7f);
		LOG.add("hunger");
	}

	/**
	 * Silence.
	 *
	 * <p>Every sound within 24 blocks stops - footsteps, mobs, the ambience bed, the drone. It
	 * lasts eight seconds, and it exists because the loudest thing a horror mod can do is
	 * stop making noise: the player fills the gap themselves.</p>
	 */
	private static void eventSilence(ServerLevel level, ServerPlayer player) {
		for (var other : level.players()) {
			if (other.distanceTo(player) > 24) {
				continue;
			}

			// a client-side packet, so it cannot be cancelled by a server-side effect
			other.connection.send(new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(null, null));

			other.displayClientMessage(net.minecraft.network.chat.Component
					.literal("\\").withStyle(net.minecraft.ChatFormatting.DARK_GRAY), true);
		}

		LOG.add("silence");
	}

	/** A bolt out of a clear sky, on the block the player is about to walk onto. */
	private static void eventLightning(ServerLevel level, ServerPlayer player) {
		var ahead = player.position().add(player.getViewVector(1.0f).scale(6.0));
		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.EVENT);

		if (bolt == null) {
			return;
		}

		bolt.setPos(ahead.x, player.getY(), ahead.z);
		bolt.setVisualOnly(true); // scares, does not kill: the death event is its own thing
		level.addFreshEntity(bolt);

		level.playSound(null, ahead.x, ahead.y, ahead.z, ModSounds.SUB_HIT, SoundSource.AMBIENT, 1.5f, 0.4f);
		LOG.add("lightning_ahead");
	}

	/** Something spawns behind the player and immediately forgets it exists. */
	private static void eventMobs(ServerLevel level, ServerPlayer player) {
		var behind = player.position().subtract(player.getViewVector(1.0f).scale(10.0));
		BlockPos pos = BlockPos.containing(behind.x,
				level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
						(int) behind.x, (int) behind.z), behind.z);

		EntityType<?> type = switch (level.random.nextInt(4)) {
			case 0 -> EntityType.ZOMBIE;
			case 1 -> EntityType.SKELETON;
			case 2 -> EntityType.CREEPER;
			default -> EntityType.ENDERMAN;
		};

		var entity = type.create(level, EntitySpawnReason.EVENT);

		if (entity == null) {
			return;
		}

		entity.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);

		if (entity instanceof net.minecraft.world.entity.Mob mob) {
			mob.setPersistenceRequired();
			mob.setTarget(player);
		}

		level.addFreshEntity(entity);
		level.playSound(null, pos, ModSounds.STRUCTURE_APPEAR, SoundSource.HOSTILE, 1.2f, 0.7f);
		LOG.add("spawned " + type.toShortString());
	}

	/** Moves the player a few blocks in a random direction. Never into a wall, never into lava. */
	private static void eventTeleport(ServerLevel level, ServerPlayer player) {
		for (int attempt = 0; attempt < 8; attempt++) {
			double angle = level.random.nextDouble() * Math.PI * 2;
			double distance = 8 + level.random.nextDouble() * 10;
			int x = player.getBlockX() + (int) (Math.cos(angle) * distance);
			int z = player.getBlockZ() + (int) (Math.sin(angle) * distance);
			int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos target = new BlockPos(x, y, z);

			if (!level.getBlockState(target).isAir() || !level.getBlockState(target.above()).isAir()) {
				continue;
			}

			if (level.getBlockState(target.below()).getFluidState().isSource()
					|| level.getBlockState(target.below()).is(net.minecraft.tags.BlockTags.BEDS)) {
				continue;
			}

			level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1, player.getZ(), 40, 0.4, 0.8, 0.4, 0.1);
			player.teleportTo(x + 0.5, y, z + 0.5);
			level.playSound(null, target, ModSounds.PHASE_SHIFT, SoundSource.AMBIENT, 1.2f, 0.5f);
			LOG.add("displaced");

			if (level.random.nextInt(3) == 0) {
				player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 140, 0));
			}

			return;
		}
	}

	/** The server speaks. This is the only event that changes no block at all. */
	private static void eventMessage(MinecraftServer server, ServerLevel level, ServerPlayer player, IntegrityState state) {
		String[] lines = {
				"ERR_404: player entity desynced from world (recovered)",
				"ERR_404: block update packet rejected by client",
				"ERR_422: record edited while open",
				"WARN: entity count mismatch - 1 phantom",
				"ERR_404: " + state.deletedChunks().size() + " chunks have no owner",
		};

		String line = lines[level.random.nextInt(lines.length)];

		for (ServerPlayer other : server.getPlayerList().getPlayers()) {
			other.sendSystemMessage(Component.literal("[" + player.getGameProfile().name() + ": " + line + "]")
					.withStyle(ChatFormatting.DARK_GRAY));
		}

		LOG.add("message");
	}

	private static boolean insideAnchor(IntegrityState state, ServerPlayer player) {
		for (BlockPos anchor : state.anchorPositions()) {
			if (dev.error404.block.AnchorCoreBlock.protects(anchor, player.blockPosition())) {
				return true;
			}
		}

		return false;
	}

	public static void reset() {
		nextTick = 0L;
		fired = 0;
		LOG.clear();
	}

	public static int firedThisSession() {
		return fired;
	}
}
