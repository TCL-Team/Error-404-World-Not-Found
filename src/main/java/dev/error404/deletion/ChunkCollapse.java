package dev.error404.deletion;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.core.particles.ParticleTypes;

import dev.error404.ModSounds;
import dev.error404.integrity.IntegrityState;

/**
 * Makes a chunk visibly stop existing, in front of the player, in about a second.
 *
 * <p>The old deletion front removed 256 blocks per pass from ring chunks, which is why nobody
 * ever saw it happen: from the player's viewpoint a few distant blocks quietly turned to air
 * somewhere behind a hill. This class deletes a chunk the way the game would if it failed to
 * load - everything above the void floor in one go - and does it in <b>view</b>: it strips the
 * chunk, then pushes a full chunk packet so the client redraws the hole immediately instead of
 * waiting for the next block-update batch.</p>
 *
 * <p>It is bounded on purpose: {@link #MAX_BLOCKS} blocks, journaled through
 * {@link DeletionJournal} so {@code /error404 restore} puts it back, and it never touches a
 * chunk that contains a player, an anchor, the world spawn, or a protected block.</p>
 */
public final class ChunkCollapse {
	/** What DeletionJournal can remember for one chunk; collapses are always fully restorable. */
	public static final int MAX_BLOCKS = DeletionJournal.PER_CHUNK;

	private ChunkCollapse() {
	}

	/**
	 * Removes everything above the void floor in the chunk at {@code chunkX/chunkZ}.
	 *
	 * @param notify whether to play the collapse sound and particles (false for silent background work)
	 * @return number of blocks removed, 0 if the chunk was skipped
	 */
	public static int collapse(ServerLevel level, int chunkX, int chunkZ, boolean notify) {
		if (!level.getChunkSource().hasChunk(chunkX, chunkZ)) {
			return 0;
		}

		LevelChunk chunk = level.getChunk(chunkX, chunkZ);

		if (isProtected(level, chunk, chunkX, chunkZ)) {
			return 0;
		}

		int minY = level.getMinY() + 1;
		int removed = 0;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

		for (int x = chunkX << 4; x < (chunkX << 4) + 16; x++) {
			for (int z = chunkZ << 4; z < (chunkZ << 4) + 16; z++) {
				int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);

				for (int y = top; y >= minY && removed < MAX_BLOCKS; y--) {
					cursor.set(x, y, z);
					BlockState state = level.getBlockState(cursor);

					if (state.isAir()) {
						continue;
					}

					if (DeletionRules.isProtected(level, cursor)) {
						continue;
					}

					DeletionJournal.record(chunkX, chunkZ, cursor, state);
					level.setBlock(cursor, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					removed++;
				}
			}
		}

		if (removed == 0) {
			return 0;
		}

		// Residue: a thin floor of void stone so the hole reads as "deleted", not "exploded".
		for (int i = 0; i < 6; i++) {
			int x = (chunkX << 4) + level.random.nextInt(16);
			int z = (chunkZ << 4) + level.random.nextInt(16);
			int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
			BlockPos pos = new BlockPos(x, y, z);

			if (!DeletionRules.isProtected(level, pos) && level.getBlockState(pos).isAir()) {
				level.setBlock(pos, dev.error404.ModBlocks.VOID_STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}

		syncChunk(level, chunk);
		IntegrityState.get(level.getServer()).markDeletedChunk(chunkX, chunkZ);

		if (notify) {
			double cx = (chunkX << 4) + 8;
			double cz = (chunkZ << 4) + 8;

			level.playSound(null, cx, level.getSeaLevel(), cz, ModSounds.CHUNK_DELETE,
					SoundSource.AMBIENT, 2.0f, 0.55f);
			level.playSound(null, cx, level.getSeaLevel(), cz, ModSounds.SUB_HIT,
					SoundSource.AMBIENT, 1.6f, 0.7f);
			level.sendParticles(ParticleTypes.PORTAL, cx, level.getSeaLevel() + 4, cz,
					260, 8.0, 6.0, 8.0, 0.35);
		}

		return removed;
	}

	/**
	 * Forces the client to redraw the whole chunk now. Without this the hole appears whenever
	 * the block updates happen to be flushed, which is the difference between "a chunk was
	 * deleted" and "the ground under me is gone".
	 */
	private static void syncChunk(ServerLevel level, LevelChunk chunk) {
		ClientboundLevelChunkWithLightPacket packet = new ClientboundLevelChunkWithLightPacket(
				chunk, level.getChunkSource().getLightEngine(), null, null);

		for (ServerPlayer player : level.players()) {
			// Only the player who can actually see it needs the redraw; 10 chunks is the
			// render distance floor, so anyone closer than that is looking at this hole.
			double dx = player.getX() - ((chunk.getPos().x << 4) + 8);
			double dz = player.getZ() - ((chunk.getPos().z << 4) + 8);

			if (dx * dx + dz * dz < 160.0 * 160.0) {
				player.connection.send(packet);
			}
		}
	}

	/**
	 * Refuses to delete a chunk that would delete something the player needs: anyone standing
	 * in it, an anchor, world spawn, or the block entities the config protects.
	 */
	public static boolean isProtected(ServerLevel level, LevelChunk chunk, int chunkX, int chunkZ) {
		if (DeletionRules.isChunkProtected(level, chunkX, chunkZ)) {
			return true;
		}

		for (ServerPlayer player : level.players()) {
			if ((player.getBlockX() >> 4) == chunkX && (player.getBlockZ() >> 4) == chunkZ) {
				return true;
			}
		}

		return false;
	}

	/** Chunk positions around {@code (x, z)} that are loaded, unprotected and not already gone. */
	public static java.util.List<net.minecraft.world.level.ChunkPos> candidates(
			ServerLevel level, ServerPlayer player, int minRadius, int maxRadius) {
		java.util.List<net.minecraft.world.level.ChunkPos> list = new java.util.ArrayList<>();
		IntegrityState state = IntegrityState.get(level.getServer());
		int px = player.getBlockX() >> 4;
		int pz = player.getBlockZ() >> 4;

		for (int dx = -maxRadius; dx <= maxRadius; dx++) {
			for (int dz = -maxRadius; dz <= maxRadius; dz++) {
				int distance = Math.max(Math.abs(dx), Math.abs(dz));

				if (distance < minRadius || distance > maxRadius) {
					continue;
				}

				int cx = px + dx;
				int cz = pz + dz;

				if (state.hasDeletedChunk(cx, cz)) {
					continue;
				}

				if (!level.getChunkSource().hasChunk(cx, cz)) {
					continue;
				}

				if (isProtected(level, level.getChunk(cx, cz), cx, cz)) {
					continue;
				}

				list.add(new net.minecraft.world.level.ChunkPos(cx, cz));
			}
		}

		return list;
	}
}
