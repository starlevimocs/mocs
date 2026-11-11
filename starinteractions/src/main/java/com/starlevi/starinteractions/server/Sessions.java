package com.starlevi.starinteractions.server;

import com.starlevi.starinteractions.Catalog;
import com.starlevi.starinteractions.StarInteractions;
import com.starlevi.starinteractions.net.Net;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * El servidor de las interacciones. Nada pasa sin que el servidor lo decida:
 * <ul>
 *   <li>entre jugadores siempre se pide permiso (cartel con Y / N, vence a los 15 segundos);</li>
 *   <li>en las interacciones de a dos acomoda a los dos frente a frente, los mantiene en su lugar mientras dura
 *       y le manda el "ancla" a todos los que estan cerca, asi todos los ven en el mismo lugar;</li>
 *   <li>de la mano: los dos caminan libres, pero si se alejan el servidor los acerca;</li>
 *   <li>cargar: el que va cargado va montado en el otro, asi nunca se separan;</li>
 *   <li>modo toque: valida la distancia, reparte la pose a los que miran y decide como reacciona el aldeano.</li>
 * </ul>
 * Todo se limpia solo si alguien se desconecta, muere, cambia de dimension o se aleja.
 */
@Mod.EventBusSubscriber(modid = StarInteractions.MOD_ID)
public final class Sessions {
    private static final int REQUEST_SECONDS = 15;
    private static final double RANGE = 64 * 64;

    private record Pending(UUID requester, String interaction, long expires) {
    }

    private static final class Pair {
        UUID a, b;
        Vec3 posA, posB;
        float yawA, yawB;
        long end;
    }

    private static final class Linked {
        UUID a, b;
        int mode;
    }

    private static final class Touch {
        UUID toucher;
        UUID target;
        Vec3 lock;
        long lastInput;
        long started;
        final int[] count = new int[8];
        int rejections;
        int rewards;
        boolean penalized;
        long lastReaction;
        Net.TouchPose pose = new Net.TouchPose();
    }

    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final List<Pair> PAIRS = new ArrayList<>();
    private static final List<Linked> LINKS = new ArrayList<>();
    private static final Map<UUID, Touch> TOUCHES = new HashMap<>();
    private static final Map<UUID, String> EMOTES = new HashMap<>();
    private static final Map<String, Long> HEART_COOLDOWN = new HashMap<>();
    private static final Map<UUID, Long> LAST_REQUEST = new HashMap<>();

    private Sessions() {
    }

    // ================================================================ utilidades

    private static void tell(ServerPlayer p, String key, Object... args) {
        p.displayClientMessage(Component.translatable("message.starinteractions." + key, args).withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    private static void broadcast(ServerLevel level, Vec3 at, Object msg) {
        for (ServerPlayer p : level.players()) {
            if (p.position().distanceToSqr(at) < RANGE) {
                Net.toPlayer(p, msg);
            }
        }
    }

    private static boolean busy(UUID id) {
        for (Pair p : PAIRS) {
            if (p.a.equals(id) || p.b.equals(id)) {
                return true;
            }
        }
        for (Linked l : LINKS) {
            if (l.a.equals(id) || l.b.equals(id)) {
                return true;
            }
        }
        if (TOUCHES.containsKey(id)) {
            return true;
        }
        for (Touch t : TOUCHES.values()) {
            if (t.target.equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static ServerPlayer player(MinecraftServer server, UUID id) {
        return server.getPlayerList().getPlayer(id);
    }

    // ================================================================ pedidos

    public static void onRequest(ServerPlayer player, Net.Request msg) {
        Catalog.Entry entry = Catalog.get(msg.interaction());
        Entity target = player.serverLevel().getEntity(msg.targetId());
        if (entry == null || entry.kind() == Catalog.Kind.SOLO || target == null || target == player || !target.isAlive()) {
            return;
        }
        long now = player.serverLevel().getGameTime();
        Long last = LAST_REQUEST.get(player.getUUID());
        if (last != null && now - last < 10) {
            return; // nada de llenar de pedidos
        }
        LAST_REQUEST.put(player.getUUID(), now);
        if (player.distanceToSqr(target) > 6 * 6) {
            tell(player, "too_far");
            return;
        }
        if (busy(player.getUUID()) || busy(target.getUUID())) {
            tell(player, "busy");
            return;
        }
        if (entry.kind() == Catalog.Kind.TOUCH && !(target instanceof ServerPlayer)) {
            if (target instanceof LivingEntity living && McaBridge.isVillager(target)) {
                startTouch(player, living);
            } else {
                tell(player, "cant_touch");
            }
            return;
        }
        if (!(target instanceof ServerPlayer other)) {
            tell(player, "players_only");
            return;
        }
        PENDING.put(other.getUUID(), new Pending(player.getUUID(), entry.id(), now + REQUEST_SECONDS * 20L));
        Net.toPlayer(other, new Net.Prompt(player.getId(), player.getGameProfile().getName(), entry.id(), REQUEST_SECONDS));
        MutableComponent yes = Component.translatable("message.starinteractions.accept").withStyle(s -> s.withColor(ChatFormatting.GREEN).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/starint accept")));
        MutableComponent no = Component.translatable("message.starinteractions.decline").withStyle(s -> s.withColor(ChatFormatting.RED)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/starint decline")));
        other.sendSystemMessage(Component.translatable("message.starinteractions.asks", player.getDisplayName(), Component.translatable(entry.label()))
                .withStyle(ChatFormatting.LIGHT_PURPLE).append(" ").append(yes).append(" ").append(no));
        tell(player, "asked", other.getDisplayName());
    }

    public static void onAnswer(ServerPlayer target, Net.Answer msg) {
        answer(target, msg.accept());
    }

    private static void answer(ServerPlayer target, boolean accept) {
        Pending p = PENDING.remove(target.getUUID());
        if (p == null) {
            return;
        }
        ServerPlayer requester = player(target.server, p.requester);
        long now = target.serverLevel().getGameTime();
        if (requester == null || now > p.expires || requester.level() != target.level()) {
            tell(target, "expired");
            return;
        }
        if (!accept) {
            tell(requester, "declined", target.getDisplayName());
            return;
        }
        if (busy(requester.getUUID()) || busy(target.getUUID()) || requester.distanceToSqr(target) > 8 * 8
                || !requester.isAlive() || !target.isAlive()) {
            tell(requester, "busy");
            tell(target, "busy");
            return;
        }
        Catalog.Entry entry = Catalog.get(p.interaction);
        if (entry == null) {
            return;
        }
        switch (entry.kind()) {
            case PAIR -> startPair(requester, target, entry);
            case HANDS, CARRY -> startLink(requester, target, Catalog.linkMode(entry.id()));
            case TOUCH -> startTouch(requester, target);
            default -> {
            }
        }
    }

    // ================================================================ de a dos

    private static void startPair(ServerPlayer a, ServerPlayer b, Catalog.Entry entry) {
        ServerLevel level = a.serverLevel();
        stopEmote(a);
        stopEmote(b);
        if (a.isPassenger()) {
            a.stopRiding();
        }
        if (b.isPassenger()) {
            b.stopRiding();
        }
        Vec3 anchor = a.position();
        Vec3 to = b.position().subtract(anchor).multiply(1, 0, 1);
        float yaw = to.lengthSqr() < 1.0E-4 ? a.getYRot() : (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90F;
        double rad = Math.toRadians(yaw);
        double d = entry.distance() / 16.0;
        Vec3 spot = new Vec3(anchor.x - Math.sin(rad) * d, anchor.y, anchor.z + Math.cos(rad) * d);
        AABB box = b.getDimensions(b.getPose()).makeBoundingBox(spot);
        if (!level.noCollision(b, box)) {
            tell(a, "no_room");
            tell(b, "no_room");
            return;
        }
        Pair p = new Pair();
        p.a = a.getUUID();
        p.b = b.getUUID();
        p.posA = anchor;
        p.posB = spot;
        p.yawA = yaw;
        p.yawB = yaw + 180F;
        p.end = level.getGameTime() + entry.ticks();
        PAIRS.add(p);
        a.teleportTo(level, anchor.x, anchor.y, anchor.z, p.yawA, 0);
        b.teleportTo(level, spot.x, spot.y, spot.z, p.yawB, 0);
        broadcast(level, anchor, new Net.PairStart(entry.id(), a.getId(), b.getId(), anchor.x, anchor.y, anchor.z, yaw));
        if (entry.id().equals("kiss") || entry.id().endsWith("_kiss") || entry.id().equals("hug")) {
            level.sendParticles(ParticleTypes.HEART, (anchor.x + spot.x) / 2, anchor.y + 2.1, (anchor.z + spot.z) / 2, 4, 0.25, 0.2, 0.25, 0);
        }
    }

    private static void tickPairs(MinecraftServer server) {
        for (Iterator<Pair> it = PAIRS.iterator(); it.hasNext(); ) {
            Pair p = it.next();
            ServerPlayer a = player(server, p.a);
            ServerPlayer b = player(server, p.b);
            boolean valid = a != null && b != null && a.isAlive() && b.isAlive() && a.level() == b.level();
            if (!valid || a.serverLevel().getGameTime() >= p.end) {
                it.remove();
                if (!valid) {
                    ServerPlayer any = a != null ? a : b;
                    if (any != null) {
                        broadcast(any.serverLevel(), any.position(), new Net.PairStop(a != null ? a.getId() : -1, b != null ? b.getId() : -1));
                    }
                }
                continue;
            }
            hold(a, p.posA, p.yawA);
            hold(b, p.posB, p.yawB);
        }
    }

    /** Mientras dura, cada uno se queda en su lugar (el cliente tambien traba el movimiento). */
    private static void hold(ServerPlayer p, Vec3 pos, float yaw) {
        if (p.position().distanceToSqr(pos) > 0.3 * 0.3) {
            p.connection.teleport(pos.x, pos.y, pos.z, yaw, p.getXRot());
        }
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0;
    }

    // ================================================================ de la mano y cargar

    private static void startLink(ServerPlayer a, ServerPlayer b, int mode) {
        if (mode == Catalog.LINK_NONE) {
            return;
        }
        stopEmote(a);
        stopEmote(b);
        Linked l = new Linked();
        l.a = a.getUUID();
        l.b = b.getUUID();
        l.mode = mode;
        if (mode != Catalog.LINK_HANDS) {
            // a carga a b: b va montado en a
            if (a.isPassenger()) {
                a.stopRiding();
            }
            if (b.isPassenger()) {
                b.stopRiding();
            }
            if (!b.startRiding(a, true)) {
                tell(a, "busy");
                return;
            }
            a.connection.send(new ClientboundSetPassengersPacket(a));
        }
        LINKS.add(l);
        broadcast(a.serverLevel(), a.position(), new Net.Link(a.getId(), b.getId(), mode));
        tell(a, mode == Catalog.LINK_HANDS ? "hands_hint" : "carry_hint");
        tell(b, mode == Catalog.LINK_HANDS ? "hands_hint" : "carried_hint");
    }

    private static void endLink(MinecraftServer server, Linked l) {
        ServerPlayer a = player(server, l.a);
        ServerPlayer b = player(server, l.b);
        if (l.mode != Catalog.LINK_HANDS && b != null && a != null && b.getVehicle() == a) {
            b.stopRiding();
            a.connection.send(new ClientboundSetPassengersPacket(a));
        }
        ServerPlayer any = a != null ? a : b;
        if (any != null) {
            broadcast(any.serverLevel(), any.position(), new Net.Link(a != null ? a.getId() : -1, b != null ? b.getId() : -1, Catalog.LINK_NONE));
        }
    }

    private static void tickLinks(MinecraftServer server) {
        for (Iterator<Linked> it = LINKS.iterator(); it.hasNext(); ) {
            Linked l = it.next();
            ServerPlayer a = player(server, l.a);
            ServerPlayer b = player(server, l.b);
            boolean valid = a != null && b != null && a.isAlive() && b.isAlive() && a.level() == b.level();
            if (valid && l.mode == Catalog.LINK_HANDS) {
                double dist = a.distanceTo(b);
                if (a.isShiftKeyDown() || b.isShiftKeyDown() || dist > 7 || a.isPassenger() || b.isPassenger()) {
                    valid = false;
                } else if (dist > 1.35) {
                    // se alejaron: el que acepto es llevado hacia el otro, como de la mano
                    Vec3 pull = a.position().subtract(b.position()).multiply(1, 0, 1).normalize().scale(Math.min(0.32, (dist - 1.2) * 0.22));
                    b.setDeltaMovement(b.getDeltaMovement().add(pull));
                    b.hurtMarked = true;
                }
            } else if (valid) {
                if (b.getVehicle() != a || a.isShiftKeyDown()) {
                    valid = false;
                }
            }
            if (valid && a.serverLevel().getGameTime() % 40 == 0) {
                broadcast(a.serverLevel(), a.position(), new Net.Link(a.getId(), b.getId(), l.mode));
            }
            if (!valid) {
                it.remove();
                endLink(server, l);
            }
        }
    }

    public static void onRelease(ServerPlayer player) {
        MinecraftServer server = player.server;
        for (Iterator<Linked> it = LINKS.iterator(); it.hasNext(); ) {
            Linked l = it.next();
            if (l.a.equals(player.getUUID()) || l.b.equals(player.getUUID())) {
                it.remove();
                endLink(server, l);
            }
        }
        Touch t = TOUCHES.remove(player.getUUID());
        if (t != null) {
            endTouch(player.serverLevel(), t, player, "");
        }
        stopEmote(player);
    }

    // ================================================================ emotes

    public static void onEmote(ServerPlayer player, Net.Emote msg) {
        if (msg.id().isEmpty()) {
            stopEmote(player);
            return;
        }
        Catalog.Entry entry = Catalog.get(msg.id());
        if (entry == null || entry.kind() != Catalog.Kind.SOLO) {
            return;
        }
        for (Pair p : PAIRS) {
            if (p.a.equals(player.getUUID()) || p.b.equals(player.getUUID())) {
                return;
            }
        }
        EMOTES.put(player.getUUID(), entry.id());
        broadcast(player.serverLevel(), player.position(), new Net.EmoteState(player.getId(), entry.id()));
    }

    private static void stopEmote(ServerPlayer player) {
        if (EMOTES.remove(player.getUUID()) != null) {
            broadcast(player.serverLevel(), player.position(), new Net.EmoteState(player.getId(), ""));
        }
    }

    // ================================================================ modo toque

    public static final int EV_PET = 1;
    public static final int EV_CARESS = 2;
    public static final int EV_BOOP = 3;
    public static final int EV_POKE = 4;
    public static final int EV_ARM = 5;
    public static final int EV_HOLD = 6;

    private static void startTouch(ServerPlayer toucher, LivingEntity target) {
        if (toucher.distanceToSqr(target) > 3.2 * 3.2) {
            tell(toucher, "touch_closer");
            return;
        }
        ServerLevel level = toucher.serverLevel();
        Touch t = new Touch();
        t.toucher = toucher.getUUID();
        t.target = target.getUUID();
        t.lock = target.position();
        t.started = level.getGameTime();
        t.lastInput = t.started;
        TOUCHES.put(toucher.getUUID(), t);
        stopEmote(toucher);
        if (target instanceof Mob mob) {
            mob.getNavigation().stop();
        }
        Net.toPlayer(toucher, new Net.TouchSession(target.getId(), true, ""));
        broadcast(level, target.position(), new Net.TouchState(toucher.getId(), target.getId(), true, t.pose, 0));
    }

    private static void endTouch(ServerLevel level, Touch t, ServerPlayer toucher, String message) {
        Entity target = level.getEntity(t.target);
        int targetId = target != null ? target.getId() : -1;
        if (toucher != null) {
            Net.toPlayer(toucher, new Net.TouchSession(targetId, false, message));
            broadcast(level, toucher.position(), new Net.TouchState(toucher.getId(), targetId, false, new Net.TouchPose(), 0));
        }
    }

    public static void onTouchInput(ServerPlayer player, Net.TouchInput msg) {
        Touch t = TOUCHES.get(player.getUUID());
        if (t == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        Entity target = level.getEntity(t.target);
        if (!(target instanceof LivingEntity living) || !target.isAlive()) {
            return;
        }
        t.lastInput = level.getGameTime();
        t.pose = msg.pose();
        // la mano no puede estar lejos del cuerpo que toca
        Vec3 hand = new Vec3(t.pose.hx, t.pose.hy, t.pose.hz);
        if (hand.distanceToSqr(target.position().add(0, 1, 0)) > 3 * 3) {
            t.pose.touching = false;
        }
        int fx = msg.event() > 0 ? react(player, living, t, msg.event()) : 0;
        if (!TOUCHES.containsKey(player.getUUID())) {
            return; // la reaccion corto la sesion
        }
        Net.TouchState state = new Net.TouchState(player.getId(), target.getId(), true, t.pose, fx);
        for (ServerPlayer p : level.players()) {
            if ((p != player || fx != 0) && p.position().distanceToSqr(target.position()) < RANGE) {
                Net.toPlayer(p, state);
            }
        }
    }

    /** Como reacciona a cada caricia. Devuelve el efecto para la pantalla: 1 le gusta, 2 no le gusta, 3 corazon. */
    private static int react(ServerPlayer player, LivingEntity target, Touch t, int event) {
        if (event < 1 || event > 6) {
            return 0;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        boolean villager = McaBridge.isVillager(target);
        int hearts = villager ? McaBridge.hearts(target, player) : 100;
        String personality = villager ? McaBridge.personality(target) : "";
        int need = switch (event) {
            case EV_PET -> McaBridge.isChild(target) ? -50 : 5;
            case EV_CARESS -> 30;
            case EV_BOOP -> 15;
            case EV_POKE -> 20;
            case EV_HOLD -> 25;
            default -> 10;
        };
        if (personality.equals("shy") || personality.equals("grumpy")) {
            need += 10;
        } else if (personality.equals("flirty") || personality.equals("friendly") || personality.equals("peppy")) {
            need -= 5;
        }
        if (hearts < need) {
            // todavia no hay confianza para eso
            t.rejections++;
            if (now - t.lastReaction > 20) {
                t.lastReaction = now;
                McaBridge.gesture(target, t.rejections > 1 ? "angry" : "no");
                McaBridge.say(target, player, t.rejections > 1 ? "touch.leave" : "touch.reject");
            }
            if (!t.penalized) {
                t.penalized = true;
                McaBridge.rewardHearts(target, player, -1);
            }
            if (t.rejections >= 2) {
                TOUCHES.remove(player.getUUID());
                endTouch(level, t, player, "message.starinteractions.touch_rejected");
            }
            return 2;
        }
        t.count[event]++;
        int n = t.count[event];
        String line = null;
        String gesture = null;
        boolean reward = false;
        switch (event) {
            case EV_PET -> {
                if (n % 5 == 0) {
                    line = "touch.head";
                    gesture = personality.equals("shy") ? "shy" : personality.equals("peppy") ? "cheer" : personality.equals("grumpy") ? "hips" : "hum";
                    reward = true;
                }
            }
            case EV_CARESS -> {
                if (n % 4 == 0) {
                    line = "touch.cheek";
                    gesture = personality.equals("flirty") ? "flirty" : "shy";
                    reward = true;
                }
            }
            case EV_BOOP -> {
                if (now - t.lastReaction > 30) {
                    line = "touch.boop";
                    gesture = "surprised";
                }
            }
            case EV_POKE -> {
                if (n % 3 == 0) {
                    line = "touch.tickle";
                    gesture = "laugh";
                    reward = n == 3;
                }
            }
            case EV_HOLD -> {
                line = "touch.hand";
                reward = true;
            }
            default -> {
                if (n % 6 == 0) {
                    line = "touch.arm";
                }
            }
        }
        if (line == null) {
            return 1;
        }
        t.lastReaction = now;
        if (villager) {
            if (gesture != null && (t.pose.arms == 0)) {
                McaBridge.gesture(target, gesture);
            }
            McaBridge.say(target, player, line);
        }
        if (reward && t.rewards < 4) {
            String key = player.getUUID() + ":" + target.getUUID();
            Long last = HEART_COOLDOWN.get(key);
            // los corazones por caricias tienen un limite: unos pocos por rato
            if (last == null || now - last > 20 * 150 || t.rewards > 0) {
                if (t.rewards == 0) {
                    HEART_COOLDOWN.put(key, now);
                }
                t.rewards++;
                if (villager) {
                    McaBridge.rewardHearts(target, player, 1);
                }
                level.sendParticles(ParticleTypes.HEART, target.getX(), target.getY() + target.getBbHeight() + 0.3, target.getZ(), 3, 0.25, 0.15, 0.25, 0);
                return 3;
            }
        }
        return 1;
    }

    private static void tickTouches(MinecraftServer server) {
        for (Iterator<Map.Entry<UUID, Touch>> it = TOUCHES.entrySet().iterator(); it.hasNext(); ) {
            Touch t = it.next().getValue();
            ServerPlayer toucher = player(server, t.toucher);
            if (toucher == null) {
                it.remove();
                continue;
            }
            ServerLevel level = toucher.serverLevel();
            Entity target = level.getEntity(t.target);
            long now = level.getGameTime();
            boolean valid = target instanceof LivingEntity && target.isAlive() && toucher.isAlive()
                    && toucher.distanceToSqr(target) < 4 * 4 && now - t.lastInput < 20 * 25 && now - t.started < 20 * 180;
            if (!valid) {
                it.remove();
                endTouch(level, t, toucher, "");
                continue;
            }
            if (target instanceof Mob mob) {
                // el aldeano se queda quieto, mirando a quien lo toca
                mob.getNavigation().stop();
                if (mob.position().distanceToSqr(t.lock) > 0.02) {
                    mob.setPos(t.lock.x, mob.getY(), t.lock.z);
                }
                mob.setDeltaMovement(0, Math.min(0, mob.getDeltaMovement().y), 0);
                Vec3 to = toucher.position().subtract(mob.position());
                float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90F;
                mob.setYRot(yaw);
                mob.setYBodyRot(yaw);
                mob.setYHeadRot(yaw);
            }
        }
    }

    // ================================================================ eventos

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (!PAIRS.isEmpty()) {
            tickPairs(server);
        }
        if (!LINKS.isEmpty()) {
            tickLinks(server);
        }
        if (!TOUCHES.isEmpty()) {
            tickTouches(server);
        }
        if (!PENDING.isEmpty() && server.getTickCount() % 20 == 0) {
            long now = server.overworld().getGameTime();
            PENDING.values().removeIf(p -> now > p.expires + 40);
        }
    }

    private static void cleanup(ServerPlayer player) {
        onRelease(player);
        PENDING.remove(player.getUUID());
        EMOTES.remove(player.getUUID());
        // si alguien lo estaba tocando a el
        for (Iterator<Map.Entry<UUID, Touch>> it = TOUCHES.entrySet().iterator(); it.hasNext(); ) {
            Touch t = it.next().getValue();
            if (t.target.equals(player.getUUID())) {
                it.remove();
                endTouch(player.serverLevel(), t, player(player.server, t.toucher), "");
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            cleanup(p);
        }
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            cleanup(p);
        }
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("starint")
                .then(Commands.literal("accept").executes(ctx -> {
                    answer(ctx.getSource().getPlayerOrException(), true);
                    return 1;
                }))
                .then(Commands.literal("decline").executes(ctx -> {
                    answer(ctx.getSource().getPlayerOrException(), false);
                    return 1;
                }))
                .then(Commands.literal("release").executes(ctx -> {
                    onRelease(ctx.getSource().getPlayerOrException());
                    return 1;
                })));
    }
}
