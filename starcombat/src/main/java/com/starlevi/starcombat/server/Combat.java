package com.starlevi.starcombat.server;

import com.mojang.brigadier.CommandDispatcher;
import com.starlevi.starcombat.Config;
import com.starlevi.starcombat.Moves;
import com.starlevi.starcombat.Net;
import com.starlevi.starcombat.Sounds;
import com.starlevi.starcombat.StarCombat;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** El servidor decide que pega: lleva el movimiento de cada jugador, aplica los golpes y valida el gancho. */
@Mod.EventBusSubscriber(modid = StarCombat.MOD_ID)
public final class Combat {
    private static final class St {
        Moves.Move move;
        long start;
        int hitIdx;
        boolean grapple;
        long gStart;
        Vec3 anchor;
        int target = -1;
        int fly;
        boolean yanked;
        long noFall;
        long gReady;
    }

    private static final Map<UUID, St> STATES = new HashMap<>();

    private Combat() {
    }

    private static St of(ServerPlayer p) {
        return STATES.computeIfAbsent(p.getUUID(), k -> new St());
    }

    // ------------------------------------------------------------ movimientos

    public static void act(ServerPlayer p, int idx) {
        Moves.Move move = Moves.get(idx);
        if (move == null || idx == Moves.PLUNGE_LAND || !StarCombat.holds(p) || p.isSpectator() || !p.isAlive()) {
            return;
        }
        St st = of(p);
        long now = p.level().getGameTime();
        if (st.move != null) {
            long el = now - st.start;
            if (st.move.prep || el < Math.min(st.move.chainAt, st.move.duration) - 3) {
                return;
            }
        }
        if (move.prep && p.onGround()) {
            return;
        }
        begin(p, st, move, false);
    }

    private static void begin(ServerPlayer p, St st, Moves.Move move, boolean self) {
        st.move = move;
        st.start = p.level().getGameTime();
        st.hitIdx = 0;
        Net.MoveMsg msg = new Net.MoveMsg(p.getId(), move.idx);
        if (self) {
            Net.toWatchersAndSelf(p, msg);
        } else {
            Net.toWatchers(p, msg);
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) {
            return;
        }
        St st = STATES.get(p.getUUID());
        if (st == null) {
            return;
        }
        long now = p.level().getGameTime();
        if (st.move != null) {
            if (!StarCombat.holds(p) || !p.isAlive()) {
                st.move = null;
            } else {
                long el = now - st.start;
                if (st.move.prep) {
                    p.resetFallDistance();
                    st.noFall = now + 30;
                    if (el >= 2 && (p.onGround() || p.isInWater())) {
                        begin(p, st, Moves.get(Moves.PLUNGE_LAND), true);
                    } else if (el >= st.move.duration) {
                        st.move = null;
                    }
                } else {
                    while (st.move != null && st.hitIdx < st.move.hits.length && st.move.hits[st.hitIdx].tick <= el) {
                        hit(p, st.move, st.move.hits[st.hitIdx]);
                        st.hitIdx++;
                    }
                    if (el >= st.move.duration) {
                        st.move = null;
                    }
                }
            }
        }
        if (st.grapple) {
            p.resetFallDistance();
            long el = now - st.gStart;
            if (!StarCombat.holds(p) || !p.isAlive()) {
                grappleEnd(p, true);
            } else if (st.target >= 0) {
                if (!st.yanked && el >= st.fly) {
                    st.yanked = true;
                    yank(p, st);
                }
                if (el >= st.fly + 8) {
                    grappleEnd(p, true);
                }
            } else if (el > 100) {
                grappleEnd(p, true);
            }
        }
    }

    private static boolean canHit(ServerPlayer p, LivingEntity e) {
        if (e == p || !e.isAlive() || e.isSpectator() || e instanceof ArmorStand || e.isPassengerOfSameVehicle(p)) {
            return false;
        }
        if (e instanceof OwnableEntity own && p.getUUID().equals(own.getOwnerUUID())) {
            return false;
        }
        if (e instanceof Player other) {
            return Config.pvp && p.server.isPvpAllowed() && p.canHarmPlayer(other);
        }
        return !e.isAlliedTo(p);
    }

    private static void hit(ServerPlayer p, Moves.Move move, Moves.Hit hit) {
        ServerLevel level = p.serverLevel();
        Vec3 fwd = Moves.forward(p.getYRot());
        Vec3 center = p.position().add(fwd.scale(hit.fwd));
        double reach = hit.range + 1.5;
        AABB box = new AABB(center.x - reach, center.y - 2.5, center.z - reach, center.x + reach, center.y + 4.5, center.z + reach);
        List<LivingEntity> found = level.getEntitiesOfClass(LivingEntity.class, box, e -> canHit(p, e));
        int count = 0;
        double cos = Math.cos(Math.toRadians(hit.half));
        for (LivingEntity e : found) {
            double dx = e.getX() - center.x, dz = e.getZ() - center.z;
            double dist = Math.sqrt(dx * dx + dz * dz) - e.getBbWidth() * 0.5;
            if (dist > hit.range) {
                continue;
            }
            if (hit.half < 180) {
                double px = e.getX() - p.getX(), pz = e.getZ() - p.getZ();
                double len = Math.sqrt(px * px + pz * pz);
                if (len > 1.3 && (px * fwd.x + pz * fwd.z) / len < cos) {
                    continue;
                }
            }
            if (!p.hasLineOfSight(e)) {
                continue;
            }
            e.invulnerableTime = 0;
            float damage = hit.damage * Config.damageMultiplier;
            if (damage > 0 && !e.hurt(p.damageSources().playerAttack(p), damage)) {
                continue;
            }
            count++;
            if (Config.fire && hit.fire > 0) {
                e.setSecondsOnFire(hit.fire);
            }
            if (hit.knock > 0) {
                double kx = p.getX() - e.getX(), kz = p.getZ() - e.getZ();
                if (hit.fwd > 0) {
                    kx = center.x - e.getX();
                    kz = center.z - e.getZ();
                    if (kx * kx + kz * kz < 0.01) {
                        kx = -fwd.x;
                        kz = -fwd.z;
                    }
                }
                e.knockback(hit.knock, kx, kz);
            }
            if (hit.lift > 0) {
                e.setDeltaMovement(e.getDeltaMovement().add(0, hit.lift, 0));
            }
            e.hurtMarked = true;
            if (count == 1) {
                level.playSound(null, e.getX(), e.getY(), e.getZ(), Sounds.HIT.get(), SoundSource.PLAYERS, 1.0F,
                        0.85F + level.random.nextFloat() * 0.3F);
            }
        }
        if (count > 0) {
            Net.toPlayer(p, new Net.HitMsg(Math.min(count, 100), move.heavy));
        }
    }

    // ------------------------------------------------------------ gancho

    public static void grapple(ServerPlayer p, double x, double y, double z, int targetId) {
        if (!StarCombat.holds(p) || p.isSpectator() || !p.isAlive()) {
            return;
        }
        St st = of(p);
        long now = p.level().getGameTime();
        if (st.grapple || now < st.gReady) {
            return;
        }
        Vec3 eye = p.getEyePosition();
        Vec3 anchor = new Vec3(x, y, z);
        int target = -1;
        if (targetId >= 0) {
            Entity e = p.level().getEntity(targetId);
            if (!(e instanceof LivingEntity le) || !Config.pullMobs || !canHit(p, le) || e.distanceTo(p) > Config.grappleRange + 4) {
                return;
            }
            anchor = e.position().add(0, e.getBbHeight() * 0.6, 0);
            target = targetId;
        } else {
            double dist = eye.distanceTo(anchor);
            if (dist > Config.grappleRange + 4 || dist < 1.5) {
                return;
            }
            // tiene que haber un bloque donde dice
            Vec3 dir = anchor.subtract(eye).normalize();
            BlockHitResult res = p.level().clip(new ClipContext(eye, anchor.add(dir.scale(0.6)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            if (res.getType() != HitResult.Type.BLOCK || res.getLocation().distanceTo(anchor) > 2.0) {
                return;
            }
            anchor = res.getLocation();
        }
        st.grapple = true;
        st.gStart = now;
        st.anchor = anchor;
        st.target = target;
        st.fly = flyTicks(eye.distanceTo(anchor));
        st.yanked = false;
        st.gReady = now + Config.grappleCooldown;
        Net.toWatchers(p, new Net.GrappleMsg(p.getId(), true, anchor.x, anchor.y, anchor.z, target));
        p.level().playSound(p, p.getX(), p.getY(), p.getZ(), Sounds.GRAPPLE.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** Ticks que tarda la hoja en llegar. Igual en el cliente. */
    public static int flyTicks(double dist) {
        return Math.max(2, (int) Math.ceil(dist / 3.2));
    }

    private static void yank(ServerPlayer p, St st) {
        Entity e = p.level().getEntity(st.target);
        if (!(e instanceof LivingEntity le) || !le.isAlive()) {
            return;
        }
        Vec3 to = p.position().add(0, 0.6, 0).subtract(le.position());
        double dist = to.length();
        if (dist < 2.2 || dist > Config.grappleRange + 8) {
            return;
        }
        double speed = Math.min(2.2, 0.55 + dist * 0.11);
        Vec3 v = to.normalize().scale(speed);
        le.setDeltaMovement(v.x, Math.max(0.32, v.y + 0.28), v.z);
        le.hurtMarked = true;
        le.invulnerableTime = 0;
        le.hurt(p.damageSources().playerAttack(p), 2 * Config.damageMultiplier);
        if (Config.fire) {
            le.setSecondsOnFire(2);
        }
        le.setDeltaMovement(v.x, Math.max(0.32, v.y + 0.28), v.z);
        p.level().playSound(null, le.getX(), le.getY(), le.getZ(), Sounds.LATCH.get(), SoundSource.PLAYERS, 1.0F, 1.1F);
    }

    /** {@code self} = tambien se le avisa al propio jugador (cuando lo corta el servidor). */
    public static void grappleEnd(ServerPlayer p, boolean self) {
        St st = STATES.get(p.getUUID());
        if (st == null || !st.grapple) {
            return;
        }
        st.grapple = false;
        st.noFall = p.level().getGameTime() + 70;
        Net.GrappleMsg msg = new Net.GrappleMsg(p.getId(), false, 0, 0, 0, -1);
        if (self) {
            Net.toWatchersAndSelf(p, msg);
        } else {
            Net.toWatchers(p, msg);
        }
    }

    @SubscribeEvent
    public static void fall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            St st = STATES.get(p.getUUID());
            if (st != null && (st.grapple || p.level().getGameTime() < st.noFall
                    || (st.move != null && (st.move.prep || st.move.idx == Moves.PLUNGE_LAND)))) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            Net.toPlayer(p, new Net.ConfigMsg(Config.grappleRange, Config.grappleCooldown));
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }

    // ------------------------------------------------------------ comandos

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("starcombat").requires(s -> s.hasPermission(2))
                .then(Commands.literal("give")
                        .executes(c -> give(c.getSource(), List.of(c.getSource().getPlayerOrException())))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(c -> give(c.getSource(), EntityArgument.getPlayers(c, "targets")))))
                .then(Commands.literal("reload").executes(c -> {
                    Config.load();
                    for (ServerPlayer p : c.getSource().getServer().getPlayerList().getPlayers()) {
                        Net.toPlayer(p, new Net.ConfigMsg(Config.grappleRange, Config.grappleCooldown));
                    }
                    c.getSource().sendSuccess(() -> Component.translatable("commands.starcombat.reload"), true);
                    return 1;
                })));
    }

    private static int give(CommandSourceStack source, Collection<ServerPlayer> players) {
        for (ServerPlayer p : players) {
            ItemStack stack = new ItemStack(StarCombat.EMBER_FANGS.get());
            if (!p.getInventory().add(stack)) {
                p.drop(stack, false);
            }
        }
        source.sendSuccess(() -> Component.translatable("commands.starcombat.give", players.size()), true);
        return players.size();
    }
}
