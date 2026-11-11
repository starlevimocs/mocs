package com.starlevi.starhero.client;

import com.google.gson.JsonObject;
import com.starlevi.starhero.StarHero;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Puente con CubOsu (sin depender de el para compilar).
 * <p>
 * CubOsu guarda cada cancion importada (.osz) como un "QTE"; las de osu!mania tienen un {@code ManiaChart}.
 * Star Hero las arranca con el estilo "guitar" de CubOsu (el mastil en perspectiva, como Guitar Hero) y sin la
 * camara de CubOsu (la camara de concierto es la de Star Hero). Mientras suena, lee el tiempo de la cancion, el
 * beat, las teclas apretadas y los aciertos para animar a la banda.
 */
public final class CubOsuBridge {
    public static final String ID = "starhero";

    public record Song(String name, String title, String artist, int lanes, float bpm, int offset, int leadIn) {
    }

    /** Lo que esta pasando en la cancion ahora. */
    public static final class Poll {
        public float songMs;
        public float beatMs;
        public boolean[] down = new boolean[0];
        public long[] flash = new long[0];
        public int combo;
        public int score;
        public int misses;
        public int hits;
    }

    public interface FinishListener {
        void onFinish(int score, float accuracy, int combo, int misses, String rank, boolean success);
    }

    private static final Map<String, Field> FIELDS = new HashMap<>();
    private static boolean listening;

    private CubOsuBridge() {
    }

    public static boolean available() {
        return ModList.get().isLoaded("cubosu");
    }

    private static Object field(Object obj, String name) throws ReflectiveOperationException {
        if (obj == null) {
            return null;
        }
        String key = obj.getClass().getName() + "#" + name;
        Field f = FIELDS.get(key);
        if (f == null) {
            Class<?> c = obj.getClass();
            while (c != null && f == null) {
                try {
                    f = c.getDeclaredField(name);
                } catch (NoSuchFieldException e) {
                    c = c.getSuperclass();
                }
            }
            if (f == null) {
                throw new NoSuchFieldException(name);
            }
            f.setAccessible(true);
            FIELDS.put(key, f);
        }
        return f.get(obj);
    }

    private static int intField(Object obj, String name, int def) {
        try {
            Object v = field(obj, name);
            return v instanceof Number n ? n.intValue() : def;
        } catch (ReflectiveOperationException e) {
            return def;
        }
    }

    private static float floatField(Object obj, String name, float def) {
        try {
            Object v = field(obj, name);
            return v instanceof Number n ? n.floatValue() : def;
        } catch (ReflectiveOperationException e) {
            return def;
        }
    }

    private static String strField(Object obj, String name) {
        try {
            Object v = field(obj, name);
            return v instanceof String s ? s : "";
        } catch (ReflectiveOperationException e) {
            return "";
        }
    }

    /** Canciones de osu!mania importadas en CubOsu. */
    @SuppressWarnings("unchecked")
    public static List<Song> songs() {
        List<Song> out = new ArrayList<>();
        if (!available()) {
            return out;
        }
        try {
            Class<?> api = Class.forName("com.cotii.cubosu.api.CubOsuApi");
            List<String> names = (List<String>) api.getMethod("songs").invoke(null);
            Method get = Class.forName("com.cotii.cubosu.client.qte.SavedStore").getMethod("get", String.class);
            for (String name : names) {
                Object def = get.invoke(null, name);
                if (def == null) {
                    continue;
                }
                // el tipo se guarda como "osumania" (y el enum se llama MANIA); las de osu! normal no sirven
                Object type = field(def, "type");
                String typeId = type == null ? "" : (strField(type, "id") + " " + type).toLowerCase();
                if (!typeId.contains("mania") || name.endsWith("_intro")) {
                    continue;
                }
                Object chart = field(def, "mania");
                if (chart == null) {
                    continue;
                }
                Object info = field(chart, "info");
                String title = info != null ? strField(info, "title") : "";
                if (title.isEmpty()) {
                    title = strField(chart, "songTitle");
                }
                if (title.isEmpty()) {
                    title = name;
                }
                String artist = info != null ? strField(info, "artist") : "";
                out.add(new Song(name, title, artist, intField(chart, "lanes", 4), floatField(chart, "bpm", 120),
                        intField(chart, "offset", 0), intField(chart, "leadIn", 0)));
            }
        } catch (Throwable t) {
            StarHero.LOGGER.warn("[Star Hero] No se pudo leer la lista de canciones de CubOsu", t);
        }
        out.sort(Comparator.comparing(s -> s.title().toLowerCase()));
        return out;
    }

    /** Arranca la cancion con el estilo guitarra y sin la camara de CubOsu. */
    public static boolean start(String name) {
        if (!available()) {
            return false;
        }
        try {
            JsonObject over = new JsonObject();
            JsonObject mania = new JsonObject();
            mania.addProperty("style", "guitar");
            mania.addProperty("individual_lanes", false);
            over.add("mania", mania);
            JsonObject cinematics = new JsonObject();
            cinematics.addProperty("enabled", false);
            over.add("cinematics", cinematics);
            over.addProperty("lock_movement", true);
            Method m = Class.forName("com.cotii.cubosu.client.qte.QteManager")
                    .getMethod("startSaved", String.class, String.class, Boolean.class, JsonObject.class);
            return (boolean) m.invoke(null, name, ID, null, over);
        } catch (Throwable t) {
            StarHero.LOGGER.warn("[Star Hero] No se pudo arrancar la cancion en CubOsu, pruebo sin estilos", t);
            try {
                Class<?> api = Class.forName("com.cotii.cubosu.api.CubOsuApi");
                return (boolean) api.getMethod("play", String.class, String.class).invoke(null, name, ID);
            } catch (Throwable t2) {
                return false;
            }
        }
    }

    public static void stop() {
        if (!available()) {
            return;
        }
        try {
            Class.forName("com.cotii.cubosu.api.CubOsuApi").getMethod("stop", String.class).invoke(null, ID);
        } catch (Throwable ignored) {
        }
    }

    public static void openSongMenu() {
        try {
            Class.forName("com.cotii.cubosu.api.CubOsuApi").getMethod("openSongMenu").invoke(null);
        } catch (Throwable ignored) {
        }
    }

    /** Estado de la cancion que esta sonando (null si no hay). */
    public static Poll poll() {
        if (!available()) {
            return null;
        }
        try {
            Object inst = Class.forName("com.cotii.cubosu.client.qte.QteManager").getMethod("get", String.class).invoke(null, ID);
            if (inst == null) {
                return null;
            }
            Object mania = field(inst, "mania");
            if (mania == null) {
                return null;
            }
            Poll p = new Poll();
            p.songMs = (float) inst.getClass().getMethod("songTime").invoke(inst);
            p.beatMs = (float) inst.getClass().getMethod("beatMs").invoke(inst);
            Object down = field(mania, "laneDown");
            Object flash = field(mania, "laneFlash");
            if (down instanceof boolean[] d) {
                p.down = d.clone();
            }
            if (flash instanceof long[] f) {
                p.flash = f.clone();
            }
            p.combo = intField(mania, "combo", 0);
            p.score = intField(mania, "score", 0);
            p.misses = intField(mania, "misses", 0);
            p.hits = intField(mania, "perfects", 0) + intField(mania, "greats", 0) + intField(mania, "goods", 0);
            return p;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Se entera cuando termina la cancion (exito o no), con el resultado de CubOsu. */
    public static void listen(FinishListener listener) {
        if (listening || !available()) {
            return;
        }
        try {
            Class<?> api = Class.forName("com.cotii.cubosu.api.CubOsuApi");
            Class<?> iface = Class.forName("com.cotii.cubosu.api.CubOsuListener");
            Object proxy = Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface}, (obj, method, args) -> {
                switch (method.getName()) {
                    case "onFinish" -> {
                        Object r = args[0];
                        Class<?> rc = r.getClass();
                        String id = (String) rc.getMethod("id").invoke(r);
                        if (ID.equals(id)) {
                            listener.onFinish((int) rc.getMethod("score").invoke(r), (float) rc.getMethod("accuracy").invoke(r),
                                    (int) rc.getMethod("combo").invoke(r), (int) rc.getMethod("misses").invoke(r),
                                    String.valueOf(rc.getMethod("rank").invoke(r)), (boolean) rc.getMethod("success").invoke(r));
                        }
                        return null;
                    }
                    case "hashCode" -> {
                        return System.identityHashCode(obj);
                    }
                    case "equals" -> {
                        return obj == args[0];
                    }
                    case "toString" -> {
                        return "StarHeroListener";
                    }
                    default -> {
                        return null;
                    }
                }
            });
            api.getMethod("addListener", iface).invoke(null, proxy);
            listening = true;
        } catch (Throwable t) {
            StarHero.LOGGER.warn("[Star Hero] No se pudo escuchar los resultados de CubOsu", t);
        }
    }
}
