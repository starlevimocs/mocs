package com.starlevi.starvfx;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.world.phys.Vec3;

/** Cuentas y lectura de json que usan igual el servidor y el cliente. */
public final class Geo {
    private Geo() {
    }

    /** De coordenadas de la escena (relativas al origen, giradas {@code yaw} grados) a coordenadas del mundo. */
    public static Vec3 toWorld(Vec3 origin, float yaw, double x, double y, double z) {
        if (yaw == 0) {
            return origin.add(x, y, z);
        }
        double r = Math.toRadians(yaw);
        double c = Math.cos(r), s = Math.sin(r);
        return origin.add(x * c - z * s, y, x * s + z * c);
    }

    /** Del mundo a coordenadas de la escena. */
    public static Vec3 toLocal(Vec3 origin, float yaw, Vec3 world) {
        Vec3 d = world.subtract(origin);
        if (yaw == 0) {
            return d;
        }
        double r = Math.toRadians(-yaw);
        double c = Math.cos(r), s = Math.sin(r);
        return new Vec3(d.x * c - d.z * s, d.y, d.x * s + d.z * c);
    }

    public static float num(JsonObject o, String key, float def) {
        try {
            JsonElement e = o.get(key);
            return e != null && e.isJsonPrimitive() ? e.getAsFloat() : def;
        } catch (RuntimeException ex) {
            return def;
        }
    }

    public static String str(JsonObject o, String key, String def) {
        try {
            JsonElement e = o.get(key);
            return e != null && e.isJsonPrimitive() ? e.getAsString() : def;
        } catch (RuntimeException ex) {
            return def;
        }
    }

    public static boolean bool(JsonObject o, String key, boolean def) {
        try {
            JsonElement e = o.get(key);
            return e != null && e.isJsonPrimitive() ? e.getAsBoolean() : def;
        } catch (RuntimeException ex) {
            return def;
        }
    }

    /** Lee [x, y, z]; si no esta, {@code def}. */
    public static Vec3 vec(JsonObject o, String key, Vec3 def) {
        try {
            JsonElement e = o.get(key);
            if (e != null && e.isJsonArray()) {
                JsonArray a = e.getAsJsonArray();
                if (a.size() >= 3) {
                    return new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
                }
            }
        } catch (RuntimeException ignored) {
            // formato raro: se usa el valor por defecto
        }
        return def;
    }

    public static JsonArray arr(double x, double y, double z) {
        JsonArray a = new JsonArray();
        a.add(round(x));
        a.add(round(y));
        a.add(round(z));
        return a;
    }

    public static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /** "#rrggbb" a 0xRRGGBB. */
    public static int color(JsonObject o, String key, int def) {
        String s = str(o, key, null);
        if (s == null) {
            return def;
        }
        try {
            return Integer.parseInt(s.startsWith("#") ? s.substring(1) : s, 16) & 0xFFFFFF;
        } catch (NumberFormatException ex) {
            return def;
        }
    }
}
