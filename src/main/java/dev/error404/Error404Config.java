package dev.error404;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Plain JSON config, written to {@code config/error404.json} on first launch.
 *
 * <p>Defaults follow the "hardcore" preset chosen for this mod, but every destructive
 * behaviour still has an explicit off switch and a backup path.</p>
 */
public final class Error404Config {
	private static final Logger LOGGER = LoggerFactory.getLogger(Error404.MOD_ID + "/config");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String FILE_NAME = "error404.json";

	private static Error404Config instance = new Error404Config();

	// --- master switches -------------------------------------------------------
	/** Master switch. When false the mod registers its command but never touches the world. */
	public boolean enabled = true;
	/** Keep a region backup of the world before a session starts. */
	public boolean backupOnStart = true;
	/** Real block deletion. Set to false for the "render only" safe mode described in the docs. */
	public boolean realBlockDeletion = true;
	/** Allows the DELETE ending to rename the world folder (never deletes it). */
	public boolean destructiveEnding = false;
	/** Reduces full-screen flashing for photosensitive players. */
	public boolean photosensitivity = false;

	// --- threat level ----------------------------------------------------------
	/**
	 * "chill" | "standard" | "nightmare". Changes what 422 is *allowed to do* (damage,
	 * wall-breaking, waking you up, how many exist), not just its health. See {@link Difficulty}.
	 */
	public String difficulty = "standard";

	// --- chaos ----------------------------------------------------------------
	/**
	 * The "the mod does things to you" layer: a chunk vanishing, your inventory shuffling,
	 * a lightning bolt with no storm, doors opening by themselves. Off makes the mod a slow
	 * deletion simulator; on makes it the reason people stop playing at 3 am.
	 */
	public boolean chaos = true;

	/**
	 * The anti-player layer: takes your spawn point, your name, your items and the door behind you.
	 * Off by default because it is genuinely hostile - a server that turns this on is choosing to
	 * be a bad place to live, which is exactly the reaction this layer is designed to provoke.
	 */
	public boolean banLayer = false;

	// --- integrity pacing ------------------------------------------------------
	/** Seconds of survival per 1% of integrity lost (hardcore preset = 45). */
	public int secondsPerIntegrityPoint = 45;
	/** Extra integrity lost per brand new chunk the player uncovers, in hundredths of a point. */
	public int newChunkDrainHundredths = 2;
	/** Integrity regained per 30s inside an anchor zone. */
	public int anchorRecoveryPer30s = 1;

	// --- deletion rules --------------------------------------------------------
	/** Never delete blocks within this many blocks of a player. */
	public int playerSafeRadius = 8;
	/** Chunk budget per second for the deletion front. */
	/**
	 * How fast the front travels, in chunks per second. 4, up from 2: at 2 the deletion was slow
	 * enough to read as a glitch being fixed, at 4 it reads as the world losing. `chill` halves it
	 * at runtime, so the safe preset is unaffected.
	 */
	public int maxChunksPerSecond = 4;
	/** Protect chests, beds, spawners and other block entities. */
	public boolean protectBlockEntities = true;

	// --- feedback --------------------------------------------------------------
	/** Show the integrity line on the HUD (off by default: horror needs a clean screen). */
	public boolean hud = false;
	/**
	 * Announce the mod in chat when a session starts. Off by default: on a horror server the
	 * players are not supposed to be told that the world is being deleted.
	 */
	public boolean announceOnJoin = false;

	public static Error404Config get() {
		return instance;
	}

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}

	public static void load() {
		Path file = path();
		if (!Files.exists(file)) {
			save();
			return;
		}

		try (Reader reader = Files.newBufferedReader(file)) {
			Error404Config loaded = GSON.fromJson(reader, Error404Config.class);
			if (loaded != null) {
				instance = loaded;
			}
		} catch (IOException | RuntimeException e) {
			LOGGER.warn("could not read {} - keeping defaults", file, e);
		}
	}

	public static void save() {
		Path file = path();
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file)) {
				GSON.toJson(instance, writer);
			}
		} catch (IOException e) {
			LOGGER.warn("could not write {}", file, e);
		}
	}
}
