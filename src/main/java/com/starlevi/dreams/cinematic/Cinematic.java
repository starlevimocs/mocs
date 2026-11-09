package com.starlevi.dreams.cinematic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * Una cinematica: una camara que se mueve por un camino (puntos marcados) o gira alrededor de un punto.
 *
 * @param frames      puntos del camino (solo PATH); el "ticks" de cada punto es lo que tarda en llegar a el
 *                    desde el anterior, asi que el del primer punto no cuenta
 * @param orbit       parametros de la orbita (solo ORBIT)
 * @param hidePlayers si es true, los jugadores no se dibujan mientras dura la cinematica
 * @param onEnd       comando que ejecuta el servidor al terminar (puede ser null)
 */
public record Cinematic(String name, Kind kind, List<Keyframe> frames, Orbit orbit, boolean hidePlayers, String onEnd) {

    public enum Kind {
        PATH,
        ORBIT
    }

    public record Keyframe(double x, double y, double z, float yaw, float pitch, int ticks) {
    }

    /**
     * La camara da vueltas alrededor de (cx, cy, cz) a "radius" bloques, "height" bloques por encima del centro,
     * mirando hacia un punto "lookHeight" bloques por encima del centro.
     */
    public record Orbit(double cx, double cy, double cz, double radius, double height, double lookHeight,
                        double startDeg, double sweepDeg, int ticks) {
    }

    public Cinematic {
        frames = List.copyOf(frames);
    }

    public static Cinematic emptyPath(String name) {
        return new Cinematic(name, Kind.PATH, List.of(), null, false, null);
    }

    public static Cinematic orbit(String name, Orbit orbit, boolean hidePlayers, String onEnd) {
        return new Cinematic(name, Kind.ORBIT, List.of(), orbit, hidePlayers, onEnd);
    }

    /** Duracion total en ticks (20 por segundo). Un camino necesita al menos 2 puntos. */
    public int durationTicks() {
        if (kind == Kind.ORBIT) {
            return orbit == null ? 0 : orbit.ticks();
        }
        if (frames.size() < 2) {
            return 0;
        }
        int total = 0;
        for (int i = 1; i < frames.size(); i++) {
            total += Math.max(1, frames.get(i).ticks());
        }
        return total;
    }

    public Cinematic withFrame(Keyframe frame) {
        List<Keyframe> copy = new ArrayList<>(frames);
        copy.add(frame);
        return new Cinematic(name, Kind.PATH, copy, null, hidePlayers, onEnd);
    }

    public Cinematic withoutLastFrame() {
        List<Keyframe> copy = new ArrayList<>(frames);
        if (!copy.isEmpty()) {
            copy.remove(copy.size() - 1);
        }
        return new Cinematic(name, kind, copy, orbit, hidePlayers, onEnd);
    }

    public Cinematic withoutFrames() {
        return new Cinematic(name, Kind.PATH, List.of(), null, hidePlayers, onEnd);
    }

    public Cinematic withHidePlayers(boolean value) {
        return new Cinematic(name, kind, frames, orbit, value, onEnd);
    }

    public Cinematic withOnEnd(String command) {
        return new Cinematic(name, kind, frames, orbit, hidePlayers, command);
    }

    // ---------------------------------------------------------------- NBT

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", name);
        tag.putString("kind", kind.name());
        tag.putBoolean("hidePlayers", hidePlayers);
        if (onEnd != null) {
            tag.putString("onEnd", onEnd);
        }
        if (kind == Kind.ORBIT && orbit != null) {
            CompoundTag o = new CompoundTag();
            o.putDouble("cx", orbit.cx());
            o.putDouble("cy", orbit.cy());
            o.putDouble("cz", orbit.cz());
            o.putDouble("radius", orbit.radius());
            o.putDouble("height", orbit.height());
            o.putDouble("lookHeight", orbit.lookHeight());
            o.putDouble("startDeg", orbit.startDeg());
            o.putDouble("sweepDeg", orbit.sweepDeg());
            o.putInt("ticks", orbit.ticks());
            tag.put("orbit", o);
        } else {
            ListTag list = new ListTag();
            for (Keyframe f : frames) {
                CompoundTag k = new CompoundTag();
                k.putDouble("x", f.x());
                k.putDouble("y", f.y());
                k.putDouble("z", f.z());
                k.putFloat("yaw", f.yaw());
                k.putFloat("pitch", f.pitch());
                k.putInt("ticks", f.ticks());
                list.add(k);
            }
            tag.put("frames", list);
        }
        return tag;
    }

    public static Cinematic fromTag(CompoundTag tag) {
        String name = tag.getString("name");
        boolean hidePlayers = tag.getBoolean("hidePlayers");
        String onEnd = tag.contains("onEnd") ? tag.getString("onEnd") : null;

        if ("ORBIT".equals(tag.getString("kind")) && tag.contains("orbit")) {
            CompoundTag o = tag.getCompound("orbit");
            Orbit orbit = new Orbit(o.getDouble("cx"), o.getDouble("cy"), o.getDouble("cz"),
                    o.getDouble("radius"), o.getDouble("height"), o.getDouble("lookHeight"),
                    o.getDouble("startDeg"), o.getDouble("sweepDeg"), o.getInt("ticks"));
            return orbit(name, orbit, hidePlayers, onEnd);
        }

        List<Keyframe> frames = new ArrayList<>();
        ListTag list = tag.getList("frames", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag k = list.getCompound(i);
            frames.add(new Keyframe(k.getDouble("x"), k.getDouble("y"), k.getDouble("z"),
                    k.getFloat("yaw"), k.getFloat("pitch"), k.getInt("ticks")));
        }
        return new Cinematic(name, Kind.PATH, frames, null, hidePlayers, onEnd);
    }
}
