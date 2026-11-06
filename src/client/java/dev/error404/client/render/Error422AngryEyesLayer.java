package dev.error404.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

import dev.error404.Error404;

/**
 * The "it is looking back" eyes: same pass as the cold ones, red, and only drawn while the
 * player is holding eye contact ({@code stareProgress > 0}).
 *
 * <p>Written as a plain render layer instead of vanilla's {@code EyesLayer} because that one
 * takes no state, and quietly drawing the red texture all the time would leak the mechanic.</p>
 */
public class Error422AngryEyesLayer extends RenderLayer<Error422RenderState, Error422Model> {
	public static final Identifier TEXTURE = Error404.id("textures/entity/error_422_eyes_angry.png");
	private static final RenderType EYES = RenderTypes.eyes(TEXTURE);

	public Error422AngryEyesLayer(RenderLayerParent<Error422RenderState, Error422Model> parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords,
			Error422RenderState state, float yRot, float xRot) {
		if (state.stareProgress <= 0.0f) {
			return;
		}

		collector.order(1).submitModel(getParentModel(), state, poseStack, EYES, lightCoords,
				OverlayTexture.NO_OVERLAY, state.outlineColor, null);
	}
}
