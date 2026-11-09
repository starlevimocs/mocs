package com.starlevi.dreams.dialogue;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/**
 * Una conversacion completa: un grafo de nodos. Cada nodo es una linea dicha por un personaje.
 *
 * @param start id del nodo con el que empieza
 */
public record Dialogue(ResourceLocation id, String start, Map<String, Node> nodes) {

    /**
     * @param speaker    id del personaje (archivo en assets/&lt;ns&gt;/speakers/)
     * @param expression nombre del retrato a mostrar (por ejemplo "neutral", "sad")
     * @param next       id del siguiente nodo, o null si la conversacion termina aqui
     * @param choices    opciones del jugador; si no esta vacia, ignora "next"
     */
    public record Node(String id, ResourceLocation speaker, String expression, String text,
                       String next, List<Choice> choices) {
    }

    public record Choice(String text, String next) {
    }

    public Node startNode() {
        return nodes.get(start);
    }
}
