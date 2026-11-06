package dev.error404.integrity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Persistent, server-authoritative state of the dying world.
 *
 * <p>Everything the mod does is derived from this: the phase, the deletion radius, which
 * anomalies already fired, and the per-world seed that keeps client effects in sync.</p>
 */
public class IntegrityState extends SavedData {
	public static final int MAX_INTEGRITY = 100;
	public static final int MIN_INTEGRITY = 0;

	private static final Codec<IntegrityState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.fieldOf("integrity").forGetter(state -> state.integrity),
			Codec.BOOL.fieldOf("active").forGetter(state -> state.active),
			Codec.BOOL.fieldOf("ended").forGetter(state -> state.ended),
			Codec.INT.fieldOf("ending").forGetter(state -> state.ending),
			Codec.INT.fieldOf("phase").forGetter(state -> state.phase.ordinal()),
			Codec.LONG.fieldOf("session_seed").forGetter(state -> state.sessionSeed),
			Codec.INT.fieldOf("fragments").forGetter(state -> state.dataFragments),
			Codec.LONG.fieldOf("age_ticks").forGetter(state -> state.ageTicks),
			Codec.INT.fieldOf("front_radius").forGetter(state -> state.frontRadius),
			Codec.LONG.listOf().fieldOf("deleted_chunks").forGetter(state -> new ArrayList<>(state.deletedChunks)),
			Codec.LONG.listOf().fieldOf("anchors").forGetter(state -> new ArrayList<>(state.anchors)),
			Codec.STRING.listOf().fieldOf("found_anomalies").forGetter(state -> new ArrayList<>(state.foundAnomalies))
	).apply(instance, IntegrityState::new));

	private static final SavedDataType<IntegrityState> TYPE = new SavedDataType<>(
			"error404_integrity",
			IntegrityState::new,
			CODEC,
			null
	);

	/** 100 = healthy world, 0 = "world not found". */
	private int integrity = MAX_INTEGRITY;
	private boolean active = false;
	private boolean ended = false;
	/** 0 = still running, 1 = SALVAGE, 2 = DELETE, 3 = 0x00. */
	private int ending = 0;
	/** Monotonic phase: never goes backwards, even if integrity is restored. */
	private Phase phase = Phase.SIGNAL;
	private long sessionSeed = 0L;
	private int dataFragments = 0;
	private long ageTicks = 0L;
	/** How far (in chunks) from the player the deletion front is currently working. */
	private int frontRadius = 4;
	/** Chunk keys (x << 32 | z) touched by the front, for /error404 restore. */
	private final Set<Long> deletedChunks = new LinkedHashSet<>();
	/** Packed anchor-core positions. */
	private final Set<Long> anchors = new LinkedHashSet<>();
	/** Error codes of anomalies the player has run into. */
	private final Set<String> foundAnomalies = new LinkedHashSet<>();
	/** Live structures (name -> centre). Runtime copy mirrored from AnomalyManager. */
	private final java.util.Map<String, BlockPos> activeAnomalies = new java.util.LinkedHashMap<>();

	/** Runtime-only pacing accumulator, deliberately not persisted. */
	private long tickAccumulator = 0L;
	/** Chunks already counted for the "new ground" drain, runtime only. */
	private final Set<Long> seenChunks = new HashSet<>();
	/** Anchor-zone recovery accumulator, runtime only. */
	private long anchorAccumulator = 0L;
	/** Sub-point drain (hundredths of a point), runtime only. */
	private int fractionalDrain = 0;

	public IntegrityState() {
	}

	private IntegrityState(int integrity, boolean active, boolean ended, int ending, int phaseOrdinal, long sessionSeed,
			int dataFragments, long ageTicks, int frontRadius, List<Long> deletedChunks, List<Long> anchors,
			List<String> foundAnomalies) {
		this.integrity = clamp(integrity);
		this.active = active;
		this.ended = ended;
		this.ending = ending;
		this.sessionSeed = sessionSeed;
		this.dataFragments = dataFragments;
		this.ageTicks = ageTicks;
		this.frontRadius = Math.max(1, frontRadius);
		this.deletedChunks.addAll(deletedChunks);
		this.anchors.addAll(anchors);
		this.foundAnomalies.addAll(foundAnomalies);

		Phase stored = Phase.values()[Math.max(0, Math.min(Phase.values().length - 1, phaseOrdinal))];
		Phase derived = Phase.fromIntegrity(this.integrity);
		this.phase = stored.ordinal() > derived.ordinal() ? stored : derived;
	}

	public static IntegrityState get(MinecraftServer server) {
		ServerLevel overworld = server.getLevel(ServerLevel.OVERWORLD);
		if (overworld == null) {
			return new IntegrityState();
		}

		return overworld.getDataStorage().computeIfAbsent(TYPE);
	}

	// ------------------------------------------------------------------ getters

	public int integrity() {
		return integrity;
	}

	public boolean active() {
		return active;
	}

	public boolean ended() {
		return ended;
	}

	public int ending() {
		return ending;
	}

	public Phase phase() {
		return phase;
	}

	public long sessionSeed() {
		return sessionSeed;
	}

	public int dataFragments() {
		return dataFragments;
	}

	public long ageTicks() {
		return ageTicks;
	}

	public int frontRadius() {
		return frontRadius;
	}

	public Set<Long> deletedChunks() {
		return deletedChunks;
	}

	// ------------------------------------------------------------------ mutators

	public boolean shouldAdvancePhase() {
		return !ended && Phase.fromIntegrity(integrity).ordinal() > phase.ordinal();
	}

	public Phase advancePhase() {
		if (shouldAdvancePhase()) {
			phase = Phase.fromIntegrity(integrity);
			setDirty();
		}

		return phase;
	}

	public void forcePhase(Phase newPhase) {
		if (newPhase != null && newPhase.ordinal() > phase.ordinal()) {
			phase = newPhase;
			setDirty();
		}
	}

	public void start(long seed, int startingIntegrity) {
		active = true;
		ended = false;
		ending = 0;
		sessionSeed = seed;
		integrity = clamp(startingIntegrity);
		phase = Phase.SIGNAL;
		ageTicks = 0L;
		tickAccumulator = 0L;
		setDirty();
	}

	public void stop() {
		active = false;
		setDirty();
	}

	public void end(int endingCode) {
		ended = true;
		active = false;
		ending = endingCode;
		setDirty();
	}

	public void end() {
		end(2);
	}

	/**
	 * {@code /error404 restore}: put the world back into a playable, un-ended state.
	 * Deliberately keeps the phase where it was - the player earned that scar.
	 */
	public void reopen() {
		ended = false;
		ending = 0;
		active = false;
		setDirty();
	}

	public void setIntegrity(int value) {
		int clamped = clamp(value);
		if (clamped == integrity) {
			return;
		}

		integrity = clamped;
		setDirty();
		// Deliberately does NOT end the world here: the director owns that decision so the
		// right ending (including the secret 0x00) can be chosen and its finale played.
	}

	public void drain(int points) {
		if (!active || ended || points <= 0) {
			return;
		}

		setIntegrity(integrity - points);
	}

	/**
	 * Sub-point drain used by the exploration cost: {@code hundredths} is charged in whole
	 * points as soon as enough of them have piled up, and never rounds a point away.
	 */
	public void drainHundredths(int hundredths) {
		if (!active || ended || hundredths <= 0) {
			return;
		}

		fractionalDrain += hundredths;

		if (fractionalDrain >= 100) {
			int points = fractionalDrain / 100;
			fractionalDrain -= points * 100;
			setIntegrity(integrity - points);
		}
	}

	public void restore(int points) {
		if (ended || points <= 0) {
			return;
		}

		setIntegrity(integrity + points);
	}

	public void addFragment() {
		if (dataFragments < 8) {
			dataFragments++;
			setDirty();
		}
	}

	public void setFrontRadius(int radius) {
		frontRadius = Math.max(1, radius);
		setDirty();
	}

	public boolean hasDeletedChunk(int chunkX, int chunkZ) {
		return deletedChunks.contains(((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL));
	}

	public void markDeletedChunk(int chunkX, int chunkZ) {
		if (deletedChunks.size() < 200_000 && deletedChunks.add(((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL))) {
			setDirty();
		}
	}

	public void clearDeletedChunks() {
		deletedChunks.clear();
		setDirty();
	}

	/** Anchor cores as block positions, using vanilla's own long packing. */
	public List<BlockPos> anchorPositions() {
		List<BlockPos> positions = new ArrayList<>();
		for (long packed : anchors) {
			positions.add(BlockPos.of(packed));
		}

		return positions;
	}

	public void addAnchor(BlockPos pos) {
		if (anchors.add(pos.asLong())) {
			setDirty();
		}
	}

	public void removeAnchor(BlockPos pos) {
		if (anchors.remove(pos.asLong())) {
			setDirty();
		}
	}

	// ------------------------------------------------------------------ anomalies

	public void registerAnomaly(String name, BlockPos centre) {
		activeAnomalies.put(name, centre);
		setDirty();
	}

	public void unregisterAnomaly(String name) {
		activeAnomalies.remove(name);
		setDirty();
	}

	public java.util.Map<String, BlockPos> activeAnomalies() {
		return java.util.Collections.unmodifiableMap(activeAnomalies);
	}

	public void clearAnomalies() {
		activeAnomalies.clear();
		foundAnomalies.clear();
		setDirty();
	}

	public boolean hasFoundAnomaly(String code) {
		return foundAnomalies.contains(code);
	}

	public void markAnomalyFound(String code) {
		if (foundAnomalies.add(code)) {
			setDirty();
		}
	}

	public Set<String> foundAnomalies() {
		return new LinkedHashSet<>(foundAnomalies);
	}

	public long addTicks(long ticks) {
		tickAccumulator += ticks;
		return tickAccumulator;
	}

	public void consumeTicks(long ticks) {
		tickAccumulator = Math.max(0L, tickAccumulator - ticks);
	}

	public void resetAccumulator() {
		tickAccumulator = 0L;
	}

	public void tickAge() {
		ageTicks++;
	}

	/**
	 * Counts a chunk the party has just uncovered. Returns {@code true} the first time a chunk
	 * is seen, so the caller can charge integrity for it.
	 */
	public boolean markChunkSeen(int chunkX, int chunkZ) {
		long key = ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
		return seenChunks.size() < 500_000 && seenChunks.add(key);
	}

	/** Accumulates anchor-zone time; returns how many whole recovery units are ready. */
	public int accumulateAnchorTicks(long ticks, long ticksPerUnit) {
		anchorAccumulator += ticks;

		if (ticksPerUnit <= 0 || anchorAccumulator < ticksPerUnit) {
			return 0;
		}

		int units = (int) (anchorAccumulator / ticksPerUnit);
		anchorAccumulator -= units * ticksPerUnit;
		return units;
	}

	public void resetAnchorAccumulator() {
		anchorAccumulator = 0L;
	}

	private static int clamp(int value) {
		return Math.max(MIN_INTEGRITY, Math.min(MAX_INTEGRITY, value));
	}
}
