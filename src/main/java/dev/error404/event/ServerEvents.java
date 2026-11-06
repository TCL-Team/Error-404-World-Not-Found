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
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> AnomalyManager.clear());

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
				level.playSound(null, target, ModSounds.ANCHOR_HUM, net.minecraft.sounds.SoundSource.BLOCKS, 0.9f, 1.0f);
				serverPlayer.sendSystemMessage(Component.literal("ERR_404: stability zone created (r=" + AnchorCoreBlock.PROTECTED_RADIUS + ")").withStyle(ChatFormatting.AQUA));
			}

			return InteractionResult.PASS;
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

		if (Error404Config.get().enabled) {
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

	/** Right-click a void pearl: escape 16 blocks straight up, at a cost. */
	private static boolean useVoidPearl(ServerPlayer player, ServerLevel level, net.minecraft.world.InteractionHand hand) {
		IntegrityState state = IntegrityState.get(level.getServer());
		state.drain(1);

		player.playSound(ModSounds.DATA_CHIRP, 1.0f, 0.6f);
		player.teleportTo(player.getX(), player.getY() + 16, player.getZ());
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL, player.getX(), player.getY(), player.getZ(), 40, 0.5, 1.0, 0.5, 0.1);

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
