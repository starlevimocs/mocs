package com.starlevi.starhero.client;

import com.starlevi.starhero.content.Instrument;
import com.starlevi.starhero.net.Net;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/** El show visto desde este cliente: quien toca que, el ritmo, lo que toca cada uno, la euforia. */
public final class GigClient {
    /** Un integrante de la banda en pantalla. */
    public static final class Performer {
        public final int entityId;
        public final Instrument instrument;
        public final boolean player;
        public final String name;
        public int pressed;
        public int fret;
        public final long[] laneHit = new long[8];
        public long lastHit;
        public int lastLane;
        public int strokes;
        int lastSub = Integer.MIN_VALUE;

        // ---- coreografia de este cuadro (la calcula StarPoses.update, suavizada)
        public long frameTime;
        public final float[] handL = {5, 12, -2};
        public final float[] handR = {-5, 12, -2};
        public float bodyX, bodyY, bodyZ;
        public float headX, headY, headZ;
        public float legA, legSpread, legYaw;
        public boolean sitting;
        /** desplazamiento de todo el cuerpo: al costado, arriba, al frente (bloques) y giro (grados). */
        public float ox, oy, oz, oyaw;
        public float guitarAngle = 0.42F;

        Performer(Net.Member m) {
            this.entityId = m.entityId();
            this.instrument = Instrument.byId(m.instrument());
            this.player = m.player();
            this.name = m.name();
        }

        void hit(int lane, long now) {
            lane = Math.max(0, Math.min(7, lane));
            laneHit[lane] = now;
            lastHit = now;
            lastLane = lane;
            strokes++;
            StageFx.onHit(this);
        }
    }

    public static Net.LobbyState lobby;

    static boolean active;
    static BlockPos amp = BlockPos.ZERO;
    static Direction facing = Direction.NORTH;
    static Vec3 center = Vec3.ZERO;
    static String song = "";
    static String title = "";
    static float bpm = 120;
    static int offsetMs;
    static int leadInMs;
    static long startMs;
    static final Map<Integer, Performer> PERFORMERS = new HashMap<>();
    static float hype = 0.5F;
    static int bandScore;
    static int bandCombo;

    // el jugador de este cliente
    static boolean localPerformer;
    static boolean cubStarted;
    static boolean resultSent;
    static long lastRunning;
    static CubOsuBridge.Poll lastPoll;
    static long[] lastFlash = new long[0];
    static int lastHits;
    static int lastMisses;
    static int lastPressed = -1;
    static int sendTimer;

    // cartel del final y saludo de la banda
    static Net.GigEnd ending;
    static long endingAt;
    static boolean finale;
    static long finaleUntil;
    static boolean cameraOwner;
    static long lastBeatIndex = Long.MIN_VALUE;

    private GigClient() {
    }

    public static boolean active() {
        return active;
    }

    public static Performer performer(int entityId) {
        return active ? PERFORMERS.get(entityId) : null;
    }

    public static boolean isPerformer(int entityId) {
        return active && PERFORMERS.containsKey(entityId);
    }

    public static Instrument instrumentOf(int entityId) {
        Performer p = performer(entityId);
        return p == null ? null : p.instrument;
    }

    public static float hype() {
        return hype;
    }

    /** Milisegundos de la cancion (negativo antes de que arranque). */
    public static double songMs(long now) {
        if (lastPoll != null && cubStarted && now - lastRunning < 300) {
            return lastPoll.songMs + (now - lastRunning);
        }
        return now - startMs - leadInMs;
    }

    public static double beatMs() {
        if (lastPoll != null && lastPoll.beatMs > 1) {
            return lastPoll.beatMs;
        }
        return 60000.0 / Math.max(30, bpm);
    }

    /** Beat actual (con fraccion). Negativo durante la cuenta regresiva. */
    public static double beat(long now) {
        return (songMs(now) - offsetMs) / beatMs();
    }

    // ================================================================ mensajes del servidor

    static void start(Net.GigStart msg) {
        Minecraft mc = Minecraft.getInstance();
        active = true;
        amp = msg.amp();
        facing = Direction.from2DDataValue(msg.facing());
        center = new Vec3(msg.cx(), msg.cy(), msg.cz());
        song = msg.song();
        title = msg.songTitle();
        bpm = msg.bpm() > 0 ? msg.bpm() : 120;
        offsetMs = msg.offsetMs();
        leadInMs = msg.leadInMs();
        startMs = Util.getMillis() + msg.countdownMs();
        hype = 0.5F;
        bandScore = 0;
        bandCombo = 0;
        ending = null;
        PERFORMERS.clear();
        localPerformer = false;
        for (Net.Member m : msg.members()) {
            PERFORMERS.put(m.entityId(), new Performer(m));
            if (mc.player != null && m.entityId() == mc.player.getId()) {
                localPerformer = true;
            }
        }
        cubStarted = false;
        resultSent = false;
        finale = false;
        cameraOwner = localPerformer;
        lastBeatIndex = Long.MIN_VALUE;
        lastPoll = null;
        lastFlash = new long[0];
        lastHits = 0;
        lastMisses = 0;
        lastPressed = -1;
        StarCamera.reset();
        if (mc.screen instanceof StageScreen) {
            mc.setScreen(null);
        }
    }

    static void notes(Net.GigNotes msg) {
        Performer p = performer(msg.entityId());
        if (p == null) {
            return;
        }
        long now = Util.getMillis();
        p.pressed = msg.pressed();
        p.fret = msg.fret();
        for (int i = 0; i < 8; i++) {
            if ((msg.hits() & (1 << i)) != 0) {
                p.hit(i, now);
            }
        }
    }

    static void state(Net.GigState msg) {
        hype = msg.hype();
        bandScore = msg.bandScore();
        bandCombo = msg.bandCombo();
    }

    static void end(Net.GigEnd msg) {
        ending = msg;
        endingAt = Util.getMillis();
        hype = msg.hype();
        if (cubStarted) {
            CubOsuBridge.stop();
        }
        cubStarted = false;
        localPerformer = false;
        if (active) {
            // la banda saluda unos segundos antes de volver a la normalidad
            finale = true;
            finaleUntil = endingAt + 5500;
        }
    }

    public static boolean finale() {
        return active && finale;
    }

    // ================================================================ cada tick

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!active || mc.player == null) {
            return;
        }
        if (mc.player.position().distanceToSqr(center) > 96 * 96) {
            active = false;
            PERFORMERS.clear();
            return;
        }
        long now = Util.getMillis();
        if (finale && now > finaleUntil) {
            active = false;
            finale = false;
            cameraOwner = false;
            PERFORMERS.clear();
            return;
        }
        // efectos al compas (luces, pirotecnia, humo)
        long beatIndex = (long) Math.floor(beat(now));
        if (beatIndex != lastBeatIndex) {
            lastBeatIndex = beatIndex;
            if (beatIndex >= 0 || finale) {
                StageFx.onBeat(beatIndex);
            }
        }
        StageFx.tick(now);
        if (!localPerformer || finale) {
            return;
        }
        if (!cubStarted) {
            if (now >= startMs) {
                cubStarted = true;
                lastRunning = now;
                if (!CubOsuBridge.start(song)) {
                    mc.player.displayClientMessage(Component.translatable("message.starhero.missing_song", title).withStyle(ChatFormatting.RED), false);
                    sendResult(0, 0, 0, 0, "", false);
                    localPerformer = false;
                }
            }
            return;
        }
        CubOsuBridge.Poll poll = CubOsuBridge.poll();
        if (poll == null) {
            if (!resultSent && now - lastRunning > 2000) {
                // CubOsu termino sin avisar (lo cerraron): se manda lo ultimo que se vio
                CubOsuBridge.Poll last = lastPoll;
                sendResult(last == null ? 0 : last.score, -1, last == null ? 0 : last.combo, last == null ? 0 : last.misses, "", false);
            }
            return;
        }
        lastRunning = now;
        lastPoll = poll;
        int pressed = 0;
        for (int i = 0; i < poll.down.length && i < 8; i++) {
            if (poll.down[i]) {
                pressed |= 1 << i;
            }
        }
        int hits = 0;
        for (int i = 0; i < poll.flash.length && i < 8; i++) {
            long before = i < lastFlash.length ? lastFlash[i] : 0;
            if (poll.flash[i] != before && poll.flash[i] != 0) {
                hits |= 1 << i;
            }
        }
        lastFlash = poll.flash;
        int fret = 0;
        for (int i = 7; i >= 0; i--) {
            if ((pressed & (1 << i)) != 0) {
                fret = i;
                break;
            }
        }
        Performer me = PERFORMERS.get(mc.player.getId());
        if (me != null) {
            me.pressed = pressed;
            me.fret = fret;
            for (int i = 0; i < 8; i++) {
                if ((hits & (1 << i)) != 0) {
                    me.hit(i, now);
                }
            }
        }
        int newHits = Math.max(0, poll.hits - lastHits);
        int newMisses = Math.max(0, poll.misses - lastMisses);
        lastHits = poll.hits;
        lastMisses = poll.misses;
        if (pressed != lastPressed || hits != 0 || newMisses > 0 || ++sendTimer >= 5) {
            sendTimer = 0;
            lastPressed = pressed;
            Net.toServer(new Net.PerformerUpdate(pressed, hits, fret, poll.combo, poll.score, poll.misses, newHits, newMisses));
        }
    }

    static void sendResult(int score, float accuracy, int combo, int misses, String rank, boolean completed) {
        if (resultSent) {
            return;
        }
        resultSent = true;
        Net.toServer(new Net.PerformerResult(score, accuracy, combo, misses, rank, completed));
    }

    /** Resultado que avisa CubOsu al terminar la cancion. */
    static void onCubOsuFinish(int score, float accuracy, int combo, int misses, String rank, boolean success) {
        if (active && localPerformer) {
            sendResult(score, accuracy, combo, misses, rank, success);
        }
    }

    static Vec3 forward() {
        return new Vec3(facing.getStepX(), 0, facing.getStepZ());
    }

    static Vec3 side() {
        return new Vec3(-facing.getStepZ(), 0, facing.getStepX());
    }
}
