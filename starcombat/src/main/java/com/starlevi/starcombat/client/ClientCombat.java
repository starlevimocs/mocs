package com.starlevi.starcombat.client;

import com.starlevi.starcombat.Moves;
import com.starlevi.starcombat.Net;
import com.starlevi.starcombat.Sounds;
import com.starlevi.starcombat.StarCombat;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Estado del combate en el cliente: que movimiento hace cada jugador (para dibujarlo), y para el jugador local,
 * los clics, los combos y el gancho. El jugador local arranca al instante y le avisa al servidor.
 */
public final class ClientCombat {
    /** Una muestra de la estela: punta y base de la hoja en un instante. */
    static final class Sample {
        final Vec3 tip, base;
        final long ms;

        Sample(Vec3 tip, Vec3 base, long ms) {
            this.tip = tip;
            this.base = base;
            this.ms = ms;
        }
    }

    static final class St {
        Moves.Move move;
        long startMs;
        int lastTick = -1;
        boolean grapple;
        long gStartMs;
        /** 0 = el gancho sigue; si no, cuando empezo a volver */
        long gEndMs;
        Vec3 anchor;
        int target = -1;
        float flyTicks = 2;
        boolean arrived;
        @SuppressWarnings("unchecked")
        final ArrayDeque<Sample>[] trails = new ArrayDeque[]{new ArrayDeque<Sample>(), new ArrayDeque<Sample>()};

        float ticks() {
            return (Util.getMillis() - startMs) / 50F;
        }

        /** avance del movimiento 0..1, o -1 si no hay */
        float u() {
            if (move == null) {
                return -1;
            }
            float u = ticks() / move.duration;
            return u >= 1 ? -1 : Math.max(0, u);
        }

        boolean retracting() {
            return gEndMs != 0 && Util.getMillis() - gEndMs < RETRACT_MS;
        }
    }

    static final long RETRACT_MS = 160;
    static final Map<Integer, St> STATES = new HashMap<>();
    private static final Random RANDOM = new Random();

    // jugador local
    private static int lights, heavies;
    private static long comboUntilMs;
    private static int buffered = -1;
    private static long bufferedMs;
    private static long grappleReadyMs;
    private static boolean pulling;
    static boolean canGrapple;
    static float shake;
    static float grappleRange = 32;
    static int grappleCooldown = 24;

    private ClientCombat() {
    }

    static void clear() {
        STATES.clear();
        lights = heavies = 0;
        buffered = -1;
        pulling = false;
        canGrapple = false;
        shake = 0;
    }

    private static St of(int id) {
        return STATES.computeIfAbsent(id, k -> new St());
    }

    // ------------------------------------------------------------ mensajes del servidor

    public static void onMove(int entityId, int idx) {
        Moves.Move move = Moves.get(idx);
        if (move == null) {
            return;
        }
        St s = of(entityId);
        s.move = move;
        s.startMs = Util.getMillis();
        s.lastTick = -1;
    }

    public static void onGrapple(int entityId, boolean start, double x, double y, double z, int target) {
        Minecraft mc = Minecraft.getInstance();
        St s = of(entityId);
        if (start) {
            Entity e = mc.level == null ? null : mc.level.getEntity(entityId);
            Vec3 anchor = new Vec3(x, y, z);
            beginGrapple(s, anchor, target, e == null ? 10 : e.getEyePosition().distanceTo(anchor));
        } else if (s.grapple) {
            s.grapple = false;
            s.gEndMs = Util.getMillis();
            if (mc.player != null && mc.player.getId() == entityId) {
                pulling = false;
            }
        }
    }

    private static void beginGrapple(St s, Vec3 anchor, int target, double dist) {
        s.grapple = true;
        s.gStartMs = Util.getMillis();
        s.gEndMs = 0;
        s.anchor = anchor;
        s.target = target;
        s.flyTicks = Math.max(2, (int) Math.ceil(dist / 3.2));
        s.arrived = false;
    }

    public static void onHit(int count, boolean heavy) {
        shake = Math.min(1.6F, shake + (heavy ? 0.9F : 0.5F) + count * 0.08F);
    }

    public static void onConfig(float range, int cooldown) {
        grappleRange = range;
        grappleCooldown = cooldown;
    }

    // ------------------------------------------------------------ geometria

    /** +1 = brazo derecho, -1 = izquierdo. */
    static int armSide(Player p, InteractionHand hand) {
        boolean right = (p.getMainArm() == HumanoidArm.RIGHT) == (hand == InteractionHand.MAIN_HAND);
        return right ? 1 : -1;
    }

    static int mainSide(Entity e) {
        return e instanceof Player p ? armSide(p, InteractionHand.MAIN_HAND) : 1;
    }

    static boolean firstPerson(Entity e) {
        Minecraft mc = Minecraft.getInstance();
        return mc.options.getCameraType().isFirstPerson() && e == mc.getCameraEntity();
    }

    static Vec3 basePos(Entity e, float partial) {
        return new Vec3(Mth.lerp(partial, e.xo, e.getX()), Mth.lerp(partial, e.yo, e.getY()), Mth.lerp(partial, e.zo, e.getZ()));
    }

    /** Giro extra del cuerpo por el movimiento (grados, positivo = a la derecha). */
    static float spin(St s) {
        float u = s == null ? -1 : s.u();
        return u < 0 || s.move.bodySpin == 0 ? 0 : s.move.bodySpin * Moves.smooth(u);
    }

    static float bodyYaw(Entity e, St s, float partial) {
        float yaw = e instanceof LivingEntity le ? Mth.rotLerp(partial, le.yBodyRotO, le.yBodyRot) : e.getViewYRot(partial);
        return yaw + spin(s);
    }

    static Vec3 shoulder(Entity e, St s, int side, float partial) {
        Vec3 r = Moves.right(bodyYaw(e, s, partial));
        double h = e.isCrouching() ? 1.08 : 1.38;
        return basePos(e, partial).add(r.x * side * 0.36, h, r.z * side * 0.36);
    }

    /** Donde esta la mano que sostiene la cadena, apuntando hacia {@code toward}. */
    static Vec3 hand(Entity e, St s, int side, float partial, Vec3 toward) {
        if (firstPerson(e)) {
            Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
            Vector3f look = cam.getLookVector(), up = cam.getUpVector(), left = cam.getLeftVector();
            Vec3 c = cam.getPosition();
            double f = 0.42, l = -side * 0.42, d = -0.36;
            return new Vec3(c.x + look.x() * f + left.x() * l + up.x() * d,
                    c.y + look.y() * f + left.y() * l + up.y() * d,
                    c.z + look.z() * f + left.z() * l + up.z() * d);
        }
        Vec3 sh = shoulder(e, s, side, partial);
        Vec3 d = toward.subtract(sh);
        return d.lengthSqr() < 1.0E-6 ? sh : sh.add(d.normalize().scale(0.62));
    }

    private static Vec3 anchorNow(St s, float partial) {
        if (s.target >= 0) {
            ClientLevel level = Minecraft.getInstance().level;
            Entity t = level == null ? null : level.getEntity(s.target);
            if (t != null) {
                return basePos(t, partial).add(0, t.getBbHeight() * 0.6, 0);
            }
        }
        return s.anchor;
    }

    /** Donde esta la hoja de ese lado si esta en el aire; {@code null} si esta en la mano. */
    static Vec3 blade(Entity e, St s, int side, float partial) {
        if (s == null) {
            return null;
        }
        if ((s.grapple || s.retracting()) && s.anchor != null && side == mainSide(e)) {
            Vec3 a = anchorNow(s, partial);
            Vec3 h = hand(e, s, side, partial, a);
            if (s.grapple) {
                float f = Mth.clamp((Util.getMillis() - s.gStartMs) / 50F / s.flyTicks, 0, 1);
                return h.lerp(a, f);
            }
            float r = Mth.clamp((Util.getMillis() - s.gEndMs) / (float) RETRACT_MS, 0, 1);
            return a.lerp(h, r * r);
        }
        float u = s.u();
        if (u < 0) {
            return null;
        }
        Vec3 local = Moves.local(side > 0 ? s.move.right : s.move.left, u);
        if (local == null) {
            return null;
        }
        Vec3 base = basePos(e, partial);
        if (e.isCrouching()) {
            local = new Vec3(local.x, Math.max(0.12, local.y - 0.25), local.z);
        }
        return Moves.world(base, e.getViewYRot(partial), local);
    }

    static boolean flying(Entity e, int side) {
        St s = STATES.get(e.getId());
        if (s == null) {
            return false;
        }
        if ((s.grapple || s.retracting()) && side == mainSide(e)) {
            return true;
        }
        float u = s.u();
        return u >= 0 && Moves.local(side > 0 ? s.move.right : s.move.left, u) != null;
    }

    // ------------------------------------------------------------ entradas del jugador local

    /** 0 = clic liviano, 1 = pesado. Se guarda un momento para encadenar. */
    static void input(int kind) {
        buffered = kind;
        bufferedMs = Util.getMillis();
    }

    /** Clic derecho sobre algo que se usa (cofre, puerta, aldeano): se deja pasar. */
    static boolean useIsInteraction(Minecraft mc) {
        HitResult hit = mc.hitResult;
        if (hit == null || mc.level == null) {
            return false;
        }
        if (hit.getType() == HitResult.Type.ENTITY) {
            return true;
        }
        if (hit.getType() == HitResult.Type.BLOCK && mc.player != null && !mc.player.isShiftKeyDown()) {
            BlockPos pos = ((BlockHitResult) hit).getBlockPos();
            BlockState state = mc.level.getBlockState(pos);
            return state.hasBlockEntity() || state.getMenuProvider(mc.level, pos) != null
                    || state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock
                    || state.getBlock() instanceof FenceGateBlock || state.getBlock() instanceof ButtonBlock
                    || state.getBlock() instanceof LeverBlock;
        }
        return false;
    }

    private static boolean airborne(LocalPlayer p) {
        return !p.onGround() && !p.isInWater() && !p.onClimbable() && !p.getAbilities().flying && !p.isPassenger()
                && !p.isFallFlying();
    }

    private static void startLocal(LocalPlayer p, int kind) {
        long now = Util.getMillis();
        if (now > comboUntilMs) {
            lights = 0;
            heavies = 0;
        }
        int idx;
        if (kind == 0) {
            idx = Moves.L1 + lights;
            lights = (lights + 1) % 4;
        } else if (airborne(p)) {
            idx = Moves.PLUNGE;
            lights = 0;
        } else if (lights >= 2) {
            idx = Moves.CYCLONE;
            lights = 0;
        } else {
            idx = (heavies & 1) == 0 ? Moves.H1 : Moves.H2;
            heavies++;
            lights = 0;
        }
        Moves.Move move = Moves.get(idx);
        St s = of(p.getId());
        s.move = move;
        s.startMs = now;
        s.lastTick = -1;
        comboUntilMs = now + move.duration * 50L + 700;
        Net.toServer(new Net.Act(idx));
        Vec3 f = Moves.forward(p.getYRot());
        if (move.prep) {
            p.setDeltaMovement(f.x * 0.35, -1.0, f.z * 0.35);
        } else if (move.lunge > 0 && p.onGround()) {
            p.setDeltaMovement(p.getDeltaMovement().add(f.x * move.lunge, 0, f.z * move.lunge));
        }
        PunchyCompat.attack(move.left == null || (move.right != null && (idx & 1) == 0), move.heavy);
    }

    private static void tryGrapple(Minecraft mc, LocalPlayer p) {
        St s = of(p.getId());
        long now = Util.getMillis();
        if (s.grapple) {
            endLocalGrapple(p, s, true);
            return;
        }
        if (now < grappleReadyMs) {
            return;
        }
        Object[] found = findGrapple(mc, p);
        if (found == null) {
            p.level().playLocalSound(p.getX(), p.getY(), p.getZ(), Sounds.CHAIN.get(), SoundSource.PLAYERS, 0.5F, 1.4F, false);
            return;
        }
        Vec3 anchor = (Vec3) found[0];
        int target = (Integer) found[1];
        beginGrapple(s, anchor, target, p.getEyePosition().distanceTo(anchor));
        pulling = target < 0;
        grappleReadyMs = now + grappleCooldown * 50L;
        Net.toServer(new Net.GrappleReq(anchor.x, anchor.y, anchor.z, target));
        p.level().playLocalSound(p.getX(), p.getY(), p.getZ(), Sounds.GRAPPLE.get(), SoundSource.PLAYERS, 1.0F, 1.0F, false);
        PunchyCompat.attack(true, true);
    }

    /** Busca a que engancharse: {punto, id de criatura o -1}. */
    private static Object[] findGrapple(Minecraft mc, LocalPlayer p) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getViewVector(1);
        Vec3 end = eye.add(look.scale(grappleRange));
        BlockHitResult block = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        double blockDist = block.getType() == HitResult.Type.BLOCK ? block.getLocation().distanceTo(eye) : grappleRange;
        Vec3 stop = eye.add(look.scale(blockDist));
        AABB box = p.getBoundingBox().expandTowards(look.scale(blockDist)).inflate(1.0);
        EntityHitResult ent = ProjectileUtil.getEntityHitResult(p, eye, stop, box,
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e.isPickable(), blockDist * blockDist);
        if (ent != null && ent.getEntity().distanceTo(p) > 3.0) {
            Entity e = ent.getEntity();
            return new Object[]{e.position().add(0, e.getBbHeight() * 0.6, 0), e.getId()};
        }
        if (block.getType() == HitResult.Type.BLOCK && blockDist > 2.5) {
            return new Object[]{block.getLocation(), -1};
        }
        return null;
    }

    private static void endLocalGrapple(LocalPlayer p, St s, boolean hop) {
        if (!s.grapple) {
            return;
        }
        s.grapple = false;
        s.gEndMs = Util.getMillis();
        Net.toServer(new Net.GrappleEnd());
        if (pulling && hop && s.arrived) {
            Vec3 v = p.getDeltaMovement();
            p.setDeltaMovement(v.x * 0.45, Math.max(0.62, v.y * 0.5 + 0.35), v.z * 0.45);
        }
        pulling = false;
    }

    private static void pull(LocalPlayer p, St s) {
        float t = (Util.getMillis() - s.gStartMs) / 50F;
        if (t < s.flyTicks) {
            return;
        }
        Vec3 from = p.position().add(0, p.getBbHeight() * 0.55, 0);
        Vec3 to = s.anchor.subtract(from);
        double dist = to.length();
        float since = t - s.flyTicks;
        if (dist < 1.7 || since > 70 || p.input.jumping || (since > 6 && p.horizontalCollision && p.getDeltaMovement().lengthSqr() < 0.01 && dist < 3)) {
            endLocalGrapple(p, s, true);
            return;
        }
        if (p.isShiftKeyDown() && since > 3) {
            endLocalGrapple(p, s, false);
            return;
        }
        double speed = Math.min(1.35, 0.5 + since * 0.09);
        Vec3 v = to.normalize().scale(Math.min(speed, dist));
        p.setDeltaMovement(v.x, v.y + 0.04, v.z);
        p.resetFallDistance();
    }

    // ------------------------------------------------------------ tick

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer p = mc.player;
        if (level == null || p == null) {
            if (!STATES.isEmpty()) {
                clear();
            }
            return;
        }
        long now = Util.getMillis();
        shake *= 0.72F;
        if (shake < 0.01F) {
            shake = 0;
        }

        // --- jugador local
        boolean holds = StarCombat.holds(p) && !p.isSpectator();
        St mine = STATES.get(p.getId());
        if (holds) {
            if (mc.screen == null && mc.options.keyAttack.isDown() && buffered < 0) {
                boolean idle = mine == null || mine.u() < 0;
                if (idle || mine.ticks() >= mine.move.chainAt) {
                    input(0);
                }
            }
            if (buffered >= 0 && now - bufferedMs > 450) {
                buffered = -1;
            }
            if (buffered >= 0 && (mine == null || !mine.grapple)) {
                boolean free = mine == null || mine.u() < 0 || (!mine.move.prep && mine.ticks() >= mine.move.chainAt);
                if (free) {
                    int kind = buffered;
                    buffered = -1;
                    startLocal(p, kind);
                    mine = STATES.get(p.getId());
                }
            }
            while (ClientEvents.KEY_GRAPPLE.consumeClick()) {
                if (mc.screen == null) {
                    tryGrapple(mc, p);
                    mine = STATES.get(p.getId());
                }
            }
            if (mine != null && mine.grapple && pulling) {
                pull(p, mine);
            }
            if ((mine != null && mine.grapple) || now < grappleReadyMs) {
                canGrapple = false;
            } else if ((level.getGameTime() & 1) == 0) {
                canGrapple = findGrapple(mc, p) != null;
            }
        } else {
            buffered = -1;
            canGrapple = false;
            if (mine != null) {
                if (mine.grapple) {
                    endLocalGrapple(p, mine, false);
                }
                mine.move = null;
            }
        }

        // --- todos: sonidos, particulas y limpieza
        for (Integer id : new ArrayList<>(STATES.keySet())) {
            St s = STATES.get(id);
            Entity e = level.getEntity(id);
            if (e == null || !e.isAlive()) {
                STATES.remove(id);
                continue;
            }
            float u = s.u();
            if (u < 0) {
                s.move = null;
            } else {
                int tick = (int) s.ticks();
                for (int k = s.lastTick + 1; k <= tick; k++) {
                    moveTick(level, e, s, k);
                }
                s.lastTick = tick;
            }
            if (s.grapple && !s.arrived && (now - s.gStartMs) / 50F >= s.flyTicks) {
                s.arrived = true;
                Vec3 a = anchorNow(s, 1);
                level.playLocalSound(a.x, a.y, a.z, Sounds.LATCH.get(), SoundSource.PLAYERS, 1.0F, 1.0F, false);
                burst(level, a, 10, 0.12);
            }
            for (int side = -1; side <= 1; side += 2) {
                Vec3 b = blade(e, s, side, 1);
                if (b != null) {
                    embers(level, b);
                }
            }
            for (ArrayDeque<Sample> trail : s.trails) {
                while (!trail.isEmpty() && now - trail.peekFirst().ms > 400) {
                    trail.pollFirst();
                }
            }
            if (s.move == null && !s.grapple && !s.retracting()) {
                if (s.trails[0].isEmpty() && s.trails[1].isEmpty()) {
                    STATES.remove(id);
                }
            }
        }
    }

    private static void sound(ClientLevel level, Entity e, SoundEvent sound, float volume, float pitch) {
        level.playLocalSound(e.getX(), e.getY() + 1, e.getZ(), sound, SoundSource.PLAYERS, volume, pitch + (RANDOM.nextFloat() - 0.5F) * 0.12F, false);
    }

    private static void moveTick(ClientLevel level, Entity e, St s, int k) {
        Moves.Move m = s.move;
        if (k == 0) {
            if (m.prep) {
                sound(level, e, Sounds.IGNITE.get(), 0.9F, 1.0F);
            } else {
                sound(level, e, m.heavy ? Sounds.SWING_HEAVY.get() : Sounds.SWING.get(), 0.9F, m.heavy ? 1.0F : 0.95F + m.idx * 0.05F);
                sound(level, e, Sounds.CHAIN.get(), 0.6F, m.heavy ? 0.8F : 1.0F);
            }
        }
        for (Moves.Hit h : m.hits) {
            if (h.tick != k) {
                continue;
            }
            Vec3 f = Moves.forward(e.getYRot());
            if (h.fx == 1) {
                Vec3 at = e.position().add(f.scale(h.range - 0.9)).add(0, 0.2, 0);
                burst(level, at, 26, 0.22);
                level.addParticle(ParticleTypes.EXPLOSION, at.x, at.y + 0.3, at.z, 0, 0, 0);
                level.playLocalSound(at.x, at.y, at.z, Sounds.SLAM.get(), SoundSource.PLAYERS, 0.8F, 1.3F, false);
            } else if (h.fx == 2) {
                Vec3 at = e.position().add(f.scale(h.fwd)).add(0, 0.15, 0);
                ring(level, at, h.range);
                level.addParticle(ParticleTypes.EXPLOSION, at.x, at.y + 0.4, at.z, 0, 0, 0);
                level.playLocalSound(at.x, at.y, at.z, Sounds.SLAM.get(), SoundSource.PLAYERS, 1.0F, 1.0F, false);
                if (e == Minecraft.getInstance().player) {
                    shake = Math.min(1.6F, shake + 0.7F);
                }
            } else if (k > 0) {
                sound(level, e, Sounds.SWING.get(), 0.5F, 1.25F);
            }
        }
    }

    private static void embers(ClientLevel level, Vec3 at) {
        for (int i = 0; i < 2; i++) {
            level.addParticle(RANDOM.nextInt(3) == 0 ? ParticleTypes.SMALL_FLAME : ParticleTypes.FLAME,
                    at.x + (RANDOM.nextDouble() - 0.5) * 0.5, at.y + (RANDOM.nextDouble() - 0.5) * 0.5, at.z + (RANDOM.nextDouble() - 0.5) * 0.5,
                    (RANDOM.nextDouble() - 0.5) * 0.03, 0.02 + RANDOM.nextDouble() * 0.03, (RANDOM.nextDouble() - 0.5) * 0.03);
        }
        if (RANDOM.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.LAVA, at.x, at.y, at.z, 0, 0, 0);
        }
    }

    private static void burst(ClientLevel level, Vec3 at, int count, double speed) {
        for (int i = 0; i < count; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2, up = RANDOM.nextDouble();
            level.addParticle(ParticleTypes.FLAME, at.x, at.y + 0.1, at.z,
                    Math.cos(a) * speed * (1 - up * 0.6), up * speed * 1.2, Math.sin(a) * speed * (1 - up * 0.6));
        }
        for (int i = 0; i < count / 4; i++) {
            level.addParticle(ParticleTypes.LAVA, at.x, at.y + 0.2, at.z, 0, 0, 0);
        }
    }

    private static void ring(ClientLevel level, Vec3 at, float radius) {
        int n = 44;
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            double c = Math.cos(a), sn = Math.sin(a);
            double sp = radius / 16.0;
            level.addParticle(ParticleTypes.FLAME, at.x + c * 0.6, at.y + 0.1, at.z + sn * 0.6, c * sp, 0.02, sn * sp);
            if ((i & 1) == 0) {
                level.addParticle(ParticleTypes.FLAME, at.x + c * 0.4, at.y + 0.1, at.z + sn * 0.4, c * sp * 0.55, 0.08, sn * sp * 0.55);
            }
            if (i % 4 == 0) {
                level.addParticle(ParticleTypes.LAVA, at.x + c * radius * 0.5, at.y + 0.2, at.z + sn * radius * 0.5, 0, 0, 0);
                level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x + c * radius * 0.6, at.y + 0.2, at.z + sn * radius * 0.6, c * 0.02, 0.06, sn * 0.02);
            }
        }
    }
}
