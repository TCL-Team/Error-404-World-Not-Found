package dev.error404.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.error404.client.ClientState;

/**
 * Pulls the fog in and turns the horizon black as the world degrades, without touching the
 * sky shader.
 *
 * <p>Targets the atmospheric environment (the one that applies outdoors, which is where all
 * of this mod happens) instead of the abstract {@code FogEnvironment}, because the concrete
 * method has the full typed signature and keeps this mixin readable across versions.</p>
 *
 * <p>If this mixin ever fails to apply the mod still works - it just looks less suffocating,
 * which is why it lives in its own file and its own config with {@code required: false}.</p>
 */
@Mixin(AtmosphericFogEnvironment.class)
public class FogMixin {
	@Inject(
			method = "setupFog(Lnet/minecraft/client/renderer/fog/FogData;Lnet/minecraft/client/Camera;Lnet/minecraft/client/multiplayer/ClientLevel;FLnet/minecraft/client/DeltaTracker;)V",
			at = @At("TAIL"),
			require = 0
	)
	private void error404$tightenFog(FogData fog, Camera camera, ClientLevel level, float renderDistance,
			DeltaTracker deltaTracker, CallbackInfo ci) {
		int phase = ClientState.phase();

		if (phase <= 1) {
			return;
		}

		// Fog rushes in as the phase climbs: by ANNEXATION the horizon is ~40 blocks away.
		float closeness = Math.min(1.0f, (phase - 1) / 3.0f);
		float start = Math.max(2.0f, 96.0f - 74.0f * closeness);
		float end = Math.max(start + 6.0f, Math.min(renderDistance, 192.0f - 140.0f * closeness));

		fog.environmentalStart = Math.min(fog.environmentalStart, start);
		fog.environmentalEnd = Math.min(fog.environmentalEnd, end);
		fog.skyEnd = Math.min(fog.skyEnd, end);
		fog.renderDistanceStart = Math.min(fog.renderDistanceStart, start);
		fog.renderDistanceEnd = Math.min(fog.renderDistanceEnd, end);
	}
}
