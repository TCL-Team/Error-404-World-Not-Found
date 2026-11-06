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
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(server, handler.getPlayer()));

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

				state.addAnchor(target);
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

		// Safety first: back the world up before anything gets deleted.
		WorldBackup.backup(server, false);
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
			DeletionFront.tick(server);
			AnomalyManager.tick(server);
			dev.error404.haunt.PhantomServer.tick(server);
			dev.error404.haunt.HomeInvasion.tick(server.overworld());
			dev.error404.haunt.Chaos.tick(server);

			// The chunk the player is standing in can be reclaimed. It is announced first.
			dev.error404.deletion.PersonalCollapse.tick(server.overworld());

			if (server.getTickCount() % 200 == 0) {
				for (ServerPlayer player : server.overworld().players()) {
					if (server.overworld().random.nextInt(3) == 0
							&& dev.error404.deletion.PersonalCollapse.allowed(server.overworld(), player)) {
						dev.error404.deletion.PersonalCollapse.start(server.overworld(), player,
								dev.error404.deletion.PersonalCollapse.WARNING_TICKS);
						break;
					}
				}
			}

			// One path sample per second is enough to replay a minute of walking.
			if (server.getTickCount() % 20 == 0) {
				dev.error404.haunt.PathMemory.sample(server.overworld());

				for (ServerPlayer player : server.overworld().players()) {
					dev.error404.haunt.PlayerModel.observe(player);
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

		ServerLevel level = server.overworld();

		if (level == null) {
			return;
		}

		int charge = 0;

		for (ServerPlayer player : level.players()) {
			int cx = player.getBlockX() >> 4;
			int cz = player.getBlockZ() >> 4;

			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					if (state.markChunkSeen(cx + dx, cz + dz)) {
						charge += cfg.newChunkDrainHundredths;
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

		ServerLevel level = server.overworld();

		if (level == null) {
			return;
		}

		boolean inside = false;

		for (ServerPlayer player : level.players()) {
			for (BlockPos anchor : state.anchorPositions()) {
				if (AnchorCoreBlock.protects(anchor, player.blockPosition())) {
					inside = true;
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
			level.playSound(null, level.players().getFirst().blockPosition(), ModSounds.ANCHOR_HUM,
					net.minecraft.sounds.SoundSource.BLOCKS, 0.6f, 1.2f);
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
