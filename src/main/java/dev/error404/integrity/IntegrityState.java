package dev.error404.integrity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Persistent, server-authoritative state of the dying world.
 *
 * <p>Everything the mod does is derived from this: the phase, the deletion budget, which
 * anomalies already fired, and the per-world seed that keeps client effects in sync.</p>
 */
public class IntegrityState extends SavedData {
	public static final int MAX_INTEGRITY = 100;
	public static final int MIN_INTEGRITY = 0;

	private static final Codec<IntegrityState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.fieldOf("integrity").forGetter(state -> state.integrity),
			Codec.BOOL.fieldOf("active").forGetter(state -> state.active),
			Codec.BOOL.fieldOf("ended").forGetter(state -> state.ended),
			Codec.INT.fieldOf("phase").forGetter(state -> state.phase.ordinal()),
			Codec.LONG.fieldOf("session_seed").forGetter(state -> state.sessionSeed),
			Codec.INT.fieldOf("fragments").forGetter(state -> state.dataFragments),
			Codec.LONG.fieldOf("age_ticks").forGetter(state -> state.ageTicks)
	).apply(instance, IntegrityState::new));

	private static final SavedDataType<IntegrityState> TYPE = new SavedDataType<>(
			"error404_integrity",
			IntegrityState::new,
			CODEC,
			null
	);

	/** 100 = healthy world, 0 = "world not found". */
	private int integrity = MAX_INTEGRITY;
	/** Has the session been started (either manually or automatically)? */
	private boolean active = false;
	/** Set once an ending has been reached so the director stops. */
	private boolean ended = false;
	/** Monotonic phase: never goes backwards, even if integrity is restored. */
	private Phase phase = Phase.SIGNAL;
	/** Deterministic seed shared with clients for visual effects. */
	private long sessionSeed = 0L;
	/** Data fragments the player(s) collected, 0..8. */
	private int dataFragments = 0;
	/** How long this world has been "alive" under the mod, in ticks. */
	private long ageTicks = 0L;

	/** Runtime-only pacing accumulator, deliberately not persisted. */
	private long tickAccumulator = 0L;

	public IntegrityState() {
	}

	private IntegrityState(int integrity, boolean active, boolean ended, int phaseOrdinal, long sessionSeed, int dataFragments, long ageTicks) {
		this.integrity = clamp(integrity);
		this.active = active;
		this.ended = ended;
		this.sessionSeed = sessionSeed;
		this.dataFragments = dataFragments;
		this.ageTicks = ageTicks;

		int ordinal = Math.max(0, Math.min(Phase.values().length - 1, phaseOrdinal));
		Phase stored = Phase.values()[ordinal];
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

	public int integrity() {
		return integrity;
	}

	public boolean active() {
		return active;
	}

	public boolean ended() {
		return ended;
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

	/** True when the world got worse than the phase we already announced. */
	public boolean shouldAdvancePhase() {
		return !ended && Phase.fromIntegrity(integrity).ordinal() > phase.ordinal();
	}

	/** Advance the phase from integrity and return the resulting phase. */
	public Phase advancePhase() {
		if (shouldAdvancePhase()) {
			phase = Phase.fromIntegrity(integrity);
			setDirty();
		}

		return phase;
	}

	/** Force a phase (debug command); never moves the phase backwards. */
	public void forcePhase(Phase newPhase) {
		if (newPhase != null && newPhase.ordinal() > phase.ordinal()) {
			phase = newPhase;
			setDirty();
		}
	}

	public void start(long seed, int startingIntegrity) {
		active = true;
		ended = false;
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

	public void end() {
		ended = true;
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

		if (integrity <= MIN_INTEGRITY) {
			end();
		}
	}

	public void drain(int points) {
		if (!active || ended || points <= 0) {
			return;
		}

		setIntegrity(integrity - points);
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

	/** Add ticks to the pacing accumulator and return the total. */
	public long addTicks(long ticks) {
		tickAccumulator += ticks;
		return tickAccumulator;
	}

	/** Consume {@code ticks} from the pacing accumulator (keeps the remainder). */
	public void consumeTicks(long ticks) {
		tickAccumulator = Math.max(0L, tickAccumulator - ticks);
	}

	public void resetAccumulator() {
		tickAccumulator = 0L;
	}

	public void tickAge() {
		ageTicks++;
	}

	private static int clamp(int value) {
		return Math.max(MIN_INTEGRITY, Math.min(MAX_INTEGRITY, value));
	}
}
