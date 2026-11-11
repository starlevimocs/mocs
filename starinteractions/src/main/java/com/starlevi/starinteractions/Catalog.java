package com.starlevi.starinteractions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Todas las interacciones: de a dos, emotes, ir de la mano, cargar y el modo toque. */
public final class Catalog {
    public enum Kind { PAIR, SOLO, HANDS, CARRY, TOUCH }

    /**
     * @param distance px entre los dos (16 = 1 bloque), igual que en el archivo de la animacion
     * @param ticks    cuanto dura (para tener a los dos en su lugar)
     */
    public record Entry(String id, Kind kind, String category, float distance, int ticks) {
        public String label() {
            return "interaction.starinteractions." + id;
        }
    }

    public static final int LINK_NONE = 0;
    public static final int LINK_HANDS = 1;
    public static final int LINK_BACK = 2;
    public static final int LINK_ARMS = 3;
    public static final int LINK_SHOULDERS = 4;

    private static final Map<String, Entry> ALL = new LinkedHashMap<>();

    private static void pair(String id, String category, float distance, float seconds) {
        ALL.put(id, new Entry(id, Kind.PAIR, category, distance, (int) Math.ceil(seconds * 20) + 2));
    }

    private static void solo(String... ids) {
        for (String id : ids) {
            ALL.put(id, new Entry(id, Kind.SOLO, "emote", 0, 0));
        }
    }

    static {
        pair("handshake", "greet", 17, 2.4F);
        pair("fist_bump", "greet", 19, 1.6F);
        pair("high_five", "greet", 13, 1.6F);
        pair("bow_each", "greet", 20, 2.4F);

        pair("hug", "care", 5.5F, 3.4F);
        pair("pat_head", "care", 11, 3.2F);
        pair("comfort", "care", 8, 4.4F);
        pair("cheek_kiss", "care", 8.5F, 2.0F);
        pair("forehead_kiss", "care", 8, 3.2F);
        pair("kiss", "care", 8, 3.0F);

        pair("dance_fun", "fun", 15, 8.0F);
        pair("dance_slow", "fun", 6, 10.0F);
        pair("dance_tango", "fun", 6, 9.0F);
        pair("lift_spin", "fun", 5.5F, 5.2F);
        pair("toast", "fun", 16, 3.0F);
        pair("photo", "fun", 12, 5.0F);
        pair("tickle", "fun", 10, 2.8F);
        pair("slap", "fun", 12, 1.0F);

        ALL.put("hold_hands", new Entry("hold_hands", Kind.HANDS, "together", 0, 0));
        ALL.put("carry_back", new Entry("carry_back", Kind.CARRY, "together", 0, 0));
        ALL.put("carry_arms", new Entry("carry_arms", Kind.CARRY, "together", 0, 0));
        ALL.put("carry_shoulders", new Entry("carry_shoulders", Kind.CARRY, "together", 0, 0));
        ALL.put("touch", new Entry("touch", Kind.TOUCH, "together", 0, 0));

        solo("wave", "bow", "clap", "cheer", "laugh", "cry", "facepalm", "shrug", "think", "no", "nod", "point",
                "salute", "shy", "angry", "sad", "surprised", "stretch", "yawn", "hips", "welcome", "pray",
                "flirty", "look_around", "dance_solo", "sit_ground");
    }

    private Catalog() {
    }

    public static Entry get(String id) {
        return ALL.get(id);
    }

    public static List<Entry> category(String category) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : ALL.values()) {
            if (e.category().equals(category)) {
                out.add(e);
            }
        }
        return out;
    }

    public static int linkMode(String id) {
        return switch (id) {
            case "hold_hands" -> LINK_HANDS;
            case "carry_back" -> LINK_BACK;
            case "carry_arms" -> LINK_ARMS;
            case "carry_shoulders" -> LINK_SHOULDERS;
            default -> LINK_NONE;
        };
    }
}
