package dev.error404.client.render;

import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

import dev.error404.Error404;

/**
 * Full-bright eyes over the head. They are always on, so a player looking into a dark forest
 * sees two points of light before they see the body - which is the point.
 */
public class Error422EyesLayer extends EyesLayer {
	private static final RenderType EYES = RenderTypes.eyes(Error404.id("textures/entity/error_422_eyes.png"));

	public Error422EyesLayer(RenderLayerParent<Error422RenderState, Error422Model> parent) {
		super(parent);
	}

	@Override
	public RenderType renderType() {
		return EYES;
	}
}
