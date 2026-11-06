package dev.error404.ending;

/**
 * The three ways a session can end. The code stored in {@link dev.error404.integrity.IntegrityState}
 * matches the ordinal + 1 so old saves stay readable.
 */
public enum EndingType {
	/**
	 * A. WORLD FOUND - the player put the save back together: an anchor on the last chunk,
	 * eight data fragments committed. The deletion front stops, the anomalies stay.
	 */
	SALVAGE(1, "WORLD FOUND", "the world was written again"),

	/**
	 * B. WORLD NOT FOUND - integrity hit zero. The region could not be processed.
	 * Non-destructive by default: the save is renamed, never deleted.
	 */
	DELETE(2, "WORLD NOT FOUND", "requested region could not be processed"),

	/**
	 * C. 0x00 - the secret ending. Reached by staring back three times.
	 * Nothing is left to process.
	 */
	NULL(3, "0x00", "nothing to process");

	private final int code;
	private final String title;
	private final String line;

	EndingType(int code, String title, String line) {
		this.code = code;
		this.title = title;
		this.line = line;
	}

	public int code() {
		return code;
	}

	public String title() {
		return title;
	}

	public String line() {
		return line;
	}

	public int color() {
		return switch (this) {
			case SALVAGE -> 0xFF9BE7D0;
			case DELETE -> 0xFFB0182C;
			case NULL -> 0xFF6E6E6E;
		};
	}

	public static EndingType byCode(int code) {
		for (EndingType type : values()) {
			if (type.code == code) {
				return type;
			}
		}

		return null;
	}

	public static EndingType byName(String name) {
		for (EndingType type : values()) {
			if (type.name().equalsIgnoreCase(name)) {
				return type;
			}
		}

		return null;
	}
}
