package dev.error404.haunt;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;

import dev.error404.Error404;
import dev.error404.ModSounds;
import dev.error404.entity.Error422Entity;
import dev.error404.integrity.IntegrityState;
import dev.error404.integrity.Phase;

/**
 * The moment the mod stops being about the world and starts being about the player: 422
 * shows up inside the base.
 *
 * <p>Triggered by the player being stationary for a long time in the same small area during
 * CASCADE or later - i.e. hiding. It is deliberately not a random timer: the player earns it
 * by thinking they are safe. The entity appears 12-18 blocks away, *behind* them, inside the
 * walls if there is room, and the room's lights go out one by one.</p>
 *
 * <p>Fires at most once per 10 minutes, never when an anchor zone covers the player, and
 * never in creative.</p>
 */
public final class HomeInvasion {
	private static final int STILL_REQUIRED_TICKS = 20 * 100;
	private static final int INVASION_COOLDOWN_TICKS = 20 * 600;
	private static final int SAME_AREA_RADIUS = 12;

	private static long lastInvasionTick = -1L;
	/** Position + stillness tracked per dimension: a shared BlockPos made a Nether camper reset
	 * the overworld tracker, and distanceTo across dimensions is meaningless. */
	private static final java.util.Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, BlockPos> LAST_POS
			= new java.util.HashMap<>();
	private static final java.util.Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, Integer> STILL
			= new java.util.HashMap<>();

	private HomeInvasion() {
	}

	public static void tick(ServerLevel level) {
		IntegrityState state = IntegrityState.get(level.getServer());

		if (!state.active() || state.ended() || state.phase().ordinal() < Phase.CASCADE.ordinal()) {
			reset();
			return;
		}

		if (level.players().isEmpty()) {
			return;
		}

		// Track the player who is standing still the longest. Co-op is handled by looking at
		// all of them: if anyone is camping, the house gets a visitor.
		// the player who has been standing still longest, not simply the first in the list
		ServerPlayer camper = level.players().get(level.random.nextInt(level.players().size()));
		BlockPos camperPos = camper.blockPosition();

		var key = level.dimension();
		BlockPos lastPos = LAST_POS.get(key);
		int stillTicks = STILL.getOrDefault(key, 0);

		LAST_POS.put(key, camperPos);
		STILL.put(key, stillTicks);

		if (lastPos == null || camperPos.distSqr(lastPos) > (double) SAME_AREA_RADIUS * SAME_AREA_RADIUS) {
			STILL.put(key, 0);
			return;
		}

		stillTicks++;
		STILL.put(key, stillTicks);

		if (stillTicks < STILL_REQUIRED_TICKS) {
			return;
		}

		long now = state.ageTicks();

		if (lastInvasionTick >= 0 && now - lastInvasionTick < INVASION_COOLDOWN_TICKS) {
			return;
		}

		if (inAnchorZone(state, camper)) {
			STILL.put(key, 0);
			return;
		}

		if (invade(level, camper)) {
			lastInvasionTick = now;
			STILL.put(key, 0);
		}
	}

	private static boolean inAnchorZone(IntegrityState state, ServerPlayer player) {
		for (BlockPos anchor : state.anchorPositions()) {
			if (dev.error404.block.AnchorCoreBlock.protects(anchor, player.blockPosition())) {
				return true;
			}
		}

		return false;
	}

	private static boolean invade(ServerLevel level, ServerPlayer player) {
		// behind the player, inside the building if the space is there
		double[] offsets = {16.0, 12.0, 18.0};

		for (double distance : offsets) {
			var behind = player.position().subtract(player.getViewVector(1.0f).scale(distance));
			BlockPos base = BlockPos.containing(behind.x, player.getY(), behind.z);

			for (int dy = 0; dy <= 4; dy++) {
				BlockPos candidate = base.above(dy);

				if (!level.getBlockState(candidate).isAir()
						|| !level.getBlockState(candidate.above()).isAir()) {
					continue;
				}

				if (dev.error404.deletion.DeletionRules.isProtected(level, candidate)) {
					continue;
				}

				Error422Entity entity = dev.error404.entity.ModEntities.ERROR_422
						.create(level, EntitySpawnReason.EVENT);

				if (entity == null) {
					return false;
				}

				entity.setPos(candidate.getX() + 0.5, candidate.getY(), candidate.getZ() + 0.5);
				entity.setStalkTarget(player.blockPosition());
				level.addFreshEntity(entity);

				// the house reacts before the entity does
				killLights(level, player.blockPosition(), 14);
				level.playSound(null, player.blockPosition(), ModSounds.PHASE_SHIFT,
						SoundSource.AMBIENT, 1.0f, 0.35f);
				level.playSound(null, candidate, ModSounds.ENTITY_AMBIENT,
						SoundSource.HOSTILE, 1.0f, 0.6f);

				player.displayClientMessage(Component.literal("ERR_404: the room is smaller than it was")
						.withStyle(ChatFormatting.DARK_RED), false);

				for (ServerPlayer other : level.players()) {
					other.sendSystemMessage(Component.literal("[Server] " + player.getGameProfile().name()
							+ "'s residence failed validation").withStyle(ChatFormatting.YELLOW));
				}

				Error404.LOGGER.info("[error404] home invasion: 422 placed inside {}'s base at {}",
						player.getGameProfile().name(), candidate);
				return true;
			}
		}

		return false;
	}

	/** Puts out every light in the room, one by one, over about a second. */
	private static void killLights(ServerLevel level, BlockPos centre, int radius) {
		int removed = 0;

		for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-radius, -4, -radius), centre.offset(radius, 6, radius))) {
			if (removed >= 24) {
				return;
			}

			var state = level.getBlockState(pos);
			String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK
					.getKey(state.getBlock()).getPath();

			if (!path.contains("torch") && !path.contains("lantern") && !path.contains("candle")
					&& !state.is(net.minecraft.world.level.block.Blocks.GLOWSTONE)
					&& !path.contains("lamp") && !path.contains("sea_lantern")) {
				continue;
			}

			if (dev.error404.deletion.DeletionRules.isProtected(level, pos)) {
				continue;
			}

			dev.error404.deletion.DeletionJournal.record(level.dimension(), pos.getX() >> 4, pos.getZ() >> 4, pos, state);
			level.removeBlock(pos, false);
			level.playSound(null, pos, ModSounds.STATIC_BURST, SoundSource.BLOCKS, 0.5f, 1.5f);
			removed++;
		}
	}

	public static void reset() {
		LAST_POS.clear();
		STILL.clear();
	}
}
