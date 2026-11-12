package com.starlevi.dreams.dialogue;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/**
 * Una conversacion completa: un grafo de nodos. Cada nodo es una linea dicha por un personaje.
 *
 * @param start     id del nodo con el que empieza
 * @param cinematic nombre de una cinematica que se reproduce mientras dura el dialogo (puede ser null)
 */
public record Dialogue(ResourceLocation id, String start, Map<String, Node> nodes, String cinematic) {

    /**
     * @param speaker    id del personaje (archivo en assets/&lt;ns&gt;/speakers/)
     * @param expression nombre del retrato a mostrar (por ejemplo "neutral", "sad")
     * @param next       id del siguiente nodo, o null si la conversacion termina aqui
     * @param choices    opciones del jugador; si no esta vacia, ignora "next"
     * @param color      color de esta linea (para el Arbol Madre: de que color se enciende al decirla), o null
     * @param branches   desvios: al llegar a este nodo, si se cumple alguna condicion se salta a otro nodo
     * @param action     algo que pasa al llegar a este nodo (aceptar o entregar una mision), o null
     */
    public record Node(String id, ResourceLocation speaker, String expression, String text,
                       String next, List<Choice> choices, Integer color, List<Branch> branches, Action action) {
    }

    /**
     * @param next   nodo al que lleva; null = cierra la conversacion
     * @param cond   si no se cumple, la opcion no aparece (null = siempre)
     * @param action lo que pasa al elegirla, o null
     */
    public record Choice(String text, String next, Cond cond, Action action) {
    }

    public record Branch(Cond cond, String next) {
    }

    /**
     * Condicion sobre una mision.
     *
     * @param state "locked", "available", "active", "ready" o "done"
     * @param not   true = se cumple cuando NO esta en ese estado
     */
    public record Cond(String quest, String state, boolean not) {
    }

    /**
     * @param type  "accept" (aceptar), "turn_in" (entregar) o "contribute" (aportar a una mision de comunidad)
     * @param quest id de la mision
     */
    public record Action(String type, String quest) {
    }

    public Node startNode() {
        return nodes.get(start);
    }
}
