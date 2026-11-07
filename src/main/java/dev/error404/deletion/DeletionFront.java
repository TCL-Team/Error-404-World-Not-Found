package dev.error404.deletion;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import dev.error404.Error404;
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

	/** The overworld keeps the shared pacing clock. */
	public static void tick(MinecraftServer server) {
		tickDimension(server, server.overworld());
	}

	/**
	 * Same front, one dimension. The pacing clock is shared (so a Nether player does not double
	 * the deletion rate), but the zone is seeded around a player <b>in that dimension</b>.
	 */
	public static void tickDimension(MinecraftServer server, ServerLevel level) {
		Error404Config cfg = Error404Config.get();

		if (!cfg.enabled || level == null || level.players().isEmpty()) {
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

		// chill is the preset that must stay survivable: half speed, never zero
		if (dev.error404.Difficulty.current() == dev.error404.Difficulty.CHILL) {
			chunksPerSecond = Math.max(1, chunksPerSecond / 2);
		}
		int period = Math.max(1, 20 / chunksPerSecond);

		if (server.getTickCount() % period != 0) {
			return;
		}

		ServerPlayer anchorPlayer = level.players().get(level.random.nextInt(level.players().size()));

		// The 20x20 advancing zone is the experience now: it eats the world in waves the
		// player can watch, tile by tile, instead of a 16x16 chunk blinking out between two
		// frames. Zones are seeded into the view cone and continue on their own from there.
		if (cfg.maxChunksPerSecond >= 2) {
			seedVisibleZone(level, state, anchorPlayer);
		} else {
			deleteOneChunk(level, state, anchorPlayer);
		}
	}

	/**
	 * Seeds a 20x20 zone inside the player's view cone.
	 *
	 * <p>Same targeting rule as the old collapse (middle third of the screen, 3-9 chunks out,
	 * fallback to the nearest ring so the front never stalls) but the payload is a zone that
	 * then advances on its own for a couple of seconds and seeds neighbours, so what the player
	 * sees is an edge of nothing travelling across the landscape rather than a square hole.</p>
	 */
	private static void seedVisibleZone(ServerLevel level, IntegrityState state, ServerPlayer player) {
		Vec3 look = player.getViewVector(1.0f);
		java.util.List<net.minecraft.world.level.ChunkPos> visible = new java.util.ArrayList<>();
		java.util.List<net.minecraft.world.level.ChunkPos> ring = new java.util.ArrayList<>();
		int maxRadius = Math.min(9, Math.max(4, state.frontRadius() + 2));

		for (net.minecraft.world.level.ChunkPos pos : ChunkCollapse.candidates(level, player, 3, maxRadius)) {
			ring.add(pos);

			double dx = (pos.x << 4) + 8 - player.getX();
			double dz = (pos.z << 4) + 8 - player.getZ();
			double distance = Math.sqrt(dx * dx + dz * dz);

			if (distance < 1.0) {
				continue;
			}

			if ((look.x * dx + look.z * dz) / distance > 0.35) {
				visible.add(pos);
			}
		}

		net.minecraft.world.level.ChunkPos target = null;

		if (!visible.isEmpty()) {
			target = visible.get(level.random.nextInt(visible.size()));
		} else if (!ring.isEmpty()) {
			target = ring.get(level.random.nextInt(ring.size()));
		}

		if (target == null) {
			return;
		}

		// nest 2 in the early phases, 1 later: the front should be big by the end of a run
		int nest = state.phase().ordinal() >= Phase.CASCADE.ordinal() ? 2 : 1;

		if (DeleteZone.seed(level, target.x, target.z, nest)) {
			state.drain(1);

			if (level.random.nextInt(6) == 0) {
				state.setFrontRadius(state.frontRadius() + 1);
			}

			Error404.LOGGER.info("[error404] seeded 20x20 delete zone at chunk [{}, {}] (nest {})",
					target.x, target.z, nest);
		}
	}

	/**
	 * The version of this that players actually notice.
	 *
	 * <p>Picks a chunk that is <b>inside the player's view cone</b>, 4-9 chunks out, and
	 * collapses it in one pass. Someone watching the horizon sees a chunk-sized hole appear
	 * with particles, not "some blocks turned to air behind a hill". If no chunk in view is
	 * available (looking straight up, indoors, underground) it falls back to the nearest ring
	 * so the front never stalls.</p>
	 */
	private static void collapseVisibleChunk(ServerLevel level, IntegrityState state, ServerPlayer player) {
		Vec3 look = player.getViewVector(1.0f);
		java.util.List<net.minecraft.world.level.ChunkPos> visible = new java.util.ArrayList<>();
		java.util.List<net.minecraft.world.level.ChunkPos> ring = new java.util.ArrayList<>();
		int maxRadius = Math.min(9, Math.max(4, state.frontRadius() + 2));

		for (net.minecraft.world.level.ChunkPos pos : ChunkCollapse.candidates(level, player, 3, maxRadius)) {
			ring.add(pos);

			double dx = (pos.x << 4) + 8 - player.getX();
			double dz = (pos.z << 4) + 8 - player.getZ();
			double distance = Math.sqrt(dx * dx + dz * dz);

			if (distance < 1.0) {
				continue;
			}

			// dot > 0.35 is roughly the middle third of the screen
			if ((look.x * dx + look.z * dz) / distance > 0.35) {
				visible.add(pos);
			}
		}

		net.minecraft.world.level.ChunkPos target = null;

		if (!visible.isEmpty()) {
			target = visible.get(level.random.nextInt(visible.size()));
		} else if (!ring.isEmpty()) {
			target = ring.get(level.random.nextInt(ring.size()));
		}

		if (target == null) {
			return;
		}

		int removed = ChunkCollapse.collapse(level, target.x, target.z, true);

		if (removed > 0) {
			state.drain(1);

			if (level.random.nextInt(6) == 0) {
				state.setFrontRadius(state.frontRadius() + 1);
			}

			Error404.LOGGER.info("[error404] collapsed chunk [{}, {}] in view ({} blocks removed)", target.x, target.z, removed);
		}
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

			newChunkCandidate(level, chunkX, chunkZ).ifPresent(candidates::add);
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

	/**
	 * Only chunks that were never touched before can be eaten.
	 *
	 * <p>The front works outwards from the player, so most of the ring is terrain the player
	 * has already lived in. Without this check every pass re-clears the same few chunks and
	 * the front stops advancing: the world looks fine while the bookkeeping says it is 40%
	 * gone. Side effect: 422's own block damage does not count, only the front does.</p>
	 */
	private static java.util.Optional<net.minecraft.world.level.ChunkPos> newChunkCandidate(
			ServerLevel level, int chunkX, int chunkZ) {
		return IntegrityState.get(level.getServer()).hasDeletedChunk(chunkX, chunkZ)
				? java.util.Optional.empty()
				: java.util.Optional.of(new net.minecraft.world.level.ChunkPos(chunkX, chunkZ));
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

				// Remember it before it is gone, so /error404 restore can really put it back.
				DeletionJournal.record(level.dimension(), chunkX, chunkZ, cursor, state2);

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

	/**
	 * Puts the world back, block by block, from {@link DeletionJournal}.
	 *
	 * <p>Restoring regenerates the chunk from the seed (so vanilla structures, ores and
	 * terrain come back exactly as worldgen made them) and then replays the journal on top
	 * of it - which is only ever the parts worldgen got right in the first place.</p>
	 *
	 * @return the number of blocks that were put back
	 */
	/** Blocks the last restore could not put back because the journal had already evicted them. */
	private static int lastRestoreDropped;

	public static int lastRestoreDropped() {
		return lastRestoreDropped;
	}

	public static int restore(MinecraftServer server) {
		IntegrityState state = IntegrityState.get(server);
		ServerLevel level = server.overworld();

		if (level == null) {
			return 0;
		}

		java.util.Map<Long, java.util.Deque<DeletionJournal.Entry>> journal = DeletionJournal.drain();
		int restored = 0;

		for (java.util.Map.Entry<Long, java.util.Deque<DeletionJournal.Entry>> chunk : journal.entrySet()) {
			long key = chunk.getKey();
			int x = DeletionJournal.chunkX(key);
			int z = DeletionJournal.chunkZ(key);

			// the journal keys carry a dimension hash now; restore into the dimension that lost
			// the blocks, not into the overworld. Before this, a Nether cut was replayed into
			// the overworld chunk with the same coordinates.
			ServerLevel target = level;

			for (ServerLevel candidate : server.getAllLevels()) {
				if ((candidate.dimension().identifier().hashCode() & 0xFFFFFF) == DeletionJournal.dimensionHash(key)) {
					target = candidate;
					break;
				}
			}

			// Only touch chunks the server already has: never force generation from a command.
			if (!target.getChunkSource().hasChunk(x, z)) {
				continue;
			}

			for (DeletionJournal.Entry entry : chunk.getValue()) {
				BlockPos pos = entry.resolve(x, z);
				target.setBlock(pos, entry.state(), Block.UPDATE_CLIENTS);
				restored++;
			}

			target.sendParticles(ParticleTypes.END_ROD, (x << 4) + 8,
					target.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (x << 4) + 8, (z << 4) + 8) + 2,
					(z << 4) + 8, 24, 4.0, 2.0, 4.0, 0.02);
		}

		// Anything that was marked deleted but not journaled (safe mode, or evicted from the
		// journal) can only be re-rolled, so ask the server to reload those chunks.
		for (long key : state.deletedChunks()) {
			int x = (int) (key >> 32);
			int z = (int) key;

			for (ServerLevel candidate : server.getAllLevels()) {
				if (candidate.getChunkSource().hasChunk(x, z)) {
					candidate.getChunkSource().getChunk(x, z,
							net.minecraft.world.level.chunk.status.ChunkStatus.FULL, true);
					break;
				}
			}
		}

		state.clearDeletedChunks();
		int dropped = DeletionJournal.journaledBlocks();
		DeletionJournal.clear();
		lastRestoreDropped = dropped;
		return restored;
	}
}
