package dev.error404.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;

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

	private VoidAmbience() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(VoidAmbience::tick);
		ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> {
			ambienceTimer = 0;
			deletedAmbience = false;
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

		// Ambient bed: one looping music-free ambience, swapped between two moods.
		if (ambienceTimer-- <= 0) {
			ambienceTimer = 20 * 24;
			boolean shouldBeDeleted = phase >= 2;

			if (shouldBeDeleted != deletedAmbience) {
				deletedAmbience = shouldBeDeleted;
				client.getSoundManager().stop(Identifier.fromNamespaceAndPath(Error404.MOD_ID, deletedAmbience ? "ambience_calm" : "ambience_deleted"),
						SoundSource.AMBIENT);
				client.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forLocalAmbience(
						deletedAmbience ? ModSounds.AMBIENCE_DELETED : ModSounds.AMBIENCE_CALM, 0.55f, 1.0f));
			}
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
