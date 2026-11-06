package dev.error404;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * Every sound in the mod is synthesized by {@code tools/gen_sounds.py} - nothing here is
 * sampled from another mod, so the whole audio package is original.
 */
public final class ModSounds {
	private ModSounds() {
	}

	public static final SoundEvent STATIC_BURST = register("static_burst");
	public static final SoundEvent ERROR_BLIP = register("error_blip");
	public static final SoundEvent WHISPER = register("whisper");
	public static final SoundEvent HEARTBEAT = register("heartbeat");
	public static final SoundEvent DATA_CHIRP = register("data_chirp");
	public static final SoundEvent SUB_HIT = register("sub_hit");
	public static final SoundEvent PHASE_SHIFT = register("phase_shift");
	public static final SoundEvent ANCHOR_HUM = register("anchor_hum");
	public static final SoundEvent FRAGMENT_PICKUP = register("fragment_pickup");
	public static final SoundEvent ENTITY_AMBIENT = register("entity_ambient");
	public static final SoundEvent ENTITY_ATTACK = register("entity_attack");
	public static final SoundEvent STRUCTURE_APPEAR = register("structure_appear");
	public static final SoundEvent STRUCTURE_REVOKE = register("structure_revoke");
	public static final SoundEvent CHUNK_DELETE = register("chunk_delete");
	public static final SoundEvent FOOTSTEP_ECHO = register("footstep_echo");
	public static final SoundEvent ERROR_TONE = register("error_tone");
	/** Sub-bass loop that plays while 422 is within earshot. */
	public static final SoundEvent PROXIMITY_DRONE = register("proximity_drone");
	/** The sudden-death impact. */
	public static final SoundEvent DEATH_STING = register("death_sting");
	public static final SoundEvent TITLE_DRONE = register("title_drone");
	public static final SoundEvent SCARE_STING = register("scare_sting");
	public static final SoundEvent SCARE_BREATH = register("scare_breath");
	public static final SoundEvent SCARE_WHISPER_NAME = register("scare_whisper_name");
	public static final SoundEvent AMBIENCE_CALM = register("ambience_calm");
	public static final SoundEvent AMBIENCE_DELETED = register("ambience_deleted");

	private static SoundEvent register(String name) {
		Identifier id = Error404.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	/** Forces static initialization so every event is registered in order. */
	public static void initialize() {
		Error404.LOGGER.info("[error404] registered {} sound events", BuiltInRegistries.SOUND_EVENT.size());
	}
}
