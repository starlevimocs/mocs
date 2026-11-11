package com.starlevi.starhero.server;

import com.starlevi.starhero.StarHero;
import com.starlevi.starhero.content.Instrument;
import com.starlevi.starhero.content.InstrumentItem;
import com.starlevi.starhero.net.Net;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

/**
 * Del lado del servidor: los escenarios, el grupo, el arranque sincronizado, el publico y el final del show.
 */
@Mod.EventBusSubscriber(modid = StarHero.MOD_ID)
public final class Stages {
    private static final Map<String, Stage> STAGES = new HashMap<>();
    private static final Random RANDOM = new Random();
    private static final int MAX_BAND = 6;

    private Stages() {
    }

    private static String key(ServerLevel level, BlockPos pos) {
        return level.dimension().location() + "@" + pos.asLong();
    }

    private static Stage find(ServerLevel level, BlockPos pos) {
        return STAGES.get(key(level, pos));
    }

    private static Stage stageOf(ServerPlayer player) {
        for (Stage s : STAGES.values()) {
            if (s.players.containsKey(player.getUUID())) {
                return s;
            }
        }
        return null;
    }

    private static ServerLevel level(MinecraftServer server, Stage s) {
        return server.getLevel(s.dimension);
    }

    // ================================================================ amplificador y menu

    public static void onAmplifierUsed(ServerPlayer player, BlockPos pos, Direction facing) {
        ServerLevel level = player.serverLevel();
        Stage stage = find(level, pos);
        if (stage == null) {
            Stage old = stageOf(player);
            if (old != null) {
                leave(player, old);
            }
            stage = new Stage(level.dimension(), pos, facing, player.getUUID());
            STAGES.put(key(level, pos), stage);
        }
        stage.facing = facing;
        if (stage.playing()) {
            player.displayClientMessage(Component.translatable("message.starhero.busy").withStyle(ChatFormatting.GOLD), true);
            return;
        }
        if (!stage.players.containsKey(player.getUUID())) {
            boolean allowed = stage.players.isEmpty() || stage.bandMode || player.getUUID().equals(stage.leader);
            if (!allowed) {
                player.displayClientMessage(Component.translatable("message.starhero.private").withStyle(ChatFormatting.GOLD), true);
                return;
            }
            Stage old = stageOf(player);
            if (old != null && old != stage) {
                leave(player, old);
            }
            if (stage.players.isEmpty()) {
                stage.leader = player.getUUID();
            }
            stage.players.put(player.getUUID(), defaultInstrument(player, stage));
        }
        sendLobby(player.server, stage, player);
    }

    private static Instrument defaultInstrument(ServerPlayer player, Stage stage) {
        Instrument held = Instrument.fromItem(player.getMainHandItem().getItem());
        if (held != null) {
            return held;
        }
        for (Instrument i : Instrument.values()) {
            if (!stage.players.containsValue(i)) {
                return i;
            }
        }
        return Instrument.GUITAR;
    }

    public static void onLobbyAction(ServerPlayer player, Net.LobbyAction action) {
        Stage stage = find(player.serverLevel(), action.amp());
        if (stage == null || !stage.players.containsKey(player.getUUID())) {
            return;
        }
        boolean isLeader = player.getUUID().equals(stage.leader);
        switch (action.action()) {
            case "instrument" -> {
                if (!stage.playing()) {
                    stage.players.put(player.getUUID(), Instrument.byId(action.arg()));
                }
            }
            case "mode" -> {
                if (isLeader && !stage.playing()) {
                    stage.bandMode = "band".equals(action.arg());
                    if (stage.bandMode) {
                        invite(player, stage);
                    }
                }
            }
            case "song" -> {
                if (isLeader && !stage.playing()) {
                    String[] p = action.arg().split("\t");
                    stage.song = p[0];
                    stage.songTitle = p.length > 1 ? p[1] : p[0];
                    stage.bpm = p.length > 2 ? parseF(p[2], 120) : 120;
                    stage.offsetMs = p.length > 3 ? (int) parseF(p[3], 0) : 0;
                    stage.leadInMs = p.length > 4 ? (int) parseF(p[4], 0) : 0;
                }
            }
            case "start" -> {
                if (isLeader && !stage.playing()) {
                    start(player.server, stage, player);
                    return;
                }
            }
            case "leave" -> {
                leave(player, stage);
                return;
            }
            default -> {
            }
        }
        broadcastLobby(player.server, stage);
    }

    private static float parseF(String s, float def) {
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static void leave(ServerPlayer player, Stage stage) {
        stage.players.remove(player.getUUID());
        Stage.Stats st = stage.stats.get(player.getUUID());
        if (st != null) {
            st.finished = true;
        }
        if (stage.players.isEmpty() || player.getUUID().equals(stage.leader)) {
            if (!stage.playing()) {
                for (UUID id : stage.players.keySet()) {
                    ServerPlayer p = player.server.getPlayerList().getPlayer(id);
                    if (p != null) {
                        p.displayClientMessage(Component.translatable("message.starhero.disbanded").withStyle(ChatFormatting.GRAY), false);
                    }
                }
                STAGES.values().remove(stage);
                return;
            }
            // en pleno show el lider se fue: sigue el siguiente
            stage.players.keySet().stream().findFirst().ifPresent(id -> stage.leader = id);
        }
        broadcastLobby(player.server, stage);
    }

    private static void invite(ServerPlayer leader, Stage stage) {
        BlockPos a = stage.amp;
        MutableComponent button = Component.translatable("message.starhero.join_button").withStyle(s -> s
                .withColor(ChatFormatting.LIGHT_PURPLE).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/starhero join " + a.getX() + " " + a.getY() + " " + a.getZ()))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("message.starhero.join_hover"))));
        for (ServerPlayer p : leader.serverLevel().players()) {
            if (p != leader && !stage.players.containsKey(p.getUUID()) && p.distanceToSqr(Vec3.atCenterOf(a)) < 48 * 48) {
                p.sendSystemMessage(Component.translatable("message.starhero.invite", leader.getDisplayName()).withStyle(ChatFormatting.AQUA)
                        .append(" ").append(button));
            }
        }
        leader.displayClientMessage(Component.translatable("message.starhero.invited").withStyle(ChatFormatting.AQUA), true);
    }

    // ================================================================ estado del grupo

    /** Aldeanos de la banda cerca del amplificador. */
    private static List<Mob> nearbyNpcs(ServerLevel level, Stage stage) {
        BandData band = BandData.get(level.getServer());
        AABB box = new AABB(stage.amp).inflate(24);
        List<Mob> list = level.getEntitiesOfClass(Mob.class, box, m -> m.isAlive() && band.get(m.getUUID()) != null);
        list.sort(Comparator.comparingDouble(m -> m.distanceToSqr(Vec3.atCenterOf(stage.amp))));
        return list;
    }

    private static List<Net.Member> lobbyMembers(MinecraftServer server, Stage stage) {
        List<Net.Member> out = new ArrayList<>();
        for (Map.Entry<UUID, Instrument> e : stage.players.entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null) {
                out.add(new Net.Member(p.getId(), p.getGameProfile().getName(), e.getValue().id(), true, e.getKey().equals(stage.leader)));
            }
        }
        ServerLevel level = level(server, stage);
        if (level != null) {
            BandData band = BandData.get(server);
            for (Mob m : nearbyNpcs(level, stage)) {
                if (out.size() >= MAX_BAND) {
                    break;
                }
                out.add(new Net.Member(m.getId(), m.getDisplayName().getString(), band.get(m.getUUID()).id(), false, false));
            }
        }
        return out;
    }

    private static Net.LobbyState lobbyState(MinecraftServer server, Stage stage, boolean open) {
        return new Net.LobbyState(stage.amp, open, stage.bandMode, stage.song, stage.songTitle, lobbyMembers(server, stage));
    }

    private static void sendLobby(MinecraftServer server, Stage stage, ServerPlayer opener) {
        Net.toPlayer(opener, lobbyState(server, stage, true));
        for (UUID id : stage.players.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null && p != opener) {
                Net.toPlayer(p, lobbyState(server, stage, false));
            }
        }
    }

    private static void broadcastLobby(MinecraftServer server, Stage stage) {
        for (UUID id : stage.players.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                Net.toPlayer(p, lobbyState(server, stage, false));
            }
        }
    }

    // ================================================================ arrancar el show

    private static void start(MinecraftServer server, Stage stage, ServerPlayer leader) {
        ServerLevel level = level(server, stage);
        if (level == null) {
            return;
        }
        if (stage.song.isEmpty()) {
            leader.displayClientMessage(Component.translatable("message.starhero.no_song").withStyle(ChatFormatting.RED), true);
            return;
        }
        Vec3 ampC = Vec3.atBottomCenterOf(stage.amp);
        Vec3 f = stage.forward();
        Vec3 s = stage.side();
        Vec3 base = ampC.add(f.scale(3.0));
        float yaw = stage.facing.toYRot();

        // quienes tocan: los jugadores cerca y los aldeanos de la banda
        List<Entity> front = new ArrayList<>();
        List<Entity> back = new ArrayList<>();
        Map<UUID, Instrument> instruments = new HashMap<>();
        for (Map.Entry<UUID, Instrument> e : new ArrayList<>(stage.players.entrySet())) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null || p.level() != level || p.distanceToSqr(ampC) > 40 * 40) {
                stage.players.remove(e.getKey());
                continue;
            }
            instruments.put(p.getUUID(), e.getValue());
            (e.getValue() == Instrument.DRUMS ? back : front).add(p);
        }
        BandData band = BandData.get(server);
        for (Mob m : nearbyNpcs(level, stage)) {
            if (front.size() + back.size() >= MAX_BAND) {
                break;
            }
            Instrument i = band.get(m.getUUID());
            instruments.put(m.getUUID(), i);
            (i == Instrument.DRUMS ? back : front).add(m);
        }
        if (front.isEmpty() && back.isEmpty()) {
            return;
        }
        // el lider adelante en el medio
        front.sort(Comparator.comparingInt(e -> e.getUUID().equals(stage.leader) ? 0 : (e instanceof Player ? 1 : 2)));

        stage.slots.clear();
        double[] lane = {0, -2.2, 2.2, -4.3, 4.3, -6.3};
        for (int i = 0; i < front.size(); i++) {
            Vec3 pos = base.add(s.scale(lane[Math.min(i, lane.length - 1)]));
            place(level, stage, front.get(i), instruments.get(front.get(i).getUUID()), pos, yaw);
        }
        for (int i = 0; i < back.size(); i++) {
            Vec3 pos = base.subtract(f.scale(1.6)).add(s.scale(i == 0 ? 0 : (i % 2 == 0 ? 3.0 : -3.0)));
            place(level, stage, back.get(i), instruments.get(back.get(i).getUUID()), pos, yaw);
        }
        Vec3 c = Vec3.ZERO;
        for (Stage.Slot slot : stage.slots.values()) {
            c = c.add(slot.pos);
        }
        stage.center = c.scale(1.0 / stage.slots.size());

        stage.phase = Stage.Phase.COUNTDOWN;
        stage.startTick = level.getGameTime() + 60;
        stage.hype = 0.5F;
        stage.lastBeat = -1;
        stage.stats.clear();
        for (UUID id : stage.players.keySet()) {
            stage.stats.put(id, new Stage.Stats());
        }

        List<Net.Member> members = new ArrayList<>();
        for (Stage.Slot slot : stage.slots.values()) {
            Entity e = level.getEntity(slot.entity);
            if (e != null) {
                boolean isPlayer = e instanceof ServerPlayer;
                String name = isPlayer ? ((ServerPlayer) e).getGameProfile().getName() : e.getDisplayName().getString();
                members.add(new Net.Member(e.getId(), name, slot.instrument.id(), isPlayer, e.getUUID().equals(stage.leader)));
            }
        }
        Net.GigStart msg = new Net.GigStart(stage.amp, stage.facing.get2DDataValue(), stage.song, stage.songTitle, 3000,
                stage.bpm, stage.offsetMs, stage.leadInMs, stage.center.x, stage.center.y, stage.center.z, members);
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(stage.center) < 72 * 72) {
                Net.toPlayer(p, msg);
            }
        }
        gatherCrowd(level, stage);
    }

    private static void place(ServerLevel level, Stage stage, Entity e, Instrument instrument, Vec3 pos, float yaw) {
        Vec3 target = pos;
        AABB box = e.getType().getDimensions().makeBoundingBox(target);
        if (!level.noCollision(e, box)) {
            target = e.position(); // no entra (pared, mueble): toca donde esta
        }
        stage.slots.put(e.getUUID(), new Stage.Slot(e.getUUID(), instrument, target, yaw));
        if (e instanceof ServerPlayer sp) {
            sp.teleportTo(level, target.x, target.y, target.z, yaw, 0);
        } else if (e instanceof Mob m) {
            m.getNavigation().stop();
            m.moveTo(target.x, target.y, target.z, yaw, 0);
            m.setYBodyRot(yaw);
            m.setYHeadRot(yaw);
        }
    }

    // ================================================================ durante el show

    public static void onPerformerUpdate(ServerPlayer player, Net.PerformerUpdate u) {
        Stage stage = stageOf(player);
        if (stage == null || stage.phase != Stage.Phase.PLAYING) {
            return;
        }
        Stage.Stats st = stage.stats.computeIfAbsent(player.getUUID(), k -> new Stage.Stats());
        st.score = u.score();
        st.combo = u.combo();
        st.bestCombo = Math.max(st.bestCombo, u.combo());
        st.misses = u.misses();
        stage.hype = Mth.clamp(stage.hype + u.newHits() * 0.0045F - u.newMisses() * 0.03F, 0, 1);
        Net.GigNotes notes = new Net.GigNotes(player.getId(), u.pressed(), u.hits(), u.fret());
        for (ServerPlayer p : player.serverLevel().players()) {
            if (p != player && p.distanceToSqr(player) < 64 * 64) {
                Net.toPlayer(p, notes);
            }
        }
    }

    public static void onPerformerResult(ServerPlayer player, Net.PerformerResult r) {
        Stage stage = stageOf(player);
        if (stage == null || !stage.playing()) {
            return;
        }
        Stage.Stats st = stage.stats.computeIfAbsent(player.getUUID(), k -> new Stage.Stats());
        st.finished = true;
        st.score = Math.max(st.score, r.score());
        st.bestCombo = Math.max(st.bestCombo, r.maxCombo());
        st.misses = r.misses();
        st.accuracy = r.accuracy();
        st.rank = r.rank();
        if (!r.completed()) {
            player.displayClientMessage(Component.translatable("message.starhero.not_completed").withStyle(ChatFormatting.GRAY), true);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || STAGES.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        for (Stage stage : new ArrayList<>(STAGES.values())) {
            ServerLevel level = level(server, stage);
            if (level == null || !level.getBlockState(stage.amp).is(StarHero.AMPLIFIER.get())) {
                STAGES.values().remove(stage);
                continue;
            }
            if (stage.playing()) {
                tickShow(server, level, stage);
            } else if (stage.players.isEmpty()) {
                STAGES.values().remove(stage);
            }
        }
    }

    private static void tickShow(MinecraftServer server, ServerLevel level, Stage stage) {
        long now = level.getGameTime();
        if (stage.phase == Stage.Phase.COUNTDOWN && now >= stage.startTick) {
            stage.phase = Stage.Phase.PLAYING;
        }
        // los aldeanos de la banda se quedan en su lugar, mirando al publico
        for (Stage.Slot slot : stage.slots.values()) {
            Entity e = level.getEntity(slot.entity);
            if (e instanceof Mob m && !(e instanceof Player)) {
                m.getNavigation().stop();
                if (m.position().distanceToSqr(slot.pos) > 0.04) {
                    m.moveTo(slot.pos.x, slot.pos.y, slot.pos.z, slot.yaw, 0);
                }
                m.setDeltaMovement(0, Math.min(0, m.getDeltaMovement().y), 0);
                m.setYRot(slot.yaw);
                m.setYBodyRot(slot.yaw);
                m.setYHeadRot(slot.yaw);
            }
        }
        // jugadores que se fueron o se desconectaron
        for (UUID id : stage.players.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            Stage.Stats st = stage.stats.get(id);
            if (st != null && !st.finished && (p == null || p.level() != level || p.distanceToSqr(stage.center) > 64 * 64)) {
                st.finished = true;
            }
        }

        if (stage.phase == Stage.Phase.PLAYING) {
            if (now % 10 == 0) {
                int score = 0;
                int combo = 0;
                for (Stage.Stats st : stage.stats.values()) {
                    score += st.score;
                    combo += st.combo;
                }
                Net.GigState msg = new Net.GigState(stage.hype, score, combo);
                for (ServerPlayer p : level.players()) {
                    if (p.distanceToSqr(stage.center) < 72 * 72) {
                        Net.toPlayer(p, msg);
                    }
                }
            }
            long beat = (long) Math.floor(stage.beat(now));
            if (beat != stage.lastBeat && beat >= 0) {
                stage.lastBeat = beat;
                onBeat(level, stage, beat);
            }
            boolean allDone = !stage.stats.isEmpty() && stage.stats.values().stream().allMatch(s -> s.finished);
            if (allDone || now - stage.startTick > 20 * 60 * 12) {
                finish(server, level, stage);
            }
        }
    }

    /** El publico: aldeanos cerca que no tocan. */
    private static List<LivingEntity> crowd(ServerLevel level, Stage stage) {
        AABB box = new AABB(stage.center, stage.center).inflate(26, 8, 26);
        return level.getEntitiesOfClass(LivingEntity.class, box, e -> isVillager(e) && !stage.slots.containsKey(e.getUUID()));
    }

    private static void gatherCrowd(ServerLevel level, Stage stage) {
        Vec3 f = stage.forward();
        Vec3 s = stage.side();
        for (LivingEntity e : crowd(level, stage)) {
            if (e instanceof Mob m && RANDOM.nextFloat() < 0.85F) {
                Vec3 spot = stage.center.add(f.scale(5 + RANDOM.nextDouble() * 4)).add(s.scale(RANDOM.nextDouble() * 9 - 4.5));
                m.getNavigation().moveTo(spot.x, spot.y, spot.z, 0.65);
            }
        }
    }

    private static void onBeat(ServerLevel level, Stage stage, long beat) {
        // notas musicales sobre la banda, de colores
        if (beat % 2 == 0) {
            for (Stage.Slot slot : stage.slots.values()) {
                if (RANDOM.nextFloat() < 0.5F) {
                    level.sendParticles(ParticleTypes.NOTE, slot.pos.x, slot.pos.y + 2.3, slot.pos.z, 0,
                            RANDOM.nextInt(25) / 24.0, 0, 0, 1);
                }
            }
        }
        for (LivingEntity e : crowd(level, stage)) {
            if (e instanceof Mob m) {
                if (beat % 4 == 0 && m.getNavigation().isDone()) {
                    m.getLookControl().setLookAt(stage.center.x, stage.center.y + 1.4, stage.center.z);
                    m.setYHeadRot((float) (Mth.atan2(stage.center.z - m.getZ(), stage.center.x - m.getX()) * Mth.RAD_TO_DEG) - 90);
                }
                // con el publico encendido, saltan al ritmo
                if (stage.hype > 0.62F && m.onGround() && RANDOM.nextFloat() < (stage.hype - 0.55F)) {
                    m.setDeltaMovement(m.getDeltaMovement().add(0, 0.36, 0));
                    m.hasImpulse = true;
                }
                if (stage.hype > 0.8F && RANDOM.nextFloat() < 0.08F) {
                    level.sendParticles(ParticleTypes.HAPPY_VILLAGER, m.getX(), m.getY() + 2.1, m.getZ(), 2, 0.3, 0.2, 0.3, 0);
                }
            }
        }
    }

    private static void finish(MinecraftServer server, ServerLevel level, Stage stage) {
        int total = 0;
        float accSum = 0;
        int accN = 0;
        int best = 0;
        for (Stage.Stats st : stage.stats.values()) {
            total += st.score;
            best = Math.max(best, st.bestCombo);
            if (st.accuracy >= 0) {
                accSum += st.accuracy > 1.5F ? st.accuracy / 100F : st.accuracy;
                accN++;
            }
        }
        float acc = accN > 0 ? accSum / accN : 0.5F;
        float hype = stage.hype;
        int tips = Mth.clamp(Math.round(acc * acc * 10 * (0.4F + hype)) + stage.slots.size() - 1, 0, 64);
        String headline = hype > 0.78F ? "message.starhero.end.crazy" : hype > 0.55F ? "message.starhero.end.good"
                : hype > 0.32F ? "message.starhero.end.meh" : "message.starhero.end.bad";
        String detail = String.format(Locale.ROOT, "%,d|%.1f|%d", total, acc * 100, best);

        for (UUID id : stage.stats.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null && tips > 0) {
                ItemStack stack = new ItemStack(Items.EMERALD, tips);
                if (!p.getInventory().add(stack)) {
                    p.drop(stack, false);
                }
            }
        }
        for (LivingEntity e : crowd(level, stage)) {
            level.sendParticles(hype > 0.5F ? ParticleTypes.HEART : ParticleTypes.ANGRY_VILLAGER,
                    e.getX(), e.getY() + 2.1, e.getZ(), hype > 0.5F ? 2 : 1, 0.3, 0.2, 0.3, 0);
        }
        Net.GigEnd msg = new Net.GigEnd(headline, detail, tips, hype);
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(stage.center) < 72 * 72) {
                Net.toPlayer(p, msg);
            }
        }
        stage.phase = Stage.Phase.LOBBY;
        stage.slots.clear();
        stage.stats.clear();
    }

    // ================================================================ aldeanos que se suman a la banda

    static boolean isVillager(Entity e) {
        if (e instanceof AbstractVillager) {
            return true;
        }
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        return id != null && "mca".equals(id.getNamespace()) && id.getPath().contains("villager") && !id.getPath().contains("zombie");
    }

    /** Solo los aldeanos de MCA tienen cuerpo de persona (brazos para tocar). */
    private static boolean canJoinBand(Entity e) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        return e instanceof Mob && id != null && "mca".equals(id.getNamespace()) && id.getPath().contains("villager")
                && !id.getPath().contains("zombie");
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof InstrumentItem item) || !canJoinBand(event.getTarget())) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Entity target = event.getTarget();
        BandData band = BandData.get(player.server);
        Instrument current = band.get(target.getUUID());
        ServerLevel level = player.serverLevel();
        if (player.isShiftKeyDown() && current != null) {
            band.set(target.getUUID(), null);
            player.displayClientMessage(Component.translatable("message.starhero.npc_left", target.getDisplayName()).withStyle(ChatFormatting.GRAY), false);
            level.sendParticles(ParticleTypes.SMOKE, target.getX(), target.getY() + 1.8, target.getZ(), 6, 0.2, 0.2, 0.2, 0.01);
            return;
        }
        band.set(target.getUUID(), item.instrument);
        player.displayClientMessage(Component.translatable("message.starhero.npc_joined", target.getDisplayName(),
                Component.translatable(item.instrument.roleKey())).withStyle(ChatFormatting.LIGHT_PURPLE), false);
        for (int i = 0; i < 5; i++) {
            level.sendParticles(ParticleTypes.NOTE, target.getX() + RANDOM.nextGaussian() * 0.3, target.getY() + 2.1,
                    target.getZ() + RANDOM.nextGaussian() * 0.3, 0, RANDOM.nextInt(25) / 24.0, 0, 0, 1);
        }
        for (Stage stage : STAGES.values()) {
            if (!stage.playing() && stage.amp.distSqr(target.blockPosition()) < 24 * 24) {
                broadcastLobby(player.server, stage);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            Stage stage = stageOf(p);
            if (stage != null) {
                leave(p, stage);
            }
        }
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("starhero")
                .then(Commands.literal("join").then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
                    Stage stage = find(p.serverLevel(), pos);
                    if (stage == null || !stage.bandMode || stage.playing()) {
                        p.displayClientMessage(Component.translatable("message.starhero.cant_join").withStyle(ChatFormatting.GRAY), true);
                        return 0;
                    }
                    onAmplifierUsed(p, pos, stage.facing);
                    return 1;
                })))
                .then(Commands.literal("leave").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    Stage stage = stageOf(p);
                    if (stage != null) {
                        leave(p, stage);
                    }
                    return 1;
                })));
    }
}
