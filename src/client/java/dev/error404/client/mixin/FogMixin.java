package dev.error404.client.mixin;

import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.FogEnvironment;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.error404.client.ClientState;

/**
 * Pulls the fog in and turns it black as the world degrades, without touching the sky
 * shader. If this mixin ever fails to apply, the mod still works - it just looks less
 * suffocating, which is why it is isolated in its own file.
 */
@Mixin(FogEnvironment.class)
public class FogMixin {
	@Inject(method = "setupFog", at = @At("TAIL"), require = 0)
	private void error404$tightenFog(FogEnvironment.FogDataAccess access, FogData fogData, CallbackInfo ci) {
		int phase = ClientState.phase();

		if (phase <= 0) {
			return;
		}

		// Fog rushes in as the phase climbs: by ANNEXATION you can barely see 60 blocks.
		float closeness = Math.min(1.0f, phase / 4.0f);
		fogData.renderDistanceStart = Math.min(fogData.renderDistanceStart, 64.0f - 40.0f * closeness);
		fogData.renderDistanceEnd = Math.min(fogData.renderDistanceEnd, 120.0f - 80.0f * closeness);
	}
}
