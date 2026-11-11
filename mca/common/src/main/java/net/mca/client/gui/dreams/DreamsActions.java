package net.mca.client.gui.dreams;

import java.util.List;

/**
 * Dreams: las acciones del menu de interaccion, agrupadas por pestana.
 * DIALOGUE = respuesta del dialogo principal de MCA (el aldeano contesta y puede animarse),
 * COMMAND = boton de MCA que va al servidor, CLIENT = algo que pasa en la pantalla.
 */
public final class DreamsActions {
    public enum Kind { DIALOGUE, COMMAND, CLIENT }

    public record Action(String id, Kind kind, String target, boolean adultOnly) {
        public String label() {
            return "gui.dreams.action." + id;
        }
    }

    public record Tab(String id, String icon, List<Action> actions) {
        public String label() {
            return "gui.dreams.tab." + id;
        }
    }

    private static Action d(String id) {
        return new Action(id, Kind.DIALOGUE, id, false);
    }

    private static Action dAdult(String id) {
        return new Action(id, Kind.DIALOGUE, id, true);
    }

    private static Action c(String id, String button) {
        return new Action(id, Kind.COMMAND, "gui.button." + button, false);
    }

    private static Action ui(String id) {
        return new Action(id, Kind.CLIENT, id, false);
    }

    public static final List<Tab> TABS = List.of(
            new Tab("talk", "❦", List.of(
                    d("talk"), ui("say"), d("chat"), d("joke"), d("story"), d("rumors"),
                    d("rock_paper_scissor"), d("apologize"))),
            new Tab("gestures", "✋", List.of(
                    c("release", "dreams_release"), ui("touch"), d("hug"), d("high_five"), d("handshake"), d("fist_bump"),
                    d("bow_each"), d("pat_head"), d("comfort"), d("forehead_kiss"), d("hold_hand"), d("carry_back"),
                    ui("gift"))),
            new Tab("fun", "♪", List.of(
                    d("dance_fun"), d("toast"), d("photo"), d("tickle"), d("lift_spin"), d("rock_paper_scissor"),
                    d("joke"))),
            new Tab("romance", "❤", List.of(
                    c("release", "dreams_release"), dAdult("flirt"), dAdult("kiss"), dAdult("cheek_kiss"), d("hold_hand"),
                    dAdult("carry_arms"), dAdult("dance_slow"), dAdult("dance_tango"), ui("gift"), dAdult("procreate"),
                    dAdult("adopt"))),
            new Tab("orders", "⚑", List.of(
                    c("follow", "follow"), c("stay", "stay"), c("move", "move"), c("ridehorse", "ridehorse"),
                    c("sethome", "sethome"), c("gohome", "gohome"), c("setworkplace", "setworkplace"),
                    c("fishing", "fishing"), c("harvesting", "harvesting"), c("hunting", "hunting"),
                    c("chopping", "chopping"), c("stopworking", "stopworking"),
                    c("guard", "profession.guard"), c("archer", "profession.archer"), c("armor", "armor"))),
            new Tab("more", "☰", List.of(
                    c("trade", "trade"), c("inventory", "inventory"), ui("family"), d("hire"),
                    c("pick_up", "pick_up")))
    );

    private static Boolean touchMod;

    /** El modo toque lo trae el mod Star Interactions; sin el, el boton no aparece. */
    public static boolean touchAvailable() {
        if (touchMod == null) {
            try {
                Class.forName("com.starlevi.starinteractions.client.TouchClient", false, DreamsActions.class.getClassLoader());
                touchMod = true;
            } catch (Throwable t) {
                touchMod = false;
            }
        }
        return touchMod;
    }

    /** Pide el modo toque con ese aldeano. */
    public static void requestTouch(int entityId) {
        try {
            Class.forName("com.starlevi.starinteractions.client.TouchClient").getMethod("request", int.class).invoke(null, entityId);
        } catch (Throwable t) {
            touchMod = false;
        }
    }

    private DreamsActions() {
    }
}
