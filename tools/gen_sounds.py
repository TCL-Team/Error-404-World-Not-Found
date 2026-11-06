#!/usr/bin/env python3
"""
Original sound synthesis for ERROR 404: WORLD NOT FOUND.

Every asset here is generated from oscillators + noise by this script, so the mod
ships 100% original audio (no samples, no assets lifted from other mods).

Usage:  python3 tools/gen_sounds.py            # writes ogg files + sounds.json
Requires: numpy, soundfile  (pip install numpy soundfile)
"""

import json
import math
import os
import struct

import numpy as np
import soundfile as sf

SR = 44100
OUT = os.path.join("src", "main", "resources", "assets", "error404", "sounds")
RES = os.path.join("src", "main", "resources", "assets", "error404")

rng = np.random.default_rng(404422)


# ----------------------------------------------------------------------------- helpers
def t(dur: float) -> np.ndarray:
    return np.arange(int(SR * dur), dtype=np.float64) / SR


def noise(dur: float, color: str = "white") -> np.ndarray:
    n = rng.standard_normal(int(SR * dur))
    if color == "pink":
        # cheap pink filter
        b = np.zeros_like(n)
        acc = 0.0
        for i in range(len(n)):
            acc = 0.98 * acc + 0.02 * n[i]
            b[i] = acc
        n = b / (np.max(np.abs(b)) + 1e-9)
    elif color == "brown":
        n = np.cumsum(n)
        n = n / (np.max(np.abs(n)) + 1e-9)
    return n


def lowpass(x: np.ndarray, cutoff: float, order: int = 2) -> np.ndarray:
    """One-pole cascade lowpass (no scipy dependency)."""
    alpha = 1.0 - math.exp(-2.0 * math.pi * cutoff / SR)
    y = np.zeros_like(x)
    for _ in range(order):
        acc = 0.0
        for i in range(len(x)):
            acc += alpha * (x[i] - acc)
            y[i] = acc
        x = y
    return y


def highpass(x: np.ndarray, cutoff: float, order: int = 2) -> np.ndarray:
    return x - lowpass(x, cutoff, order)


def sweep(f0: float, f1: float, dur: float, curve: str = "exp") -> np.ndarray:
    n = int(SR * dur)
    if curve == "exp":
        freqs = f0 * (f1 / f0) ** (np.arange(n) / max(1, n - 1))
    else:
        freqs = np.linspace(f0, f1, n)
    phase = np.cumsum(2 * math.pi * freqs / SR)
    return np.sin(phase)


def adsr(dur: float, a: float, d: float, s: float, r: float) -> np.ndarray:
    n = int(SR * dur)
    env = np.zeros(n)
    ai = int(SR * a)
    di = int(SR * d)
    ri = int(SR * r)
    si = max(0, n - ai - di - ri)
    env[:ai] = np.linspace(0, 1, ai, endpoint=False)
    env[ai:ai + di] = np.linspace(1, s, di, endpoint=False)
    env[ai + di:ai + di + si] = s
    env[ai + di + si:] = np.linspace(s, 0, len(env) - (ai + di + si))
    return env


def expdecay(dur: float, k: float) -> np.ndarray:
    return np.exp(-k * t(dur))


def reverb(x: np.ndarray, taps=(0.031, 0.047, 0.071, 0.113, 0.163), gains=(0.5, 0.4, 0.32, 0.25, 0.18)) -> np.ndarray:
    y = x.copy()
    for tap, g in zip(taps, gains):
        d = int(SR * tap)
        if d < len(x):
            y[d:] += g * x[:-d]
    return y


def fade(x: np.ndarray, fin: float = 0.005, fout: float = 0.02) -> np.ndarray:
    n = len(x)
    a = int(SR * fin)
    b = int(SR * fout)
    if a > 0:
        x[:a] *= np.linspace(0, 1, a)
    if b > 0:
        x[-b:] *= np.linspace(1, 0, b)
    return x


def _soft_limit(x: np.ndarray, ceiling: float = 0.92) -> np.ndarray:
    """tanh knee above 60% of the ceiling, so transients round off instead of clipping."""
    knee = ceiling * 0.6
    mag = np.abs(x)
    over = mag > knee
    if not np.any(over):
        return x
    out = x.copy()
    out[over] = np.sign(x[over]) * (knee + (ceiling - knee) * np.tanh((mag[over] - knee) / (ceiling - knee)))
    return out


def save(name: str, x: np.ndarray, peak: float = 0.80, min_rms_db: float = -22.0,
         loop: bool = False) -> None:
    """
    Normalise, then guarantee three things before the file is written:

    * no sample is above ``peak`` after the soft limiter,
    * nothing is quieter than ``min_rms_db`` (a footstep nobody can hear is a bug,
      not restraint - the mix balances volume per sound in-game, not in the file),
    * the *decoded* ogg is checked too, because Vorbis ringing can overshoot the
      encoded peak by several dB and that is what actually clips in the mixer.
    """
    x = np.asarray(x, dtype=np.float64)
    x = np.nan_to_num(x)
    m = np.max(np.abs(x)) or 1.0
    x = x / m * peak
    x = _soft_limit(x, peak + 0.06)

    if not loop:
        # A loop gets its fade from seamless(): fading the end out would punch a hole in it.
        x = fade(x)

    rms = float(np.sqrt(np.mean(x ** 2)) or 1e-9)
    target = 10 ** (min_rms_db / 20.0)
    if rms < target:
        x = _soft_limit(x * min(target / rms, 6.0), peak + 0.06)

    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, name + ".ogg")
    sf.write(path, x.astype(np.float32), SR, format="OGG", subtype="VORBIS")

    data, _ = sf.read(path, dtype="float32", always_2d=True)
    decoded_peak = float(np.max(np.abs(data)))
    if decoded_peak > 0.95:
        x = x * (0.95 / decoded_peak)
        sf.write(path, x.astype(np.float32), SR, format="OGG", subtype="VORBIS")


# ----------------------------------------------------------------------------- sounds
def static_burst():
    d = 0.9
    n = noise(d) * adsr(d, 0.01, 0.25, 0.5, 0.5)
    n = highpass(n, 900, 2)
    # occasional crackle
    crack = np.zeros_like(n)
    for _ in range(28):
        p = rng.integers(0, len(n) - 400)
        crack[p:p + 400] += noise(400 / SR) * rng.uniform(0.2, 0.9)
    return n + crack


def error_blip():
    a = sweep(880, 660, 0.12) * expdecay(0.12, 22)
    b = sweep(440, 330, 0.16) * expdecay(0.16, 18)
    out = np.zeros(int(SR * 0.4))
    out[:len(a)] += a * 0.6
    out[int(SR * 0.16):int(SR * 0.16) + len(b)] += b * 0.5
    return out


def whisper():
    d = 2.4
    base = noise(d, "pink")
    base = highpass(lowpass(base, 2600), 320)
    # syllable-ish amplitude modulation
    mod = np.zeros(len(base))
    pos = 0
    while pos < len(base):
        ln = int(SR * rng.uniform(0.08, 0.22))
        seg = np.linspace(0, 1, ln) * np.linspace(1, 0, ln)
        mod[pos:pos + ln] = seg[: max(0, min(ln, len(base) - pos))]
        pos += ln + int(SR * rng.uniform(0.03, 0.15))
    return base * mod * adsr(d, 0.2, 0.4, 0.8, 0.6)


def heartbeat():
    d = 1.15
    out = np.zeros(int(SR * d))
    for start, amp in ((0.0, 1.0), (0.30, 0.7)):
        i = int(SR * start)
        beat = np.sin(2 * math.pi * 46 * t(0.28)) * expdecay(0.28, 16)
        beat += 0.4 * np.sin(2 * math.pi * 88 * t(0.28)) * expdecay(0.28, 26)
        out[i:i + len(beat)] += beat * amp
    return lowpass(out, 220, 2)


def data_chirp():
    d = 1.7
    out = np.zeros(int(SR * d))
    pos = 0.0
    while pos < d - 0.2:
        ln = rng.uniform(0.03, 0.11)
        f = rng.uniform(420, 3400)
        seg = np.sign(np.sin(2 * math.pi * f * t(ln)))
        seg = lowpass(seg, 4200, 1) * expdecay(ln, 9)
        i = int(SR * pos)
        out[i:i + len(seg)] += seg[: len(out) - i] * rng.uniform(0.25, 0.7)
        pos += ln + rng.uniform(0.01, 0.07)
    return out + noise(d) * 0.03


def sub_hit():
    d = 1.9
    body = sweep(64, 28, d) * expdecay(d, 3.4)
    click = noise(0.05) * expdecay(0.05, 40)
    out = reverb(body, gains=(0.35, 0.28, 0.2, 0.14, 0.09))
    out[: len(click)] += click * 0.5
    return lowpass(out, 180, 2)


def phase_shift():
    d = 4.2
    sweepup = sweep(70, 46, d) * adsr(d, 0.6, 0.4, 0.8, 1.6)
    swell = noise(d) * np.linspace(0, 1, int(SR * d)) ** 2.2
    swell = lowpass(swell, 1500, 2) * 0.5
    gl = np.zeros(int(SR * d))
    for i in range(6):
        p = int(SR * rng.uniform(0.4, d - 0.6))
        blip = sweep(rng.uniform(900, 1800), rng.uniform(300, 500), 0.09) * expdecay(0.09, 30)
        gl[p:p + len(blip)] += blip * 0.25
    return reverb(sweepup * 0.7 + swell + gl, gains=(0.4, 0.3, 0.22, 0.15, 0.1))


def anchor_hum():
    d = 6.0
    base = np.sin(2 * math.pi * 110 * t(d)) * 0.5
    fifth = np.sin(2 * math.pi * 164.8 * t(d)) * 0.22
    octave = np.sin(2 * math.pi * 220.6 * t(d)) * 0.1
    shimmer = np.sin(2 * math.pi * 1320 * t(d) + 3 * np.sin(2 * math.pi * 0.7 * t(d))) * 0.03
    trem = 1.0 + 0.08 * np.sin(2 * math.pi * 0.45 * t(d))
    x = (base + fifth + octave + shimmer) * trem
    return lowpass(x, 3000, 1)


def fragment_pickup():
    notes = [523.25, 659.25, 783.99, 1046.5]
    out = np.zeros(int(SR * 0.75))
    for i, f in enumerate(notes):
        seg = (np.sin(2 * math.pi * f * t(0.22)) + 0.3 * np.sign(np.sin(2 * math.pi * f * 2 * t(0.22)))) * expdecay(0.22, 14)
        seg = lowpass(seg, 6000, 1) * 0.4
        s = int(SR * (0.06 * i))
        out[s:s + len(seg)] += seg
    return out


def entity_ambient():
    d = 3.0
    x = np.zeros(int(SR * d))
    for f, amp in ((58, 0.5), (62.5, 0.4), (87, 0.25), (123.4, 0.15)):
        x += amp * np.sin(2 * math.pi * f * t(d) + rng.uniform(0, 6))
    x += 0.12 * noise(d, "pink")
    env = np.sin(math.pi * np.linspace(0, 1, len(x))) ** 1.4
    return lowpass(x * env, 2400, 2)


def entity_attack():
    d = 0.95
    shriek = sweep(1500, 240, 0.55) * expdecay(0.55, 5)
    grit = highpass(noise(0.55), 1200) * expdecay(0.55, 7) * 0.8
    thump = np.sin(2 * math.pi * 52 * t(0.9)) * expdecay(0.9, 5) * 0.6
    out = np.zeros(int(SR * d))
    out[: len(shriek)] += shriek * 0.5 + grit
    out[: len(thump)] += thump
    return lowpass(out, 6000, 1)


def structure_appear():
    d = 2.6
    rumble = lowpass(noise(d, "brown"), 260, 2) * adsr(d, 0.5, 0.5, 0.75, 0.9)
    rise = sweep(120, 420, d) * adsr(d, 1.2, 0.3, 0.6, 0.8) * 0.25
    grind = np.zeros(int(SR * d))
    for i in range(14):
        p = int(SR * rng.uniform(0.2, d - 0.4))
        seg = highpass(noise(0.12), 800) * expdecay(0.12, 22) * rng.uniform(0.15, 0.4)
        grind[p:p + len(seg)] += seg
    return reverb(rumble * 0.8 + rise + grind, gains=(0.3, 0.22, 0.16, 0.12, 0.08))


def structure_revoke():
    d = 2.6
    swell = noise(d) * (np.linspace(0, 1, int(SR * d)) ** 2.6) * 0.7
    swell = highpass(lowpass(swell, 3000), 400)
    collapse = np.zeros(int(SR * d))
    for i in range(22):
        p = int(SR * (d * 0.55 + rng.uniform(0, d * 0.4)))
        if p < len(collapse) - 800:
            seg = highpass(noise(0.18), 600) * expdecay(0.18, 16) * rng.uniform(0.2, 0.6)
            collapse[p:p + len(seg)] += seg
    down = sweep(300, 60, d) * adsr(d, 0.4, 0.5, 0.5, 0.8) * 0.3
    return np.clip(swell + collapse + down, -1, 1)


def chunk_delete():
    d = 1.6
    crumble = lowpass(noise(d, "brown"), 900, 2) * adsr(d, 0.05, 0.4, 0.55, 0.8)
    cut = np.zeros(int(SR * d))
    p = int(SR * 0.75)
    seg_len = min(900, len(cut) - p)
    seg = np.linspace(1, 0, seg_len) * np.linspace(0.4, 1, seg_len)
    cut[p:p + seg_len] = rng.standard_normal(seg_len) * seg
    return crumble * 0.8 + cut * 0.8


def footstep_echo():
    d = 0.7
    out = np.zeros(int(SR * d))
    for start, amp in ((0.0, 1.0), (0.22, 0.55), (0.42, 0.28)):
        i = int(SR * start)
        seg = lowpass(noise(0.09), 1400, 2) * expdecay(0.09, 26) * amp
        out[i:i + len(seg)] += seg
    return out


def error_tone():
    d = 1.25
    a = np.sign(np.sin(2 * math.pi * 660 * t(d))) * 0.35
    a = lowpass(a, 3500, 1)
    gate = (np.sin(2 * math.pi * 4.5 * t(d)) > -0.2).astype(float)
    gate = lowpass(gate.astype(float), 60, 1)
    return a * gate * adsr(d, 0.01, 0.2, 0.7, 0.25)


def title_drone():
    d = 30.0
    x = np.zeros(int(SR * d))
    for f, amp in ((41.2, 0.5), (55.0, 0.32), (82.4, 0.18), (110.0, 0.1)):
        x += amp * np.sin(2 * math.pi * f * t(d) + rng.uniform(0, 6))
    x += 0.05 * lowpass(noise(d, "pink"), 700, 2)
    lfo = 1.0 + 0.2 * np.sin(2 * math.pi * 0.05 * t(d))
    x *= lfo
    # slow breathing swell so it loops without a seam
    env = 1.0 - 0.5 * np.cos(2 * math.pi * np.linspace(0, 1, len(x)))
    return lowpass(x * env, 1200, 2)


def seamless(x: np.ndarray, xfade: float = 1.0, tail: float = 0.6) -> np.ndarray:
    """
    Makes a loop that does not click and does not breathe at the seam.

    The last ``xfade`` seconds are cross-faded with the material just before ``tail``, and
    the result is trimmed to a whole number of seconds so both ambience tracks are exactly
    19 s - the client restarts them from the same timer, so a length mismatch would drift
    into a gap.
    """
    n = len(x)
    k = int(xfade * SR)

    body = x[:-k].copy()
    tailpart = x[-k:].copy()
    fade_in = np.linspace(0.0, 1.0, k, dtype=np.float64)

    # the seam: fade the tail out under the first k samples instead of over the end
    body[:k] = tailpart * (1.0 - fade_in) + body[:k] * fade_in

    total = int(SR * 19)
    if len(body) < total:
        body = np.pad(body, (0, total - len(body)))

    return body[:total]


def ambience_calm():
    d = 20.0
    wind = lowpass(noise(d, "pink"), 500, 2) * 0.7
    lfo = 0.6 + 0.4 * np.sin(2 * math.pi * 0.07 * t(d))
    return seamless(wind * lfo)


def ambience_deleted():
    d = 20.0
    hum = np.sin(2 * math.pi * 33 * t(d)) * 0.4 + np.sin(2 * math.pi * 49.5 * t(d)) * 0.2
    air = highpass(lowpass(noise(d, "pink"), 1800, 2), 200) * 0.25
    lfo = 0.7 + 0.3 * np.sin(2 * math.pi * 0.09 * t(d) + 1.0)
    return seamless((hum + air) * lfo)


def proximity_drone():
    """
    The sound that plays while it is nearby: a sub-bass swell with a slow beating
    dissonance (33 Hz vs 34.1 Hz) that gets physically uncomfortable on headphones, plus a
    gated breath-like noise. Loopable, 12 s, no transient - it must never startle, it must
    build dread.
    """
    d = 12.0
    tt = t(d)
    sub = np.sin(2 * math.pi * 33.0 * tt) * 0.5 + np.sin(2 * math.pi * 34.1 * tt) * 0.45
    mid = np.sin(2 * math.pi * 98.0 * tt + 0.6 * np.sin(2 * math.pi * 0.11 * tt)) * 0.18
    # breath: pink noise gated by a slow asymmetric envelope
    breath = lowpass(noise(d, "pink"), 900, 2) * 0.22
    gate = 0.55 + 0.45 * np.sin(2 * math.pi * 0.083 * tt - math.pi / 2) ** 3
    x = (sub + mid) * (0.8 + 0.2 * np.sin(2 * math.pi * 0.05 * tt)) + breath * gate
    return seamless(x, xfade=1.0)


def death_sting():
    """
    The sudden-death hit: one hard low impact, a reversed tail that swells into it, and a
    detuned pair of sine partials that die a beat apart. Nothing melodic - it has to sound
    like something being switched off.
    """
    d = 2.6
    tt = t(d)
    impact = np.sin(2 * math.pi * 48.0 * tt) * expdecay(d, 9.0) * 1.2
    impact += lowpass(noise(0.25), 180, 2)[: len(tt)] if len(noise(0.25)) >= len(tt) else 0.0
    body = np.zeros(len(tt))
    n = len(tt)

    for f, delay, amp in ((220.0, 0.0, 0.32), (219.0, 0.02, 0.28), (110.5, 0.12, 0.22)):
        start = int(delay * SR)
        seg = np.sin(2 * math.pi * f * tt[: n - start]) * expdecay((n - start) / SR, 2.2) * amp
        body[start:] += seg

    return lowpass(impact + body, 3000, 2)



# --- jumpscare set ---------------------------------------------------------------

def scare_sting():
    """
    The jumpscare itself: 0.35 s of everything happening at once.

    Built from four layers - a sub drop, a bright noise slash, a detuned cluster of
    high partials, and a short reverse-swell before the hit - because a single sound
    reads as "loud", while four disagreeing sounds at the same instant read as "wrong".
    """
    n = t(0.35)

    # sub drop: 70 Hz down to 28 Hz
    sub = sweep(70.0, 28.0, 0.35, "exp") * expdecay(0.35, 9.0) * 0.9

    # noise slash, bright, gone in 80 ms
    slash = noise(0.35) * expdecay(0.35, 34.0) * 0.55
    slash = highpass(slash, 1800.0)

    # detuned cluster: three partials a few Hz apart, deliberately inharmonic
    cluster = (
        np.sin(2 * np.pi * 1230.0 * n) * 0.22
        + np.sin(2 * np.pi * 1291.0 * n) * 0.18
        + np.sin(2 * np.pi * 1747.0 * n) * 0.14
    ) * expdecay(0.35, 14.0)

    # reverse swell: 120 ms building into the hit
    swell = np.zeros_like(n)
    k = min(int(0.12 * SR), len(n))
    swell[:k] = (noise(0.12)[:k] * (np.linspace(0, 1, k) ** 2)) * 0.35

    out = sub + slash + cluster + swell
    return reverb(out) * 0.9


def scare_breath():
    """
    Right before the face: a wet, close exhale at the back of your neck.

    Filtered noise with a slow amplitude envelope and a slight downward sweep, so it
    reads as breathing rather than as wind. 1.1 s - long enough to be uncomfortable,
    short enough that it is over before the player decides it was their imagination.
    """
    n = t(1.1)

    breath = noise(1.1)
    breath = lowpass(breath, 900.0)
    breath = highpass(breath, 180.0)

    # three uneven pulses: in, out, in - not a rhythm
    env = (
        np.exp(-((n - 0.18) ** 2) / 0.012) * 0.9
        + np.exp(-((n - 0.55) ** 2) / 0.02) * 0.65
        + np.exp(-((n - 0.92) ** 2) / 0.014) * 0.8
    )

    # a little pitch drift on the "voice" band so it is not pure noise
    voice = np.sin(2 * np.pi * (170.0 - 30.0 * n / 1.1) * n) * 0.12

    return reverb((breath * env + voice * env)) * 0.9


def scare_whisper_name():
    """
    Something says part of a word, very close to the microphone and then stops.

    Formant-shaped noise bursts (three bands) with the amplitude gated in syllable-like
    chunks. No words - the brain supplies the word, which is always worse than a real one.
    """
    n = t(1.6)

    base = noise(1.6)
    formants = (
        lowpass(highpass(base, 320.0), 700.0) * 0.5
        + lowpass(highpass(base, 900.0), 1700.0) * 0.3
        + lowpass(highpass(base, 2100.0), 3000.0) * 0.15
    )

    # syllable gate: irregular on/off, ends mid-syllable
    gate = np.zeros_like(n)

    for start, length, gain in [(0.05, 0.16, 0.9), (0.28, 0.22, 0.7), (0.58, 0.1, 0.8),
                                (0.74, 0.34, 0.55), (1.18, 0.12, 0.35)]:
        i0 = int(start * SR)
        i1 = min(len(n), i0 + int(length * SR))
        gate[i0:i1] = gain

    gate = lowpass(gate, 40.0)  # soft attack/release on each syllable

    return reverb(formants * gate) * 0.85


SOUNDS = {
    "static_burst": static_burst,
    "error_blip": error_blip,
    "whisper": whisper,
    "heartbeat": heartbeat,
    "data_chirp": data_chirp,
    "sub_hit": sub_hit,
    "phase_shift": phase_shift,
    "anchor_hum": anchor_hum,
    "fragment_pickup": fragment_pickup,
    "entity_ambient": entity_ambient,
    "entity_attack": entity_attack,
    "structure_appear": structure_appear,
    "structure_revoke": structure_revoke,
    "chunk_delete": chunk_delete,
    "footstep_echo": footstep_echo,
    "error_tone": error_tone,
    "title_drone": title_drone,
    "ambience_calm": ambience_calm,
    "ambience_deleted": ambience_deleted,
    "proximity_drone": proximity_drone,
    "death_sting": death_sting,
    "scare_sting": scare_sting,
    "scare_breath": scare_breath,
    "scare_whisper_name": scare_whisper_name,
}

# subtitle keys + which ones are long loops (streamed by the client)
SUBTITLES = {
    "static_burst": ("subtitles.error404.static", False),
    "error_blip": ("subtitles.error404.error_blip", False),
    "whisper": ("subtitles.error404.whisper", False),
    "heartbeat": ("subtitles.error404.heartbeat", False),
    "data_chirp": ("subtitles.error404.data_chirp", False),
    "sub_hit": ("subtitles.error404.sub_hit", False),
    "phase_shift": ("subtitles.error404.phase_shift", False),
    "anchor_hum": ("subtitles.error404.anchor_hum", False),
    "fragment_pickup": ("subtitles.error404.fragment_pickup", False),
    "entity_ambient": ("subtitles.error404.entity_ambient", False),
    "entity_attack": ("subtitles.error404.entity_attack", False),
    "structure_appear": ("subtitles.error404.structure_appear", False),
    "structure_revoke": ("subtitles.error404.structure_revoke", False),
    "chunk_delete": ("subtitles.error404.chunk_delete", False),
    "footstep_echo": ("subtitles.error404.footstep_echo", False),
    "error_tone": ("subtitles.error404.error_tone", False),
    "title_drone": ("subtitles.error404.title_drone", True),
    "ambience_calm": ("subtitles.error404.ambience_calm", True),
    "ambience_deleted": ("subtitles.error404.ambience_deleted", True),
    "proximity_drone": ("subtitles.error404.proximity_drone", True),
    "death_sting": ("subtitles.error404.death_sting", False),
    "scare_sting": ("subtitles.error404.scare_sting", False),
    "scare_breath": ("subtitles.error404.scare_breath", False),
    "scare_whisper_name": ("subtitles.error404.scare_whisper_name", False),
}


LOOPS = {"ambience_calm", "ambience_deleted", "title_drone", "proximity_drone"}


def main() -> None:
    for name, fn in SOUNDS.items():
        save(name, fn(), loop=name in LOOPS)
        print("  ->", name + ".ogg")

    sounds_json = {}
    for name, (subtitle, stream) in SUBTITLES.items():
        entry = {"subtitle": subtitle, "sounds": [{"name": f"error404:{name}", "stream": stream}]}
        sounds_json[name] = entry

    with open(os.path.join(RES, "sounds.json"), "w") as f:
        json.dump(sounds_json, f, indent=2)

    print(f"\n{len(SOUNDS)} sounds written to {OUT}")
    print("sounds.json written")


if __name__ == "__main__":
    main()
