package net.mca.client.dreams;

import net.mca.client.dreams.anim.DreamsAnimation;
import net.mca.client.model.VillagerEntityBaseModelMCA;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Dreams: reproduce las animaciones de MCA (estilo Blockbench) en el aldeano y en el jugador.
 * <p>
 * Las de pareja (beso, abrazo, bailes...) usan un "ancla" que manda el servidor: los dos se dibujan en posiciones
 * calculadas desde esa ancla, asi que siempre quedan juntos y alineados, aunque el servidor los tenga un poco
 * corridos. Se aplican al final de la pose normal y, si EMF / Fresh Animations esta animando, otra vez despues de
 * EMF (Starcompat llama a {@link #afterEmfAnimation}); los huesos que la animacion no usa siguen con EMF.
 */
public final class DreamsPoses {
    private static final class Active {
        String name = "";
        final DreamsAnimation anim;
        final String participant;
        final long startTick;
        final boolean anchored;
        final double ax, ay, az;
        final float ayaw;

        Active(DreamsAnimation anim, String participant, long startTick, boolean anchored, double ax, double ay, double az, float ayaw) {
            this.anim = anim;
            this.participant = participant;
            this.startTick = startTick;
            this.anchored = anchored;
            this.ax = ax;
            this.ay = ay;
            this.az = az;
            this.ayaw = ayaw;
        }
    }

    private static final Map<Integer, Active> ACTIVE = new HashMap<>();
    private static final Map<Integer, Boolean> PUSHED = new HashMap<>();
    private static Perspective savedPerspective;
    private static boolean localLocked;

    private DreamsPoses() {
    }

    // ------------------------------------------------------------------ arrancar

    /** Animacion de pareja que manda el servidor. */
    public static void startPair(String name, int villagerId, int playerId, double ax, double ay, double az, float ayaw) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            if (client.world == null) {
                return;
            }
            Optional<DreamsAnimation> anim = DreamsAnimation.get(name);
            if (anim.isEmpty()) {
                return;
            }
            long now = client.world.getTime();
            Active va = new Active(anim.get(), "villager", now, true, ax, ay, az, ayaw);
            Active pa = new Active(anim.get(), "player", now, true, ax, ay, az, ayaw);
            va.name = name;
            pa.name = name;
            ACTIVE.put(villagerId, va);
            ACTIVE.put(playerId, pa);
            if (client.player != null && client.player.getId() == playerId) {
                localLocked = true;
                if (anim.get().thirdPerson && savedPerspective == null) {
                    savedPerspective = client.options.getPerspective();
                    client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
                }
            }
        });
    }

    /** Animacion de una sola persona (saludar, hablar...). No pisa una de pareja. */
    public static void startSolo(Entity entity, String name) {
        Active current = ACTIVE.get(entity.getId());
        if (current != null && current.anchored) {
            return;
        }
        DreamsAnimation.get(name).ifPresent(anim ->
                ACTIVE.put(entity.getId(), new Active(anim, "self", entity.getWorld().getTime(), false, 0, 0, 0, 0)));
    }

    /** Gesto que manda el servidor (al contestar en una charla). */
    public static void startSoloById(int entityId, String name) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            if (client.world != null) {
                Entity e = client.world.getEntityById(entityId);
                if (e != null) {
                    Active current = ACTIVE.get(entityId);
                    if (current == null || !current.anchored) {
                        ACTIVE.remove(entityId);
                    }
                    startSolo(e, name);
                }
            }
        });
    }

    /** jugador (id) -> aldeano (id) que lleva de la mano, en brazos o en la espalda. */
    private static final Map<Integer, Integer> HELD = new HashMap<>();

    /** De la mano / en brazos / en la espalda (lo manda el servidor). */
    public static void setAttach(int villagerId, int mode, int playerId) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            HELD.values().removeIf(v -> v == villagerId);
            if (mode != 0 && playerId >= 0) {
                HELD.put(playerId, villagerId);
            }
            if (client.world != null && client.world.getEntityById(villagerId) instanceof net.mca.entity.VillagerEntityMCA v) {
                v.dreamsAttach = mode;
                v.dreamsAttachTo = playerId;
                v.calculateDimensions();
            }
        });
    }

    private static net.minecraft.entity.player.PlayerEntity partner(net.mca.entity.VillagerEntityMCA v) {
        return v.getWorld().getEntityById(v.dreamsAttachTo) instanceof net.minecraft.entity.player.PlayerEntity p ? p : null;
    }

    private static int attachMode(Entity entity) {
        if (entity instanceof net.mca.entity.VillagerEntityMCA v && v.dreamsAttach != 0) {
            if (v.dreamsAttach == net.mca.server.dreams.DreamsAttach.HAND) {
                return partner(v) != null ? v.dreamsAttach : 0;
            }
            if (v.getVehicle() instanceof net.minecraft.entity.player.PlayerEntity) {
                return v.dreamsAttach;
            }
        }
        return 0;
    }

    private static int carriedMode(Entity entity) {
        if (entity instanceof net.minecraft.entity.player.PlayerEntity) {
            Integer id = HELD.get(entity.getId());
            if (id != null) {
                Entity v = entity.getWorld().getEntityById(id);
                if (v instanceof net.mca.entity.VillagerEntityMCA m && m.dreamsAttachTo == entity.getId()) {
                    return attachMode(v);
                }
            }
        }
        return 0;
    }

    public static boolean isAnimating(Entity entity) {
        return current(entity) != null;
    }

    public static boolean isLocalPlayerLocked() {
        return localLocked;
    }

    private static Active current(Entity entity) {
        Active a = ACTIVE.get(entity.getId());
        if (a == null || entity.getWorld() == null) {
            return null;
        }
        float t = time(entity, a);
        if (!a.anim.loop && t > a.anim.length) {
            ACTIVE.remove(entity.getId());
            return null;
        }
        return a;
    }

    private static float time(Entity entity, Active a) {
        float pt = MinecraftClient.getInstance().getTickDelta();
        float t = (entity.getWorld().getTime() - a.startTick + pt) / 20.0F;
        return a.anim.loop ? t % a.anim.length : t;
    }

    /** Cada tick del cliente: libera la camara y el movimiento cuando termina la animacion del jugador. */
    public static void clientTick(MinecraftClient client) {
        if (client.player == null) {
            ACTIVE.clear();
            localLocked = false;
            return;
        }
        if (localLocked && current(client.player) == null) {
            localLocked = false;
            if (savedPerspective != null) {
                client.options.setPerspective(savedPerspective);
                savedPerspective = null;
            }
        }
    }

    // ------------------------------------------------------------------ dibujo

    private static Vec3d anchorToWorld(Active a, float[] local) {
        double yaw = Math.toRadians(a.ayaw);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);   // hacia adelante
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);  // a la derecha
        double x = local[0] / 16.0, y = local[1] / 16.0, z = local[2] / 16.0;
        return new Vec3d(a.ax + fx * z + rx * x, a.ay + y, a.az + fz * z + rz * x);
    }

    /**
     * Antes de dibujar: lleva a la entidad a su lugar en la pareja (sin moverla de verdad) y la gira.
     * Devuelve true si empujo una matriz (hay que sacarla al terminar de dibujar).
     */
    /** true mientras se dibuja el retrato del aldeano en la interfaz (ahi no hay que correrlo). */
    public static boolean guiRender;

    public static void beforeRender(LivingEntity entity, float tickDelta, MatrixStack matrices) {
        if (guiRender) {
            return;
        }
        int mode = attachMode(entity);
        if (mode == net.mca.server.dreams.DreamsAttach.HAND) {
            // de la mano: se dibuja exactamente al lado del jugador (el servidor lo lleva ahi, pero con algo de
            // retraso; en pantalla nunca se separan) y mirando para el mismo lado
            net.minecraft.entity.player.PlayerEntity pl = partner((net.mca.entity.VillagerEntityMCA) entity);
            float b = MathHelper.lerpAngleDegrees(tickDelta, pl.prevBodyYaw, pl.bodyYaw);
            double rad = Math.toRadians(b);
            Vec3d p = pl.getLerpedPos(tickDelta);
            Vec3d target = p.add(-Math.cos(rad) * net.mca.server.dreams.DreamsAttach.HAND_SIDE, 0, -Math.sin(rad) * net.mca.server.dreams.DreamsAttach.HAND_SIDE);
            Vec3d actual = entity.getLerpedPos(tickDelta);
            matrices.push();
            PUSHED.put(entity.getId(), true);
            matrices.translate(target.x - actual.x, target.y - actual.y, target.z - actual.z);
            float rel = MathHelper.clamp(MathHelper.wrapDegrees(entity.headYaw - b), -60, 60);
            entity.bodyYaw = b;
            entity.prevBodyYaw = b;
            entity.headYaw = b + rel;
            entity.prevHeadYaw = b + rel;
            return;
        }
        if (mode == net.mca.server.dreams.DreamsAttach.ARMS || mode == net.mca.server.dreams.DreamsAttach.BACK) {
            // en brazos: de costado y recostado; en la espalda: apenas inclinado hacia adelante
            net.mca.entity.VillagerEntityMCA v = (net.mca.entity.VillagerEntityMCA) entity;
            float hip = 0.75F * v.getRawScaleFactor();
            float yaw = MathHelper.lerpAngleDegrees(tickDelta, entity.prevBodyYaw, entity.bodyYaw);
            float turn = 0;
            float recline = -0.28F;
            if (mode == net.mca.server.dreams.DreamsAttach.ARMS) {
                turn = 90.0F;
                yaw -= 90.0F;
                recline = 1.2F;
            }
            double r = Math.toRadians(yaw);
            float axX = (float) -Math.cos(r), axZ = (float) -Math.sin(r); // la derecha del aldeano
            matrices.push();
            PUSHED.put(entity.getId(), true);
            matrices.translate(0, hip, 0);
            matrices.multiply(new org.joml.Quaternionf().rotationAxis(recline, axX, 0, axZ));
            matrices.translate(0, -hip, 0);
            if (turn != 0) {
                matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(turn));
            }
            return;
        }
        Active a = current(entity);
        if (a != null && !a.anchored) {
            // gesto solo con "root" (saltar de alegria, paso atras): se mueve en su propio frente
            DreamsAnimation.Bone r = a.anim.bones(a.participant).get("root");
            if (r != null && r.position != null) {
                float[] p = r.position.sample(time(entity, a));
                float w = a.anim.weight(time(entity, a));
                double yaw = Math.toRadians(MathHelper.lerpAngleDegrees(tickDelta, entity.prevBodyYaw, entity.bodyYaw));
                double fx = -Math.sin(yaw), fz = Math.cos(yaw), rx = -Math.cos(yaw), rz = -Math.sin(yaw);
                double x = p[0] / 16.0 * w, y = p[1] / 16.0 * w, z = p[2] / 16.0 * w;
                matrices.push();
                PUSHED.put(entity.getId(), true);
                matrices.translate(fx * z + rx * x, y, fz * z + rz * x);
            }
            return;
        }
        if (a == null) {
            return;
        }
        DreamsAnimation.Bone root = a.anim.bones(a.participant).get("root");
        float t = time(entity, a);
        float[] pos = root != null && root.position != null ? root.position.sample(t)
                : (a.participant.equals("villager") ? new float[]{0, 0, a.anim.distance} : new float[]{0, 0, 0});
        float rotY = root != null && root.rotation != null ? root.rotation.sample(t)[1]
                : (a.participant.equals("villager") ? 180 : 0);
        float w = a.anim.weight(t);

        Vec3d target = anchorToWorld(a, pos);
        Vec3d actual = entity.getLerpedPos(tickDelta);
        matrices.push();
        PUSHED.put(entity.getId(), true);
        matrices.translate((target.x - actual.x) * w, (target.y - actual.y) * w, (target.z - actual.z) * w);

        float yaw = a.ayaw + rotY;
        float body = MathHelper.lerpAngleDegrees(w, entity.bodyYaw, yaw);
        entity.bodyYaw = body;
        entity.prevBodyYaw = body;
        entity.headYaw = body;
        entity.prevHeadYaw = body;
    }

    public static void afterRender(LivingEntity entity, MatrixStack matrices) {
        if (PUSHED.remove(entity.getId()) != null) {
            matrices.pop();
        }
    }

    /** Pone los huesos de la animacion encima de la pose actual. */
    public static void apply(LivingEntity entity, BipedEntityModel<?> model) {
        Active a = current(entity);
        boolean changed = false;
        if (a == null || !a.anchored) {
            changed = applyAttach(entity, model);
        }
        if (a == null) {
            if (changed) {
                syncWears(model);
            }
            return;
        }
        float t = time(entity, a);
        float w = a.anim.weight(t);
        for (Map.Entry<String, DreamsAnimation.Bone> e : a.anim.bones(a.participant).entrySet()) {
            ModelPart part = part(model, e.getKey());
            if (part == null) {
                continue;
            }
            DreamsAnimation.Bone bone = e.getValue();
            if (bone.rotation != null) {
                float[] r = bone.rotation.sample(t);
                part.pitch = MathHelper.lerp(w, part.pitch, r[0] * MathHelper.RADIANS_PER_DEGREE);
                part.yaw = MathHelper.lerp(w, part.yaw, r[1] * MathHelper.RADIANS_PER_DEGREE);
                part.roll = MathHelper.lerp(w, part.roll, r[2] * MathHelper.RADIANS_PER_DEGREE);
            }
            if (bone.position != null) {
                float[] p = bone.position.sample(t);
                // x = derecha, y = arriba, z = hacia adelante (el frente del modelo es -Z)
                part.pivotX -= p[0] * w;
                part.pivotY -= p[1] * w;
                part.pivotZ -= p[2] * w;
            }
        }
        syncWears(model);
    }

    private static void syncWears(BipedEntityModel<?> model) {
        if (model instanceof PlayerEntityModel<?> p) {
            p.hat.copyTransform(p.head);
            p.jacket.copyTransform(p.body);
            p.leftSleeve.copyTransform(p.leftArm);
            p.rightSleeve.copyTransform(p.rightArm);
            p.leftPants.copyTransform(p.leftLeg);
            p.rightPants.copyTransform(p.rightLeg);
        } else {
            model.hat.copyTransform(model.head);
        }
    }

    // ------------------------------------------------------------------ de la mano / en brazos / en la espalda

    private static void set(ModelPart p, float pitch, float yaw, float roll) {
        p.pitch = pitch;
        p.yaw = yaw;
        p.roll = roll;
    }

    /** Pone las piernas y el cuerpo en su lugar (EMF / FA los sientan porque van "montados"). */
    private static void resetSeat(BipedEntityModel<?> m) {
        m.body.pivotY = 0;
        m.body.pivotZ = 0;
        set(m.body, 0, 0, 0);
        m.head.pivotY = 0;
        m.head.pivotZ = 0;
        m.rightLeg.pivotY = 12;
        m.rightLeg.pivotZ = 0;
        m.leftLeg.pivotY = 12;
        m.leftLeg.pivotZ = 0;
        m.rightArm.pivotY = 2;
        m.rightArm.pivotZ = 0;
        m.leftArm.pivotY = 2;
        m.leftArm.pivotZ = 0;
    }

    private static boolean applyAttach(LivingEntity entity, BipedEntityModel<?> m) {
        int mode = attachMode(entity);
        float pt = MinecraftClient.getInstance().getTickDelta();
        if (mode == net.mca.server.dreams.DreamsAttach.HAND) {
            // camina con su animacion normal (la de EMF / Fresh Animations); solo cambia la mano que va agarrada
            m.leftArm.pitch *= 0.3F;
            m.leftArm.yaw = 0;
            m.leftArm.roll = -0.15F;
        } else if (mode == net.mca.server.dreams.DreamsAttach.ARMS) {
            resetSeat(m);
            // piernas colgando sobre un brazo, un brazo alrededor del cuello, mirando al jugador
            set(m.rightLeg, -0.44F, 0, 0.04F);
            set(m.leftLeg, -0.5F, 0, -0.04F);
            set(m.leftArm, -0.8F, 0, -0.6F);
            set(m.rightArm, -0.55F, 0.2F, -0.35F);
            set(m.head, -0.25F, -0.4F, 0);
        } else if (mode == net.mca.server.dreams.DreamsAttach.BACK) {
            resetSeat(m);
            // piernas abrazando la cintura, brazos por encima de los hombros
            set(m.rightLeg, -1.25F, 0, 0.35F);
            set(m.leftLeg, -1.25F, 0, -0.35F);
            set(m.rightArm, -1.15F, 0, -0.3F);
            set(m.leftArm, -1.15F, 0, 0.3F);
            set(m.head, 0.15F, -0.2F, 0.12F);
        } else {
            int carried = carriedMode(entity);
            if (carried == net.mca.server.dreams.DreamsAttach.HAND) {
                // solo la mano derecha, que agarra la del aldeano
                m.rightArm.pitch *= 0.3F;
                m.rightArm.yaw = 0;
                m.rightArm.roll = 0.15F;
            } else if (carried == net.mca.server.dreams.DreamsAttach.ARMS) {
                set(m.rightArm, -1.05F, -0.15F, 0.05F);
                set(m.leftArm, -1.05F, 0.15F, -0.05F);
            } else if (carried == net.mca.server.dreams.DreamsAttach.BACK) {
                set(m.rightArm, 0.35F, 0, 0.3F);
                set(m.leftArm, 0.35F, 0, -0.3F);
            } else {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ fundido a negro (procrear)

    /** Lo llama el HUD: pantalla negra con corazones mientras la pareja "se va a dormir". */
    public static void renderOverlay(net.minecraft.client.gui.DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }
        Active a = ACTIVE.get(client.player.getId());
        if (a == null || !"procreate".equals(a.name)) {
            return;
        }
        float t = time(client.player, a);
        float alpha;
        if (t < 3.6F) {
            alpha = 0;
        } else if (t < 4.5F) {
            alpha = (t - 3.6F) / 0.9F;
        } else if (t < 6.3F) {
            alpha = 1;
        } else {
            alpha = Math.max(0, 1 - (t - 6.3F) / 0.7F);
        }
        if (alpha <= 0.01F) {
            return;
        }
        int w = context.getScaledWindowWidth();
        int h = context.getScaledWindowHeight();
        int al = (int) (alpha * 255) << 24;
        context.fill(0, 0, w, h, al | 0x050208);
        if (alpha > 0.5F) {
            int ha = (int) ((alpha - 0.5F) * 2 * 255);
            String hearts = "\u2764  \u2764  \u2764";
            float beat = 1 + 0.08F * MathHelper.sin(t * 7);
            context.getMatrices().push();
            context.getMatrices().translate(w / 2.0F, h / 2.0F, 0);
            context.getMatrices().scale(2 * beat, 2 * beat, 1);
            int tw = client.textRenderer.getWidth(hearts);
            context.drawText(client.textRenderer, hearts, -tw / 2, -4, (ha << 24) | 0xFF6F9A, false);
            context.getMatrices().pop();
        }
    }

    // ------------------------------------------------------------------ vida: mirada y postura

    private static final Map<Entity, double[]> LOOK = new java.util.WeakHashMap<>();
    private static final Map<ModelPart, float[]> WRITTEN = new java.util.WeakHashMap<>();

    /** Suma a un hueso, pero una sola vez por pose (si nadie lo volvio a calcular desde la ultima vez, no repite). */
    private static void add(ModelPart part, float pitch, float yaw, float roll) {
        float[] w = WRITTEN.get(part);
        if (w != null && part.pitch == w[0] && part.yaw == w[1] && part.roll == w[2]) {
            return;
        }
        part.pitch += pitch;
        part.yaw += yaw;
        part.roll += roll;
        if (w == null) {
            w = new float[3];
            WRITTEN.put(part, w);
        }
        w[0] = part.pitch;
        w[1] = part.yaw;
        w[2] = part.roll;
    }

    /**
     * Lo que hace que un aldeano parezca una persona: la postura cambia con el animo y la personalidad (triste:
     * cabeza gacha y hombros caidos; seguro: pecho afuera; timido: mira al piso...) y, cuando un jugador esta
     * cerca, lo mira a los ojos (un rato si, un rato no; siempre si le estan hablando o lo estan mirando).
     * Va encima de la pose normal y de EMF, igual que las interacciones.
     */
    public static void applyLife(net.mca.entity.VillagerEntityMCA v, BipedEntityModel<?> m) {
        if (guiRender || v.isSleeping() || v.getVillagerBrain().isPanicking() || attachMode(v) > net.mca.server.dreams.DreamsAttach.HAND) {
            return;
        }
        Active a = current(v);
        if (a != null && a.anchored) {
            return;
        }
        // ---- postura
        float head = 0, body = 0, arms = 0;
        switch (v.getVillagerBrain().getMood().getName()) {
            case "depressed" -> {
                head += 0.28F;
                body += 0.10F;
            }
            case "sad" -> {
                head += 0.18F;
                body += 0.06F;
            }
            case "unhappy" -> head += 0.06F;
            case "happy" -> head -= 0.05F;
            case "overjoyed" -> {
                head -= 0.08F;
                body -= 0.03F;
            }
            default -> {
            }
        }
        switch (v.getVillagerBrain().getPersonality()) {
            case SHY -> head += 0.08F;
            case GLOOMY -> head += 0.07F;
            case GRUMPY -> head += 0.04F;
            case LAZY -> body += 0.07F;
            case CONFIDENT, ATHLETIC -> {
                body -= 0.05F;
                head -= 0.04F;
                arms += 0.07F;
            }
            default -> {
            }
        }
        // respira: los hombros suben y bajan apenas
        float breath = MathHelper.sin((v.age + MinecraftClient.getInstance().getTickDelta()) * 0.09F + v.getId()) * 0.012F;
        if (a == null) {
            add(m.body, body + breath, 0, 0);
            add(m.rightArm, 0, 0, arms + breath * 2);
            add(m.leftArm, 0, 0, -arms - breath * 2);
        }

        // ---- mirada
        MinecraftClient client = MinecraftClient.getInstance();
        double[] st = LOOK.computeIfAbsent(v, k -> new double[]{0, 0, 0, 0});   // yaw, pitch, peso, ultimo ms
        long now = net.minecraft.util.Util.getMeasuringTimeMs();
        float dt = (float) Math.min(0.1, Math.max(0, (now - st[3]) / 1000.0));
        if (st[3] == 0) {
            dt = 0;
        }
        st[3] = now;
        float want = 0, wy = 0, wp = 0;
        net.minecraft.entity.player.PlayerEntity pl = v.getWorld().getClosestPlayer(v, 6.5);
        if (pl != null && !pl.isSpectator()) {
            Vec3d eye = v.getEyePos();
            Vec3d to = pl.getEyePos().subtract(eye);
            float bodyYaw = v.bodyYaw;
            float yawTo = (float) (MathHelper.atan2(to.z, to.x) * 180 / Math.PI) - 90F;
            float rel = MathHelper.wrapDegrees(yawTo - bodyYaw);
            boolean talking = net.mca.client.gui.InteractScreen.isTalkingTo(v.getUuid());
            boolean stared = client.targetedEntity == v;
            // se interesa de a ratos (cada uno a su ritmo); el timido aguanta menos la mirada
            long cycle = 5200 + (v.getId() % 7) * 450L;
            float share = v.getVillagerBrain().getPersonality() == net.mca.entity.ai.relationship.Personality.SHY ? 0.3F : 0.6F;
            boolean interested = ((now + v.getId() * 977L) % cycle) < cycle * share;
            if (Math.abs(rel) < 95 && (talking || stared || interested)) {
                want = 1;
                wy = MathHelper.clamp(rel, -62, 62) * MathHelper.RADIANS_PER_DEGREE;
                double dist = Math.sqrt(to.x * to.x + to.z * to.z);
                wp = MathHelper.clamp((float) -(MathHelper.atan2(to.y, dist)), -0.5F, 0.5F);
            }
        }
        float k = dt <= 0 ? 0 : 1 - (float) Math.exp(-dt / 0.18F);
        st[2] += (want - st[2]) * k;
        if (want > 0) {
            st[0] += (wy - st[0]) * k;
            st[1] += (wp - st[1]) * k;
        }
        float w = (float) st[2] * (a != null ? 0.5F : 1);
        float[] wr = WRITTEN.get(m.head);
        if (wr == null || m.head.pitch != wr[0] || m.head.yaw != wr[1] || m.head.roll != wr[2]) {
            m.head.pitch = MathHelper.lerp(w, m.head.pitch + head, (float) st[1] + head * 0.5F);
            m.head.yaw = MathHelper.lerp(w, m.head.yaw, (float) st[0]);
            if (wr == null) {
                wr = new float[3];
                WRITTEN.put(m.head, wr);
            }
            wr[0] = m.head.pitch;
            wr[1] = m.head.yaw;
            wr[2] = m.head.roll;
        }
    }

    private static ModelPart part(BipedEntityModel<?> m, String bone) {
        return switch (bone) {
            case "head" -> m.head;
            case "body" -> m.body;
            case "right_arm" -> m.rightArm;
            case "left_arm" -> m.leftArm;
            case "right_leg" -> m.rightLeg;
            case "left_leg" -> m.leftLeg;
            default -> null; // "root" se usa en beforeRender
        };
    }

    /**
     * Starcompat lo llama (por reflexion) cada vez que EMF termina de animar el modelo de la entidad que se esta
     * dibujando. Para los aldeanos de MCA tambien vuelve a poner sus poses propias (panico, bebes, pechos).
     */
    public static void afterEmfAnimation(LivingEntity entity, Object model) {
        if (model instanceof VillagerEntityBaseModelMCA<?> mca) {
            mca.dreamsAfterEmfAnimation();
        } else if (model instanceof BipedEntityModel<?> biped) {
            apply(entity, biped);
        }
    }
}
