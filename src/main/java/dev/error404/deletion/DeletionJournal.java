package dev.error404.deletion;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Keeps enough of the world to be able to give it back.
 *
 * <p>The front never "deletes" a chunk - it eats blocks. Every block it removes is written
 * here first (position relative to the chunk, original state), so {@code /error404 restore}
 * can replay the world instead of pretending to. Chunk regeneration is not usable for this:
 * once a chunk has been saved with holes, vanilla has no API to throw it away and re-run
 * worldgen without touching region files behind the server's back.</p>
 *
 * <p>Bounded on purpose: at most {@link #PER_CHUNK} blocks per chunk and {@link #MAX_CHUNKS}
 * chunks are remembered, oldest evicted first. Memory stays around a few megabytes, and a
 * long session degrades to "the first cuts cannot be undone" instead of an OOM.</p>
 */
public final class DeletionJournal {
	/**
	 * Deepest slice a single chunk can remember.
	 *
	 * <p>Raised from 1024 when deletion became 20x20 zones: a 20x20 zone touches up to nine
	 * chunks, and a chunk corner inside a zone can lose far more than 1024 blocks once the
	 * zone eats a hillside. 4096 keeps one full zone per chunk fully restorable, which is the
	 * promise {@code /error404 restore} makes.</p>
	 */
	public static final int PER_CHUNK = 4096;
	public static final int MAX_CHUNKS = 512;

	private static final Map<Long, Deque<Entry>> JOURNAL = new HashMap<>();
	private static final Deque<Long> ORDER = new ArrayDeque<>();

	/** One removed block: 3 packed relative coordinates plus the state to put back. */
	public record Entry(short x, short y, short z, BlockState state) {
		public BlockPos resolve(int chunkX, int chunkZ) {
			return new BlockPos((chunkX << 4) + x, y, (chunkZ << 4) + z);
		}
	}

	private DeletionJournal() {
	}

	public static void record(int chunkX, int chunkZ, BlockPos pos, BlockState state) {
		long key = key(chunkX, chunkZ);
		Deque<Entry> entries = JOURNAL.get(key);

		if (entries == null) {
			entries = new ArrayDeque<>(PER_CHUNK);
			JOURNAL.put(key, entries);

			if (ORDER.size() >= MAX_CHUNKS) {
				Long evicted = ORDER.pollFirst();

				if (evicted != null) {
					JOURNAL.remove(evicted);
				}
			}

			ORDER.addLast(key);
		}

		if (entries.size() >= PER_CHUNK) {
			entries.pollFirst();
		}

		entries.addLast(new Entry((short) (pos.getX() - (chunkX << 4)), (short) pos.getY(),
				(short) (pos.getZ() - (chunkZ << 4)), state));
	}

	public static int journaledChunks() {
		return JOURNAL.size();
	}

	public static int journaledBlocks() {
		int total = 0;

		for (Deque<Entry> entries : JOURNAL.values()) {
			total += entries.size();
		}

		return total;
	}

	/** Returns every remembered block, newest chunk first, and forgets it. */
	public static Map<Long, Deque<Entry>> drain() {
		Map<Long, Deque<Entry>> copy = new HashMap<>(JOURNAL);
		JOURNAL.clear();
		ORDER.clear();
		return copy;
	}

	public static void clear() {
		JOURNAL.clear();
		ORDER.clear();
	}

	private static long key(int chunkX, int chunkZ) {
		return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
	}
}
