package net.mca.client.model;

import com.google.common.collect.ImmutableList;
import net.mca.Config;
import net.mca.entity.VillagerLike;
import net.mca.entity.ai.relationship.AgeState;
import net.mca.entity.ai.relationship.VillagerDimensions;
import net.minecraft.client.model.*;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;

public class VillagerEntityBaseModelMCA<T extends LivingEntity & VillagerLike<T>> extends BipedEntityModel<T> implements CommonVillagerModel<T> {
    protected static final String BREASTS = "breasts";

    public final ModelPart breasts;

    final VillagerDimensions.Mutable dimensions = new VillagerDimensions.Mutable(AgeState.ADULT);
    float breastSize;

    // Dreams: datos del ultimo setAngles, para volver a aplicar las poses propias de MCA despues de EMF
    protected T lastVillager;
    protected float lastAnimationProgress;
    private final float[][] babyPose = new float[7][6];

    public VillagerEntityBaseModelMCA(ModelPart root) {
        this(root, root);
    }

    /**
     * Dreams: {@code root} puede ser el modelo del jugador (para que EMF / Fresh Animations lo animen) y
     * {@code extras} el arbol propio de MCA de donde salen los pechos.
     */
    public VillagerEntityBaseModelMCA(ModelPart root, ModelPart extras) {
        super(root);
        this.breasts = extras.getChild(BREASTS);
    }

    public static ModelData getModelData(Dilation dilation) {
        ModelData modelData = BipedEntityModel.getModelData(dilation, 0.0f);
        ModelPartData data = modelData.getRoot();

        data.addChild(BREASTS, newBreasts(dilation, 0), ModelTransform.NONE);

        return modelData;
    }

    protected static ModelPartBuilder newBreasts(Dilation dilation, int oy) {
        ModelPartBuilder builder = ModelPartBuilder.create();
        if (Config.getInstance().enableBoobs) {
            builder.uv(18, 21 + oy).cuboid(-3.25F, -1.25F, -1.5F, 6, 3, 3, dilation);
        }
        return builder;
    }

    @Override
    protected Iterable<ModelPart> getHeadParts() {
        return ImmutableList.of(head, hat);
    }

    @Override
    protected Iterable<ModelPart> getBodyParts() {
        return ImmutableList.of(body, rightArm, leftArm, rightLeg, leftLeg);
    }

    @Override
    public void animateModel(T entity, float limbAngle, float limbDistance, float tickDelta) {
        super.animateModel(entity, limbDistance, limbAngle, tickDelta);
        riding |= entity.getAgeState() == AgeState.BABY;
    }

    @Override
    public void setAngles(T villager, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
        if (villager.getAgeState() == AgeState.BABY && !villager.hasVehicle()) {
            limbDistance = (float)Math.sin(villager.age / 12F);
            limbAngle = (float)Math.cos(villager.age / 9F) * 3;
            headYaw += (float)Math.sin(villager.age / 2F);
        }

        //remove the boost for babies
        if (villager.isBaby()) {
            limbAngle /= 3.0f;
        }

        //and add our own
        limbAngle /= (0.2f + villager.getRawScaleFactor());

        super.setAngles(villager, limbAngle, limbDistance, animationProgress, headYaw, headPitch);

        if (villager.getVillagerBrain().isPanicking()) {
            float toRadians = (float)Math.PI / 180;

            float armRaise = (((float)Math.sin(animationProgress / 5) * 30 - 180)
                    + ((float)Math.sin(animationProgress / 3) * 3))
                    * toRadians;
            float waveSideways = ((float)Math.sin(animationProgress / 2) * 12 - 17) * toRadians;

            this.leftArm.pitch = armRaise;
            this.leftArm.roll = -waveSideways;
            this.rightArm.pitch = -armRaise;
            this.rightArm.roll = waveSideways;
        }

        applyVillagerDimensions(villager, villager.isInSneakingPose());

        lastVillager = villager;
        lastAnimationProgress = animationProgress;
        if (villager.getAgeState() == AgeState.BABY) {
            savePose(babyPose);
        }
    }

    private ModelPart[] mainParts() {
        return new ModelPart[]{head, hat, body, rightArm, leftArm, rightLeg, leftLeg};
    }

    private void savePose(float[][] into) {
        ModelPart[] parts = mainParts();
        for (int i = 0; i < parts.length; i++) {
            ModelPart p = parts[i];
            into[i][0] = p.pivotX; into[i][1] = p.pivotY; into[i][2] = p.pivotZ;
            into[i][3] = p.pitch; into[i][4] = p.yaw; into[i][5] = p.roll;
        }
    }

    private void loadPose(float[][] from) {
        ModelPart[] parts = mainParts();
        for (int i = 0; i < parts.length; i++) {
            ModelPart p = parts[i];
            p.pivotX = from[i][0]; p.pivotY = from[i][1]; p.pivotZ = from[i][2];
            p.pitch = from[i][3]; p.yaw = from[i][4]; p.roll = from[i][5];
        }
    }

    /**
     * Dreams: corre la animacion de EMF (si hay) y despues vuelve a poner lo que es propio de MCA:
     * bebes con su balanceo, brazos en panico, y pechos / ropa siguiendo al cuerpo animado.
     */
    protected void applyEmfAnimation() {
        if (lastVillager == null || !net.mca.client.dreams.EmfBridge.animate(lastVillager, this)) {
            return;
        }
        if (lastVillager.getAgeState() == AgeState.BABY) {
            loadPose(babyPose);
        } else if (lastVillager.getVillagerBrain().isPanicking()) {
            float toRadians = (float)Math.PI / 180;
            float armRaise = (((float)Math.sin(lastAnimationProgress / 5) * 30 - 180)
                    + ((float)Math.sin(lastAnimationProgress / 3) * 3)) * toRadians;
            float waveSideways = ((float)Math.sin(lastAnimationProgress / 2) * 12 - 17) * toRadians;
            this.leftArm.pitch = armRaise;
            this.leftArm.roll = -waveSideways;
            this.rightArm.pitch = -armRaise;
            this.rightArm.roll = waveSideways;
        }
        applyVillagerDimensions(lastVillager, lastVillager.isInSneakingPose());
        syncDerivedParts();
    }

    /** Partes que copian a otras (segunda capa de ropa). */
    protected void syncDerivedParts() {
        hat.copyTransform(head);
    }

    @Override
    public void copyBipedStateTo(BipedEntityModel<T> target) {
        super.copyBipedStateTo(target);

        if (target instanceof VillagerEntityBaseModelMCA<T> m) {
            copyCommonAttributes(m);

            m.breasts.visible = breasts.visible;
            m.breasts.copyTransform(breasts);
        }
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, float red, float green, float blue, float alpha) {
        applyEmfAnimation();
        renderCommon(matrices, vertices, light, overlay, red, green, blue, alpha);
    }

    @Override
    public ModelPart getBreastPart() {
        return breasts;
    }

    @Override
    public ModelPart getBodyPart() {
        return body;
    }

    @Override
    public Iterable<ModelPart> getCommonHeadParts() {
        return getHeadParts();
    }

    @Override
    public Iterable<ModelPart> getCommonBodyParts() {
        return getBodyParts();
    }

    @Override
    public Iterable<ModelPart> getBreastParts() {
        return ImmutableList.of(breasts);
    }

    @Override
    public VillagerDimensions.Mutable getDimensions() {
        return dimensions;
    }

    @Override
    public float getBreastSize() {
        return breastSize;
    }

    @Override
    public void setBreastSize(float breastSize) {
        this.breastSize = breastSize;
    }
}
