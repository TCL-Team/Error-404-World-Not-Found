package dev.error404.event;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

import dev.error404.Error404;
import dev.error404.Error404Config;
import dev.error404.ModBlocks;
import dev.error404.ModItems;
import dev.error404.ModSounds;
import dev.error404.anomaly.AnomalyManager;
import dev.error404.block.AnchorCoreBlock;
import dev.error404.deletion.DeletionFront;
import dev.error404.deletion.WorldBackup;
import dev.error404.integrity.IntegrityState;
import dev.error404.integrity.Phase;
import dev.error404.network.EffectPayload;
import dev.error404.network.ModNetworking;

/**
 * Wires the mod into the server lifecycle and keeps the client in sync.
 *
 * <p>Also the home of the mod's "positive" interactions: anchors create stability zones,
 * void pearls teleport out of a deletion front that is about to eat you.</p>
 */
public final class ServerEvents {
	private static int syncCooldown = 0;
	private static Phase lastSyncedPhase = null;

	private ServerEvents() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(ServerEvents::onServerStarted);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			AnomalyManager.clear();
			dev.error404.haunt.PhantomServer.reset();
			dev.error404.haunt.HomeInvasion.reset();
			dev.error404.haunt.Chaos.reset();
			dev.error404.haunt.PathMemory.clear();
			dev.error404.deletion.PersonalCollapse.cancel();
			dev.error404.haunt.PlayerModel.clear();
			dev.error404.haunt.Scares.reset();
			dev.error404.haunt.Malicious.reset();
			dev.error404.deletion.DeleteZone.resume();
			dev.error404.haunt.ServerSpine.restore(server);
			dev.error404.haunt.Presence.reset();
			dev.error404.haunt.Disturbing.reset();
			dev.error404.deletion.DeleteZone.reset();
			dev.error404.commands.CommandGate.reset();
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(server, handler.getPlayer()));

		// Disconnect cleanup. Without this the mod kept a habit profile and a path history for
		// every player who had ever joined, for the lifetime of the server process.
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			var player = handler.getPlayer();
			dev.error404.haunt.PlayerModel.forget(player.getUUID());
			dev.error404.haunt.PathMemory.forget(player.getUUID());
			dev.error404.haunt.Scares.forget(player.getUUID());
			dev.error404.network.ModNetworking.forgetState(player.getUUID());
		});

		ServerTickEvents.END_SERVER_TICK.register(ServerEvents::onEndTick);

		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.PASS;
			}

			ItemStack stack = player.getItemInHand(hand);

			if (stack.is(ModItems.VOID_PEARL)) {
				return useVoidPearl(serverPlayer, (ServerLevel) level, hand) ? InteractionResult.SUCCESS : InteractionResult.PASS;
			}

			if (stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() == ModBlocks.ANCHOR_CORE) {
				BlockPos target = hitResult.getBlockPos().relative(hitResult.getDirection());
				IntegrityState state = IntegrityState.get(level.getServer());

				if (state.anchorPositions().size() >= 3) {
					serverPlayer.sendSystemMessage(Component.literal("ERR_404: no more than 3 anchors can exist").withStyle(ChatFormatting.RED));
					return InteractionResult.FAIL;
				}

				state.addAnchor(((ServerLevel) level).dimension(), target);
				AnchorCoreBlock.onPlacedBy(serverPlayer, target);
				dev.error404.Achievements.grant(serverPlayer, dev.error404.Achievements.ANCHOR_DOWN);
				level.playSound(null, target, ModSounds.ANCHOR_HUM, net.minecraft.sounds.SoundSource.BLOCKS, 0.9f, 1.0f);
				serverPlayer.sendSystemMessage(Component.literal("ERR_404: stability zone created (r=" + AnchorCoreBlock.PROTECTED_RADIUS + ")").withStyle(ChatFormatting.AQUA));
			}

			return InteractionResult.PASS;
		});

		// A death in the session is not a setback, it is a meal: every 422 in the world gets
		// faster, and the player is told - in the death message - that it noticed.
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(entity instanceof ServerPlayer player)) {
				return;
			}

			MinecraftServer server = player.level().getServer();

			if (server == null || !Error404Config.get().enabled) {
				return;
			}

			ServerLevel level = player.level() instanceof ServerLevel sl ? sl : server.overworld();

			for (dev.error404.entity.Error422Entity error422 : level.getEntitiesOfClass(
					dev.error404.entity.Error422Entity.class, player.getBoundingBox().inflate(256.0))) {
				error422.feedOnDeath();
			}

			IntegrityState state = IntegrityState.get(server);
			state.drain(3);
			state.registerDeath();
			ModNetworking.broadcastEffect(server, ModNetworking.EFFECT_DESATURATE, "ERR_422", 60);

			// It fed, it left. Five minutes of not being hunted, so the player can go and be
			// sad about their stuff instead of dying again at their own death point.
			AnomalyManager.withdrawFor(server, 20L * 300L);

			for (dev.error404.entity.Error422Entity error422 : level.getEntitiesOfClass(
					dev.error404.entity.Error422Entity.class, player.getBoundingBox().inflate(48.0))) {
				error422.vanish();
			}

			player.sendSystemMessage(Component.literal("ERR_422: acknowledged. it has what it came for.")
					.withStyle(ChatFormatting.DARK_GRAY));
		});

		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (state.is(ModBlocks.ANCHOR_CORE) && level instanceof ServerLevel serverLevel) {
				IntegrityState.get(serverLevel.getServer()).removeAnchor(pos);

				if (player instanceof ServerPlayer serverPlayer) {
					serverPlayer.sendSystemMessage(Component.literal("ERR_404: stability zone lost").withStyle(ChatFormatting.RED));
				}
			}
		});
	}

	private static void onServerStarted(MinecraftServer server) {
		if (!Error404Config.get().enabled) {
			return;
		}

		// Safety first, but never on the server thread: zipping a region folder blocks the first
		// join for as long as it takes.
		WorldBackup.backupAsync(server);
		Error404.LOGGER.info("[error404] server ready (backupOnStart={}, realBlockDeletion={})",
				Error404Config.get().backupOnStart, Error404Config.get().realBlockDeletion);
	}

	private static void onJoin(MinecraftServer server, ServerPlayer player) {
		IntegrityState state = IntegrityState.get(server);

		if (Error404Config.get().enabled && Error404Config.get().announceOnJoin) {
			player.sendSystemMessage(Component.literal("[Server] error404 loaded. /error404 status, /error404 stop, /error404 restore")
					.withStyle(ChatFormatting.DARK_GRAY));
		}

		ModNetworking.sendState(player, server);
	}

	private static void onEndTick(MinecraftServer server) {
		Error404Config cfg = Error404Config.get();

		if (!cfg.enabled) {
			return;
		}

		IntegrityState state = IntegrityState.get(server);

		if (state.active() && !state.ended()) {
			// Zones advance every tick: their own wave timer paces the blocks, and calling them
			// from the (throttled) deletion front would slow the front down to a crawl.
			dev.error404.haunt.Malicious.restoreNames(server);

			if (server.getTickCount() % 20 == 0) {
				dev.error404.haunt.ServerSpine.tick(server);
			}
			dev.error404.deletion.DeleteZone.tick(server.overworld());
			DeletionFront.tick(server);
			AnomalyManager.tick(server);
			dev.error404.haunt.PhantomServer.tick(server);

			// Every dimension, not just the overworld. A player who runs to the Nether to escape
			// the mod used to escape it completely - 422 never followed, chaos never fired and
			// the deletion front never reached them. That was the largest hole in the design.
			for (ServerLevel dimension : server.getAllLevels()) {
				if (dimension.players().isEmpty()) {
					continue;
				}

				dev.error404.anomaly.AnomalyManager.tick(server, dimension);
				dev.error404.haunt.HomeInvasion.tick(dimension);
				dev.error404.haunt.Chaos.tick(server, dimension);
				dev.error404.haunt.Disturbing.tick(dimension);
				dev.error404.deletion.PersonalCollapse.tick(dimension);
				dev.error404.haunt.Scares.tick(dimension);
				dev.error404.haunt.Malicious.tick(dimension);
				dev.error404.haunt.Presence.tick(dimension);
				dev.error404.deletion.DeletionFront.tickDimension(server, dimension);
			}

			// The chunk the player is standing in can be reclaimed. It is announced first.
			dev.error404.deletion.PersonalCollapse.tick(server.overworld());

			if (server.getTickCount() % 200 == 0) {
				for (ServerLevel dimension : server.getAllLevels()) {
					for (ServerPlayer player : dimension.players()) {
						if (dimension.random.nextInt(3) == 0
								&& dev.error404.deletion.PersonalCollapse.allowed(dimension, player)) {
							dev.error404.deletion.PersonalCollapse.start(dimension, player,
									dev.error404.deletion.PersonalCollapse.WARNING_TICKS);
							break;
						}
					}
				}
			}

			// One path sample per second is enough to replay a minute of walking.
			if (server.getTickCount() % 20 == 0) {
				for (ServerLevel dimension : server.getAllLevels()) {
					dev.error404.haunt.PathMemory.sample(dimension);

					for (ServerPlayer player : dimension.players()) {
						dev.error404.haunt.PlayerModel.observe(player);
					}
				}
			}
			tickExplorationCost(server, state, cfg);
			tickAnchorRecovery(server, state, cfg);
		}

		// 4x per second is plenty for a HUD-ish sync, and keeps the payload cheap.
		if (syncCooldown-- > 0) {
			return;
		}

		syncCooldown = 5;

		boolean phaseChanged = lastSyncedPhase != state.phase();
		ModNetworking.broadcastState(server);

		if (phaseChanged) {
			ModNetworking.broadcastEffect(server, ModNetworking.EFFECT_PHASE_SHIFT, state.phase().name(), 60);
			lastSyncedPhase = state.phase();
		}
	}

	/**
	 * New ground costs integrity: every chunk the party uncovers for the first time charges
	 * {@code newChunkDrainHundredths} hundredths of a point. That is what makes exploring -
	 * the core activity of Minecraft - the thing that kills the world.
	 *
	 * <p>Runs at most once per second and only looks at chunks around players that are already
	 * loaded, so it never causes chunk generation by itself.</p>
	 */
	private static void tickExplorationCost(MinecraftServer server, IntegrityState state, Error404Config cfg) {
		if (cfg.newChunkDrainHundredths <= 0 || server.getTickCount() % 20 != 0) {
			return;
		}

		int charge = 0;

		// every dimension: exploring the Nether used to be free, which made a nether hub the
		// cheapest way to survive the mod
		for (ServerLevel level : server.getAllLevels()) {
		for (ServerPlayer player : level.players()) {
			int cx = player.getBlockX() >> 4;
			int cz = player.getBlockZ() >> 4;

			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					if (state.markChunkSeen(level.dimension(), cx + dx, cz + dz)) {
						charge += cfg.newChunkDrainHundredths;
					}
				}
			}
		}
		}

		while (charge >= 100) {
			charge -= 100;
			state.drain(1);
		}

		if (charge > 0) {
			// Sub-point charges are kept in the runtime accumulator so nothing is lost.
			state.drainHundredths(charge);
		}
	}

	/** Standing inside a 32-block anchor zone slowly gives the world back. */
	private static void tickAnchorRecovery(MinecraftServer server, IntegrityState state, Error404Config cfg) {
		if (cfg.anchorRecoveryPer30s <= 0 || state.anchorPositions().isEmpty()) {
			return;
		}

		boolean inside = false;

		// an anchor in the Nether has to heal the world too, and only for players actually
		// standing in it
		for (ServerLevel level : server.getAllLevels()) {
			for (ServerPlayer player : level.players()) {
				for (BlockPos anchor : state.anchorPositions(level.dimension())) {
					if (AnchorCoreBlock.protects(anchor, player.blockPosition())) {
						inside = true;
						break;
					}
				}

				if (inside) {
					break;
				}
			}

			if (inside) {
				break;
			}
		}

		if (!inside) {
			state.resetAnchorAccumulator();
			return;
		}

		long ticksPerUnit = 30L * 20L;
		int units = state.accumulateAnchorTicks(1L, ticksPerUnit);

		if (units > 0) {
			state.restore(units * cfg.anchorRecoveryPer30s);
			// the hum plays at the anchor that is doing the recovering, so it is always audible
			for (ServerLevel dimension : server.getAllLevels()) {
				for (BlockPos anchor : state.anchorPositions(dimension.dimension())) {
					if (!dimension.players().isEmpty()
							&& dimension.players().stream().anyMatch(p -> AnchorCoreBlock.protects(anchor, p.blockPosition()))) {
						dimension.playSound(null, anchor, ModSounds.ANCHOR_HUM,
								net.minecraft.sounds.SoundSource.BLOCKS, 0.6f, 1.2f);
					}
				}
			}
		}
	}

	/** Right-click a void pearl: escape 16 blocks straight up, at a cost. */
	private static boolean useVoidPearl(ServerPlayer player, ServerLevel level, net.minecraft.world.InteractionHand hand) {
		IntegrityState state = IntegrityState.get(level.getServer());
		state.drain(1);

		// Find real headroom instead of assuming 16 blocks of it: teleporting a player into
		// stone is a death sentence that has nothing to do with the entity.
		int targetY = -1;

		for (int dy = 12; dy <= 48; dy += 4) {
			BlockPos candidate = player.blockPosition().above(dy);

			if (level.getBlockState(candidate).isAir()
					&& level.getBlockState(candidate.above()).isAir()
					&& level.getBlockState(candidate.above(2)).isAir()) {
				targetY = candidate.getY();
				break;
			}
		}

		if (targetY < 0) {
			player.displayClientMessage(Component.literal("ERR_404: no gap big enough to fall through")
					.withStyle(ChatFormatting.DARK_RED), true);
			return false;
		}

		player.playSound(ModSounds.DATA_CHIRP, 1.0f, 0.6f);
		player.teleportTo(player.getX(), targetY, player.getZ());
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.SLOW_FALLING, 120, 0, false, false));
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL, player.getX(), player.getY(), player.getZ(), 40, 0.5, 1.0, 0.5, 0.1);

		// It follows the pearl. Not instantly - but it knows where you went.
		for (dev.error404.entity.Error422Entity error422 : level.getEntitiesOfClass(
				dev.error404.entity.Error422Entity.class, player.getBoundingBox().inflate(48.0))) {
			error422.setStalkTarget(player.blockPosition());
		}

		if (!player.getAbilities().instabuild) {
			player.getItemInHand(hand).shrink(1);
		}

		return true;
	}

	/** Kept for the command layer: force a state push immediately. */
	public static void pushState(MinecraftServer server) {
		ModNetworking.broadcastState(server);
	}

	/** One-shot overlay on every client (used by the 422 stare attack and endings). */
	public static void flashGlitch(MinecraftServer server, String message) {
		ModNetworking.broadcastEffect(server, ModNetworking.EFFECT_STATIC, message, 14);
	}

	/**
	 * SALVAGE ending housekeeping: the deletion front is told to stop touching new chunks,
	 * anomalies are allowed to stay (they are the proof the player survived something), but
	 * their revocation timers are cleared so the world stops dissolving.
	 */
	public static void clearAnomaliesAfterSalvage(MinecraftServer server) {
		AnomalyManager.freeze();
		DeletionFront.hold(server);
	}
}
