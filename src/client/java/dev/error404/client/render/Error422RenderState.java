package dev.error404.client.render;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class Error422RenderState extends LivingEntityRenderState {
	/** 0 base, 1 missing texture, 2 the face. */
	public int textureVariant;
	/** 0-1, how long the player has been staring at it. Drives the glitch shader-ish wobble. */
	public float stareProgress;
}
