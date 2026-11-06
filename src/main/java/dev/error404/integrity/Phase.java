package dev.error404.integrity;

import net.minecraft.network.chat.Component;

/**
 * The five states of a dying world. Phases only ever move forward: once the world has
 * desynced it never pretends to be healthy again.
 */
public enum Phase {
	SIGNAL("ERR_404: integrity nominal", "the world is still here"),
	DESYNC("ERR_404: chunk desync detected", "something stopped saving"),
	DELETION("ERR_404: region deleted", "the world is being removed"),
	CASCADE("ERR_404: cascade failure", "it is removing itself now"),
	ANNEXATION("ERR_404: world not found", "there is nothing left to process");

	private final String errorLine;
	private final String whisper;

	Phase(String errorLine, String whisper) {
		this.errorLine = errorLine;
		this.whisper = whisper;
	}

	/** Chat line shown when the world enters this phase. */
	public String errorLine() {
		return errorLine;
	}

	/** One-line description used by {@code /error404 status}. */
	public String whisper() {
		return whisper;
	}

	public Component displayName() {
		return Component.literal(name() + " (" + (ordinal() * 20) + "%-" + ((ordinal() + 1) * 20) + "%)");
	}

	/** Phase for a given integrity value. */
	public static Phase fromIntegrity(int integrity) {
		if (integrity <= 0) {
			return ANNEXATION;
		}

		if (integrity > 80) {
			return SIGNAL;
		}

		if (integrity > 60) {
			return DESYNC;
		}

		if (integrity > 40) {
			return DELETION;
		}

		if (integrity > 20) {
			return CASCADE;
		}

		return ANNEXATION;
	}

	public static Phase byName(String name) {
		for (Phase phase : values()) {
			if (phase.name().equalsIgnoreCase(name)) {
				return phase;
			}
		}

		return null;
	}
}
