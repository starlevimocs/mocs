#!/usr/bin/env python3
"""Sintetiza los sonidos de Star Combat (originales, hechos con ruido y senos). Uso: python3 tools/make_sounds.py"""
import os, subprocess, tempfile
import numpy as np
from scipy.io import wavfile
from scipy.signal import butter, sosfilt

SR = 44100
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'starcombat', 'sounds')
os.makedirs(OUT, exist_ok=True)
rng = np.random.default_rng(11)

def t(dur):
    return np.arange(int(SR * dur)) / SR

def band(x, lo, hi):
    return sosfilt(butter(2, [lo, hi], btype='band', fs=SR, output='sos'), x)

def low(x, f):
    return sosfilt(butter(2, f, btype='low', fs=SR, output='sos'), x)

def high(x, f):
    return sosfilt(butter(2, f, btype='high', fs=SR, output='sos'), x)

def sweep_noise(dur, f0, f1, f2, q=0.55):
    """Ruido que pasa por un filtro que sube y baja: el silbido de algo que corta el aire."""
    n = int(SR * dur)
    x = rng.standard_normal(n + 2048)
    out = np.zeros(n)
    hop = 256
    win = np.hanning(hop * 2)
    for i in range(0, n - hop * 2, hop):
        u = i / n
        f = f0 + (f1 - f0) * (u / 0.45) if u < 0.45 else f1 + (f2 - f1) * ((u - 0.45) / 0.55)
        seg = band(x[i:i + hop * 2 + 512], f * (1 - q), f * (1 + q))[256:256 + hop * 2]
        out[i:i + hop * 2] += seg * win
    return out

def env(n, attack, release, power=2.0):
    u = np.arange(n) / n
    e = np.minimum(1, u / max(1e-4, attack)) * np.minimum(1, (1 - u) / max(1e-4, release)) ** power
    return np.clip(e, 0, 1)

def crackle(dur, rate, seed=0):
    r = np.random.default_rng(seed)
    n = int(SR * dur)
    x = np.zeros(n)
    for _ in range(int(dur * rate)):
        i = r.integers(0, n - 400)
        k = r.integers(40, 300)
        x[i:i + k] += r.standard_normal(k) * np.exp(-np.arange(k) / (k / 5)) * r.uniform(0.3, 1)
    return high(x, 1500)

def ticks(dur, count, seed=0, accel=False):
    """Eslabones que chocan: golpecitos metalicos cortos."""
    r = np.random.default_rng(seed)
    n = int(SR * dur)
    x = np.zeros(n + SR // 4)
    for j in range(count):
        u = (j + r.uniform(0, 0.7)) / count
        if accel:
            u = u ** 0.6
        i = int(u * (n - 1))
        k = int(SR * r.uniform(0.02, 0.05))
        tt = np.arange(k) / SR
        tick = np.zeros(k)
        for f in r.uniform(2100, 7200, 4):
            tick += np.sin(2 * np.pi * f * tt + r.uniform(0, 6)) * np.exp(-tt * r.uniform(90, 220))
        tick += r.standard_normal(k) * np.exp(-tt * 600) * 0.6
        x[i:i + k] += tick * r.uniform(0.35, 1)
    return x[:n]

def save(name, x, peak=0.9):
    x = x / (np.max(np.abs(x)) + 1e-9) * peak
    fade = int(SR * 0.008)
    x[:fade] *= np.linspace(0, 1, fade)
    x[-fade:] *= np.linspace(1, 0, fade)
    with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as f:
        wav = f.name
    wavfile.write(wav, SR, (x * 32767).astype(np.int16))
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav, '-ac', '1', '-c:a', 'libvorbis', '-q:a', '4',
                    os.path.join(OUT, name + '.ogg')], check=True)
    os.unlink(wav)

# --- tajos livianos: silbido corto con chispas
for i, (f0, f1, f2) in enumerate([(500, 2600, 900), (650, 3100, 1100), (420, 2200, 800)]):
    d = 0.3
    x = sweep_noise(d, f0, f1, f2) * env(int(SR * d), 0.25, 0.6)
    x += low(rng.standard_normal(len(x)), 500) * env(len(x), 0.3, 0.6) * 0.5      # cuerpo de fuego
    x += crackle(d, 60, i) * env(len(x), 0.2, 0.5) * 0.25
    save('swing%d' % (i + 1), x)

# --- tajos pesados: mas largos y graves
for i, (f0, f1, f2) in enumerate([(220, 1500, 380), (260, 1800, 420)]):
    d = 0.6
    x = sweep_noise(d, f0, f1, f2, 0.6) * env(int(SR * d), 0.3, 0.6)
    x += low(rng.standard_normal(len(x)), 300) * env(len(x), 0.35, 0.6) * 0.9
    x += crackle(d, 70, 10 + i) * env(len(x), 0.3, 0.5) * 0.25
    save('swing_heavy%d' % (i + 1), x)

# --- cadenas
for i in range(3):
    d = 0.32
    x = ticks(d, 11 + i * 2, 20 + i) * env(int(SR * d), 0.05, 0.7, 1.0)
    save('chain%d' % (i + 1), x, 0.7)

# --- golpe que entra: corte + golpe sordo
for i in range(3):
    d = 0.28
    tt = t(d)
    x = high(rng.standard_normal(len(tt)), 2500) * np.exp(-tt * 38) * 0.8
    for f in (1700 + i * 210, 2950 + i * 130, 4300):
        x += np.sin(2 * np.pi * f * tt) * np.exp(-tt * 30) * 0.22
    x += np.sin(2 * np.pi * (150 - 60 * tt / d) * tt) * np.exp(-tt * 22) * 1.1
    x += crackle(d, 80, 30 + i) * np.exp(-tt * 8) * 0.3
    save('hit%d' % (i + 1), x)

# --- estallido contra el suelo
d = 1.1
tt = t(d)
f = 95 * np.exp(-tt * 3.2) + 38
x = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-tt * 4.5) * 1.5
x += low(rng.standard_normal(len(tt)), 900) * np.exp(-tt * 9) * 1.2
x += band(rng.standard_normal(len(tt)), 300, 2500) * np.exp(-tt * 3.5) * (0.6 + 0.4 * np.sin(2 * np.pi * 23 * tt)) * 0.5
x += crackle(d, 120, 40) * np.exp(-tt * 2.5) * 0.5
save('slam', x)

# --- brasas que se encienden (antes de caer)
d = 0.7
tt = t(d)
x = low(rng.standard_normal(len(tt)), 700) * (0.7 + 0.3 * np.sin(2 * np.pi * 17 * tt)) * env(len(tt), 0.45, 0.4)
x += sweep_noise(d, 300, 1400, 900) * env(len(tt), 0.5, 0.35) * 0.6
x += crackle(d, 90, 50) * env(len(tt), 0.4, 0.4) * 0.4
save('ignite', x, 0.8)

# --- gancho: cadena que sale disparada
d = 0.5
x = ticks(d, 26, 60, accel=True) * env(int(SR * d), 0.03, 0.5, 1.0) * 0.7
x += sweep_noise(d, 500, 3200, 2200) * env(int(SR * d), 0.2, 0.7) * 0.9
save('grapple', x, 0.8)

# --- gancho que se clava
d = 0.4
tt = t(d)
x = np.zeros(len(tt))
for f, a, dec in ((690, 1.0, 14), (1130, 0.7, 18), (1910, 0.5, 24), (2890, 0.35, 30), (4370, 0.2, 40)):
    x += np.sin(2 * np.pi * f * tt) * np.exp(-tt * dec) * a
x += rng.standard_normal(len(tt)) * np.exp(-tt * 250) * 1.5
x += np.sin(2 * np.pi * 110 * tt) * np.exp(-tt * 30) * 0.8
save('latch', x, 0.85)
print(sorted(os.listdir(OUT)))
