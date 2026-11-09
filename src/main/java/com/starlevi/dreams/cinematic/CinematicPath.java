package com.starlevi.dreams.cinematic;

import net.minecraft.util.Mth;

import java.util.List;

/** Calcula donde esta la camara (posicion y rotacion) en un instante de la cinematica. */
public final class CinematicPath {

    public record Pose(double x, double y, double z, float yaw, float pitch) {
    }

    private CinematicPath() {
    }

    /**
     * @param t    tiempo transcurrido en ticks (admite decimales para que el movimiento sea fluido)
     * @param loop si es true, una orbita da vueltas sin parar; un camino se queda en su ultimo punto
     */
    public static Pose evaluate(Cinematic cinematic, double t, boolean loop) {
        if (cinematic.kind() == Cinematic.Kind.ORBIT && cinematic.orbit() != null) {
            return orbit(cinematic.orbit(), t, loop);
        }
        return path(cinematic.frames(), t);
    }

    private static Pose orbit(Cinematic.Orbit o, double t, boolean loop) {
        double total = Math.max(1, o.ticks());
        double progress = loop ? (t % total) / total : Mth.clamp(t / total, 0.0, 1.0);
        double theta = Math.toRadians(o.startDeg() + o.sweepDeg() * progress);

        double x = o.cx() + o.radius() * Math.cos(theta);
        double z = o.cz() + o.radius() * Math.sin(theta);
        double y = o.cy() + o.height();

        double dx = o.cx() - x;
        double dz = o.cz() - z;
        double dy = (o.cy() + o.lookHeight()) - y;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        return new Pose(x, y, z, yaw, pitch);
    }

    private static Pose path(List<Cinematic.Keyframe> frames, double t) {
        int n = frames.size();
        if (n == 0) {
            return new Pose(0, 0, 0, 0, 0);
        }
        if (n == 1) {
            Cinematic.Keyframe f = frames.get(0);
            return new Pose(f.x(), f.y(), f.z(), f.yaw(), f.pitch());
        }

        // el giro (yaw) se "desenrolla" para que siempre tome el camino mas corto entre dos puntos
        double[] yaw = new double[n];
        yaw[0] = frames.get(0).yaw();
        for (int i = 1; i < n; i++) {
            yaw[i] = yaw[i - 1] + Mth.wrapDegrees(frames.get(i).yaw() - frames.get(i - 1).yaw());
        }

        double start = 0;
        for (int i = 1; i < n; i++) {
            double duration = Math.max(1, frames.get(i).ticks());
            if (t <= start + duration || i == n - 1) {
                double u = Mth.clamp((t - start) / duration, 0.0, 1.0);
                return segment(frames, yaw, i - 1, u);
            }
            start += duration;
        }
        Cinematic.Keyframe last = frames.get(n - 1);
        return new Pose(last.x(), last.y(), last.z(), last.yaw(), last.pitch());
    }

    /** Curva suave (Catmull-Rom) que pasa por todos los puntos marcados. */
    private static Pose segment(List<Cinematic.Keyframe> f, double[] yaw, int i, double u) {
        int n = f.size();
        int i0 = Math.max(i - 1, 0);
        int i1 = i;
        int i2 = i + 1;
        int i3 = Math.min(i + 2, n - 1);

        double x = catmull(f.get(i0).x(), f.get(i1).x(), f.get(i2).x(), f.get(i3).x(), u);
        double y = catmull(f.get(i0).y(), f.get(i1).y(), f.get(i2).y(), f.get(i3).y(), u);
        double z = catmull(f.get(i0).z(), f.get(i1).z(), f.get(i2).z(), f.get(i3).z(), u);
        double yw = catmull(yaw[i0], yaw[i1], yaw[i2], yaw[i3], u);
        double pt = catmull(f.get(i0).pitch(), f.get(i1).pitch(), f.get(i2).pitch(), f.get(i3).pitch(), u);
        return new Pose(x, y, z, (float) yw, (float) Mth.clamp(pt, -90.0, 90.0));
    }

    private static double catmull(double p0, double p1, double p2, double p3, double u) {
        double u2 = u * u;
        double u3 = u2 * u;
        return 0.5 * ((2.0 * p1)
                + (-p0 + p2) * u
                + (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * u2
                + (-p0 + 3.0 * p1 - 3.0 * p2 + p3) * u3);
    }
}
