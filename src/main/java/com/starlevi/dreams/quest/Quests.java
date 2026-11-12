package com.starlevi.dreams.quest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.starlevi.dreams.DreamsMod;
import com.starlevi.dreams.network.DreamsNetwork;
import com.starlevi.dreams.network.SyncQuestsPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Las misiones. Cada una es un json (las de ejemplo vienen en el mod; las propias van en
 * {@code config/dreams/quests/<id>.json} y pisan a las de ejemplo con el mismo nombre).
 * <p>
 * Una mision es personal (cada jugador la hace y la entrega) o de comunidad (el aporte de todos suma a la misma
 * meta y se completa sola al llegar). Objetivos: traer items, matar criaturas, llegar a un lugar.
 */
@Mod.EventBusSubscriber(modid = DreamsMod.MOD_ID)
public final class Quests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<String> BUILTIN = List.of("primera_luz", "raices_dormidas");
    private static final Map<String, JsonObject> DEFS = new LinkedHashMap<>();
    private static final Map<UUID, String> LAST_SENT = new HashMap<>();
    private static boolean loaded;

    private Quests() {
    }

    // ================================================================ definiciones

    public static Path dir() {
        return FMLPaths.CONFIGDIR.get().resolve("dreams").resolve("quests");
    }

    public static synchronized void reload() {
        DEFS.clear();
        for (String id : BUILTIN) {
            try (InputStream in = DreamsMod.class.getResourceAsStream("/data/dreams/quests/" + id + ".json")) {
                if (in != null) {
                    DEFS.put(id, JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject());
                }
            } catch (IOException | RuntimeException e) {
                LOGGER.warn("[Dreams] No se pudo leer la mision de ejemplo {}", id, e);
            }
        }
        try {
            Files.createDirectories(dir());
            try (Stream<Path> files = Files.list(dir())) {
                for (Path f : files.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList()) {
                    String id = f.getFileName().toString().replace(".json", "");
                    try {
                        DEFS.put(id, JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject());
                    } catch (RuntimeException e) {
                        LOGGER.warn("[Dreams] La mision {} esta mal escrita: {}", id, e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.warn("[Dreams] No se pudo leer la carpeta de misiones", e);
        }
        loaded = true;
        LAST_SENT.clear();
        LOGGER.info("[Dreams] {} mision(es) cargadas.", DEFS.size());
    }

    public static Map<String, JsonObject> defs() {
        if (!loaded) {
            reload();
        }
        return DEFS;
    }

    private static String str(JsonObject o, String k, String def) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : def;
    }

    private static int num(JsonObject o, String k, int def) {
        try {
            return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsInt() : def;
        } catch (RuntimeException e) {
            return def;
        }
    }

    private static boolean community(JsonObject def) {
        return str(def, "scope", "personal").equals("community");
    }

    private static JsonArray objectives(JsonObject def) {
        return def.has("objectives") && def.get("objectives").isJsonArray() ? def.getAsJsonArray("objectives") : new JsonArray();
    }

    private static String title(JsonObject def, String id) {
        return str(def, "title", id);
    }

    // ================================================================ estado

    private static Item item(JsonObject objective) {
        ResourceLocation id = ResourceLocation.tryParse(str(objective, "item", ""));
        return id == null ? null : ForgeRegistries.ITEMS.getValue(id);
    }

    private static int countItem(ServerPlayer player, Item item) {
        if (item == null) {
            return 0;
        }
        int n = 0;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static int takeItem(ServerPlayer player, Item item, int max) {
        int taken = 0;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize() && taken < max; i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item)) {
                int n = Math.min(s.getCount(), max - taken);
                s.shrink(n);
                taken += n;
            }
        }
        return taken;
    }

    /** Cuanto lleva de ese objetivo: para items de una mision personal, lo que tiene encima ahora. */
    private static int have(ServerPlayer player, JsonObject def, JsonObject state, int index) {
        JsonObject o = objectives(def).get(index).getAsJsonObject();
        int need = Math.max(1, num(o, "count", 1));
        if (!community(def) && str(o, "type", "").equals("item")) {
            return Math.min(need, countItem(player, item(o)));
        }
        return Math.min(need, QuestData.progress(state, index));
    }

    private static boolean allMet(ServerPlayer player, JsonObject def, JsonObject state) {
        JsonArray obj = objectives(def);
        for (int i = 0; i < obj.size(); i++) {
            if (have(player, def, state, i) < Math.max(1, num(obj.get(i).getAsJsonObject(), "count", 1))) {
                return false;
            }
        }
        return true;
    }

    private static JsonObject state(ServerPlayer player, String id, JsonObject def, boolean create) {
        QuestData data = QuestData.get(player.server);
        return community(def) ? data.shared(id, create) : data.personal(player.getUUID(), id, create);
    }

    private static boolean isDone(ServerPlayer player, String id) {
        JsonObject def = defs().get(id);
        return def != null && QuestData.flag(state(player, id, def, false), "done");
    }

    /** "locked", "available", "active", "ready" o "done". */
    public static String stateOf(ServerPlayer player, String id) {
        JsonObject def = defs().get(id);
        if (def == null) {
            return "locked";
        }
        JsonObject st = state(player, id, def, false);
        if (QuestData.flag(st, "done") && !repeatReady(def, st)) {
            return "done";
        }
        if (QuestData.flag(st, "active")) {
            return !community(def) && allMet(player, def, st) ? "ready" : "active";
        }
        if (def.has("requires") && def.get("requires").isJsonArray()) {
            for (JsonElement r : def.getAsJsonArray("requires")) {
                if (!isDone(player, r.getAsString())) {
                    return "locked";
                }
            }
        }
        return "available";
    }

    /** Oculta: no se anuncia ni se muestra; sirve de marca ("ya hablo con el arbol") o de favor que se repite. */
    private static boolean hidden(JsonObject def) {
        return def.has("hidden") && def.get("hidden").getAsBoolean();
    }

    /** Una mision con "repeat_minutes" vuelve a estar disponible pasado ese tiempo. */
    private static boolean repeatReady(JsonObject def, JsonObject st) {
        int minutes = num(def, "repeat_minutes", 0);
        if (minutes <= 0 || st == null || !st.has("done_at")) {
            return false;
        }
        return System.currentTimeMillis() - st.get("done_at").getAsLong() >= minutes * 60_000L;
    }

    // ================================================================ acciones

    private static void tell(ServerPlayer player, String key, ChatFormatting color, SoundEvent sound, Object... args) {
        player.displayClientMessage(Component.translatable("message.dreams." + key, args).withStyle(color), false);
        if (sound != null) {
            player.playNotifySound(sound, SoundSource.PLAYERS, 0.8F, 1);
        }
    }

    private static void run(MinecraftServer server, ServerPlayer player, JsonObject holder, String key) {
        if (!holder.has(key) || !holder.get(key).isJsonArray()) {
            return;
        }
        for (JsonElement e : holder.getAsJsonArray(key)) {
            String command = e.getAsString().replace("%player%", player.getGameProfile().getName());
            try {
                CommandSourceStack source = server.createCommandSourceStack().withLevel(player.serverLevel()).withPosition(player.position())
                        .withPermission(2).withSuppressedOutput();
                server.getCommands().performPrefixedCommand(source, command);
            } catch (RuntimeException ex) {
                LOGGER.warn("[Dreams] Fallo el comando de la mision: {}", command, ex);
            }
        }
    }

    private static void reward(ServerPlayer player, JsonObject def) {
        if (!def.has("rewards") || !def.get("rewards").isJsonObject()) {
            return;
        }
        JsonObject r = def.getAsJsonObject("rewards");
        int xp = num(r, "xp", 0);
        if (xp > 0) {
            player.giveExperiencePoints(xp);
        }
        if (r.has("items") && r.get("items").isJsonArray()) {
            for (JsonElement e : r.getAsJsonArray("items")) {
                Item item = item(e.getAsJsonObject());
                if (item != null) {
                    ItemStack stack = new ItemStack(item, Math.max(1, num(e.getAsJsonObject(), "count", 1)));
                    if (!player.getInventory().add(stack)) {
                        player.drop(stack, false);
                    }
                }
            }
        }
        run(player.server, player, r, "commands");
    }

    public static boolean accept(ServerPlayer player, String id) {
        JsonObject def = defs().get(id);
        if (def == null || !stateOf(player, id).equals("available")) {
            return false;
        }
        JsonObject fresh = state(player, id, def, true);
        fresh.addProperty("done", false);
        if (objectives(def).isEmpty()) {
            // sin objetivos: se cumple en el acto
            complete(player, id, def);
            return true;
        }
        fresh.addProperty("active", true);
        QuestData.get(player.server).setDirty();
        if (hidden(def)) {
            run(player.server, player, def, "on_accept");
            return true;
        }
        if (community(def)) {
            for (ServerPlayer p : player.server.getPlayerList().getPlayers()) {
                tell(p, "quest_community_started", ChatFormatting.AQUA, SoundEvents.AMETHYST_BLOCK_CHIME, title(def, id));
            }
        } else {
            tell(player, "quest_accepted", ChatFormatting.AQUA, SoundEvents.AMETHYST_BLOCK_CHIME, title(def, id));
        }
        run(player.server, player, def, "on_accept");
        return true;
    }

    private static void complete(ServerPlayer player, String id, JsonObject def) {
        JsonObject st = state(player, id, def, true);
        st.addProperty("active", false);
        st.addProperty("done", true);
        st.addProperty("done_at", System.currentTimeMillis());
        QuestData.get(player.server).setDirty();
        if (hidden(def)) {
            reward(player, def);
        } else if (community(def)) {
            for (ServerPlayer p : player.server.getPlayerList().getPlayers()) {
                tell(p, "quest_community_done", ChatFormatting.GOLD, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, title(def, id));
                reward(p, def);
            }
        } else {
            tell(player, "quest_done", ChatFormatting.GOLD, SoundEvents.PLAYER_LEVELUP, title(def, id));
            reward(player, def);
        }
        run(player.server, player, def, "on_complete");
    }

    /** Entregar una mision personal: se cobran los items y se dan las recompensas. */
    public static boolean turnIn(ServerPlayer player, String id) {
        JsonObject def = defs().get(id);
        if (def == null || community(def) || !stateOf(player, id).equals("ready")) {
            return false;
        }
        JsonArray obj = objectives(def);
        for (JsonElement e : obj) {
            JsonObject o = e.getAsJsonObject();
            if (str(o, "type", "").equals("item") && (!o.has("consume") || o.get("consume").getAsBoolean())) {
                takeItem(player, item(o), Math.max(1, num(o, "count", 1)));
            }
        }
        complete(player, id, def);
        return true;
    }

    /** Aportar a una mision de comunidad lo que el jugador tenga encima. */
    public static boolean contribute(ServerPlayer player, String id) {
        JsonObject def = defs().get(id);
        if (def == null || !community(def) || !stateOf(player, id).equals("active")) {
            return false;
        }
        JsonObject st = state(player, id, def, true);
        JsonArray obj = objectives(def);
        int given = 0;
        for (int i = 0; i < obj.size(); i++) {
            JsonObject o = obj.get(i).getAsJsonObject();
            if (!str(o, "type", "").equals("item")) {
                continue;
            }
            int need = Math.max(1, num(o, "count", 1)), have = QuestData.progress(st, i);
            int taken = takeItem(player, item(o), need - have);
            if (taken > 0) {
                QuestData.setProgress(st, i, have + taken);
                given += taken;
            }
        }
        QuestData.get(player.server).setDirty();
        if (given > 0) {
            tell(player, "quest_contributed", ChatFormatting.GREEN, SoundEvents.EXPERIENCE_ORB_PICKUP, given, title(def, id));
        } else {
            tell(player, "quest_nothing", ChatFormatting.GRAY, null, title(def, id));
        }
        if (allMet(player, def, st)) {
            complete(player, id, def);
        }
        return given > 0;
    }

    /** Suma avance a un objetivo que no es de items (matar, llegar). */
    private static void advance(ServerPlayer player, String id, JsonObject def, int index, int amount) {
        JsonObject st = state(player, id, def, false);
        if (!QuestData.flag(st, "active")) {
            return;
        }
        int need = Math.max(1, num(objectives(def).get(index).getAsJsonObject(), "count", 1));
        int have = QuestData.progress(st, index);
        if (have >= need) {
            return;
        }
        QuestData.setProgress(st, index, Math.min(need, have + amount));
        QuestData.get(player.server).setDirty();
        if (community(def) && allMet(player, def, st)) {
            complete(player, id, def);
        } else if (!community(def) && allMet(player, def, st)) {
            tell(player, "quest_ready", ChatFormatting.GREEN, SoundEvents.NOTE_BLOCK_CHIME.get(), title(def, id));
        }
    }

    // ---- para los comandos de admin
    public static void forceComplete(ServerPlayer player, String id) {
        JsonObject def = defs().get(id);
        if (def != null) {
            complete(player, id, def);
        }
    }

    public static void reset(ServerPlayer player, String id) {
        JsonObject def = defs().get(id);
        if (def == null) {
            return;
        }
        if (community(def)) {
            QuestData.get(player.server).resetShared(id);
        } else {
            QuestData.get(player.server).resetPersonal(player.getUUID(), id);
        }
    }

    // ================================================================ eventos

    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ResourceLocation killed = ForgeRegistries.ENTITY_TYPES.getKey(event.getEntity().getType());
        if (killed == null) {
            return;
        }
        for (Map.Entry<String, JsonObject> en : defs().entrySet()) {
            JsonArray obj = objectives(en.getValue());
            for (int i = 0; i < obj.size(); i++) {
                JsonObject o = obj.get(i).getAsJsonObject();
                if (str(o, "type", "").equals("kill") && killed.toString().equals(str(o, "entity", ""))) {
                    advance(player, en.getKey(), en.getValue(), i, 1);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % 20 != 0) {
            return;
        }
        MinecraftServer server = event.getServer();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            // llegar a un lugar
            for (Map.Entry<String, JsonObject> en : defs().entrySet()) {
                JsonArray obj = objectives(en.getValue());
                for (int i = 0; i < obj.size(); i++) {
                    JsonObject o = obj.get(i).getAsJsonObject();
                    if (!str(o, "type", "").equals("visit") || !o.has("pos") || !o.get("pos").isJsonArray() || o.getAsJsonArray("pos").size() < 3) {
                        continue;
                    }
                    String dim = str(o, "dimension", "");
                    if (!dim.isEmpty() && !dim.equals(player.serverLevel().dimension().location().toString())) {
                        continue;
                    }
                    JsonArray p = o.getAsJsonArray("pos");
                    double r = Math.max(1, num(o, "radius", 8));
                    if (player.distanceToSqr(p.get(0).getAsDouble(), p.get(1).getAsDouble(), p.get(2).getAsDouble()) <= r * r) {
                        advance(player, en.getKey(), en.getValue(), i, 1);
                    }
                }
            }
            sync(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_SENT.remove(event.getEntity().getUUID());
    }

    /** Le manda al jugador sus misiones si algo cambio desde la ultima vez. */
    public static void sync(ServerPlayer player) {
        JsonArray list = new JsonArray();
        for (Map.Entry<String, JsonObject> en : defs().entrySet()) {
            JsonObject def = en.getValue();
            String id = en.getKey();
            JsonObject q = new JsonObject();
            q.addProperty("id", id);
            q.addProperty("title", title(def, id));
            q.addProperty("description", str(def, "description", ""));
            q.addProperty("community", community(def));
            q.addProperty("state", stateOf(player, id));
            JsonObject st = state(player, id, def, false);
            JsonArray objs = new JsonArray();
            JsonArray defObjs = objectives(def);
            for (int i = 0; i < defObjs.size(); i++) {
                JsonObject o = defObjs.get(i).getAsJsonObject();
                JsonObject out = new JsonObject();
                out.addProperty("type", str(o, "type", ""));
                out.addProperty("target", str(o, "item", str(o, "entity", "")));
                out.addProperty("label", str(o, "label", ""));
                out.addProperty("need", Math.max(1, num(o, "count", 1)));
                out.addProperty("have", have(player, def, st, i));
                objs.add(out);
            }
            q.add("objectives", objs);
            list.add(q);
        }
        String json = list.toString();
        if (!json.equals(LAST_SENT.get(player.getUUID()))) {
            LAST_SENT.put(player.getUUID(), json);
            DreamsNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player), new SyncQuestsPacket(json));
        }
    }
}
