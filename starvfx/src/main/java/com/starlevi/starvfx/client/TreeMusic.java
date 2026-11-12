package com.starlevi.starvfx.client;

import com.starlevi.starvfx.StarVfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.LoopingAudioStream;
import net.minecraft.client.sounds.OggAudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * La musica del Arbol Madre: suena en bucle y sube o baja segun cuan cerca se esta. Puede ser un sonido del juego
 * o de un resource pack ("minecraft:music_disc.otherside") o un archivo propio: "file:calma" toca
 * {@code config/starvfx/music/calma.ogg}.
 */
final class TreeMusic extends AbstractTickableSoundInstance {
    private static TreeMusic current;
    private static String currentKey = "";

    private final Path file;
    private float target;
    private float max = 1;

    private TreeMusic(ResourceLocation event, Path file) {
        super(SoundEvent.createVariableRangeEvent(event), SoundSource.RECORDS, SoundInstance.createUnseededRandom());
        this.file = file;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.001F;
        this.relative = true;
        this.attenuation = Attenuation.NONE;
    }

    static Path dir() {
        return FMLPaths.CONFIGDIR.get().resolve("starvfx").resolve("music");
    }

    /** Cada tick: que musica tiene que sonar (key vacia = ninguna) y con que fuerza. */
    static void update(String key, float aura, float volume) {
        Minecraft mc = Minecraft.getInstance();
        if (current != null && (current.isStopped() || !mc.getSoundManager().isActive(current)) && current.started) {
            // se termino (o no se pudo tocar): no se reintenta hasta que el jugador se aleje y vuelva
            current = null;
        }
        if (current == null && aura < 0.02F) {
            currentKey = "";
        }
        if (key == null || key.isEmpty() || key.equals("none")) {
            if (current != null) {
                current.target = 0;
            }
            return;
        }
        if (!key.equals(currentKey)) {
            if (current != null) {
                current.stop();
                current = null;
            }
            if (aura < 0.02F) {
                return;
            }
            TreeMusic next = create(key);
            currentKey = key;
            if (next == null) {
                return;
            }
            current = next;
            // que la musica del juego no se le monte encima
            mc.getMusicManager().stopPlaying();
            mc.getSoundManager().play(next);
        }
        if (current != null) {
            current.target = aura;
            current.max = volume;
        }
    }

    static void stopAll() {
        if (current != null) {
            current.stop();
        }
        current = null;
        currentKey = "";
    }

    private static TreeMusic create(String key) {
        try {
            if (key.startsWith("file:")) {
                String name = key.substring(5).replaceAll("[^A-Za-z0-9_\\-]", "");
                Path path = dir().resolve(name + ".ogg");
                if (!Files.isRegularFile(path)) {
                    Files.createDirectories(dir());
                    StarVfx.LOGGER.warn("[Star VFX] No esta la musica {}", path);
                    return null;
                }
                return new TreeMusic(new ResourceLocation(StarVfx.MOD_ID, "tree_music"), path);
            }
            ResourceLocation id = ResourceLocation.tryParse(key);
            return id == null ? null : new TreeMusic(id, null);
        } catch (IOException | RuntimeException e) {
            StarVfx.LOGGER.warn("[Star VFX] No se pudo preparar la musica {}", key, e);
            return null;
        }
    }

    private boolean started;

    @Override
    public WeighedSoundEvents resolve(SoundManager manager) {
        started = true;
        if (file == null) {
            return super.resolve(manager);
        }
        // archivo propio: no pasa por los sonidos del juego, se lee directo del disco
        this.sound = new Sound(StarVfx.MOD_ID + ":tree_music", ConstantFloat.of(1), ConstantFloat.of(1), 1, Sound.Type.FILE, true, false, 16);
        return new WeighedSoundEvents(getLocation(), null);
    }

    @Override
    public CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers, Sound sound, boolean looping) {
        if (file == null) {
            return super.getStream(buffers, sound, looping);
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                return new LoopingAudioStream(OggAudioStream::new, new BufferedInputStream(Files.newInputStream(file)));
            } catch (IOException e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }, net.minecraft.Util.backgroundExecutor());
    }

    @Override
    public void tick() {
        float want = Math.max(0, Math.min(1, target)) * max;
        volume += Math.max(-0.012F, Math.min(0.012F, want - volume));
        if (target <= 0 && volume <= 0.002F) {
            stop();
        }
        if (volume < 0.001F) {
            volume = 0.001F;
        }
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
