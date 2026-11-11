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
        // Dreams: de la mano / en brazos / en la espalda va "montado" en el jugador, pero no sentado
        if (entity instanceof net.mca.entity.VillagerEntityMCA v && v.dreamsAttach != 0) {
            riding = false;
        }
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
        if (villager instanceof net.mca.entity.VillagerEntityMCA life) {
            net.mca.client.dreams.DreamsPoses.applyLife(life, this);
        }
        net.mca.client.dreams.DreamsPoses.apply(villager, this);
        syncDerivedParts();
        placeBreasts(villager);

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
     * Dreams: Starcompat lo llama cuando EMF termina de animar este modelo. Vuelve a poner lo que es propio
     * de MCA: bebes con su balanceo, brazos en panico, las interacciones, y pechos / ropa siguiendo al cuerpo.
     */
    public void dreamsAfterEmfAnimation() {
        if (lastVillager == null) {
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
        if (lastVillager instanceof net.mca.entity.VillagerEntityMCA life) {
            net.mca.client.dreams.DreamsPoses.applyLife(life, this);
        }
        net.mca.client.dreams.DreamsPoses.apply(lastVillager, this);
        syncDerivedParts();
        placeBreasts(lastVillager);
    }

    // --------------------------------------------------------------- Dreams: pechos que siguen al cuerpo

    private static final java.util.Map<net.minecraft.entity.Entity, float[]> PHYSICS = new java.util.WeakHashMap<>();

    /**
     * Ubica los pechos pegados al cuerpo (posicion y rotacion completas, no solo la inclinacion), con un poco
     * de fisica: un resorte suave que reacciona a los saltos, al caminar y a los giros del cuerpo, sin exagerar.
     */
    protected void placeBreasts(T villager) {
        float size = getBreastSize();
        float scaled = size * getDimensions().getBreasts();
        float sx = scaled * 0.2f + 1.05f;
        float sy = scaled * 0.75f + 0.75f;
        float sz = scaled * 0.75f + 0.75f;

        // fisica: x = desplazamiento (rad), v = velocidad
        float[] st = PHYSICS.computeIfAbsent(villager, k -> new float[]{0, 0, (float) k.getVelocity().y, (float) net.minecraft.util.Util.getMeasuringTimeMs(), 0});
        float now = (float) net.minecraft.util.Util.getMeasuringTimeMs();
        float dt = Math.min(0.05f, Math.max(0.0f, (now - st[3]) / 1000.0f));
        if (dt > 0) {
            float vy = (float) villager.getVelocity().y;
            float accel = (vy - st[2]) / Math.max(dt, 0.016f);
            float bodyTurn = body.pitch - st[4];
            float walk = (float) Math.sin(lastAnimationProgress * 0.9f) * (float) villager.getVelocity().horizontalLength() * 0.6f;
            float target = -accel * 0.012f - bodyTurn * 0.4f + walk;
            float k = 170f, d = 13f;
            st[1] += (-(st[0] - target) * k - st[1] * d) * dt;
            st[0] = Math.max(-0.14f, Math.min(0.14f, st[0] + st[1] * dt));
            st[2] = vy;
            st[3] = now;
            st[4] = body.pitch;
        }
        float jiggle = size > 0.05f ? st[0] * Math.min(1.0f, size * 1.6f) : 0;

        // posicion sobre el cuerpo (orden de rotacion de Minecraft: Z, Y, X)
        float ox = 0.25f;
        float oy = (float) (5.0f - Math.pow(size, 0.5) * 2.5f);
        float oz = -1.5f + size * 0.25f;
        float cp = (float) Math.cos(body.pitch), sp = (float) Math.sin(body.pitch);
        float cyw = (float) Math.cos(body.yaw), syw = (float) Math.sin(body.yaw);
        float cr = (float) Math.cos(body.roll), sr = (float) Math.sin(body.roll);
        // X
        float y1 = oy * cp - oz * sp;
        float z1 = oy * sp + oz * cp;
        float x1 = ox;
        // Y
        float x2 = x1 * cyw + z1 * syw;
        float z2 = -x1 * syw + z1 * cyw;
        float y2 = y1;
        // Z
        float x3 = x2 * cr - y2 * sr;
        float y3 = x2 * sr + y2 * cr;

        for (ModelPart part : getBreastParts()) {
            part.pivotX = (body.pivotX + x3) / sx;
            part.pivotY = (body.pivotY + y3 + jiggle * 1.2f) / sy;
            part.pivotZ = (body.pivotZ + z2) / sz;
            part.pitch = (float) Math.PI * 0.3f + body.pitch + jiggle;
            part.yaw = body.yaw;
            part.roll = body.roll;
        }
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
