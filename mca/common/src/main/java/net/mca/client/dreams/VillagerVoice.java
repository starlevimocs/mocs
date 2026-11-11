package net.mca.client.dreams;

import net.mca.entity.VillagerLike;
import net.mca.entity.ai.Genetics;
import net.mca.entity.ai.relationship.AgeState;
import net.mca.entity.ai.relationship.Gender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

import java.text.Normalizer;
import java.util.*;

/**
 * Dreams: voces de los aldeanos estilo "animalese" (Animal Crossing / Undertale), pero mas humanas.
 * <p>
 * Cada silaba del texto suena con una vocal sintetizada (a, e, i, o, u, y un zumbido "m"), asi la voz sigue
 * lo que se esta diciendo. El tono baja de a poco durante la frase como en el habla real, sube al final de una
 * pregunta, y cambia segun la emocion: feliz (mas agudo y saltarin), triste (grave, lento, bajito) o enojado
 * (rapido, fuerte, con golpes). Cada aldeano tiene su propia voz fija (genero, edad, gen de tono y su UUID).
 */
public final class VillagerVoice {
    public enum Emotion {
        NEUTRAL(1.0F, 0.035F, 1.0F, 1.0F),
        HAPPY(1.10F, 0.07F, 1.15F, 1.0F),
        SAD(0.88F, 0.02F, 0.72F, 0.75F),
        ANGRY(0.95F, 0.05F, 1.25F, 1.2F);

        final float pitch;
        final float variance;
        final float speed;
        final float volume;

        Emotion(float pitch, float variance, float speed, float volume) {
            this.pitch = pitch;
            this.variance = variance;
            this.speed = speed;
            this.volume = volume;
        }
    }

    private static final Random RANDOM = Random.create();
    private static final List<Burst> BURSTS = new ArrayList<>();
    private static final Map<String, SoundEvent> SOUNDS = new HashMap<>();

    private final String register; // "low" o "high"
    private final float pitch;
    private final float volume;

    private VillagerVoice(String register, float pitch, float volume) {
        this.register = register;
        this.pitch = pitch;
        this.volume = volume;
    }

    public static VillagerVoice of(VillagerLike<?> villager) {
        UUID uuid = villager.asEntity().getUuid();
        long seed = uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits();
        Random personal = Random.create(seed);
        Gender gender = villager.getGenetics().getGender();
        AgeState age = villager.getAgeState();
        float tone = villager.getGenetics().getGene(Genetics.VOICE_TONE); // 0..1

        String register = gender == Gender.FEMALE ? "high" : gender == Gender.MALE ? "low" : (personal.nextBoolean() ? "high" : "low");
        float p = 1.0F + (tone - 0.5F) * 0.28F + (personal.nextFloat() - 0.5F) * 0.08F;
        if (age != null && age != AgeState.UNASSIGNED && age != AgeState.ADULT) {
            p *= age.getPitch();
            if ("low".equals(register)) {
                p *= 1.15F; // los chicos todavia no tienen la voz grave
            }
        }
        return new VillagerVoice(register, clamp(p), "low".equals(register) ? 0.7F : 0.6F);
    }

    private static float clamp(float p) {
        return Math.max(0.5F, Math.min(2.0F, p));
    }

    private SoundEvent sound(char vowel) {
        String key = register + "_" + vowel;
        return SOUNDS.computeIfAbsent(key, k -> SoundEvent.of(new Identifier("mca", "dreams.voice." + k)));
    }

    // ------------------------------------------------------------------ emocion

    /** Adivina la emocion de una frase por su clave de traduccion, el animo del aldeano y la puntuacion. */
    public static Emotion emotionOf(VillagerLike<?> villager, Text text) {
        String key = "";
        if (text != null && text.getContent() instanceof TranslatableTextContent t) {
            key = t.getKey().toLowerCase(Locale.ROOT);
        }
        if (key.contains("slap") || key.contains("angry") || key.contains("insult") || key.contains("mad")) {
            return Emotion.ANGRY;
        }
        if (key.contains("fail") || key.contains("sad") || key.contains("cry") || key.contains("divorce")) {
            return Emotion.SAD;
        }
        if (key.contains("success") || key.contains("joke") || key.contains("happy") || key.contains("love")) {
            return Emotion.HAPPY;
        }
        String mood = villager.getVillagerBrain().getMood().getName().toLowerCase(Locale.ROOT);
        String s = text == null ? "" : text.getString().trim();
        if (mood.equals("depressed") || mood.equals("sad")) {
            return s.endsWith("!") ? Emotion.ANGRY : Emotion.SAD;
        }
        if (mood.equals("happy") || mood.equals("overjoyed")) {
            return Emotion.HAPPY;
        }
        if (mood.equals("unhappy") && s.endsWith("!")) {
            return Emotion.ANGRY;
        }
        return Emotion.NEUTRAL;
    }

    // ------------------------------------------------------------------ plan de silabas

    /** Que vocal y que tono tiene cada silaba del texto, y en que letra empieza. */
    public final class Speech {
        public final Emotion emotion;
        final int[] syllableAt;   // por letra: indice de silaba que empieza ahi, o -1
        final char[] vowels;
        final float[] pitches;

        Speech(String text, Emotion emotion) {
            this.emotion = emotion;
            String plain = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
            syllableAt = new int[text.length()];
            Arrays.fill(syllableAt, -1);
            List<Character> vs = new ArrayList<>();
            List<Integer> wordPos = new ArrayList<>();
            List<Boolean> wordEnd = new ArrayList<>();
            int n = Math.min(plain.length(), text.length());
            boolean inVowel = false;
            int wordSyllable = 0;
            for (int i = 0; i < n; i++) {
                char c = plain.charAt(i);
                boolean letter = Character.isLetter(c);
                boolean vowel = "aeiouy".indexOf(c) >= 0 && !(c == 'y' && i + 1 < n && "aeiou".indexOf(plain.charAt(i + 1)) >= 0);
                if (!letter) {
                    if (wordSyllable > 0 && !wordEnd.isEmpty()) {
                        wordEnd.set(wordEnd.size() - 1, true);
                    }
                    wordSyllable = 0;
                    inVowel = false;
                    continue;
                }
                if (vowel && !inVowel) {
                    syllableAt[i] = vs.size();
                    vs.add(c == 'y' ? 'i' : c);
                    wordPos.add(wordSyllable++);
                    wordEnd.add(false);
                }
                inVowel = vowel;
            }
            // palabras sin vocales (raro): un zumbido
            if (vs.isEmpty() && n > 0) {
                syllableAt[0] = 0;
                vs.add('m');
                wordPos.add(0);
                wordEnd.add(true);
            }

            int total = vs.size();
            vowels = new char[total];
            pitches = new float[total];
            boolean question = text.trim().endsWith("?");
            for (int i = 0; i < total; i++) {
                vowels[i] = vs.get(i);
                float progress = total <= 1 ? 0 : i / (float) (total - 1);
                float p = pitch * emotion.pitch;
                p *= 1.0F + 0.07F * (1.0F - progress);                // el tono cae durante la frase
                if (wordPos.get(i) == 0 && emotion == Emotion.ANGRY) {
                    p *= 1.07F;                                       // golpes al empezar cada palabra
                }
                if (emotion == Emotion.HAPPY) {
                    p *= (i % 2 == 0) ? 1.04F : 0.98F;                // saltarin
                }
                if (emotion == Emotion.SAD) {
                    p *= 1.0F - 0.06F * progress;                     // se apaga al final
                }
                if (question && i >= total - 3) {
                    p *= 1.0F + 0.09F * (i - (total - 4));            // sube al final de la pregunta
                }
                pitches[i] = clamp(p);
            }
        }

        public boolean startsSyllable(int charIndex) {
            return charIndex >= 0 && charIndex < syllableAt.length && syllableAt[charIndex] >= 0;
        }

        public void play(int charIndex) {
            int s = syllableAt[charIndex];
            say(vowels[s], pitches[s], emotion);
        }
    }

    public Speech plan(String text, Emotion emotion) {
        return new Speech(text, emotion);
    }

    private void say(char vowel, float p, Emotion emotion) {
        float jitter = 1.0F + (RANDOM.nextFloat() * 2.0F - 1.0F) * emotion.variance;
        float vol = volume * emotion.volume * (0.85F + RANDOM.nextFloat() * 0.15F);
        MinecraftClient.getInstance().getSoundManager()
                .play(PositionedSoundInstance.master(sound(vowel), clamp(p * jitter), vol));
    }

    private void sayAt(Entity entity, char vowel, float p, Emotion emotion) {
        float jitter = 1.0F + (RANDOM.nextFloat() * 2.0F - 1.0F) * emotion.variance;
        float vol = volume * emotion.volume;
        MinecraftClient.getInstance().getSoundManager().play(new PositionedSoundInstance(
                sound(vowel), SoundCategory.NEUTRAL, vol, clamp(p * jitter), RANDOM,
                entity.getX(), entity.getEyeY(), entity.getZ()));
    }

    /** Velocidad del texto (letras por segundo) para esta emocion. */
    public static float lettersPerSecond(Emotion emotion) {
        return 40.0F * emotion.speed;
    }

    // ------------------------------------------------------------------ frases sueltas en el mundo

    /** Reemplaza al TTS: la frase dicha desde el aldeano, silaba por silaba (con tope para frases largas). */
    public static void speakAmbient(VillagerLike<?> villager, Text text, String resolved) {
        VillagerVoice voice = of(villager);
        Speech speech = voice.plan(resolved, emotionOf(villager, text));
        BURSTS.removeIf(b -> b.entity == villager.asEntity());
        BURSTS.add(new Burst(voice, speech, villager.asEntity()));
    }

    public static void tick() {
        Iterator<Burst> it = BURSTS.iterator();
        while (it.hasNext()) {
            Burst b = it.next();
            if (b.entity.isRemoved() || b.next >= b.speech.vowels.length || b.next >= 24) {
                it.remove();
                continue;
            }
            if (--b.cooldown <= 0) {
                b.voice.sayAt(b.entity, b.speech.vowels[b.next], b.speech.pitches[b.next], b.speech.emotion);
                b.next++;
                float ticks = 2.6F / b.speech.emotion.speed;
                b.cooldown = Math.max(1, Math.round(ticks + RANDOM.nextFloat()));
            }
        }
    }

    private static final class Burst {
        final VillagerVoice voice;
        final Speech speech;
        final Entity entity;
        int next;
        int cooldown;

        Burst(VillagerVoice voice, Speech speech, Entity entity) {
            this.voice = voice;
            this.speech = speech;
            this.entity = entity;
        }
    }
}
