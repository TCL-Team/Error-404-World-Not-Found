package dev.error404;

/**
 * How bad 422 is allowed to be.
 *
 * <p>These are not "enemy health" sliders. Each preset changes a *behaviour*, because a
 * horror entity that is merely tankier is not scarier, and one that two-shots you in the
 * first ten minutes is a refund request. CHILL is for players who want the world to dissolve
 * without the entity hunting them; NIGHTMARE is the ERROR 422 video expectation.</p>
 */
public enum Difficulty {
	/** The world still dies. 422 watches, never touches. */
	CHILL(
			"chill",
			/*contact*/ 0.0f, /*stareBase*/ 0.0f, /*stareStep*/ 0.0f,
			/*breakInterval*/ 0, /*sleepFrom*/ 99, /*darkness*/ false, /*maxAlive*/ 1, /*teleport*/ 90),

	/** Intended experience: it hurts, it can kill, but the rules are learnable. */
	STANDARD(
			"standard",
			/*contact*/ 6.0f, /*stareBase*/ 3.0f, /*stareStep*/ 1.5f,
			/*breakInterval*/ 20, /*sleepFrom*/ 3, /*darkness*/ true, /*maxAlive*/ 2, /*teleport*/ 40),

	/** ERROR 422 energy: it is faster than you, it eats walls, it wakes you up, it kills. */
	NIGHTMARE(
			"nightmare",
			/*contact*/ 10.0f, /*stareBase*/ 5.0f, /*stareStep*/ 2.0f,
			/*breakInterval*/ 10, /*sleepFrom*/ 1, /*darkness*/ true, /*maxAlive*/ 3, /*teleport*/ 20);

	private final String id;
	public final float contactDamage;
	public final float stareBaseDamage;
	public final float stareDamageStep;
	/** 0 = it can never break a block. */
	public final int breakIntervalTicks;
	/** Phase ordinal from which it can wake a sleeping player (99 = never). */
	public final int sleepHauntFromPhase;
	public final boolean darknessAura;
	public final int maxAlive;
	/** Ticks between "it was not where you thought" teleports. */
	public final int teleportIntervalTicks;

	Difficulty(String id, float contactDamage, float stareBaseDamage, float stareDamageStep,
			int breakIntervalTicks, int sleepHauntFromPhase, boolean darknessAura, int maxAlive,
			int teleportIntervalTicks) {
		this.id = id;
		this.contactDamage = contactDamage;
		this.stareBaseDamage = stareBaseDamage;
		this.stareDamageStep = stareDamageStep;
		this.breakIntervalTicks = breakIntervalTicks;
		this.sleepHauntFromPhase = sleepHauntFromPhase;
		this.darknessAura = darknessAura;
		this.maxAlive = maxAlive;
		this.teleportIntervalTicks = teleportIntervalTicks;
	}

	public String id() {
		return id;
	}

	public static Difficulty byId(String id) {
		for (Difficulty difficulty : values()) {
			if (difficulty.id.equalsIgnoreCase(id)) {
				return difficulty;
			}
		}

		return STANDARD;
	}

	/** Parsed from the config file on load; anything unknown falls back to STANDARD. */
	public static Difficulty current() {
		return byId(Error404Config.get().difficulty);
	}
}
