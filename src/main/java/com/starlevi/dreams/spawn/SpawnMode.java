package com.starlevi.dreams.spawn;

/**
 * Como puede aparecer (de forma natural) un mob.
 * <ul>
 *   <li>NATURAL: aparece en todo el mundo, como lo haria su mod.</li>
 *   <li>NEVER: no aparece nunca, ni siquiera dentro de zonas.</li>
 *   <li>ZONES: solo aparece dentro de las zonas donde esta permitido.</li>
 * </ul>
 */
public enum SpawnMode {
    NATURAL,
    NEVER,
    ZONES;

    public static SpawnMode byOrdinal(int ordinal) {
        SpawnMode[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
    }
}
