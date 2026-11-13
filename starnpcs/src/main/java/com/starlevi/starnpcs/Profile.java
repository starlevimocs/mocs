package com.starlevi.starnpcs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/** Todo lo que define a un NPC. Viaja como JSON entre servidor y clientes y se guarda con la entidad. */
public final class Profile {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Gson PRETTY = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    // general
    public String name = "NPC";
    public String title = "";
    public boolean showName = true;
    public float scale = 1F;
    public boolean invulnerable = true;
    /** stay = quieto en su lugar, wander = pasea cerca de su casa */
    public String behavior = "stay";
    public float wanderRadius = 6F;
    public boolean lookAtPlayer = true;
    public boolean turnToPlayer = true;

    // apariencia
    /** player:Nombre | url:https://... | file:archivo | vacio = Steve */
    public String skin = "";
    public boolean slim = false;
    public String model = "human";
    /** true = huesos propios (modelos y animaciones de Blockbench); false = modelo vanilla de jugador */
    public boolean starBones = true;

    // animaciones (nombres dentro del modelo)
    public String idle = "idle";
    public String talk = "";
    public String see = "";
    public String interact = "";
    public String walk = "walk";
    public boolean stopOnTalk = true;
    public float seeRadius = 6F;
    /** NPCs con el mismo grupo comparten reloj y se disparan juntos */
    public String group = "";
    /** tick del mundo en que arranca el reloj de sus animaciones en bucle */
    public long epoch = 0L;

    // interaccion
    public String dialogue = "";
    public List<String> commands = new ArrayList<>();
    public List<String> lines = new ArrayList<>();
    public float linesEvery = 14F;
    public List<Trade> trades = new ArrayList<>();

    public static final class Trade {
        public String buy = "";
        public String buy2 = "";
        public String sell = "";
    }

    public static Profile fromJson(String json) {
        Profile p = null;
        try {
            p = GSON.fromJson(json, Profile.class);
        } catch (RuntimeException ignored) {
        }
        if (p == null) {
            p = new Profile();
        }
        p.fix();
        return p;
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public String toPrettyJson() {
        return PRETTY.toJson(this);
    }

    public Profile copy() {
        return fromJson(toJson());
    }

    private static String s(String v, String def) {
        return v == null ? def : v;
    }

    private void fix() {
        name = s(name, "NPC");
        title = s(title, "");
        behavior = s(behavior, "stay");
        skin = s(skin, "");
        model = s(model, "human");
        if (model.isBlank()) {
            model = "human";
        }
        idle = s(idle, "idle");
        talk = s(talk, "");
        see = s(see, "");
        interact = s(interact, "");
        walk = s(walk, "walk");
        group = s(group, "");
        dialogue = s(dialogue, "");
        if (commands == null) {
            commands = new ArrayList<>();
        }
        if (lines == null) {
            lines = new ArrayList<>();
        }
        if (trades == null) {
            trades = new ArrayList<>();
        }
        commands.removeIf(c -> c == null || c.isBlank());
        lines.removeIf(c -> c == null || c.isBlank());
        trades.removeIf(t -> t == null || t.sell == null || t.buy == null);
        for (Trade t : trades) {
            t.buy2 = s(t.buy2, "");
        }
        if (Float.isNaN(scale)) {
            scale = 1F;
        }
        scale = Mth.clamp(scale, 0.1F, 6F);
        wanderRadius = Mth.clamp(wanderRadius, 1F, 64F);
        seeRadius = Mth.clamp(seeRadius, 1F, 32F);
        linesEvery = Mth.clamp(linesEvery, 3F, 600F);
    }
}
