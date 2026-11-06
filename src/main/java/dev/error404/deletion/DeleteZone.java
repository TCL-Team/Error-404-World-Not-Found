package dev.error404.deletion;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import dev.error404.ModBlocks;
import dev.error404.ModSounds;
import dev.error404.integrity.IntegrityState;

/**
 * A 20x20 block deletion zone that eats the world in waves instead of all at once.
 *
 * <p>Why not one chunk at a time: a 16x16 chunk wiped in a single tick is a hole that appears
 * between two frames. It reads as a rendering bug. A 20x20 zone that consumes itself column by
 * column, over three to four seconds, reads as something eating the world - the player watches
 * the edge advance towards them and can still run.</p>
 *
 * <p>Geometry: the zone is seeded with one {@code 20x20} square, then every {@link #TICK_BUDGET}
 * ticks the next {@link #WAVE_COLUMNS} columns fall. When the seed finishes, it seeds up to
 * {@link #MAX_ACTIVE} neighbours, offset by exactly 20 blocks - so consecutive zones tile into a
 * visible front instead of a scatter of unrelated holes.</p>
 *
 * <p>Every removed block goes into {@link DeletionJournal} before it is removed, so
 * {@code /error404 restore} still puts the world back. That is the contract this class must not
 * break: it is allowed to be horrifying, not to be lossy.</p>
 */
public final class DeleteZone {
	/** The zone is exactly 20x20 blocks, as specified. */
	public static final int SIZE = 20;
	/** Columns (each a full vertical stack) consumed per wave. 4 columns per wave = 5 waves. */
	public static final int WAVE_COLUMNS = 4;
	/** Ticks between waves: 8 ticks is 0.4 s, so a 20x20 zone goes in about 2 seconds. */
	public static final int WAVE_INTERVAL = 8;
	/** Blocks removed per wave, before the wave is allowed to spill into the next tick. */
	public static final int TICK_BUDGET = 1600;
	/** How many zones may be eating the world at once. */
	public static final int MAX_ACTIVE = 3;
	/** Nesting limit, so a session cannot turn the whole save into a queue. */
	public static final int MAX_QUEUED = 24;

	private static final Deque<Zone> ACTIVE = new ArrayDeque<>();
	private static final Deque<Zone> QUEUED = new ArrayDeque<>();

	/** Both queues, for overlap checks. */
	private static final java.util.Collection<Zone> ALL = new java.util.AbstractCollection<>() {
		@Override
		public java.util.Iterator<Zone> iterator() {
			java.util.List<Zone> copy = new java.util.ArrayList<>(ACTIVE);
			copy.addAll(QUEUED);
			return copy.iterator();
		}

		@Override
		public int size() {
			return ACTIVE.size() + QUEUED.size();
		}
	};

	private DeleteZone() {
	}

	/** One advancing 20x20 square. Columns are addressed 0..SIZE-1 on both axes. */
	private static final class Zone {
		private final int originX;   // block coords, min corner
		private final int originZ;
		private final int minY;
		private final int topY;
		/** The dimension this zone belongs to, so a Nether seed never eats overworld blocks. */
		private final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
		private final int nest;
		private int column;
		private int wait;

		// a bottom-up walk inside the current column so the hole deepens visibly
		private int cursorY;
		private boolean columnDone = true;

		private Zone(int originX, int originZ, int minY, int topY, int nest,
				net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {
			this.originX = originX;
			this.originZ = originZ;
			this.minY = minY;
			this.topY = topY;
			this.nest = nest;
			this.dimension = dimension;
			this.cursorY = topY;
		}

		private int nextX() {
			return originX + (column % SIZE);
		}

		private int nextZ() {
			return originZ + (column / SIZE);
		}

		private boolean finished() {
			return column >= SIZE * SIZE;
		}
	}

	/** True while anything is still being eaten - the director uses this to pace itself. */
	public static boolean active() {
		return !ACTIVE.isEmpty() || !QUEUED.isEmpty();
	}

	public static int activeCount() {
		return ACTIVE.size() + QUEUED.size();
	}

	/**
	 * Seeds a zone centred on a chunk. Returns false when the area is protected or the queues
	 * are full.
	 *
	 * @param nest how many neighbours this zone is still allowed to seed (0 = seed only this one)
	 */
	public static boolean seed(ServerLevel level, int chunkX, int chunkZ, int nest) {
		int x = (chunkX << 4) + 8 - SIZE / 2;
		int z = (chunkZ << 4) + 8 - SIZE / 2;

		if (overlaps(level, x, z, x + SIZE - 1, z + SIZE - 1)) {
			return false;
		}

		if (activates(level, x, z, x + SIZE - 1, z + SIZE - 1)) {
			return false; // would swallow a player, an anchor, spawn, or a protected block
		}

		// Never start below the highest solid block in the zone, or the first wave spends its
		// entire budget eating empty sky and the player sees nothing happen.
		Zone zone = new Zone(x, z, level.getMinY() + 1,
				topOf(level, x, z), nest, level.dimension());

		if (ACTIVE.size() < MAX_ACTIVE) {
			ACTIVE.addLast(zone);
		} else if (QUEUED.size() < MAX_QUEUED) {
			QUEUED.addLast(zone);
		} else {
			return false;
		}

		return true;
	}

	/** Seeds a zone where the player is standing. Used only by personal collapse. */
	public static boolean seedAtPlayer(ServerLevel level, ServerPlayer player) {
		int x = player.getBlockX() - SIZE / 2;
		int z = player.getBlockZ() - SIZE / 2;

		for (Zone other : ALL) {
			if (overlaps(level, x, z, x + SIZE - 1, z + SIZE - 1, other)) {
				return false;
			}
		}

		ACTIVE.addLast(new Zone(x, z, level.getMinY() + 1, topOf(level, x, z), 0, level.dimension()));
		return true;
	}

	/** Highest solid block in a zone's footprint, so the first wave is never empty sky. */
	private static int topOf(ServerLevel level, int minX, int minZ) {
		int top = level.getMinY() + 32;

		for (int i = 0; i < 6; i++) {
			int x = minX + level.random.nextInt(SIZE);
			int z = minZ + level.random.nextInt(SIZE);
			top = Math.max(top, level.getHeight(
					net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
		}

		return Math.min(level.getMaxY(), top + 2);
	}

	/** Advances every active zone by one wave. Called from the server tick. */
	public static void tick(ServerLevel level) {
		if (paused || !level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) {
			return; // zones are per-dimension state; the overworld call owns the clock
		}

		if (ACTIVE.isEmpty()) {
			// promote whatever is waiting; one at a time, so the front keeps its shape
			if (!QUEUED.isEmpty()) {
				ACTIVE.addLast(QUEUED.pollFirst());
			}
			return;
		}

		java.util.Iterator<Zone> iterator = ACTIVE.iterator();

		while (iterator.hasNext()) {
			Zone zone = iterator.next();

			if (--zone.wait > 0) {
				continue;
			}

			zone.wait = WAVE_INTERVAL;
			ServerLevel target = level.getServer().getLevel(zone.dimension);

			if (target == null) {
				iterator.remove();
				continue;
			}

			int removed = eat(target, zone);

			if (zone.finished()) {
				iterator.remove();
				IntegrityState.get(level.getServer()).drain(1);
				spawnNeighbours(level, zone);
				continue;
			}

			if (removed > 0) {
				announce(target, zone);
			}
		}
	}

	/** Runs one wave: eats up to {@link #WAVE_COLUMNS} columns, {@link #TICK_BUDGET} blocks. */
	private static int eat(ServerLevel level, Zone zone) {
		int budget = TICK_BUDGET;
		int removed = 0;
		int columns = 0;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		LevelChunk chunk = null;
		ChunkPos chunkPos = null;

		while (columns < WAVE_COLUMNS && budget > 0 && !zone.finished()) {
			int x = zone.nextX();
			int z = zone.nextZ();

			if (zone.columnDone) {
				zone.cursorY = zone.topY;
				zone.columnDone = false;
			}

			for (int y = zone.cursorY; y >= zone.minY && budget > 0; y--) {
				cursor.set(x, y, z);
				BlockState state = level.getBlockState(cursor);
				zone.cursorY = y - 1;

				if (state.isAir()) {
					continue;
				}

				if (DeletionRules.isProtected(level, cursor) || state.hasBlockEntity()) {
					continue;
				}

				DeletionJournal.record(x >> 4, z >> 4, cursor, state);
				level.setBlock(cursor, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				removed++;
				budget--;
			}

			// Only advance the column when it was actually finished. Advancing after a budget
			// cut-off left vertical slivers of world standing inside a hole.
			if (zone.cursorY < zone.minY) {
				zone.columnDone = true;
				zone.column++;
				columns++;
			} else {
				zone.columnDone = false;
				columns = WAVE_COLUMNS; // finish this column next wave before starting another
			}

			// The chunk the wave is inside. Worth resyncing at the end of the wave: block
			// updates alone leave the client's light and section mesh stale for a moment.
			ChunkPos touched = new ChunkPos(x >> 4, z >> 4);

			if (chunkPos == null || !chunkPos.equals(touched)) {
				chunkPos = touched;
				chunk = level.getChunk(chunkPos.x, chunkPos.z);
			}
		}

		if (chunk != null) {
			ChunkCollapse.syncToViewers(level, chunk);
		}

		IntegrityState.get(level.getServer()).markDeletedChunk(zone.originX >> 4, zone.originZ >> 4);
		return removed;
	}

	/** Sound + particles at the advancing edge, so the player can hear where the hole is. */
	private static void announce(ServerLevel level, Zone zone) {
		double x = zone.nextX() + 0.5;
		double z = zone.nextZ() + 0.5;
		// the advancing edge's own height, not sea level: in the Nether sea level is above the
		// ceiling and in the End it is empty-space noise the player never hears
		double y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types
				.MOTION_BLOCKING_NO_LEAVES, zone.nextX(), zone.nextZ());

		level.playSound(null, x, y, z, ModSounds.CHUNK_DELETE, SoundSource.AMBIENT, 1.1f, 0.62f);
		level.sendParticles(ParticleTypes.PORTAL, x, y + 1.5, z, 60, 2.2, 2.4, 2.2, 0.25);
	}

	/**
	 * When a zone finishes it seeds up to three neighbours, offset by exactly {@link #SIZE}, so
	 * the deletion reads as one front moving through the world rather than as isolated holes.
	 */
	private static void spawnNeighbours(ServerLevel level, Zone zone) {
		if (zone.nest <= 0) {
			return;
		}

		// one direction per zone: the front travels, it does not bloom into a circle
		int[] dx = {SIZE, 0, -SIZE, 0};
		int[] dz = {0, SIZE, 0, -SIZE};
		int start = level.random.nextInt(4);

		for (int i = 0; i < 3; i++) {
			int dir = (start + i) % 4;
			int nx = zone.originX + dx[dir];
			int nz = zone.originZ + dz[dir];

			if (overlaps(level, nx, nz, nx + SIZE - 1, nz + SIZE - 1)) {
				continue;
			}

			if (activates(level, nx, nz, nx + SIZE - 1, nz + SIZE - 1)) {
				continue;
			}

			Zone next = new Zone(nx, nz, zone.minY, zone.topY, zone.nest - 1, zone.dimension);
			(QUEUED.size() < MAX_QUEUED ? QUEUED : ACTIVE).addLast(next);
		}
	}

	// ------------------------------------------------------------------ safety

	/** Refuses a zone that would contain or reach anything the mod promised not to touch. */
	public static boolean activates(ServerLevel level, int minX, int minZ, int maxX, int maxZ) {
		for (ServerPlayer player : level.players()) {
			if (player.getBlockX() >= minX - 2 && player.getBlockX() <= maxX + 2
					&& player.getBlockZ() >= minZ - 2 && player.getBlockZ() <= maxZ + 2) {
				return true;
			}
		}

		IntegrityState state = IntegrityState.get(level.getServer());

		for (BlockPos anchor : state.anchorPositions()) {
			if (anchor.getX() >= minX - 12 && anchor.getX() <= maxX + 12
					&& anchor.getZ() >= minZ - 12 && anchor.getZ() <= maxZ + 12) {
				return true;
			}
		}

		// the world spawn is where a dead player comes back; deleting it is a soft-lock
		BlockPos spawn = level.getLevelData().getRespawnData().pos();

		return spawn.getX() >= minX - 8 && spawn.getX() <= maxX + 8
				&& spawn.getZ() >= minZ - 8 && spawn.getZ() <= maxZ + 8;
	}

	private static boolean overlaps(ServerLevel level, int minX, int minZ, int maxX, int maxZ) {
		for (Zone zone : ACTIVE) {
			if (overlaps(level, minX, minZ, maxX, maxZ, zone)) {
				return true;
			}
		}

		for (Zone zone : QUEUED) {
			if (overlaps(level, minX, minZ, maxX, maxZ, zone)) {
				return true;
			}
		}

		return false;
	}

	private static boolean overlaps(ServerLevel level, int minX, int minZ, int maxX, int maxZ, Zone zone) {
		return minX < zone.originX + SIZE && maxX >= zone.originX
				&& minZ < zone.originZ + SIZE && maxZ >= zone.originZ;
	}

	/** Cleared on restore and on server stop. */
	public static void reset() {
		ACTIVE.clear();
		QUEUED.clear();
	}

	/** Frozen while a restore runs: a zone that keeps eating during a restore writes garbage back. */
	private static boolean paused;

	public static void pause() {
		paused = true;
	}

	public static void resume() {
		paused = false;
	}

	/** Debug view for {@code /error404 status}: origin + progress of each live zone. */
	public static List<String> describe() {
		List<String> lines = new ArrayList<>();

		for (Zone zone : ACTIVE) {
			lines.add("zone " + zone.originX + "," + zone.originZ + " "
					+ zone.column + "/" + (SIZE * SIZE) + " nest " + zone.nest);
		}

		return lines;
	}
}
