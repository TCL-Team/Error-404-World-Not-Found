package dev.error404.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;

import dev.error404.Error404;
import dev.error404.ModSounds;
import dev.error404.client.ClientState;

/**
 * Client-side atmosphere: fog gets darker and closer as phases advance, the ambience
 * track cross-fades from "quiet wind" to "silence of a deleted region", and the sky
 * colour is pushed towards black from DESYNC onwards.
 *
 * <p>Implemented without mixins on purpose: everything here is a plain client tick, so a
 * future Minecraft update can break the texture of the mod but not the mod itself.</p>
 */
public final class VoidAmbience {
	private static int ambienceTimer = 0;
	private static boolean deletedAmbience = false;
	private static boolean ambiencePlaying = false;
	/** The sub-bass loop that means "it is within earshot". Owned separately from the bed. */
	private static boolean dronePlaying = false;
	private static int droneTimer = 0;

	private VoidAmbience() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(VoidAmbience::tick);
		ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, level) -> {
			ambienceTimer = 0;
			deletedAmbience = false;
			ambiencePlaying = false;
		});
	}

	private static void tick(Minecraft client) {
		ClientState.tick();
		ClientLevel level = client.level;

		if (level == null || client.player == null) {
			return;
		}

		int phase = ClientState.phase();
		float intensity = Mth.clamp(phase / 4.0f, 0.0f, 1.0f);

		// Ambient bed: a looping, music-free ambience that follows the phase. Phase 1 gets a
		// heartbeat instead - the world is still quiet, something in it is not.
		// Tracks are exactly 19 s long and resume from a timer, so the restart is scheduled
		// just before the loop ends instead of a full second late (which left a silent gap).
		if (ambienceTimer-- <= 0 || !ambiencePlaying) {
			ambienceTimer = 20 * 19;
			boolean shouldBeDeleted = phase >= 2;

			if (!ambiencePlaying || shouldBeDeleted != deletedAmbience) {
				deletedAmbience = shouldBeDeleted;
				ambiencePlaying = true;

				// stop() takes the *sound event id*, not the sound-sheet path.
				client.getSoundManager().stop(
						(deletedAmbience ? ModSounds.AMBIENCE_CALM : ModSounds.AMBIENCE_DELETED).location(),
						SoundSource.AMBIENT);
				client.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forLocalAmbience(
						deletedAmbience ? ModSounds.AMBIENCE_DELETED : ModSounds.AMBIENCE_CALM, 1.0f, 0.55f));
			}
		}

		// When it is close, the world's own soundtrack stops first. Silence is the cue.
		if (ambiencePlaying && proximity(client) > 0.35f) {
			client.getSoundManager().stop(ModSounds.AMBIENCE_CALM.location(), SoundSource.AMBIENT);
			client.getSoundManager().stop(ModSounds.AMBIENCE_DELETED.location(), SoundSource.AMBIENT);
			ambiencePlaying = false;
			ambienceTimer = 20 * 8; // comes back a few seconds after it leaves
		}

		// position-dependent dread: heartbeat runs while 422 is close, footsteps answer at night
		// proximity drone: a low dissonant swell that fades in under everything else
		tickDrone(client);

		if (phase >= 1 && level.getGameTime() % 40 == 0 && ClientState.integrity() < 90) {
			playerHeartbeat(client, phase);
		}

		if (phase >= 2 && level.getGameTime() % 1200 == 0) {
			client.level.playLocalSound(client.player.getX(), client.player.getY(), client.player.getZ(),
					ModSounds.ERROR_TONE, SoundSource.AMBIENT, 0.25f, 0.45f, false);
		}

		if (phase >= 4 && client.level.getGameTime() % 260 == 0) {
			client.level.playLocalSound(client.player.getX(), client.player.getY(), client.player.getZ(),
					ModSounds.SUB_HIT, SoundSource.AMBIENT, 0.35f, 0.5f, false);
		}

		if (phase >= 3) {
			// very rare "your own footsteps, one step behind" scare
			if (level.random.nextInt(1200) == 0) {
				client.level.playLocalSound(client.player.getX(), client.player.getY(), client.player.getZ(),
						ModSounds.FOOTSTEP_ECHO, SoundSource.PLAYERS, 0.5f, 0.9f, false);
			}
		}

		if (phase >= 2 && client.level.getGameTime() % 600 == 0 && !isDay(level)) {
			client.level.playLocalSound(client.player.getX(), client.player.getY(), client.player.getZ(),
					ModSounds.WHISPER, SoundSource.AMBIENT, 0.3f, 0.75f, false);
		}
	}

	/**
	 * Starts and stops the proximity drone. It is a loop, so it is started once, faded in by
	 * the ambient volume, and stopped the moment the entity leaves - no per-tick play calls,
	 * which is what makes an ambience layer cost nothing.
	 */
	private static void tickDrone(Minecraft client) {
		float proximity = proximity(client);
		boolean wanted = proximity > 0.15f;

		if (droneTimer > 0) {
			droneTimer--;
		}

		if (wanted && !dronePlaying) {
			dronePlaying = true;
			client.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance
					.forLocalAmbience(ModSounds.PROXIMITY_DRONE, 1.0f - proximity * 0.12f, 0.45f));
			droneTimer = 20 * 19;
		} else if (wanted && dronePlaying && droneTimer == 0) {
			// restart just before the 19 s loop ends so there is no silent gap
			droneTimer = 20 * 19;
			client.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance
					.forLocalAmbience(ModSounds.PROXIMITY_DRONE, 1.0f - proximity * 0.12f, 0.45f));
		} else if (!wanted && dronePlaying) {
			dronePlaying = false;
			client.getSoundManager().stop(ModSounds.PROXIMITY_DRONE.location(), SoundSource.AMBIENT);
		}
	}

	/** How close the nearest 422 is, as 0..1, for audio ducking. */
	private static float proximity(Minecraft client) {
		if (client.player == null || client.level == null) {
			return 0.0f;
		}

		double nearest = Double.MAX_VALUE;

		for (net.minecraft.world.entity.Entity entity : client.level.entitiesForRendering()) {
			if (entity instanceof dev.error404.entity.Error422Entity) {
				nearest = Math.min(nearest, entity.distanceTo(client.player));
			}
		}

		return nearest > 18.0 ? 0.0f : (float) (1.0 - nearest / 18.0);
	}

	/** Daytime check without a dimension helper: 0-11999 is day in the overworld cycle. */
	private static boolean isDay(ClientLevel level) {
		long timeOfDay = level.getDayTime() % 24000L;
		return timeOfDay < 12000L;
	}

	/**
	 * Heartbeat that gets faster the closer 422 is. Pure client-side: it only reacts to the
	 * entity the server already synced, so there is nothing to exploit.
	 */
	private static void playerHeartbeat(Minecraft client, int phase) {
		if (client.player == null || client.level == null) {
			return;
		}

		double nearest = 999.0;

		for (net.minecraft.world.entity.Entity entity : client.level.entitiesForRendering()) {
			if (entity instanceof dev.error404.entity.Error422Entity) {
				nearest = Math.min(nearest, entity.distanceTo(client.player));
			}
		}

		if (nearest > 40.0) {
			return;
		}

		float proximity = (float) (1.0 - Math.min(1.0, nearest / 40.0));
		float volume = 0.15f + proximity * 0.55f;
		float pitch = 0.7f + proximity * 0.5f + (phase >= 4 ? 0.15f : 0.0f);

		client.level.playLocalSound(client.player.getX(), client.player.getY(), client.player.getZ(),
				ModSounds.HEARTBEAT, SoundSource.AMBIENT, volume, pitch, false);
	}

	/** Fog colour interpolation target for the sky renderer (0 = normal, 1 = void). */
	public static int fogColor() {
		float intensity = Mth.clamp(ClientState.phase() / 4.0f, 0.0f, 1.0f);
		int base = 0xC0D8FF;
		int target = 0x05060A;
		int r = (int) Mth.lerp(intensity, (base >> 16) & 0xFF, (target >> 16) & 0xFF);
		int g = (int) Mth.lerp(intensity, (base >> 8) & 0xFF, (target >> 8) & 0xFF);
		int b = (int) Mth.lerp(intensity, base & 0xFF, target & 0xFF);
		return (r << 16) | (g << 8) | b;
	}

	/** Fog start/end shrink with the phase, so the horizon disappears before the chunks do. */
	public static float fogStart() {
		return 90.0f - 60.0f * Mth.clamp(ClientState.phase() / 4.0f, 0.0f, 1.0f);
	}

	public static float fogEnd() {
		return 220.0f - 150.0f * Mth.clamp(ClientState.phase() / 4.0f, 0.0f, 1.0f);
	}

}
