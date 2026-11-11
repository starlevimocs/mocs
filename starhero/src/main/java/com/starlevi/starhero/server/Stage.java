package com.starlevi.starhero.server;

import com.starlevi.starhero.content.Instrument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Un escenario (un amplificador): el grupo que se arma y el show que se toca. */
final class Stage {
    enum Phase { LOBBY, COUNTDOWN, PLAYING }

    static final class Stats {
        int score;
        int combo;
        int bestCombo;
        int misses;
        boolean finished;
        float accuracy = -1;
        String rank = "";
        int lastHits;
    }

    static final class Slot {
        final UUID entity;
        final Instrument instrument;
        final Vec3 pos;
        final float yaw;

        Slot(UUID entity, Instrument instrument, Vec3 pos, float yaw) {
            this.entity = entity;
            this.instrument = instrument;
            this.pos = pos;
            this.yaw = yaw;
        }
    }

    final ResourceKey<Level> dimension;
    final BlockPos amp;
    Direction facing;
    UUID leader;
    boolean bandMode;
    String song = "";
    String songTitle = "";
    float bpm = 120;
    int offsetMs;
    int leadInMs;
    final LinkedHashMap<UUID, Instrument> players = new LinkedHashMap<>();

    Phase phase = Phase.LOBBY;
    long startTick;
    float hype = 0.5F;
    Vec3 center = Vec3.ZERO;
    final Map<UUID, Stats> stats = new HashMap<>();
    final Map<UUID, Slot> slots = new LinkedHashMap<>();
    long lastBeat = -1;

    Stage(ResourceKey<Level> dimension, BlockPos amp, Direction facing, UUID leader) {
        this.dimension = dimension;
        this.amp = amp;
        this.facing = facing;
        this.leader = leader;
    }

    boolean playing() {
        return phase != Phase.LOBBY;
    }

    /** Hacia el publico. */
    Vec3 forward() {
        return new Vec3(facing.getStepX(), 0, facing.getStepZ());
    }

    /** De costado (a lo largo de la fila de la banda). */
    Vec3 side() {
        return new Vec3(-facing.getStepZ(), 0, facing.getStepX());
    }

    /** Beat actual segun el reloj del servidor (para el publico). */
    double beat(long gameTime) {
        double beatMs = 60000.0 / Math.max(30, bpm);
        double ms = (gameTime - startTick) * 50.0 - leadInMs - offsetMs;
        return ms / beatMs;
    }
}
