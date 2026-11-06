package dev.error404.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import dev.error404.Error404;
import dev.error404.entity.Error422Entity;

/**
 * Renders 422 with three swappable textures and a body that occasionally jitters a few
 * pixels off its own position - the "failed to process" read.
 */
public class Error422Renderer extends MobRenderer<Error422Entity, Error422RenderState, Error422Model> {
	private static final Identifier BASE = Error404.id("textures/entity/error_422.png");
	private static final Identifier MISSING = Error404.id("textures/entity/error_422_missing.png");
	private static final Identifier FACE = Error404.id("textures/entity/error_422_face.png");
	private final RandomSource jitter = RandomSource.create();

	public Error422Renderer(EntityRendererProvider.Context context) {
		super(context, new Error422Model(context.bakeLayer(ModModelLayers.ERROR_422)), 0.0f);
		this.shadowRadius = 0.0f;
	}

	@Override
	public Error422RenderState createRenderState() {
		return new Error422RenderState();
	}

	@Override
	public void extractRenderState(Error422Entity entity, Error422RenderState state, float tickProgress) {
		super.extractRenderState(entity, state, tickProgress);
		state.textureVariant = entity.textureVariant();
		state.stareProgress = Mth.clamp(entity.stareTicks() / (float) Error422Entity.STARE_LIMIT_TICKS, 0.0f, 1.0f);
	}

	@Override
	public Identifier getTextureLocation(Error422RenderState state) {
		return switch (state.textureVariant) {
			case 1 -> MISSING;
			case 2 -> FACE;
			default -> BASE;
		};
	}

	@Override
	protected void setupRotations(Error422RenderState state, com.mojang.blaze3d.vertex.PoseStack poseStack, float bob, float yBodyRot) {
		// No death animation, no damage tilt: it is always exactly upright.
		poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0f - yBodyRot));

		// Sub-pixel corruption: the body drifts a fraction of a block off its own hitbox.
		float drift = state.textureVariant == 1 ? 0.04f : 0.008f;
		poseStack.translate((jitter.nextFloat() - 0.5f) * drift, 0.0, (jitter.nextFloat() - 0.5f) * drift);

		if (state.stareProgress > 0.0f) {
			poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(Mth.sin(state.ageInTicks * 0.9f) * 1.6f * state.stareProgress));
		}
	}

	@Override
	protected boolean isBodyVisible(Error422RenderState state) {
		return true;
	}
}
