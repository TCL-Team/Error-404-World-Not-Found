package dev.error404.client.render;

import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

import dev.error404.Error404;

/**
 * The "it is looking back" eyes: same pass, red texture instead of cold blue. Added as its
 * own layer because 1.21.11's {@code EyesLayer.renderType()} takes no state, and switching
 * render types per frame would recompile a pipeline every tick.
 *
 * <p>Whether it draws is decided by the renderer (it removes the layer while the stare is
 * not live), so this class stays stateless.</p>
 */
public class Error422AngryEyesLayer extends EyesLayer {
	public static final Identifier TEXTURE = Error404.id("textures/entity/error_422_eyes_angry.png");
	private static final RenderType EYES = RenderTypes.eyes(TEXTURE);

	public Error422AngryEyesLayer(RenderLayerParent<Error422RenderState, Error422Model> parent) {
		super(parent);
	}

	@Override
	public RenderType renderType() {
		return EYES;
	}
}
