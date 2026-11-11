package net.mca.client.dreams;

import net.mca.entity.VillagerLike;
import net.mca.entity.ai.Genetics;
import net.mca.entity.ai.relationship.AgeState;
import net.mca.entity.ai.relationship.Gender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Dreams: voces tipo "bip" (estilo Undertale / Animal Crossing) para los aldeanos de MCA.
 * <p>
 * Cada aldeano tiene una voz fija: el instrumento sale de su UUID, y el tono depende del genero,
 * la edad y el gen de tono de voz que MCA ya le asigna. Asi dos aldeanos nunca suenan igual,
 * y el mismo aldeano suena siempre igual.
 */
public final class VillagerVoice {
    @SuppressWarnings("unchecked")
    private static final RegistryEntry<SoundEvent>[] MALE = new RegistryEntry[]{
            SoundEvents.BLOCK_NOTE_BLOCK_BANJO, SoundEvents.BLOCK_NOTE_BLOCK_GUITAR,
            SoundEvents.BLOCK_NOTE_BLOCK_BIT, SoundEvents.BLOCK_NOTE_BLOCK_DIDGERIDOO,
            SoundEvents.BLOCK_NOTE_BLOCK_XYLOPHONE
    };
    @SuppressWarnings("unchecked")
    private static final RegistryEntry<SoundEvent>[] FEMALE = new RegistryEntry[]{
            SoundEvents.BLOCK_NOTE_BLOCK_BIT, SoundEvents.BLOCK_NOTE_BLOCK_FLUTE,
            SoundEvents.BLOCK_NOTE_BLOCK_HARP, SoundEvents.BLOCK_NOTE_BLOCK_XYLOPHONE,
            SoundEvents.BLOCK_NOTE_BLOCK_BANJO
    };

    private static final Random RANDOM = Random.create();
    private static final List<Burst> BURSTS = new ArrayList<>();

    private final SoundEvent sound;
    private final float pitch;
    private final float variance;
    private final float volume;

    private VillagerVoice(SoundEvent sound, float pitch, float variance, float volume) {
        this.sound = sound;
        this.pitch = pitch;
        this.variance = variance;
        this.volume = volume;
    }

    public static VillagerVoice of(VillagerLike<?> villager) {
        UUID uuid = villager.asEntity().getUuid();
        long seed = uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits();
        Gender gender = villager.getGenetics().getGender();
        AgeState age = villager.getAgeState();
        float tone = villager.getGenetics().getGene(Genetics.VOICE_TONE); // 0..1

        RegistryEntry<SoundEvent>[] pool = gender == Gender.FEMALE ? FEMALE : MALE;
        SoundEvent sound = pool[(int) Math.floorMod(seed, (long) pool.length)].value();

        float base = switch (gender) {
            case MALE -> 0.78F;
            case FEMALE -> 1.18F;
            default -> 0.98F;
        };
        // los chicos hablan mas agudo (MCA ya trae un tono por edad)
        base *= age == null || age == AgeState.UNASSIGNED ? 1.0F : age.getPitch();
        base += (tone - 0.5F) * 0.3F;
        // las voces graves suenan mejor un poco mas fuertes
        float volume = base < 0.9F ? 0.55F : 0.45F;
        return new VillagerVoice(sound, Math.max(0.5F, Math.min(2.0F, base)), 0.07F, volume);
    }

    /** Un bip "en la cabeza" del jugador (para la pantalla de conversacion). */
    public void bip() {
        float p = pitch + (RANDOM.nextFloat() * 2.0F - 1.0F) * variance;
        MinecraftClient.getInstance().getSoundManager()
                .play(PositionedSoundInstance.master(sound, Math.max(0.5F, Math.min(2.0F, p)), volume));
    }

    /** Un bip que sale del aldeano, en el mundo. */
    public void bipAt(Entity entity) {
        float p = pitch + (RANDOM.nextFloat() * 2.0F - 1.0F) * variance;
        MinecraftClient.getInstance().getSoundManager().play(new PositionedSoundInstance(
                sound, SoundCategory.NEUTRAL, volume, Math.max(0.5F, Math.min(2.0F, p)), RANDOM,
                entity.getX(), entity.getEyeY(), entity.getZ()));
    }

    /** true si el caracter suena (letras y numeros; espacios y signos no). */
    public static boolean voiced(char c) {
        return Character.isLetterOrDigit(c);
    }

    // ------------------------------------------------------------ frases sueltas en el mundo

    /** Reemplaza al TTS: unos bips desde el aldeano, tantos como la frase (con tope). */
    public static void speakAmbient(VillagerLike<?> villager, String phrase) {
        int letters = 0;
        for (int i = 0; i < phrase.length(); i++) {
            if (voiced(phrase.charAt(i))) {
                letters++;
            }
        }
        int count = Math.max(2, Math.min(18, letters / 3));
        BURSTS.removeIf(b -> b.entity == villager.asEntity());
        BURSTS.add(new Burst(of(villager), villager.asEntity(), count));
    }

    public static void tick() {
        Iterator<Burst> it = BURSTS.iterator();
        while (it.hasNext()) {
            Burst burst = it.next();
            if (burst.entity.isRemoved() || burst.remaining <= 0) {
                it.remove();
                continue;
            }
            if (--burst.cooldown <= 0) {
                burst.voice.bipAt(burst.entity);
                burst.remaining--;
                burst.cooldown = 2 + RANDOM.nextInt(2);
            }
        }
    }

    private static final class Burst {
        final VillagerVoice voice;
        final Entity entity;
        int remaining;
        int cooldown;

        Burst(VillagerVoice voice, Entity entity, int remaining) {
            this.voice = voice;
            this.entity = entity;
            this.remaining = remaining;
        }
    }
}
