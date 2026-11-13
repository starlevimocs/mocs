package com.starlevi.starnpcs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

/**
 * Archivos de config/starnpcs del servidor (modelos, skins, emotes y sonidos). Se mandan a cada jugador al entrar,
 * asi solo hace falta ponerlos en el servidor.
 */
public final class Assets {
    private static final int CHUNK = 30000;
    private static final long MAX_FILE = 8L * 1024 * 1024;
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Map<String, byte[]> FILES = new LinkedHashMap<>();

    private Assets() {
    }

    public static Path dir() {
        return FMLPaths.CONFIGDIR.get().resolve("starnpcs");
    }

    public static synchronized void reload() {
        FILES.clear();
        try {
            for (String sub : List.of("models", "skins", "emotes", "presets", "editable")) {
                Files.createDirectories(dir().resolve(sub));
            }
            writeDefaults();
            scan("models", ".bbmodel");
            scan("skins", ".png");
            scan("emotes", ".json");
            Path sounds = dir().resolve("sounds.json");
            if (Files.isRegularFile(sounds)) {
                FILES.put("sounds.json", gzip(Files.readAllBytes(sounds)));
            }
        } catch (IOException e) {
            StarNpcs.LOG.error("[Star NPCs] No se pudo leer config/starnpcs", e);
        }
        StarNpcs.LOG.info("[Star NPCs] {} archivo(s) propios para mandar a los jugadores", FILES.size());
    }

    private static void writeDefaults() throws IOException {
        for (String name : List.of("human.bbmodel", "human_slim.bbmodel")) {
            try (InputStream in = Assets.class.getResourceAsStream("/assets/starnpcs/npcmodels/" + name)) {
                if (in != null) {
                    Files.copy(in, dir().resolve("editable").resolve(name), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        try (InputStream in = Assets.class.getResourceAsStream("/starnpcs/LEEME.txt")) {
            if (in != null) {
                Files.copy(in, dir().resolve("LEEME.txt"), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void scan(String sub, String ext) throws IOException {
        try (Stream<Path> list = Files.list(dir().resolve(sub))) {
            for (Path p : (Iterable<Path>) list.sorted()::iterator) {
                String name = p.getFileName().toString();
                if (!Files.isRegularFile(p) || !name.toLowerCase().endsWith(ext)) {
                    continue;
                }
                if (Files.size(p) > MAX_FILE) {
                    StarNpcs.LOG.warn("[Star NPCs] {} pesa demasiado y no se manda", name);
                    continue;
                }
                FILES.put(sub + "/" + name, gzip(Files.readAllBytes(p)));
            }
        }
    }

    private static byte[] gzip(byte[] raw) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
            gz.write(raw);
        }
        return out.toByteArray();
    }

    public static synchronized void sendTo(ServerPlayer player) {
        Net.toPlayer(player, new Net.AssetPart("", 0, 0, new byte[0]));
        for (Map.Entry<String, byte[]> e : FILES.entrySet()) {
            byte[] data = e.getValue();
            int parts = Math.max(1, (data.length + CHUNK - 1) / CHUNK);
            for (int i = 0; i < parts; i++) {
                int from = i * CHUNK;
                Net.toPlayer(player, new Net.AssetPart(e.getKey(), i, parts, Arrays.copyOfRange(data, from, Math.min(data.length, from + CHUNK))));
            }
        }
        Net.toPlayer(player, new Net.AssetPart("#done", 0, 0, new byte[0]));
    }

    public static void sendAll(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            sendTo(p);
        }
    }

    /** Agrega un sonido a una animacion (queda en sounds.json). */
    public static void addSound(String model, String anim, float time, String sound, float volume, float pitch) throws IOException {
        JsonObject root = readSounds();
        String key = model + "/" + anim;
        JsonArray list = root.has(key) && root.get(key).isJsonArray() ? root.getAsJsonArray(key) : new JsonArray();
        JsonObject o = new JsonObject();
        o.addProperty("time", time);
        o.addProperty("sound", sound);
        o.addProperty("volume", volume);
        o.addProperty("pitch", pitch);
        list.add(o);
        root.add(key, list);
        Files.writeString(dir().resolve("sounds.json"), PRETTY.toJson(root), StandardCharsets.UTF_8);
    }

    public static boolean clearSounds(String model, String anim) throws IOException {
        JsonObject root = readSounds();
        boolean had = root.remove(model + "/" + anim) != null;
        Files.writeString(dir().resolve("sounds.json"), PRETTY.toJson(root), StandardCharsets.UTF_8);
        return had;
    }

    private static JsonObject readSounds() {
        Path file = dir().resolve("sounds.json");
        try {
            if (Files.isRegularFile(file)) {
                return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            }
        } catch (Exception e) {
            StarNpcs.LOG.warn("[Star NPCs] sounds.json esta mal escrito, se empieza de nuevo", e);
        }
        return new JsonObject();
    }
}
