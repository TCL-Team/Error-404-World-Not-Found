package dev.error404.deletion;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import dev.error404.Difficulty;
import dev.error404.ModSounds;
import dev.error404.entity.Damage;
import dev.error404.integrity.IntegrityState;
import dev.error404.integrity.Phase;

/**
 * The chunk the player is standing in stops existing.
 *
 * <p>This is the single most ERROR-422 thing in the mod and it is deliberately not random:
 * it is announced, counted down, and escapable. A collapse that just happens is a bug report;
 * a collapse with a nine second countdown on screen, the ground already flickering, and a
 * sprint to nearby solid ground is a story.</p>
 *
 * <p>Flow:</p>
 * <ol>
 *   <li>Warning: chat line + title + a sound that only plays for this event.</li>
 *   <li>Eight seconds of flicker: particles under the player, blocks around them begin to
 *       fail to render on the client (sent as a fake block-update burst).</li>
 *   <li>If the player is still inside the chunk when it fires: the chunk collapses, they
 *       fall into the void, and take world-deletion damage. If they got out, the chunk
 *       collapses behind them and they watch it go.</li>
 * </ol>
 */
public final class PersonalCollapse {
	public static final int WARNING_TICKS = 20 * 9;

	private static ServerPlayer target;
	private static int countdown;
	private static int chunkX;
	private static int chunkZ;
	/** The 20x20 zone is centred here, in block coords, at the moment the warning starts. */
	private static int centreX;
	private static int centreZ;

	private PersonalCollapse() {
	}

	public static boolean isPending() {
		return target != null;
	}

	/** Starts a collapse on the player's current chunk. Returns false if it is not allowed. */
	public static boolean start(ServerLevel level, ServerPlayer player, int warningTicks) {
		IntegrityState state = IntegrityState.get(level.getServer());

		if (!state.active() || state.ended() || isPending()) {
			return false;
		}

		if (isSafe(level, player, state)) {
			return false;
		}

		target = player;
		countdown = Math.max(20, warningTicks);
		chunkX = player.getBlockX() >> 4;
		chunkZ = player.getBlockZ() >> 4;
		centreX = player.getBlockX();
		centreZ = player.getBlockZ();

		player.sendSystemMessage(Component.literal("ERR_404: your chunk has no owner. it is being reclaimed.")
				.withStyle(net.minecraft.ChatFormatting.DARK_RED));
		player.displayClientMessage(Component.literal("YOU ARE STANDING IN A DELETED CHUNK")
				.withStyle(net.minecraft.ChatFormatting.RED), true);
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
				Component.literal("NO SAVE DATA").withStyle(net.minecraft.ChatFormatting.DARK_RED)));
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
				Component.literal("nobody wrote this chunk down").withStyle(net.minecraft.ChatFormatting.GRAY)));
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(10, 60, 20));

		level.playSound(null, player.blockPosition(), ModSounds.ERROR_TONE, SoundSource.AMBIENT, 1.4f, 0.4f);
		level.playSound(null, player.blockPosition(), ModSounds.HEARTBEAT, SoundSource.AMBIENT, 1.6f, 0.5f);

		return true;
	}

	/** Called every server tick. */
	public static void tick(ServerLevel level) {
		if (target == null) {
			return;
		}

		if (target.isRemoved() || target.hasDisconnected() || !level.getServer().isRunning()) {
			cancel();
			return;
		}

		countdown--;

		// The ground starts failing before it goes: particles at the player's feet, and the
		// heartbeat gets faster as the number gets smaller.
		if (countdown % 4 == 0) {
			level.sendParticles(ParticleTypes.PORTAL, target.getX(), target.getY() + 0.2, target.getZ(),
					12, 2.5, 0.2, 2.5, 0.12);
		}

		if (countdown % 20 == 0) {
			int seconds = Math.max(0, countdown / 20);
			target.displayClientMessage(Component.literal("chunk deletion in " + seconds)
					.withStyle(seconds <= 3 ? net.minecraft.ChatFormatting.RED : net.minecraft.ChatFormatting.GRAY), true);
			level.playSound(null, target.blockPosition(), ModSounds.HEARTBEAT, SoundSource.AMBIENT,
					0.6f + (WARNING_TICKS - countdown) / (float) WARNING_TICKS, 0.6f);
			target.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 40, 0));
		}

		if (countdown > 0) {
			return;
		}

		fire(level);
	}

	private static void fire(ServerLevel level) {
		ServerPlayer player = target;
		cancel();

		if (player == null || player.isRemoved()) {
			return;
		}

		// The zone is 20x20 blocks and centred on where the player is standing, so "inside" is
		// a rectangle rather than a chunk: a player who sprints two blocks sideways is still
		// inside it, and that is intentional.
		boolean stillInside = Math.abs(player.getBlockX() - centreX) <= DeleteZone.SIZE / 2
				&& Math.abs(player.getBlockZ() - centreZ) <= DeleteZone.SIZE / 2;

		boolean seeded = DeleteZone.seedAtPlayer(level, player);

		if (!seeded) {
			return;
		}

		if (stillInside) {
			// They gambled and lost: the floor is gone, they are in the void, and the damage
			// type says what actually happened.
			player.teleportTo(player.getX(), level.getMinY() - 24.0, player.getZ());
			player.hurtServer(level, Damage.worldDeleted(level), 8.0f);
			player.displayClientMessage(Component.literal("ERR_FATAL: player position not found in chunk")
					.withStyle(net.minecraft.ChatFormatting.DARK_RED), false);
			level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(
					player.getGameProfile().name() + " was deleted with their chunk")
					.withStyle(net.minecraft.ChatFormatting.DARK_RED), false);
		} else {
			// They made it. Reward the sprint with the sound of the thing they escaped.
			player.sendSystemMessage(Component.literal("ERR_404: 20x20 zone at " + centreX + ", "
					+ centreZ + " reclaimed. you are not in it.").withStyle(net.minecraft.ChatFormatting.GRAY));
			level.playSound(null, player.blockPosition(), ModSounds.SUB_HIT, SoundSource.AMBIENT, 1.2f, 0.7f);
		}

		IntegrityState.get(level.getServer()).drain(2);
	}

	/** Never on the first sighting, never in a safe config, never for a protected player. */
	public static boolean allowed(ServerLevel level, ServerPlayer player) {
		IntegrityState state = IntegrityState.get(level.getServer());

		if (!state.active() || state.ended() || isPending()) {
			return false;
		}

		if (state.phase().ordinal() < Phase.CASCADE.ordinal()) {
			return false;
		}

		if (state.deaths() < 1) {
			// The first one has to be survivable, or it is just a kill. It happens once the
			// player already understands the mod hurts.
			return false;
		}

		return !isSafe(level, player, state);
	}

	private static boolean isSafe(ServerLevel level, ServerPlayer player, IntegrityState state) {
		if (player.isCreative() || player.isSpectator() || player.isDeadOrDying()) {
			return true;
		}

		Difficulty difficulty = Difficulty.current();

		if (difficulty == Difficulty.CHILL) {
			return true;
		}

		for (BlockPos anchor : state.anchorPositions()) {
			if (dev.error404.block.AnchorCoreBlock.protects(anchor, player.blockPosition())) {
				return true;
			}
		}

		return false;
	}

	public static void cancel() {
		target = null;
		countdown = 0;
	}

	public static ServerPlayer target() {
		return target;
	}
}
