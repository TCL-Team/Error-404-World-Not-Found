package dev.error404.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;

import dev.error404.Error404;

/**
 * The player's only way to hold a piece of the world together.
 *
 * <p>Placing one creates a stability zone: the deletion front refuses to touch anything
 * inside {@link #PROTECTED_RADIUS} blocks of it. The anchor itself is invulnerable to the
 * deletion system (players can still break it on purpose).</p>
 */
public class AnchorCoreBlock extends Block {
	public static final int PROTECTED_RADIUS = 32;
	public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

	public AnchorCoreBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(ACTIVE, true));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(ACTIVE);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(3) != 0) {
			return;
		}

		for (int i = 0; i < 2; i++) {
			double x = pos.getX() + 0.2 + random.nextDouble() * 0.6;
			double y = pos.getY() + 1.0 + random.nextDouble() * 900.0;
			double z = pos.getZ() + 0.2 + random.nextDouble() * 0.6;
			level.addParticle(ParticleTypes.END_ROD, x, y, z, 0.0, 0.004, 0.0);
		}
	}

	/** True when {@code pos} sits inside the stability zone centred on {@code anchorPos}. */
	public static boolean protects(BlockPos anchorPos, BlockPos pos) {
		return pos.distSqr(anchorPos) < (double) PROTECTED_RADIUS * PROTECTED_RADIUS;
	}

	public static AABB protectionBox(BlockPos anchorPos) {
		return new AABB(anchorPos).inflate(PROTECTED_RADIUS);
	}

	/**
	 * The SALVAGE ritual: an anchor that already has the eight fragments committed, right
	 * clicked in the final phase, rewrites the world instead of just holding it together.
	 *
	 * <p>No GUI, no menu - the moment is the reward. See
	 * {@link dev.error404.ending.EndingManager#triggerSalvage}.</p>
	 */
	@Override
	protected InteractionResult useWithoutItem(net.minecraft.world.level.block.state.BlockState state, Level level,
			BlockPos pos, Player player, BlockHitResult hit) {
		if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.SUCCESS;
		}

		dev.error404.integrity.IntegrityState integrity = dev.error404.integrity.IntegrityState.get(serverLevel.getServer());

		if (integrity.ended()) {
			return InteractionResult.PASS;
		}

		int held = player.getInventory().countItem(dev.error404.ModItems.DATA_FRAGMENT);

		// Fragments still in the inventory are committed on the spot - the player does not
		// have to right-click each one first, the ritual is the moment that matters.
		int missing = Math.min(8 - integrity.dataFragments(), held);

		if (missing > 0) {
			int taken = player.getInventory().clearOrCountMatchingItems(
					stack -> stack.is(dev.error404.ModItems.DATA_FRAGMENT), missing, null);

			for (int i = 0; i < taken; i++) {
				integrity.addFragment();
			}
		}

		if (integrity.dataFragments() < 8) {
			player.displayClientMessage(net.minecraft.network.chat.Component
					.literal("ERR_404: anchor needs 8 fragments (" + integrity.dataFragments() + "/8 committed)")
					.withStyle(net.minecraft.ChatFormatting.GRAY), true);
			level.playSound(null, pos, dev.error404.ModSounds.ERROR_BLIP,
					net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 0.7f);
			return InteractionResult.CONSUME;
		}

		dev.error404.ending.EndingManager.triggerSalvage(serverLevel.getServer(),
				player instanceof net.minecraft.server.level.ServerPlayer serverPlayer ? serverPlayer : null);

		return InteractionResult.CONSUME;
	}

	/** Fired from {@link dev.error404.event.ServerEvents} when a player places an anchor. */
	public static void onPlacedBy(Player player, BlockPos pos) {
		Error404.LOGGER.info("[error404] anchor core placed by {} at {}", player.getName().getString(), pos);
	}
}
