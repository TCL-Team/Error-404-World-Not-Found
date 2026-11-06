package dev.error404.client.render;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartNames;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

/**
 * 422's body: vanilla humanoid proportions but 3 blocks tall, arms that reach past the
 * knees, no legs below mid-shin, and a head that is a hollow box.
 *
 * <p>UV layout is generated and painted by {@code tools/gen_textures.py} from the same box
 * sizes used here: head texOffs(0,0), body (0,32), arms (32,0), legs (48,0) on a 128x128
 * sheet. Box-UV order is west|north|east|south over the side strip, with up/down above it,
 * so the face the player looks at is the {@code north} rectangle of each box.</p>
 */
public class Error422Model extends EntityModel<LivingEntityRenderState> {
	private final ModelPart head;
	private final ModelPart body;
	private final ModelPart rightArm;
	private final ModelPart leftArm;
	private final ModelPart rightLeg;
	private final ModelPart leftLeg;

	public Error422Model(ModelPart root) {
		super(root);
		this.head = root.getChild(PartNames.HEAD);
		this.body = root.getChild(PartNames.BODY);
		this.rightArm = root.getChild(PartNames.RIGHT_ARM);
		this.leftArm = root.getChild(PartNames.LEFT_ARM);
		this.rightLeg = root.getChild(PartNames.RIGHT_LEG);
		this.leftLeg = root.getChild(PartNames.LEFT_LEG);
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

		// head: 8x8x8 at the very top, tilted a few degrees
		root.addOrReplaceChild(PartNames.HEAD,
				CubeListBuilder.create().texOffs(0, 0).addBox(-4.0f, -8.0f, -4.0f, 8.0f, 8.0f, 8.0f),
				PartPose.offsetAndRotation(0.0f, -8.0f, 0.0f, 0.06f, 0.0f, 0.08f));

		// body: thin and long
		root.addOrReplaceChild(PartNames.BODY,
				CubeListBuilder.create().texOffs(0, 32).addBox(-3.0f, 0.0f, -2.0f, 6.0f, 18.0f, 4.0f),
				PartPose.offset(0.0f, 0.0f, 0.0f));

		// arms: 26 units long, hanging past the knees
		root.addOrReplaceChild(PartNames.RIGHT_ARM,
				CubeListBuilder.create().texOffs(32, 0).mirror().addBox(-2.0f, 0.0f, -2.0f, 4.0f, 26.0f, 4.0f),
				PartPose.offsetAndRotation(-3.0f, 1.0f, 0.0f, 0.15f, 0.0f, 0.12f));
		root.addOrReplaceChild(PartNames.LEFT_ARM,
				CubeListBuilder.create().texOffs(32, 0).addBox(-2.0f, 0.0f, -2.0f, 4.0f, 26.0f, 4.0f),
				PartPose.offsetAndRotation(3.0f, 1.0f, 0.0f, -0.15f, 0.0f, -0.12f));

		// legs: short, ending in nothing
		root.addOrReplaceChild(PartNames.RIGHT_LEG,
				CubeListBuilder.create().texOffs(48, 0).mirror().addBox(-2.0f, 0.0f, -2.0f, 4.0f, 14.0f, 4.0f),
				PartPose.offset(-1.6f, 18.0f, 0.0f));
		root.addOrReplaceChild(PartNames.LEFT_LEG,
				CubeListBuilder.create().texOffs(48, 0).addBox(-2.0f, 0.0f, -2.0f, 4.0f, 14.0f, 4.0f),
				PartPose.offset(1.6f, 18.0f, 0.0f));

		return LayerDefinition.create(mesh, 128, 128);
	}

	@Override
	public void setupAnim(LivingEntityRenderState state) {
		super.setupAnim(state);

		float age = state.ageInTicks;
		float walk = state.walkAnimationSpeed;
		float limb = state.walkAnimationPos;

		head.xRot = state.xRot * Mth.RAD_TO_DEG * 0.6f;
		head.yRot = state.yRot * Mth.RAD_TO_DEG * 0.6f;

		// A constant, wrong tilt: the head is never quite level.
		head.zRot = 0.12f + Mth.sin(age * 0.03f) * 0.04f;

		// Limbs twitch instead of swinging. The faster it moves, the worse the twitch.
		float twitch = Mth.sin(age * 0.7f) * (0.05f + walk * 0.25f);
		rightArm.xRot = 0.15f + Mth.cos(limb * 0.4f) * 0.5f * walk;
		leftArm.xRot = -0.15f + Mth.cos(limb * 0.4f + Mth.PI) * 0.5f * walk;
		rightArm.zRot = 0.12f + twitch;
		leftArm.zRot = -0.12f - twitch;

		rightLeg.xRot = Mth.cos(limb * 0.4f) * 0.9f * walk;
		leftLeg.xRot = Mth.cos(limb * 0.4f + Mth.PI) * 0.9f * walk;

		// Idle: a slow breathing bob, far too regular for a living thing.
		body.yRot = Mth.sin(age * 0.02f) * 0.06f;
		body.xRot = Mth.sin(age * 0.015f) * 0.03f;
	}
}
