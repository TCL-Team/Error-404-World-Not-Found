package dev.error404.deletion;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import dev.error404.Error404Config;
import dev.error404.block.AnchorCoreBlock;
import dev.error404.integrity.IntegrityState;

/**
 * The "do not touch" list of the deletion front.
 *
 * <p>Hardcore preset or not, some things must survive or the mod is simply unfair:
 * players, spawn, anchors, block entities (chests!), and unbreakable blocks.</p>
 */
public final class DeletionRules {
	private DeletionRules() {
	}

	public static boolean isProtected(ServerLevel level, BlockPos pos) {
		Error404Config cfg = Error404Config.get();

		// never touch the void floor itself
		if (pos.getY() <= level.getMinY() + 1) {
			return true;
		}

		BlockState state = level.getBlockState(pos);

		if (state.isAir()) {
			return true;
		}

		// dragon eggs, ender chests, beacons: deleting these breaks progression or spawn points
		if (state.is(Blocks.BEDROCK) || state.is(Blocks.BARRIER) || state.is(Blocks.COMMAND_BLOCK)
				|| state.is(Blocks.STRUCTURE_BLOCK) || state.is(Blocks.JIGSAW)
				|| state.is(Blocks.END_GATEWAY) || state.is(Blocks.DRAGON_EGG)
				|| state.is(Blocks.RESPAWN_ANCHOR)
				|| state.is(Blocks.VAULT) || state.is(Blocks.TRIAL_SPAWNER)
				|| state.is(Blocks.END_PORTAL_FRAME) || state.is(Blocks.END_PORTAL)
				|| state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.SPAWNER)
				|| state.is(dev.error404.ModBlocks.ANCHOR_CORE)) {
			return true;
		}

		if (cfg.protectBlockEntities && state.hasBlockEntity()) {
			return true;
		}

		if (pos.distSqr(level.getRespawnData().globalPos().pos()) < 48 * 48) {
			return true;
		}

		IntegrityState integrity = IntegrityState.get(level.getServer());

		// anchor cores create permanent stability zones
		for (BlockPos anchor : integrity.anchorPositions()) {
			if (AnchorCoreBlock.protects(anchor, pos)) {
				return true;
			}
		}

		// never delete around a living player
		double safe = cfg.playerSafeRadius;

		for (ServerPlayer player : level.players()) {
			if (player.isCreative() || player.isSpectator()) {
				continue;
			}

			if (player.blockPosition().distSqr(pos) < safe * safe) {
				return true;
			}
		}

		BlockEntity blockEntity = level.getBlockEntity(pos);

		return blockEntity != null && cfg.protectBlockEntities;
	}

	/** Chunk-level check used by the front before it does any work at all. */
	public static boolean isChunkProtected(ServerLevel level, int chunkX, int chunkZ) {
		List<BlockPos> anchors = IntegrityState.get(level.getServer()).anchorPositions();

		for (BlockPos anchor : anchors) {
			int ax = anchor.getX() >> 4;
			int az = anchor.getZ() >> 4;
			int radius = AnchorCoreBlock.PROTECTED_RADIUS >> 4;

			if (Math.abs(ax - chunkX) <= radius && Math.abs(az - chunkZ) <= radius) {
				return true;
			}
		}

		return false;
	}
}
