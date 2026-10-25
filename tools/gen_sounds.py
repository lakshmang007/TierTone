"""Synthesizes TierTone's bundled notification tones into app/src/main/res/raw.

Each tone is short (under 1.3 s), mono 16-bit 22.05 kHz WAV, so all six together stay small.
Run from the repo root:  python tools/gen_sounds.py
"""
import math
import os
import struct
import wave

RATE = 22050
OUT = os.path.join(os.path.dirname(__file__), "..", "android", "app", "src", "main", "res", "raw")


def note(freq, dur, decay, partials=((1.0, 1.0),), attack=0.006):
    """A struck tone: a few partials (ratio, amplitude) under an exponential decay."""
    n = int(RATE * dur)
    out = []
    for i in range(n):
        t = i / RATE
        env = min(1.0, t / attack) * math.exp(-t / decay)
        s = sum(a * math.sin(2 * math.pi * freq * r * t) for r, a in partials)
        out.append(s * env)
    return out


def sweep(f0, f1, dur, decay):
    """A short pitch glide, used for the 'pop'."""
    n = int(RATE * dur)
    out, phase = [], 0.0
    for i in range(n):
        t = i / RATE
        f = f0 + (f1 - f0) * min(1.0, t / 0.06)
        phase += 2 * math.pi * f / RATE
        out.append(math.sin(phase) * min(1.0, t / 0.004) * math.exp(-t / decay))
    return out


def mix(length, *placed):
    """Overlay (offset_seconds, samples) pairs into one buffer of `length` seconds."""
    buf = [0.0] * int(RATE * length)
    for offset, samples in placed:
        start = int(RATE * offset)
        for i, s in enumerate(samples):
            if start + i < len(buf):
                buf[start + i] += s
    return buf


def write(name, samples, peak_db=-3.0):
    peak = max(abs(s) for s in samples) or 1.0
    gain = (10 ** (peak_db / 20)) / peak
    fade = int(RATE * 0.01)
    for i in range(fade):  # avoid clicks at the end
        samples[-1 - i] *= i / fade
    with wave.open(os.path.join(OUT, name + ".wav"), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, s * gain)) * 32767)) for s in samples))


BELL = ((1.0, 1.0), (2.0, 0.5), (2.76, 0.35), (5.4, 0.15))
MARIMBA = ((1.0, 1.0), (4.0, 0.25), (10.0, 0.05))
SOFT = ((1.0, 1.0), (2.0, 0.2))

os.makedirs(OUT, exist_ok=True)
# Important: a quick bright rising pair.
write("rise", mix(0.7, (0.0, note(659.25, 0.25, 0.08, SOFT)), (0.11, note(987.77, 0.55, 0.16, SOFT))))
# VIP default: two bell strikes, unmistakable.
write("bell", mix(1.3, (0.0, note(1046.5, 1.1, 0.35, BELL)), (0.2, note(1318.5, 1.1, 0.4, BELL))))
# Three-note arpeggio.
write("chime", mix(1.0, (0.0, note(1046.5, 0.5, 0.15, MARIMBA)), (0.1, note(1318.5, 0.5, 0.15, MARIMBA)),
                   (0.2, note(1568.0, 0.7, 0.22, MARIMBA))))
# Single clean high ping.
write("ping", note(1760.0, 0.6, 0.14, SOFT))
# Low bubbly pop.
write("pop", mix(0.3, (0.0, sweep(380, 900, 0.25, 0.05))))
# Two even beeps.
write("pulse", mix(0.5, (0.0, note(1174.7, 0.12, 0.05, SOFT)), (0.16, note(1174.7, 0.2, 0.06, SOFT))))
print("wrote tones to", os.path.abspath(OUT))
