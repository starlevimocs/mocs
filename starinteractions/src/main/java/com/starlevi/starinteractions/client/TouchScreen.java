package com.starlevi.starinteractions.client;

import com.starlevi.starinteractions.net.Net;
import com.starlevi.starinteractions.server.Sessions;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * La pantalla del modo toque: el cursor es tu mano. Pasala por la cabeza para acariciar, por la mejilla, toca la
 * nariz, agarra un brazo y movelo, o hacele cosquillas en la panza. Lo que haces se ve al instante en tu
 * pantalla y se le manda al servidor para que lo vean todos.
 */
public class TouchScreen extends Screen {
    private static final String[] HAND_OPEN = {
            "...#.#.#...",
            "..#.#.#.#..",
            "..#.#.#.#.#",
            "..#.#.#.#.#",
            "#.#########",
            "##.########",
            ".##########",
            ".##########",
            "..#########",
            "...#######.",
            "...#######.",
    };
    private static final String[] HAND_CLOSED = {
            "...........",
            "...........",
            "..#######..",
            ".#########.",
            "###########",
            "###########",
            "###########",
            ".##########",
            "..#########",
            "...#######.",
            "...#######.",
    };

    private final int targetId;
    private final Net.TouchPose pose = new Net.TouchPose();
    private TouchClient.Hit hover = new TouchClient.Hit();
    private TouchClient.Zone grab = TouchClient.Zone.NONE;
    private int grabSide = 1;
    private float grabDepth;
    private double startX, startY, lastX, lastY;
    private double travel;
    private long stillSince;
    private boolean held;
    private long boopAt;
    private long pokeAt;
    private long lastSend;
    private boolean dirty;

    public TouchScreen(int targetId) {
        super(Component.translatable("gui.starinteractions.touch"));
        this.targetId = targetId;
    }

    @Override
    protected void init() {
        TouchClient.targetId = targetId;
        TouchClient.openedAt = Util.getMillis();
        Minecraft mc = Minecraft.getInstance();
        Entity target = mc.level == null ? null : mc.level.getEntity(targetId);
        if (mc.player != null && target != null) {
            // mirar a la cara de quien se va a tocar
            Vec3 to = target.getEyePosition().subtract(0, 0.25, 0).subtract(mc.player.getEyePosition());
            double h = Math.sqrt(to.x * to.x + to.z * to.z);
            float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90F;
            float pitch = (float) -(Mth.atan2(to.y, h) * Mth.RAD_TO_DEG);
            mc.player.setYRot(yaw);
            mc.player.setXRot(pitch);
            mc.player.yHeadRot = yaw;
            mc.player.yRotO = yaw;
            mc.player.xRotO = pitch;
        }
        GLFW.glfwSetInputMode(mc.getWindow().getWindow(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        TouchClient.targetId = -1;
        Minecraft mc = Minecraft.getInstance();
        GLFW.glfwSetInputMode(mc.getWindow().getWindow(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        if (mc.player != null) {
            Poses.TouchView v = Poses.touchOfTarget(targetId);
            if (v != null && v.toucher == mc.player.getId()) {
                v.active = false;
                v.pose = new Net.TouchPose();
            }
        }
    }

    @Override
    public void onClose() {
        Net.toServer(new Net.Release());
        super.onClose();
    }

    private void event(int id) {
        apply();
        Net.toServer(new Net.TouchInput(pose.copy(), id));
        lastSend = Util.getMillis();
        dirty = false;
    }

    /** Pone la pose en este cliente al instante. */
    private void apply() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            Poses.localTouch(mc.player.getId(), targetId).pose = pose.copy();
        }
    }

    // ================================================================ mouse

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 1) {
            onClose();
            return true;
        }
        if (button != 0) {
            return false;
        }
        TouchClient.Hit hit = TouchClient.pick(mx, my, width, height);
        grab = hit.zone;
        grabSide = hit.side;
        grabDepth = hit.view.z;
        startX = lastX = mx;
        startY = lastY = my;
        travel = 0;
        held = false;
        stillSince = Util.getMillis();
        if (grab == TouchClient.Zone.NONE) {
            return true;
        }
        pose.touching = true;
        pose.hx = hit.world.x;
        pose.hy = hit.world.y;
        pose.hz = hit.world.z;
        switch (grab) {
            case NOSE -> {
                boopAt = Util.getMillis();
                event(Sessions.EV_BOOP);
            }
            case BODY -> {
                pokeAt = Util.getMillis();
                event(Sessions.EV_POKE);
            }
            case CHEEK -> {
                pose.headZ = -grabSide * 0.2F;
                pose.headY = grabSide * 0.1F;
            }
            case HEAD -> pose.headX = 0.1F;
            default -> {
            }
        }
        dirty = true;
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button != 0 || grab == TouchClient.Zone.NONE) {
            return false;
        }
        double moved = Math.sqrt((mx - lastX) * (mx - lastX) + (my - lastY) * (my - lastY));
        travel += moved;
        if (moved > 0.6) {
            stillSince = Util.getMillis();
        }
        lastX = mx;
        lastY = my;
        Vec3[] world = new Vec3[1];
        float[] model = TouchClient.dragPoint(mx, my, width, height, grabDepth, world);
        if (world[0] != null) {
            pose.hx = world[0].x;
            pose.hy = world[0].y;
            pose.hz = world[0].z;
        }
        switch (grab) {
            case HEAD -> {
                // la cabeza acompana la mano
                pose.headZ = Mth.clamp((float) (mx - startX) * 0.012F, -0.3F, 0.3F);
                pose.headX = 0.1F + Mth.clamp((float) (my - startY) * 0.008F, -0.12F, 0.22F);
                if (travel > 26) {
                    travel = 0;
                    event(Sessions.EV_PET);
                }
            }
            case CHEEK -> {
                pose.headZ = -grabSide * 0.2F + Mth.clamp((float) (mx - startX) * 0.004F, -0.08F, 0.08F);
                pose.headY = grabSide * 0.1F;
                pose.headX = Mth.clamp((float) (my - startY) * 0.004F, -0.08F, 0.1F);
                if (travel > 22) {
                    travel = 0;
                    event(Sessions.EV_CARESS);
                }
            }
            case RIGHT_ARM, LEFT_ARM -> {
                if (model != null) {
                    boolean right = grab == TouchClient.Zone.RIGHT_ARM;
                    // el brazo no se estira: el punto queda al alcance del hombro
                    float sx = right ? -5 : 5, sy = 2, sz = 0;
                    float vx = model[0] - sx, vy = model[1] - sy, vz = model[2] - sz;
                    float len = Mth.sqrt(vx * vx + vy * vy + vz * vz);
                    if (len > 11) {
                        vx *= 11 / len;
                        vy *= 11 / len;
                        vz *= 11 / len;
                    }
                    if (right) {
                        pose.arms |= 1;
                        pose.rx = sx + vx;
                        pose.ry = sy + vy;
                        pose.rz = sz + vz;
                    } else {
                        pose.arms |= 2;
                        pose.lx = sx + vx;
                        pose.ly = sy + vy;
                        pose.lz = sz + vz;
                    }
                }
                if (travel > 34) {
                    travel = 0;
                    event(Sessions.EV_ARM);
                }
            }
            case BODY -> {
                if (travel > 16) {
                    travel = 0;
                    pokeAt = Util.getMillis();
                    event(Sessions.EV_POKE);
                }
            }
            default -> {
            }
        }
        dirty = true;
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && grab != TouchClient.Zone.NONE) {
            grab = TouchClient.Zone.NONE;
            pose.touching = false;
            pose.arms = 0;
            pose.headX = 0;
            pose.headY = 0;
            pose.headZ = 0;
            event(0);
        }
        return true;
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        Entity target = mc.level == null ? null : mc.level.getEntity(targetId);
        if (target == null || !target.isAlive() || mc.player == null || mc.player.distanceToSqr(target) > 4.2 * 4.2) {
            onClose();
            return;
        }
        long now = Util.getMillis();
        // tomar de la mano: agarrar el brazo y quedarse quieto un momento
        if ((grab == TouchClient.Zone.RIGHT_ARM || grab == TouchClient.Zone.LEFT_ARM) && !held && now - stillSince > 1500) {
            held = true;
            event(Sessions.EV_HOLD);
        }
        // reflejos: la cabeza se echa atras con el toque en la nariz, el cuerpo se sacude con las cosquillas
        float boop = 1 - Mth.clamp((now - boopAt) / 450F, 0, 1);
        if (grab == TouchClient.Zone.NOSE || boop > 0) {
            pose.headX = -0.28F * boop;
            dirty = true;
        }
        float wig = 1 - Mth.clamp((now - pokeAt) / 700F, 0, 1);
        if (wig != pose.wiggle) {
            pose.wiggle = wig;
            dirty = true;
        }
        if (dirty && now - lastSend > 55) {
            event(0);
        } else if (now - lastSend > 4000) {
            event(0); // sigo aca
        }
        apply();
    }

    // ================================================================ dibujo

    private static void bitmap(GuiGraphics g, String[] rows, int x, int y, int color) {
        for (int r = 0; r < rows.length; r++) {
            String row = rows[r];
            for (int c = 0; c < row.length(); c++) {
                if (row.charAt(c) == '#') {
                    g.fill(x + c, y + r, x + c + 1, y + r + 1, color);
                }
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        long now = Util.getMillis();
        if (grab == TouchClient.Zone.NONE) {
            hover = TouchClient.pick(mouseX, mouseY, width, height);
        }
        TouchClient.Zone zone = grab != TouchClient.Zone.NONE ? grab : hover.zone;
        Entity target = mc.level == null ? null : mc.level.getEntity(targetId);

        // bandas arriba y abajo, como en una escena
        float open = Mth.clamp((now - TouchClient.openedAt) / 300F, 0, 1);
        int bar = (int) (16 * open);
        g.fill(0, 0, width, bar, 0xFF000000);
        g.fill(0, height - bar, width, height, 0xFF000000);

        // cartel
        Component title = Component.translatable("gui.starinteractions.touching", target == null ? Component.empty() : target.getDisplayName())
                .withStyle(ChatFormatting.BOLD);
        int tw = font.width(title) + 24;
        int tx = (width - tw) / 2;
        g.fillGradient(tx, 20, tx + tw, 38, 0xE01A1030, 0xE00A0614);
        g.fill(tx, 20, tx + tw, 21, 0xFFE2C98F);
        g.drawString(font, title, tx + 12, 25, 0xFFFFE9C0, true);
        Component hint = Component.translatable("gui.starinteractions.zone." + zone.name().toLowerCase());
        g.drawString(font, hint, (width - font.width(hint)) / 2, 42, 0xFFE8E2FF, true);
        Component exit = Component.translatable("gui.starinteractions.touch_exit");
        g.drawString(font, exit, (width - font.width(exit)) / 2, height - 12, 0xA0FFFFFF, false);

        // corazoncitos / puntos suspensivos segun como reacciona
        Poses.TouchView v = Poses.touchOfTarget(targetId);
        if (v != null && v.fx != 0) {
            float t = (now - v.fxAt) / 1100F;
            if (t < 1) {
                String txt = v.fx == 3 ? "+1 ❤" : v.fx == 1 ? "❤" : "…";
                int color = v.fx == 2 ? 0x9AA0B0 : 0xFF7FA8;
                int alpha = Math.max(8, (int) (255 * (1 - t * t)));
                g.pose().pushPose();
                g.pose().translate(mouseX + 14, mouseY - 10 - t * 22, 0);
                float sc = v.fx == 3 ? 1.7F : 1.2F;
                g.pose().scale(sc, sc, 1);
                g.drawString(font, txt, 0, 0, (alpha << 24) | color, true);
                g.pose().popPose();
            }
        }

        // la mano
        boolean closed = grab != TouchClient.Zone.NONE;
        boolean can = zone != TouchClient.Zone.NONE;
        int hx = mouseX - 5;
        int hy = mouseY - 5 + (closed && (grab == TouchClient.Zone.HEAD || grab == TouchClient.Zone.CHEEK) ? (int) (Math.sin(now / 90.0) * 1.5) : 0);
        bitmap(g, closed ? HAND_CLOSED : HAND_OPEN, hx + 1, hy + 1, 0x90000000);
        bitmap(g, closed ? HAND_CLOSED : HAND_OPEN, hx, hy, can ? 0xFFFFE2C4 : 0xB0D8D8E0);
    }
}
