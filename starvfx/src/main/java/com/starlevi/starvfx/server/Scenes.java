package com.starlevi.starvfx.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.starlevi.starvfx.StarVfx;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Las escenas son archivos json. Las que trae el mod estan adentro del jar; si existe
 * {@code config/starvfx/scenes/<nombre>.json}, manda ese (asi se pueden editar las que vienen o hacer nuevas).
 */
public final class Scenes {
    public static final List<String> BUILTIN = List.of("hoja", "grieta_cielo", "agujero_negro", "temblor");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private Scenes() {
    }

    public static Path dir() {
        return FMLPaths.CONFIGDIR.get().resolve("starvfx").resolve("scenes");
    }

    public static boolean validName(String name) {
        return name.matches("[a-z0-9_]{1,40}");
    }

    public static TreeSet<String> list() {
        TreeSet<String> out = new TreeSet<>(BUILTIN);
        try (Stream<Path> files = Files.list(dir())) {
            files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".json"))
                    .map(n -> n.substring(0, n.length() - 5)).filter(Scenes::validName).forEach(out::add);
        } catch (IOException ignored) {
            // todavia no hay carpeta
        }
        return out;
    }

    public static boolean isCustom(String name) {
        return Files.isRegularFile(dir().resolve(name + ".json"));
    }

    /** La escena, o null si no existe o esta mal escrita. */
    public static JsonObject load(String name) {
        if (!validName(name)) {
            return null;
        }
        try {
            Path file = dir().resolve(name + ".json");
            String text;
            if (Files.isRegularFile(file)) {
                text = Files.readString(file, StandardCharsets.UTF_8);
            } else {
                try (InputStream in = StarVfx.class.getResourceAsStream("/data/starvfx/scenes/" + name + ".json")) {
                    if (in == null) {
                        return null;
                    }
                    text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
            return JsonParser.parseString(text).getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            StarVfx.LOGGER.warn("[Star VFX] No se pudo leer la escena {}", name, e);
            return null;
        }
    }

    public static boolean save(String name, JsonObject scene) {
        try {
            Files.createDirectories(dir());
            Files.writeString(dir().resolve(name + ".json"), GSON.toJson(scene), StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            StarVfx.LOGGER.warn("[Star VFX] No se pudo guardar la escena {}", name, e);
            return false;
        }
    }
}
