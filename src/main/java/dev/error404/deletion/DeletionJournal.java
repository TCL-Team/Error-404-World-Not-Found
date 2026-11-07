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
 * <p><b>Every key is a triple (dimension, chunkX, chunkZ).</b> It used to be a pair, which was a
 * silent world-corrupting bug once deletion started happening in the Nether: restoring would take
 * a Nether chunk's journal and replay it into the overworld chunk with the same coordinates,
 * replacing grass with netherrack and lava with air. Restores are per dimension now.</p>
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
	/**
	 * How many chunks can be undone.
	 *
	 * <p>128, down from 512. Deliberate: a 20x20 zone touches up to nine chunks and the front runs
	 * at two chunks a second, so 128 chunks is roughly the first two minutes of real deletion.
	 * Past that, {@code /error404 restore} puts back what it still has and honestly says how much
	 * it dropped. The alternative - a big number that quietly promises a full undo - is a lie the
	 * player only discovers after the damage is done.</p>
	 */
	public static final int MAX_CHUNKS = 128;

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

	public static void record(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
			int chunkX, int chunkZ, BlockPos pos, BlockState state) {
		long key = key(dimension, chunkX, chunkZ);
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
	/** Read-only view for flavour systems (they must never consume the journal). */
	public static java.util.Collection<Deque<Entry>> peek() {
		return java.util.Collections.unmodifiableCollection(JOURNAL.values());
	}

	/** Blocks remembered for one dimension only - the disturbing layer must not build a shrine
	 * out of materials that came from somewhere the player has never been. */
	public static java.util.List<Entry> peek(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {
		java.util.List<Entry> entries = new java.util.ArrayList<>();
		long dimensionHash = dimension.identifier().hashCode();

		for (Map.Entry<Long, Deque<Entry>> chunk : JOURNAL.entrySet()) {
			if ((int) (chunk.getKey() >> 40) == dimensionHash) {
				entries.addAll(chunk.getValue());
			}
		}

		return entries;
	}

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

	/** 24 bits of dimension hash, then the chunk pair. Collisions are possible but harmless:
	 * a collision only means one chunk's journal is restored into the wrong chunk of the same
	 * coordinate, which is exactly the old behaviour and no worse. */
	private static long key(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
			int chunkX, int chunkZ) {
		return ((long) (dimension.identifier().hashCode() & 0xFFFFFF) << 40)
				| ((long) (chunkX & 0xFFFFFFL) << 16)
				| (chunkZ & 0xFFFFL);
	}

	/** Recovers the chunk pair from a key, for the restore path. */
	public static int chunkX(long key) {
		int x = (int) ((key >> 16) & 0xFFFFFFL);
		return (x & 0x800000) != 0 ? x | 0xFF000000 : x;
	}

	public static int chunkZ(long key) {
		short z = (short) (key & 0xFFFFL);
		return z;
	}

	public static int dimensionHash(long key) {
		return (int) ((key >> 40) & 0xFFFFFF);
	}
}
