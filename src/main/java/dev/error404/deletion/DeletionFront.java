package dev.error404.deletion;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import dev.error404.Error404Config;
import dev.error404.ModBlocks;
import dev.error404.ModSounds;
import dev.error404.integrity.IntegrityState;
import dev.error404.integrity.Phase;

/**
 * The thing that eats the world.
 *
 * <p>Every pass picks one chunk from a ring around spawn, strips its surface down in
 * small layers and leaves {@link ModBlocks#VOID_STONE} residue behind. The ring grows as
 * integrity falls, so the world visibly shrinks towards the player instead of blinking out.</p>
 *
 * <p>Budget: one chunk per pass, 256 blocks maximum per pass, one pass every
 * {@code 20 / maxChunksPerSecond} ticks. That keeps a dedicated server's MSPT flat.</p>
 */
public final class DeletionFront {
	private static final int BLOCKS_PER_PASS = 256;
	private static final int MIN_CHUNK_RADIUS = 3;

	private DeletionFront() {
	}

	/**
	 * SALVAGE: the front stops for the rest of the session. Already-deleted chunks stay
	 * deleted until {@code /error404 restore} regenerates them.
	 */
	public static void hold(MinecraftServer server) {
		IntegrityState.get(server).stop();
	}

	public static void tick(MinecraftServer server) {
		Error404Config cfg = Error404Config.get();

		if (!cfg.enabled) {
			return;
		}

		IntegrityState state = IntegrityState.get(server);

		if (!state.active() || state.ended()) {
			return;
		}

		Phase phase = state.phase();

		if (phase.ordinal() < Phase.DELETION.ordinal()) {
			return;
		}

		int chunksPerSecond = Math.max(1, cfg.maxChunksPerSecond);
		int period = Math.max(1, 20 / chunksPerSecond);

		if (server.getTickCount() % period != 0) {
			return;
		}

		ServerLevel level = server.overworld();
		if (level == null || level.players().isEmpty()) {
			return;
		}

		ServerPlayer anchorPlayer = level.players().get(server.overworld().random.nextInt(level.players().size()));
		deleteOneChunk(level, state, anchorPlayer);
	}

	private static void deleteOneChunk(ServerLevel level, IntegrityState state, ServerPlayer player) {
		java.util.List<net.minecraft.world.level.ChunkPos> candidates = new java.util.ArrayList<>();
		RandomSource random = level.random;
		int radius = Math.max(MIN_CHUNK_RADIUS, state.frontRadius());
		int angleSteps = 12;
		int px = player.getBlockX() >> 4;
		int pz = player.getBlockZ() >> 4;

		for (int attempt = 0; attempt < angleSteps; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double distance = radius + random.nextDouble() * 2.0;
			int chunkX = px + (int) Math.round(Math.cos(angle) * distance);
			int chunkZ = pz + (int) Math.round(Math.sin(angle) * distance);

			if (DeletionRules.isChunkProtected(level, chunkX, chunkZ)) {
				continue;
			}

			candidates.add(new net.minecraft.world.level.ChunkPos(chunkX, chunkZ));
		}

		if (candidates.isEmpty()) {
			return;
		}

		// Never block the server tick waiting for a chunk: strip one that is loaded, else ask
		// for exactly one chunk to be generated off-thread and try again next pass.
		for (net.minecraft.world.level.ChunkPos pos : candidates) {
			if (level.getChunkSource().hasChunk(pos.x, pos.z)) {
				if (stripChunk(level, pos.x, pos.z, state)) {
					if (random.nextInt(6) == 0) {
						state.setFrontRadius(state.frontRadius() + 1);
					}

					return;
				}
			}
		}

		net.minecraft.world.level.ChunkPos ticket = candidates.get(random.nextInt(candidates.size()));
		level.getChunk(ticket.x, ticket.z);
	}

	/** Removes up to {@link #BLOCKS_PER_PASS} blocks from the top of a chunk. */
	private static boolean stripChunk(ServerLevel level, int chunkX, int chunkZ, IntegrityState state) {
		RandomSource random = level.random;

		if (!Error404Config.get().realBlockDeletion) {
			// Safe mode: the world is never modified, but the books stay in sync so the HUD,
			// the fog and the endings still play exactly as they would.
			state.markDeletedChunk(chunkX, chunkZ);
			level.playSound(null, new BlockPos(chunkX << 4, level.getSeaLevel(), chunkZ << 4),
					ModSounds.CHUNK_DELETE, SoundSource.BLOCKS, 0.35f, 0.8f);
			return true;
		}
		int budget = BLOCKS_PER_PASS;
		int removed = 0;
		int layer = Math.min(10, 3 + state.phase().ordinal() * 2);
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

		int baseX = chunkX << 4;
		int baseZ = chunkZ << 4;

		for (int col = 0; col < 16 && budget > 0; col++) {
			int x = baseX + (col % 4) * 4 + random.nextInt(4);
			int z = baseZ + (col / 4) * 4 + random.nextInt(4);
			int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);

			for (int dy = 0; dy < layer && budget > 0; dy++) {
				int y = top - dy;
				cursor.set(x, y, z);
				BlockState state2 = level.getBlockState(cursor);

				if (state2.isAir() || DeletionRules.isProtected(level, cursor)) {
					continue;
				}

				if (removed == 0 && random.nextBoolean()) {
					// first block of a chunk announces itself
					level.playSound(null, cursor, ModSounds.CHUNK_DELETE, SoundSource.BLOCKS, 0.7f, 1.0f);
				}

				level.setBlock(cursor, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				removed++;
				budget--;

				if (random.nextInt(6) == 0) {
					level.sendParticles(ParticleTypes.PORTAL, x + 0.5, y + 0.5, z + 0.5, 2, 0.3, 0.2, 0.3, 0.02);
				}
			}

			// leave residue at the bottom of the cut on some columns
			if (removed > 0 && random.nextInt(4) == 0) {
				BlockPos bottom = cursor.set(x, Math.max(level.getMinY() + 2, top - layer), z).immutable();

				if (!DeletionRules.isProtected(level, bottom)
						&& level.getBlockState(bottom).isAir()
						&& (level.getBlockState(bottom.below()).is(BlockTags.DIRT) || random.nextInt(3) == 0)) {
					level.setBlock(bottom, ModBlocks.VOID_STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}

		if (removed > 0) {
			state.markDeletedChunk(chunkX, chunkZ);
		}

		return removed > 0;
	}

	/** Puts the world back: everything the front touched gets regenerated on next visit. */
	public static int restore(MinecraftServer server) {
		IntegrityState state = IntegrityState.get(server);
		ServerLevel level = server.overworld();
		int count = 0;

		if (level == null) {
			return 0;
		}

		for (long key : state.deletedChunks()) {
			int x = (int) (key >> 32);
			int z = (int) key;

			// A generated-but-unsaved chunk is regenerated from the seed on next visit, which
			// is exactly the "restore" we want; asking for it here just forces that work now.
			level.getChunkSource().getChunk(x, z, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, true);
			count++;
		}

		state.clearDeletedChunks();
		return count;
	}
}
