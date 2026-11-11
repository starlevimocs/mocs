"""
Dreams: genera las "silabas" de voz de los aldeanos (sintesis aditiva con formantes de vocales).
Salida: common/src/main/resources/assets/mca/sounds/dreams/voice/<voz>_<vocal>.ogg
Uso: python3 tools/make_voices.py   (necesita numpy y ffmpeg)
"""
import os, subprocess, tempfile, wave
import numpy as np

SR = 44100
OUT = os.path.join(os.path.dirname(__file__), "..", "common", "src", "main", "resources", "assets", "mca", "sounds", "dreams", "voice")

# formantes (Hz, ancho de banda Hz, ganancia) para cada vocal
VOWELS = {
    "a": [(800, 80, 1.0), (1200, 90, 0.55), (2500, 120, 0.25), (3300, 150, 0.12)],
    "e": [(500, 70, 1.0), (1850, 100, 0.45), (2550, 120, 0.25), (3400, 150, 0.1)],
    "i": [(300, 60, 1.0), (2250, 100, 0.35), (2950, 130, 0.25), (3600, 160, 0.1)],
    "o": [(550, 70, 1.0), (880, 80, 0.6), (2450, 120, 0.15), (3200, 150, 0.08)],
    "u": [(330, 60, 1.0), (800, 80, 0.4), (2300, 120, 0.1), (3100, 150, 0.05)],
    "m": [(280, 60, 1.0), (1100, 150, 0.08), (2300, 200, 0.04), (3200, 250, 0.02)],  # zumbido nasal
}
VOICES = {
    # f0 base, escala de formantes, aire
    "low": (118.0, 1.0, 0.02),
    "high": (212.0, 1.16, 0.035),
}
DUR = 0.12


def envelope(n):
    t = np.arange(n) / SR
    attack = np.clip(t / 0.012, 0, 1)
    release = np.clip((DUR - t) / 0.05, 0, 1)
    return attack * release ** 1.5


def synth(f0, vowel, fscale, breath, seed):
    rng = np.random.default_rng(seed)
    n = int(SR * DUR)
    t = np.arange(n) / SR
    # un poco de vibrato y una caida de tono natural, como una silaba hablada
    f = f0 * (1.0 + 0.012 * np.sin(2 * np.pi * 5.5 * t)) * (1.0 - 0.06 * t / DUR)
    phase = 2 * np.pi * np.cumsum(f) / SR
    sig = np.zeros(n)
    for k in range(1, 60):
        fk = f0 * k
        if fk > 7000:
            break
        amp = 0.0
        for (F, B, G) in VOWELS[vowel]:
            F *= fscale
            amp += G / (1.0 + ((fk - F) / B) ** 2)
        amp *= 1.0 / k ** 0.6  # caida espectral de la voz
        sig += amp * np.sin(k * phase + rng.uniform(0, 0.3))
    sig += breath * rng.standard_normal(n) * envelope(n)
    sig *= envelope(n)
    sig /= np.max(np.abs(sig)) + 1e-9
    return (sig * 0.85 * 32767).astype(np.int16)


def main():
    os.makedirs(OUT, exist_ok=True)
    seed = 1
    for voice, (f0, fscale, breath) in VOICES.items():
        for vowel in VOWELS:
            data = synth(f0, vowel, fscale, breath, seed)
            seed += 1
            with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
                path = tmp.name
            with wave.open(path, "wb") as w:
                w.setnchannels(1)
                w.setsampwidth(2)
                w.setframerate(SR)
                w.writeframes(data.tobytes())
            out = os.path.join(OUT, f"{voice}_{vowel}.ogg")
            subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", path, "-c:a", "libvorbis", "-q:a", "5", out], check=True)
            os.remove(path)
            print("ok", out)


if __name__ == "__main__":
    main()
