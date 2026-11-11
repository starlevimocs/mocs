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
                    ui("say"), d("chat"), d("joke"), d("story"), d("rumors"),
                    d("rock_paper_scissor"), d("apologize"))),
            new Tab("gestures", "✋", List.of(
                    d("hug"), d("high_five"), d("dance_fun"), ui("gift"))),
            new Tab("romance", "❤", List.of(
                    dAdult("flirt"), dAdult("kiss"), dAdult("dance_slow"), ui("gift"),
                    dAdult("procreate"), dAdult("adopt"))),
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

    private DreamsActions() {
    }
}
