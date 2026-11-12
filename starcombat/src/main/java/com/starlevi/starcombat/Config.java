package com.starlevi.starcombat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** config/starcombat.json (lo lee el servidor; el alcance y la espera del gancho se le avisan a cada cliente). */
public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static float damageMultiplier = 1.0F;
    public static float grappleRange = 32;
    public static int grappleCooldown = 24;
    public static boolean pvp = true;
    public static boolean fire = true;
    public static boolean pullMobs = true;

    private Config() {
    }

    public static void load() {
        Path file = FMLPaths.CONFIGDIR.get().resolve("starcombat.json");
        try {
            if (Files.exists(file)) {
                try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    JsonObject o = GSON.fromJson(r, JsonObject.class);
                    if (o != null) {
                        if (o.has("damage_multiplier")) damageMultiplier = o.get("damage_multiplier").getAsFloat();
                        if (o.has("grapple_range")) grappleRange = o.get("grapple_range").getAsFloat();
                        if (o.has("grapple_cooldown_ticks")) grappleCooldown = o.get("grapple_cooldown_ticks").getAsInt();
                        if (o.has("pvp")) pvp = o.get("pvp").getAsBoolean();
                        if (o.has("fire")) fire = o.get("fire").getAsBoolean();
                        if (o.has("pull_mobs")) pullMobs = o.get("pull_mobs").getAsBoolean();
                    }
                }
            }
            damageMultiplier = Math.max(0, Math.min(20, damageMultiplier));
            grappleRange = Math.max(4, Math.min(64, grappleRange));
            grappleCooldown = Math.max(0, Math.min(1200, grappleCooldown));
            JsonObject o = new JsonObject();
            o.addProperty("damage_multiplier", damageMultiplier);
            o.addProperty("grapple_range", grappleRange);
            o.addProperty("grapple_cooldown_ticks", grappleCooldown);
            o.addProperty("pvp", pvp);
            o.addProperty("fire", fire);
            o.addProperty("pull_mobs", pullMobs);
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(o, w);
            }
        } catch (Exception e) {
            StarCombat.LOGGER.warn("[Star Combat] No se pudo leer config/starcombat.json", e);
        }
    }
}
