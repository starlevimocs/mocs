package com.starlevi.starvfx.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.starlevi.starvfx.Geo;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * /starvfx: reproducir escenas, poner efectos en el mundo, zonas de camara fija y armar escenas desde adentro del
 * juego (uno se para donde quiere la camara y la anota en la linea de tiempo).
 */
public final class VfxCommands {
    public static final List<String> EFFECTS = List.of("leaf", "sky_crack", "ground_crack", "black_hole", "planet", "galaxy",
            "sky_rings", "shockwave", "pillar", "swarm", "glow");

    /** Sesion de edicion de cada admin: que escena esta armando y donde esta su origen. */
    private record Editing(String name, Vec3 origin) {
    }

    private static final Map<UUID, Editing> EDITING = new HashMap<>();
    private static final SuggestionProvider<CommandSourceStack> SCENES = (c, b) -> SharedSuggestionProvider.suggest(Scenes.list(), b);
    private static final SuggestionProvider<CommandSourceStack> TYPES = (c, b) -> SharedSuggestionProvider.suggest(EFFECTS, b);

    private VfxCommands() {
    }

    private static Component msg(String key, Object... args) {
        return Component.translatable("message.starvfx." + key, args);
    }

    private static int ok(CommandContext<CommandSourceStack> c, String key, Object... args) {
        c.getSource().sendSuccess(() -> msg(key, args), true);
        return 1;
    }

    private static int fail(CommandContext<CommandSourceStack> c, String key, Object... args) {
        c.getSource().sendFailure(msg(key, args));
        return 0;
    }

    /** El punto donde esta parado quien corre el comando, centrado en el bloque. */
    private static Vec3 here(CommandSourceStack s) {
        Vec3 p = s.getPosition();
        return new Vec3(Math.floor(p.x) + 0.5, Math.floor(p.y + 0.01), Math.floor(p.z) + 0.5);
    }

    private static <T> boolean has(CommandContext<CommandSourceStack> c, String name, Class<T> type) {
        try {
            c.getArgument(name, type);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static float f(CommandContext<CommandSourceStack> c, String name, float def) {
        return has(c, name, Float.class) ? FloatArgumentType.getFloat(c, name) : def;
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Float> num(String name) {
        return argument(name, FloatArgumentType.floatArg(0));
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(literal("starvfx").requires(s -> s.hasPermission(2))
                .then(literal("play").then(argument("scene", StringArgumentType.word()).suggests(SCENES)
                        .executes(c -> play(c, null, 0))
                        .then(argument("pos", Vec3Argument.vec3()).executes(c -> play(c, Vec3Argument.getVec3(c, "pos"), 0))
                                .then(argument("rot", FloatArgumentType.floatArg(-360, 360))
                                        .executes(c -> play(c, Vec3Argument.getVec3(c, "pos"), FloatArgumentType.getFloat(c, "rot")))))))
                .then(literal("stop").executes(c -> ok(c, "stopped", Director.stopAll())))
                .then(literal("list").executes(c -> ok(c, "scenes", String.join(", ", Scenes.list()))))
                .then(literal("reload").executes(c -> {
                    Lifecycle.reload();
                    return ok(c, "reloaded");
                }))
                // ---- cosas sueltas en pantalla
                .then(literal("text").then(num("seconds").then(argument("text", StringArgumentType.greedyString()).executes(c -> {
                    JsonObject e = event(0, "text");
                    String[] parts = StringArgumentType.getString(c, "text").split("\\|", 2);
                    e.addProperty("text", parts[0].trim());
                    if (parts.length > 1) {
                        e.addProperty("sub", parts[1].trim());
                    }
                    e.addProperty("duration", f(c, "seconds", 4));
                    Director.quick(c.getSource().getLevel(), e, f(c, "seconds", 4) + 1);
                    return 1;
                }))))
                .then(literal("shake").then(num("strength").then(num("seconds").executes(c -> {
                    JsonObject e = event(0, "shake");
                    e.addProperty("strength", f(c, "strength", 1));
                    e.addProperty("duration", f(c, "seconds", 2));
                    Director.quick(c.getSource().getLevel(), e, f(c, "seconds", 2) + 0.5F);
                    return 1;
                }))))
                .then(literal("fade").then(num("in").then(num("hold").then(num("out").executes(c -> fade(c, "#000000"))
                        .then(argument("color", StringArgumentType.word()).executes(c -> fade(c, StringArgumentType.getString(c, "color"))))))))
                // ---- efectos del mundo
                .then(literal("fx")
                        .then(literal("add").then(argument("id", StringArgumentType.word()).then(argument("type", StringArgumentType.word()).suggests(TYPES)
                                .executes(c -> fxAdd(c, null))
                                .then(argument("pos", Vec3Argument.vec3()).executes(c -> fxAdd(c, Vec3Argument.getVec3(c, "pos")))
                                        .then(num("size").executes(c -> fxAdd(c, Vec3Argument.getVec3(c, "pos")))
                                                .then(num("seconds").executes(c -> fxAdd(c, Vec3Argument.getVec3(c, "pos")))
                                                        .then(argument("color", StringArgumentType.word())
                                                                .executes(c -> fxAdd(c, Vec3Argument.getVec3(c, "pos"))))))))))
                        .then(literal("remove").then(argument("id", StringArgumentType.word()).executes(c -> {
                            String id = StringArgumentType.getString(c, "id");
                            ServerLevel level = c.getSource().getLevel();
                            boolean removed = WorldData.get(level.getServer()).removeEffect(level, id);
                            com.starlevi.starvfx.net.Net.toLevel(level, new com.starlevi.starvfx.net.Net.Fx(id, true, "", 0, 0, 0, "{}", 0));
                            return removed ? ok(c, "fx_removed", id) : fail(c, "fx_missing", id);
                        })))
                        .then(literal("clear").executes(c -> {
                            ServerLevel level = c.getSource().getLevel();
                            int n = WorldData.get(level.getServer()).clearEffects(level);
                            com.starlevi.starvfx.net.Net.toLevel(level, new com.starlevi.starvfx.net.Net.Fx("*", true, "", 0, 0, 0, "{}", 0));
                            return ok(c, "fx_cleared", n);
                        }))
                        .then(literal("list").executes(c -> {
                            StringBuilder sb = new StringBuilder();
                            for (JsonElement e : WorldData.get(c.getSource().getServer()).effects()) {
                                JsonObject o = e.getAsJsonObject();
                                sb.append(sb.length() > 0 ? ", " : "").append(Geo.str(o, "id", "?")).append(" (").append(Geo.str(o, "type", "?")).append(")");
                            }
                            return ok(c, "fx_list", sb.length() == 0 ? "-" : sb.toString());
                        })))
                // ---- zonas de camara fija
                .then(literal("zone")
                        .then(literal("add").then(argument("name", StringArgumentType.word())
                                .then(argument("from", Vec3Argument.vec3()).then(argument("to", Vec3Argument.vec3())
                                        .executes(c -> zoneAdd(c, true))
                                        .then(argument("follow", BoolArgumentType.bool()).executes(c -> zoneAdd(c, BoolArgumentType.getBool(c, "follow"))))))))
                        .then(literal("remove").then(argument("name", StringArgumentType.word()).executes(c -> {
                            String name = StringArgumentType.getString(c, "name");
                            WorldData data = WorldData.get(c.getSource().getServer());
                            if (!data.removeZone(c.getSource().getLevel(), name)) {
                                return fail(c, "zone_missing", name);
                            }
                            data.syncZones(c.getSource().getServer());
                            return ok(c, "zone_removed", name);
                        })))
                        .then(literal("list").executes(c -> {
                            StringBuilder sb = new StringBuilder();
                            for (JsonElement e : WorldData.get(c.getSource().getServer()).zones()) {
                                sb.append(sb.length() > 0 ? ", " : "").append(Geo.str(e.getAsJsonObject(), "name", "?"));
                            }
                            return ok(c, "zone_list", sb.length() == 0 ? "-" : sb.toString());
                        })))
                // ---- animaciones de jugador (para probar o para la historia)
                .then(literal("anim").then(argument("player", EntityArgument.players())
                        .then(literal("sprout").executes(c -> {
                            for (ServerPlayer p : EntityArgument.getPlayers(c, "player")) {
                                Lifecycle.sprout(p, 3.2F);
                            }
                            return 1;
                        }))
                        .then(literal("afk").executes(c -> {
                            for (ServerPlayer p : EntityArgument.getPlayers(c, "player")) {
                                Lifecycle.setAfk(p, true);
                            }
                            return 1;
                        }))
                        .then(literal("none").executes(c -> {
                            for (ServerPlayer p : EntityArgument.getPlayers(c, "player")) {
                                Lifecycle.setAfk(p, false);
                                com.starlevi.starvfx.net.Net.toLevel(p.serverLevel(), new com.starlevi.starvfx.net.Net.PlayerAnim(p.getId(), "none", 0, 0));
                            }
                            return 1;
                        }))))
                // ---- armar escenas
                .then(literal("scene")
                        .then(literal("new").then(argument("name", StringArgumentType.word()).then(num("seconds").executes(VfxCommands::sceneNew))))
                        .then(literal("edit").then(argument("name", StringArgumentType.word()).suggests(SCENES).executes(VfxCommands::sceneEdit)))
                        .then(literal("cam").then(num("t").executes(c -> sceneCam(c, false))
                                .then(num("fov").executes(c -> sceneCam(c, false))
                                        .then(literal("cut").executes(c -> sceneCam(c, true))))
                                .then(literal("cut").executes(c -> sceneCam(c, true)))))
                        .then(literal("fx").then(num("t").then(argument("type", StringArgumentType.word()).suggests(TYPES)
                                .executes(VfxCommands::sceneFx)
                                .then(num("seconds").executes(VfxCommands::sceneFx)
                                        .then(num("size").executes(VfxCommands::sceneFx)
                                                .then(argument("color", StringArgumentType.word()).executes(VfxCommands::sceneFx)))))))
                        .then(literal("text").then(num("t").then(num("seconds").then(argument("text", StringArgumentType.greedyString()).executes(c -> {
                            JsonObject e = event(f(c, "t", 0), "text");
                            String[] parts = StringArgumentType.getString(c, "text").split("\\|", 2);
                            e.addProperty("text", parts[0].trim());
                            if (parts.length > 1) {
                                e.addProperty("sub", parts[1].trim());
                            }
                            e.addProperty("duration", f(c, "seconds", 4));
                            return sceneAdd(c, "events", e);
                        })))))
                        .then(literal("fade").then(num("t").then(num("in").then(num("hold").then(num("out").executes(c -> {
                            JsonObject e = event(f(c, "t", 0), "fade");
                            e.addProperty("in", f(c, "in", 1));
                            e.addProperty("hold", f(c, "hold", 0.5F));
                            e.addProperty("out", f(c, "out", 1));
                            e.addProperty("color", "#000000");
                            return sceneAdd(c, "events", e);
                        }))))))
                        .then(literal("shake").then(num("t").then(num("strength").then(num("seconds").executes(c -> {
                            JsonObject e = event(f(c, "t", 0), "shake");
                            e.addProperty("strength", f(c, "strength", 1));
                            e.addProperty("duration", f(c, "seconds", 2));
                            return sceneAdd(c, "events", e);
                        })))))
                        .then(literal("sound").then(num("t").then(argument("sound", net.minecraft.commands.arguments.ResourceLocationArgument.id())
                                .suggests(net.minecraft.commands.synchronization.SuggestionProviders.AVAILABLE_SOUNDS).executes(c -> {
                                    JsonObject e = event(f(c, "t", 0), "sound");
                                    e.addProperty("sound", net.minecraft.commands.arguments.ResourceLocationArgument.getId(c, "sound").toString());
                                    return sceneAdd(c, "events", e);
                                }))))
                        .then(literal("command").then(num("t").then(argument("command", StringArgumentType.greedyString()).executes(c -> {
                            JsonObject e = event(f(c, "t", 0), "command");
                            e.addProperty("command", StringArgumentType.getString(c, "command"));
                            return sceneAdd(c, "events", e);
                        }))))
                        .then(literal("sprout").then(num("t").executes(c -> {
                            JsonObject e = event(f(c, "t", 0), "sprout");
                            e.addProperty("duration", 4);
                            e.addProperty("radius", 5);
                            return sceneAdd(c, "events", e);
                        })))
                        .then(literal("set").then(argument("option", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("lock", "hide_hud", "letterbox"), b))
                                .then(argument("value", BoolArgumentType.bool()).executes(c -> {
                                    Editing ed = editing(c);
                                    JsonObject scene = ed == null ? null : Scenes.load(ed.name);
                                    if (scene == null) {
                                        return fail(c, "not_editing");
                                    }
                                    scene.addProperty(StringArgumentType.getString(c, "option"), BoolArgumentType.getBool(c, "value"));
                                    Scenes.save(ed.name, scene);
                                    return ok(c, "scene_saved", ed.name);
                                }))))
                        .then(literal("undo").executes(VfxCommands::sceneUndo))
                        .then(literal("test").executes(c -> {
                            Editing ed = editing(c);
                            JsonObject scene = ed == null ? null : Scenes.load(ed.name);
                            if (scene == null) {
                                return fail(c, "not_editing");
                            }
                            Director.play(c.getSource().getLevel(), scene, ed.origin, 0);
                            return 1;
                        }))));
    }

    private static JsonObject event(float t, String type) {
        JsonObject e = new JsonObject();
        e.addProperty("t", Geo.round(t));
        e.addProperty("type", type);
        return e;
    }

    private static int play(CommandContext<CommandSourceStack> c, Vec3 pos, float rot) {
        String name = StringArgumentType.getString(c, "scene");
        JsonObject scene = Scenes.load(name);
        if (scene == null) {
            return fail(c, "no_scene", name);
        }
        Vec3 origin = pos != null ? pos : here(c.getSource());
        float seconds = Director.play(c.getSource().getLevel(), scene, origin, rot);
        return ok(c, "playing", name, String.format("%.0f", seconds));
    }

    private static int fade(CommandContext<CommandSourceStack> c, String color) {
        JsonObject e = event(0, "fade");
        e.addProperty("in", f(c, "in", 1));
        e.addProperty("hold", f(c, "hold", 1));
        e.addProperty("out", f(c, "out", 1));
        e.addProperty("color", color);
        Director.quick(c.getSource().getLevel(), e, f(c, "in", 1) + f(c, "hold", 1) + f(c, "out", 1) + 0.5F);
        return 1;
    }

    private static int fxAdd(CommandContext<CommandSourceStack> c, Vec3 pos) {
        String id = StringArgumentType.getString(c, "id");
        String type = StringArgumentType.getString(c, "type");
        if (!EFFECTS.contains(type)) {
            return fail(c, "no_effect", type);
        }
        Vec3 at = pos != null ? pos : here(c.getSource());
        JsonObject params = new JsonObject();
        float seconds = f(c, "seconds", 0);
        params.addProperty("duration", seconds);
        if (has(c, "size", Float.class)) {
            params.addProperty("size", f(c, "size", 10));
        }
        if (has(c, "color", String.class)) {
            params.addProperty("color", StringArgumentType.getString(c, "color"));
        }
        ServerLevel level = c.getSource().getLevel();
        if (seconds <= 0) {
            // sin duracion = queda puesto en el mundo hasta que lo saquen
            WorldData.get(level.getServer()).addEffect(level, id, type, at.x, at.y, at.z, params);
        }
        com.starlevi.starvfx.net.Net.toLevel(level, new com.starlevi.starvfx.net.Net.Fx(id, false, type, at.x, at.y, at.z, params.toString(), 0));
        return ok(c, seconds <= 0 ? "fx_added" : "fx_played", id, type);
    }

    private static int zoneAdd(CommandContext<CommandSourceStack> c, boolean follow) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(c, "name");
        Vec3 a = Vec3Argument.getVec3(c, "from");
        Vec3 b = Vec3Argument.getVec3(c, "to");
        JsonObject z = new JsonObject();
        z.addProperty("name", name);
        JsonArray box = new JsonArray();
        box.add(Math.floor(Math.min(a.x, b.x)));
        box.add(Math.floor(Math.min(a.y, b.y)));
        box.add(Math.floor(Math.min(a.z, b.z)));
        box.add(Math.floor(Math.max(a.x, b.x)) + 1);
        box.add(Math.floor(Math.max(a.y, b.y)) + 1);
        box.add(Math.floor(Math.max(a.z, b.z)) + 1);
        z.add("box", box);
        // la camara queda donde esta parado (y mirando) quien crea la zona
        Vec3 eye = player.getEyePosition();
        z.add("cam", Geo.arr(eye.x, eye.y, eye.z));
        z.addProperty("yaw", Geo.round(player.getYRot()));
        z.addProperty("pitch", Geo.round(player.getXRot()));
        z.addProperty("follow", follow);
        WorldData data = WorldData.get(c.getSource().getServer());
        data.addZone(c.getSource().getLevel(), z);
        data.syncZones(c.getSource().getServer());
        return ok(c, "zone_added", name);
    }

    // ---------------------------------------------------------------- armado de escenas

    private static Editing editing(CommandContext<CommandSourceStack> c) {
        return c.getSource().getEntity() == null ? null : EDITING.get(c.getSource().getEntity().getUUID());
    }

    private static int sceneNew(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(c, "name");
        if (!Scenes.validName(name)) {
            return fail(c, "bad_name");
        }
        JsonObject scene = new JsonObject();
        scene.addProperty("duration", f(c, "seconds", 10));
        scene.addProperty("lock", true);
        scene.addProperty("hide_hud", true);
        scene.addProperty("letterbox", true);
        scene.add("camera", new JsonArray());
        scene.add("events", new JsonArray());
        Scenes.save(name, scene);
        EDITING.put(player.getUUID(), new Editing(name, here(c.getSource())));
        return ok(c, "scene_new", name);
    }

    private static int sceneEdit(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(c, "name");
        JsonObject scene = Scenes.load(name);
        if (scene == null) {
            return fail(c, "no_scene", name);
        }
        // si es una de las que trae el mod, se copia a la carpeta de config para poder tocarla
        Scenes.save(name, scene);
        EDITING.put(player.getUUID(), new Editing(name, here(c.getSource())));
        return ok(c, "scene_edit", name);
    }

    private static int sceneAdd(CommandContext<CommandSourceStack> c, String list, JsonObject entry) {
        Editing ed = editing(c);
        JsonObject scene = ed == null ? null : Scenes.load(ed.name);
        if (scene == null) {
            return fail(c, "not_editing");
        }
        JsonArray arr = scene.has(list) && scene.get(list).isJsonArray() ? scene.getAsJsonArray(list) : new JsonArray();
        // ordenado por tiempo
        float t = Geo.num(entry, "t", 0);
        JsonArray sorted = new JsonArray();
        boolean placed = false;
        for (JsonElement e : arr) {
            if (!placed && e.isJsonObject() && Geo.num(e.getAsJsonObject(), "t", 0) > t) {
                sorted.add(entry);
                placed = true;
            }
            sorted.add(e);
        }
        if (!placed) {
            sorted.add(entry);
        }
        scene.add(list, sorted);
        scene.addProperty("_last", list + ":" + entry);
        Scenes.save(ed.name, scene);
        return ok(c, "scene_added", Geo.str(entry, "type", "cam"), String.valueOf(Geo.round(t)));
    }

    private static int sceneCam(CommandContext<CommandSourceStack> c, boolean cut) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        Editing ed = editing(c);
        if (ed == null) {
            return fail(c, "not_editing");
        }
        Vec3 local = player.getEyePosition().subtract(ed.origin);
        JsonObject k = new JsonObject();
        k.addProperty("t", Geo.round(f(c, "t", 0)));
        k.add("pos", Geo.arr(local.x, local.y, local.z));
        k.addProperty("yaw", Geo.round(player.getYRot()));
        k.addProperty("pitch", Geo.round(player.getXRot()));
        if (has(c, "fov", Float.class)) {
            k.addProperty("fov", f(c, "fov", 70));
        }
        if (cut) {
            k.addProperty("cut", true);
        }
        return sceneAdd(c, "camera", k);
    }

    private static int sceneFx(CommandContext<CommandSourceStack> c) {
        Editing ed = editing(c);
        if (ed == null) {
            return fail(c, "not_editing");
        }
        String type = StringArgumentType.getString(c, "type");
        if (!EFFECTS.contains(type)) {
            return fail(c, "no_effect", type);
        }
        Vec3 local = here(c.getSource()).subtract(ed.origin);
        JsonObject e = event(f(c, "t", 0), "effect");
        e.addProperty("effect", type);
        e.add("pos", Geo.arr(local.x, local.y, local.z));
        e.addProperty("duration", f(c, "seconds", 6));
        if (has(c, "size", Float.class)) {
            e.addProperty("size", f(c, "size", 10));
        }
        if (has(c, "color", String.class)) {
            e.addProperty("color", StringArgumentType.getString(c, "color"));
        }
        return sceneAdd(c, "events", e);
    }

    private static int sceneUndo(CommandContext<CommandSourceStack> c) {
        Editing ed = editing(c);
        JsonObject scene = ed == null ? null : Scenes.load(ed.name);
        if (scene == null) {
            return fail(c, "not_editing");
        }
        String last = Geo.str(scene, "_last", "");
        int sep = last.indexOf(':');
        if (sep < 0) {
            return fail(c, "nothing_to_undo");
        }
        String list = last.substring(0, sep);
        String entry = last.substring(sep + 1);
        JsonArray arr = scene.has(list) ? scene.getAsJsonArray(list) : new JsonArray();
        for (int i = arr.size() - 1; i >= 0; i--) {
            if (arr.get(i).toString().equals(entry)) {
                arr.remove(i);
                break;
            }
        }
        scene.remove("_last");
        Scenes.save(ed.name, scene);
        return ok(c, "scene_undone");
    }
}
