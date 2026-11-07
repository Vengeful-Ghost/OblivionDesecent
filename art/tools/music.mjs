#!/usr/bin/env node
// ===========================================================================================================
//  >>> WRITES THE BIOME MUSIC <<<  From the repo root:
//
//      node art/tools/music.mjs            (all three)
//      node art/tools/music.mjs forge      (one)
//
//  It renders app/src/main/res/raw/music_catacombs.wav, music_forge.wav and music_void.wav (22050 Hz mono,
//  16-bit), then rebuild the app. Same notes and the same seeded randomness every time, so it is safe to run
//  again. The Crypt keeps game_music.mp3; bosses keep boss_music.mp3.
// ===========================================================================================================
// Original music, composed here as notes and synthesised from scratch: no samples, nothing downloaded.
// Each track is rendered twice end to end and the second pass is kept, so the echoes and the reverb tail of
// the loop's end ring on into its start and MediaPlayer's loop has no seam.
//
//   Flooded Catacombs  "Black Water"   70 BPM, D minor: a harp arpeggio echoing off water, drips in a long
//                                      cavern reverb, a slow kick, and a lonely flute over the second half.
//   Infernal Forge     "The Bellows"  112 BPM, E phrygian: a driven bass ostinato, war drums and anvils, then
//                                      brass stabs and a choir, then a distorted lead.
//   Hollow Void        "Hollow Stars"  60 BPM, C lydian into its mediants: a beating drone, glass bells in the
//                                      dark, reversed swells, a slow heartbeat and a theremin line.

import { writeFileSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const SR = 22050;
const OUT = join(dirname(fileURLToPath(import.meta.url)), "..", "..", "app", "src", "main", "res", "raw");

// ── helpers ──────────────────────────────────────────────────────────────────────────────────────────────
function rng(seed) {
    let s = seed >>> 0;
    return () => ((s = (Math.imul(s, 1664525) + 1013904223) >>> 0) / 4294967296);
}
const mtof = m => 440 * Math.pow(2, (m - 69) / 12);
const PC = { C: 0, "C#": 1, Db: 1, D: 2, "D#": 3, Eb: 3, E: 4, F: 5, "F#": 6, Gb: 6, G: 7, "G#": 8, Ab: 8, A: 9, "A#": 10, Bb: 10, B: 11 };
function m(name) {
    const r = /^([A-G][#b]?)(-?\d)$/.exec(name);
    if (!r) throw new Error("bad note " + name);
    return PC[r[1]] + 12 * (+r[2] + 1);
}
const ms = list => list.split(" ").map(m);

// PolyBLEP: takes the alias whistle off saws and squares
function blep(t, dt) {
    if (t < dt) { t /= dt; return t + t - t * t - 1; }
    if (t > 1 - dt) { t = (t - 1) / dt; return t * t + t + t + 1; }
    return 0;
}

// Chamberlin state-variable filter; damp 2 = no resonance, 0.2 = ringing
class SVF {
    constructor() { this.lp = 0; this.bp = 0; this.hp = 0; }
    run(x, fc, damp) {
        const f = 2 * Math.sin(Math.PI * Math.min(fc, SR / 6.5) / SR);
        this.hp = x - this.lp - damp * this.bp;
        this.bp += f * this.hp;
        this.lp += f * this.bp;
        return this.lp;
    }
}

// Attack, then hold to the gate, then release; shaped by the power curve
function adsr(t, gate, a, d, s, r) {
    if (t < a) return t / a;
    if (t < a + d) return 1 - (1 - s) * ((t - a) / d);
    if (t < gate) return s;
    const lvl = gate < a ? gate / a : gate < a + d ? 1 - (1 - s) * ((gate - a) / d) : s;
    const k = (t - gate) / r;
    return k >= 1 ? 0 : lvl * (1 - k) * (1 - k);
}

// ── voices: each returns a Float32Array starting at the note's start ───────────────────────────────────
function saws(freqs, gate, o) {
    const rel = o.release ?? 1, n = Math.ceil((gate + rel) * SR), out = new Float32Array(n);
    const detune = o.detune ?? 8, voices = o.voices ?? 3;
    const oscs = [];
    for (const f of freqs) for (let v = 0; v < voices; v++) {
        const cents = voices === 1 ? 0 : (v / (voices - 1) * 2 - 1) * detune;
        oscs.push({ dt: f * Math.pow(2, cents / 1200) / SR, ph: (v * 0.37 + f * 0.001) % 1 });
    }
    const flt = new SVF(), flt2 = new SVF();
    const lfo = o.lfo ?? 0, lfoRate = o.lfoRate ?? 0.1, cut = o.cutoff ?? 900;
    for (let i = 0; i < n; i++) {
        const t = i / SR;
        let x = 0;
        for (const s of oscs) {
            x += 2 * s.ph - 1 - blep(s.ph, s.dt);
            s.ph += s.dt;
            if (s.ph >= 1) s.ph -= 1;
        }
        x /= Math.sqrt(oscs.length);
        let fc = cut + lfo * Math.sin(2 * Math.PI * lfoRate * t + (o.lfoPhase ?? 0));
        if (o.envCut) fc += o.envCut * Math.exp(-t / (o.envTime ?? 0.15));
        let y = flt.run(x, Math.max(60, fc), o.damp ?? 1.4);
        if (o.formant) y = flt2.run(x, o.formant, 0.25) * 0.8 + y * 0.4;
        const env = o.reverse ? Math.pow(Math.min(1, t / gate), 3) * (t < gate ? 1 : Math.max(0, 1 - (t - gate) / 0.05))
            : adsr(t, gate, o.attack ?? 1, o.decay ?? 0.3, o.sustain ?? 1, rel);
        out[i] = y * env * (o.gain ?? 0.1);
    }
    if (o.drive) for (let i = 0; i < n; i++) out[i] = Math.tanh(out[i] * o.drive) / o.drive;
    return out;
}

function sine(freq, gate, o) {
    const rel = o.release ?? 0.5, n = Math.ceil((gate + rel) * SR), out = new Float32Array(n);
    let ph = 0, ph2 = 0;
    const beat = o.beat ?? 0;
    for (let i = 0; i < n; i++) {
        const t = i / SR;
        const swell = o.swell ? 0.75 + 0.25 * Math.sin(2 * Math.PI * o.swell * t) : 1;
        let x = Math.sin(2 * Math.PI * ph);
        if (beat) x = 0.5 * (x + Math.sin(2 * Math.PI * ph2));
        ph += freq / SR;
        ph2 += (freq + beat) / SR;
        out[i] = x * swell * adsr(t, gate, o.attack ?? 0.5, 0.1, 1, rel) * (o.gain ?? 0.2);
    }
    return out;
}

// Karplus-Strong string: a burst of filtered noise ringing round a delay as long as one period
function pluck(freq, dur, o, rand) {
    const n = Math.ceil(dur * SR), out = new Float32Array(n);
    const period = SR / freq, N = Math.max(2, Math.floor(period));
    const frac = period - N; // an allpass makes up the fraction of a sample so high notes stay in tune
    const c = (1 - frac) / (1 + frac);
    const buf = new Float32Array(N);
    const bright = o.bright ?? 0.5;
    let lp = 0;
    for (let i = 0; i < N; i++) { lp += bright * ((rand() * 2 - 1) - lp); buf[i] = lp; }
    const loss = Math.pow(0.001, 1 / ((o.t60 ?? 2.5) * freq));
    let idx = 0, apIn = 0, apOut = 0;
    for (let i = 0; i < n; i++) {
        const a = buf[idx], b = buf[(idx + 1) % N];
        const avg = (a + b) * 0.5 * loss;
        apOut = c * avg + apIn - c * apOut;
        apIn = avg;
        buf[idx] = apOut;
        idx = (idx + 1) % N;
        const fade = i > n - 400 ? (n - i) / 400 : 1;
        out[i] = a * fade * (o.gain ?? 0.2);
    }
    return out;
}

// FM bell: an inharmonic modulator whose depth dies away faster than the tone, so it rings glassy then pure
function bell(freq, dur, o) {
    const n = Math.ceil(dur * SR), out = new Float32Array(n);
    const ratio = o.ratio ?? 3.5, index = o.index ?? 2.5, decay = o.decay ?? 1.6;
    let pc = 0, pm = 0;
    for (let i = 0; i < n; i++) {
        const t = i / SR;
        const idx = index * Math.exp(-t / (decay * 0.3));
        const mod = Math.sin(2 * Math.PI * pm) * idx;
        const x = Math.sin(2 * Math.PI * pc + mod);
        pc += freq / SR;
        pm += freq * ratio / SR;
        const att = Math.min(1, t / 0.003);
        out[i] = x * att * Math.exp(-t / decay) * (o.gain ?? 0.1);
    }
    return out;
}

// One continuous voice that sings a line of notes (flute, theremin, lead): gliding, with vibrato, no clicks
function line(notes, o) {
    const total = notes.reduce((s, x) => s + x[1], 0) + (o.release ?? 0.6);
    const n = Math.ceil(total * SR), out = new Float32Array(n);
    const flt = new SVF();
    let ph = 0, freq = mtof(notes[0][0]), t0 = 0, k = 0;
    let breath = 0;
    const rand = rng(o.seed ?? 7);
    for (let i = 0; i < n; i++) {
        const t = i / SR;
        while (k < notes.length - 1 && t >= t0 + notes[k][1]) { t0 += notes[k][1]; k++; }
        const [note, len] = notes[k];
        const local = t - t0;
        const target = mtof(note);
        const glide = o.glide ?? 0.04;
        freq += (target - freq) * Math.min(1, 1 / (glide * SR));
        const vibDepth = (o.vibrato ?? 0.3) * Math.min(1, Math.max(0, (local - 0.18) / 0.4));
        const f = freq * Math.pow(2, vibDepth * Math.sin(2 * Math.PI * (o.vibRate ?? 5) * t) / 12);
        ph += f / SR;
        ph -= Math.floor(ph);
        let x;
        if (o.wave === "saw") x = 2 * ph - 1 - blep(ph, f / SR);
        else x = Math.sin(2 * Math.PI * ph) * 0.8 + (Math.abs(ph * 4 - 2) - 1) * 0.35; // sine with a little triangle
        if (o.breath) { breath += 0.2 * ((rand() * 2 - 1) - breath); x += breath * o.breath; }
        if (o.cutoff) x = flt.run(x, o.cutoff, o.damp ?? 1);
        if (o.drive) x = Math.tanh(x * o.drive) / Math.tanh(o.drive);
        // each note swells in and eases off before the next; the last one rings out
        const last = k === notes.length - 1;
        const attack = o.attack ?? 0.08;
        let env = Math.min(1, local / attack);
        if (!last) env *= 1 - 0.35 * Math.max(0, (local - (len - 0.12)) / 0.12);
        else env *= local < len ? 1 : Math.max(0, 1 - (local - len) / (o.release ?? 0.6));
        out[i] = x * env * (o.gain ?? 0.12);
    }
    return out;
}

function kick(o) {
    const dur = o.decay * 4, n = Math.ceil(dur * SR), out = new Float32Array(n);
    let ph = 0;
    for (let i = 0; i < n; i++) {
        const t = i / SR;
        const f = o.f1 + (o.f0 - o.f1) * Math.exp(-t / 0.04);
        ph += f / SR;
        const click = i < 60 ? (1 - i / 60) * 0.3 : 0;
        out[i] = (Math.sin(2 * Math.PI * ph) + click) * Math.exp(-t / o.decay) * o.gain;
    }
    return out;
}

// Drum skin: a pitch-dropping body and a burst of noise through a lowpass
function tom(o, rand) {
    const dur = o.decay * 5, n = Math.ceil(dur * SR), out = new Float32Array(n);
    let ph = 0;
    const flt = new SVF();
    for (let i = 0; i < n; i++) {
        const t = i / SR;
        const f = o.f * (1 + 0.6 * Math.exp(-t / 0.03));
        ph += f / SR;
        const body = Math.sin(2 * Math.PI * ph) * Math.exp(-t / o.decay);
        const noise = flt.run(rand() * 2 - 1, o.noiseCut ?? 1200, 1.2) * Math.exp(-t / 0.05) * (o.noise ?? 0.5);
        out[i] = Math.tanh((body + noise) * 1.4) * o.gain;
    }
    return out;
}

function noiseHit(o, rand) {
    const dur = o.decay * 5, n = Math.ceil(dur * SR), out = new Float32Array(n);
    const flt = new SVF();
    for (let i = 0; i < n; i++) {
        const t = i / SR;
        flt.run(rand() * 2 - 1, o.freq, o.damp ?? 0.5);
        out[i] = flt.bp * Math.exp(-t / o.decay) * o.gain;
    }
    return out;
}

// Anvil: hammered steel, a handful of inharmonic partials ringing at their own rates over a sharp strike
function anvil(o, rand) {
    const partials = [[1, 1, 1.1], [2.76, 0.6, 0.7], [5.4, 0.4, 0.45], [8.93, 0.25, 0.25], [13.3, 0.15, 0.12]];
    const dur = 1.6, n = Math.ceil(dur * SR), out = new Float32Array(n);
    const flt = new SVF();
    for (let i = 0; i < n; i++) {
        const t = i / SR;
        let x = 0;
        for (const [r, g, d] of partials) {
            const f = o.freq * r;
            if (f < SR / 2.2) x += Math.sin(2 * Math.PI * f * t) * g * Math.exp(-t / (d * (o.ring ?? 1)));
        }
        flt.run(rand() * 2 - 1, 5000, 0.6);
        x += flt.bp * Math.exp(-t / 0.012) * 1.5;
        out[i] = x * o.gain;
    }
    return out;
}

// A drip: a tiny bubble whose pitch jumps up as it bursts
function drip(o) {
    const dur = 0.35, n = Math.ceil(dur * SR), out = new Float32Array(n);
    let ph = 0;
    for (let i = 0; i < n; i++) {
        const t = i / SR;
        const f = o.freq * (1 + 0.9 * (1 - Math.exp(-t / 0.02)));
        ph += f / SR;
        out[i] = Math.sin(2 * Math.PI * ph) * Math.min(1, t / 0.002) * Math.exp(-t / 0.07) * o.gain;
    }
    return out;
}

// Wind: noise through a bandpass that wanders slowly up and down
function wind(dur, o, rand) {
    const n = Math.ceil(dur * SR), out = new Float32Array(n);
    const flt = new SVF();
    for (let i = 0; i < n; i++) {
        const t = i / SR;
        const fc = o.lo + (o.hi - o.lo) * (0.5 + 0.5 * Math.sin(2 * Math.PI * t / o.period) * Math.sin(2 * Math.PI * t / (o.period * 2.7)));
        flt.run(rand() * 2 - 1, fc, 0.3);
        const edge = Math.min(1, t / 2, (dur - t) / 2);
        out[i] = flt.bp * o.gain * Math.max(0, edge);
    }
    return out;
}

// ── effects ──────────────────────────────────────────────────────────────────────────────────────────────
function freeverb(input, room, damp) {
    const s = SR / 44100;
    const combs = [1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617].map(x => ({ buf: new Float32Array(Math.round(x * s)), i: 0, store: 0 }));
    const aps = [556, 441, 341, 225].map(x => ({ buf: new Float32Array(Math.round(x * s)), i: 0 }));
    const out = new Float32Array(input.length);
    for (let k = 0; k < input.length; k++) {
        const x = input[k] * 0.015;
        let sum = 0;
        for (const c of combs) {
            const y = c.buf[c.i];
            c.store = y * (1 - damp) + c.store * damp;
            c.buf[c.i] = x + c.store * room;
            if (++c.i >= c.buf.length) c.i = 0;
            sum += y;
        }
        for (const a of aps) {
            const b = a.buf[a.i];
            a.buf[a.i] = sum + b * 0.5;
            sum = b - sum;
            if (++a.i >= a.buf.length) a.i = 0;
        }
        out[k] = sum * 3;
    }
    return out;
}

function echo(input, time, feedback, tone) {
    const d = Math.round(time * SR), buf = new Float32Array(d), out = new Float32Array(input.length);
    let w = 0, lp = 0;
    for (let k = 0; k < input.length; k++) {
        const y = buf[w];
        lp += tone * (y - lp);
        buf[w] = input[k] + lp * feedback;
        if (++w >= d) w = 0;
        out[k] = y;
    }
    return out;
}

// ── a track: notes placed in beats, mixed on three buses, rendered twice, the second pass kept ───────────
class Track {
    constructor(name, bpm, bars, o = {}) {
        this.name = name;
        this.beat = 60 / bpm;
        this.len = Math.round(bars * 4 * this.beat * SR);
        const n = this.len * 2 + SR * 10;
        this.dry = new Float32Array(n);
        this.rev = new Float32Array(n);
        this.dly = new Float32Array(n);
        this.o = o;
        this.rand = rng(o.seed ?? 1);
    }
    sec(bar, beat = 0) { return (bar * 4 + beat) * this.beat; }
    // place a rendered voice at bar/beat with sends to reverb and echo, in both passes
    add(buf, bar, beat, send = 0.3, echoSend = 0) {
        const at = Math.round(this.sec(bar, beat) * SR);
        for (const pass of [0, 1]) {
            const start = at + pass * this.len;
            const end = Math.min(buf.length, this.dry.length - start);
            for (let i = 0; i < end; i++) {
                const x = buf[i];
                this.dry[start + i] += x;
                this.rev[start + i] += x * send;
                this.dly[start + i] += x * echoSend;
            }
        }
    }
    render() {
        const o = this.o;
        const delayed = echo(this.dly, o.echoTime ?? this.beat * 0.75, o.echoFeedback ?? 0.4, o.echoTone ?? 0.4);
        for (let i = 0; i < delayed.length; i++) this.rev[i] += delayed[i] * 0.6;
        const wet = freeverb(this.rev, o.room ?? 0.86, o.damp ?? 0.35);
        const out = new Float32Array(this.len);
        for (let i = 0; i < this.len; i++) {
            const k = this.len + i;
            out[i] = this.dry[k] + delayed[k] * 0.8 + wet[k] * (o.wet ?? 1);
        }
        return master(out, o.drive ?? 1.1);
    }
}

// Peak to 1 and a gentle saturator for body, then set to the loudness of an ordinary game soundtrack
// (RMS near -16 dBFS) without letting any peak past -1 dBFS
const TARGET_RMS = -16;
function master(x, drive) {
    let peak = 0;
    for (const v of x) peak = Math.max(peak, Math.abs(v));
    const norm = 1 / (peak || 1), t = Math.tanh(drive);
    let peak2 = 0, sum = 0;
    for (let i = 0; i < x.length; i++) {
        x[i] = Math.tanh(x[i] * norm * drive) / t;
        peak2 = Math.max(peak2, Math.abs(x[i]));
        sum += x[i] * x[i];
    }
    const rms = Math.sqrt(sum / x.length);
    const gain = Math.min(Math.pow(10, TARGET_RMS / 20) / rms, 0.89 / peak2);
    for (let i = 0; i < x.length; i++) x[i] *= gain;
    return { samples: x, rms: 20 * Math.log10(rms * gain), peak: 20 * Math.log10(peak2 * gain) };
}

function writeWav(path, x) {
    const b = Buffer.alloc(44 + x.length * 2);
    b.write("RIFF", 0); b.writeUInt32LE(36 + x.length * 2, 4); b.write("WAVE", 8);
    b.write("fmt ", 12); b.writeUInt32LE(16, 16); b.writeUInt16LE(1, 20); b.writeUInt16LE(1, 22);
    b.writeUInt32LE(SR, 24); b.writeUInt32LE(SR * 2, 28); b.writeUInt16LE(2, 32); b.writeUInt16LE(16, 34);
    b.write("data", 36); b.writeUInt32LE(x.length * 2, 40);
    for (let i = 0; i < x.length; i++) {
        const v = Math.max(-1, Math.min(1, x[i]));
        b.writeInt16LE(Math.round(v * 32767), 44 + i * 2);
    }
    writeFileSync(path, b);
}

// ── Flooded Catacombs: "Black Water" ────────────────────────────────────────────────────────────────────
function catacombs() {
    const T = new Track("catacombs", 70, 16, { seed: 11, room: 0.9, damp: 0.3, wet: 1.2, echoTime: 60 / 70 * 0.75, echoFeedback: 0.45 });
    const r = T.rand, b = T.beat;
    // two bars a chord: pad voicing, the sub's root, and the harp's tones
    const chords = [
        ["D3 F3 A3 D4", "D2", "D4 A4 F5 A4 D5 A4 E5 A4"],
        ["Bb2 D3 F3 Bb3", "Bb1", "D4 F4 Bb4 F4 D5 F4 C5 F4"],
        ["G2 Bb2 D3 G3", "G1", "D4 G4 Bb4 G4 D5 G4 A4 G4"],
        ["A2 C#3 E3 A3", "A1", "C#4 E4 A4 E4 C#5 E4 E5 E4"],
        ["D3 F3 A3 D4", "D2", "D4 A4 F5 A4 D5 A4 E5 A4"],
        ["F2 A2 C3 F3", "F1", "C4 F4 A4 F4 C5 F4 A4 F4"],
        ["C3 E3 G3 C4", "C2", "C4 G4 E5 G4 C5 G4 D5 G4"],
        ["A2 C#3 E3 A3", "A1", "C#4 E4 A4 E4 C#5 A4 E5 A4"],
    ];
    chords.forEach(([pad, root, arp], c) => {
        const bar = c * 2;
        T.add(saws(ms(pad).map(mtof), 8 * b, { attack: 1.4, release: 2.2, cutoff: 700, lfo: 250, lfoRate: 0.07, lfoPhase: c, gain: 0.07 }), bar, 0, 0.5);
        T.add(sine(mtof(m(root) + 12), 8 * b, { attack: 0.6, release: 1.2, gain: 0.2, swell: 0.12 }), bar, 0, 0.1);
        if (bar >= 2) {
            const tones = ms(arp);
            for (let k = 0; k < 16; k++) {
                if (k % 8 !== 0 && r() < 0.18) continue; // a missed note or two keeps it from sounding wound up
                const vel = 0.6 + 0.4 * r();
                T.add(pluck(mtof(tones[k % 8]), 3, { bright: 0.45, t60: 2.2, gain: 0.16 * vel }, r), bar, k * 0.5, 0.4, 0.35);
            }
        }
    });
    // water dripping somewhere in the dark
    for (let t = 1.3; t < 64; t += 1.6 + r() * 3.2) {
        const bar = Math.floor(t / 4), beat = t % 4;
        T.add(drip({ freq: 900 + r() * 800, gain: 0.07 + r() * 0.05 }), bar, beat, 0.9, 0.5);
    }
    // a slow kick from bar 5, a far-off drum every other bar
    for (let bar = 4; bar < 16; bar++) {
        T.add(kick({ f0: 95, f1: 42, decay: 0.32, gain: 0.42 }), bar, 0, 0.25);
        T.add(kick({ f0: 90, f1: 42, decay: 0.25, gain: 0.26 }), bar, 2.5, 0.25);
        if (bar % 2 === 1) T.add(tom({ f: 82, decay: 0.3, gain: 0.22, noiseCut: 900 }, r), bar, 3, 0.6);
    }
    // the flute, over the second half
    const flute = [["A4", 2], ["D5", 2], ["F5", 3], ["E5", 1], ["C5", 2], ["A4", 2], ["C5", 3], ["D5", 1],
        ["E5", 3], ["G5", 1], ["F5", 2], ["E5", 2], ["E5", 2], ["C#5", 2], ["A4", 4]].map(([n, l]) => [m(n), l * b]);
    T.add(line(flute, { vibrato: 0.35, vibRate: 5, breath: 0.12, cutoff: 2600, gain: 0.13, release: 1.2, glide: 0.035 }), 8, 0, 0.5, 0.25);
    return T;
}

// ── Infernal Forge: "The Bellows" ───────────────────────────────────────────────────────────────────────
function forge() {
    const T = new Track("forge", 112, 24, { seed: 23, room: 0.8, damp: 0.45, wet: 0.7, echoTime: 60 / 112 * 0.75, echoFeedback: 0.3, drive: 1.4 });
    const r = T.rand, b = T.beat;
    const roots = ms("E2 E2 F2 E2 C2 D2 F2 E2");
    for (let bar = 0; bar < 24; bar++) {
        const root = roots[bar % 8], section = Math.floor(bar / 8);
        // the ostinato: root, octave and fifth hammered in eighths
        const pattern = bar % 8 === 7 ? [0, 0, 12, 0, 1, 3, 5, 7] : [0, 0, 12, 0, 0, 7, 0, 12];
        pattern.forEach((p, k) => {
            const accent = k === 0 ? 1 : 0.75 + 0.15 * r();
            T.add(saws([mtof(root + p)], 0.22, { voices: 2, detune: 6, attack: 0.004, decay: 0.1, sustain: 0.6, release: 0.06,
                cutoff: 280, envCut: 1800, envTime: 0.07, damp: 0.9, drive: 2.5, gain: 0.3 * accent }), bar, k * 0.5, 0.08);
        });
        // war drums
        T.add(kick({ f0: 120, f1: 45, decay: 0.22, gain: 0.62 }), bar, 0, 0.15);
        T.add(kick({ f0: 120, f1: 45, decay: 0.2, gain: 0.5 }), bar, 2, 0.15);
        T.add(kick({ f0: 110, f1: 45, decay: 0.16, gain: 0.4 }), bar, 3.5, 0.15);
        T.add(tom({ f: 72, decay: 0.28, gain: 0.42, noise: 0.7, noiseCut: 1500 }, r), bar, 1, 0.35);
        T.add(tom({ f: 72, decay: 0.28, gain: 0.42, noise: 0.7, noiseCut: 1500 }, r), bar, 3, 0.35);
        if (section >= 1) for (let k = 0; k < 8; k++)
            T.add(noiseHit({ freq: 6500, decay: 0.025, gain: k % 2 ? 0.05 : 0.08 }, r), bar, k * 0.5, 0.1);
        // anvils: one on the offbeat of every other bar, two a bar once the lead is in
        if (section < 2) { if (bar % 2 === 1) T.add(anvil({ freq: 520, gain: 0.12 }, r), bar, 3.5, 0.4, 0.2); }
        else {
            T.add(anvil({ freq: 520, gain: 0.11 }, r), bar, 1, 0.4, 0.15);
            T.add(anvil({ freq: 470, gain: 0.11 }, r), bar, 3, 0.4, 0.15);
        }
        // brass stabs and a choir from the second section
        if (section >= 1) {
            const chord = [root + 12, root + 19, root + 24, root + 27].map(mtof);
            T.add(saws(chord, 0.35, { attack: 0.01, decay: 0.2, sustain: 0.5, release: 0.15, cutoff: 500, envCut: 3200, envTime: 0.12, gain: 0.09 }), bar, 0, 0.35);
            T.add(saws(chord, 0.16, { attack: 0.01, decay: 0.1, sustain: 0.5, release: 0.1, cutoff: 500, envCut: 2500, envTime: 0.08, gain: 0.07 }), bar, 2.5, 0.35);
            if (bar % 2 === 0) T.add(saws([root + 24, root + 31, root + 36].map(mtof), 8 * b, { attack: 0.8, release: 1.2, cutoff: 1400, formant: 700, damp: 1, gain: 0.06, lfo: 200, lfoRate: 5.5 }), bar, 0, 0.6);
        }
    }
    // the lead, over the last section
    const lead = [["E4", 1.5], ["G4", 0.5], ["F4", 1], ["E4", 1], ["D4", 1], ["E4", 1], ["F4", 1], ["G4", 1],
        ["A4", 2], ["G4", 1], ["F4", 1], ["G4", 1.5], ["F4", 0.5], ["E4", 2], ["E4", 1], ["G4", 1], ["C5", 2],
        ["D5", 1.5], ["C5", 0.5], ["A4", 2], ["C5", 1], ["A4", 1], ["F4", 1], ["A4", 1], ["B4", 2], ["G4", 1], ["F4", 1]]
        .map(([n, l]) => [m(n), l * b]);
    T.add(line(lead, { wave: "saw", cutoff: 2200, damp: 0.9, drive: 3, vibrato: 0.25, vibRate: 6, glide: 0.025, attack: 0.02, gain: 0.08, release: 0.3 }), 16, 0, 0.35, 0.3);
    return T;
}

// ── Hollow Void: "Hollow Stars" ─────────────────────────────────────────────────────────────────────────
function voidTrack() {
    const T = new Track("void", 60, 16, { seed: 37, room: 0.93, damp: 0.25, wet: 1.4, echoTime: 0.75, echoFeedback: 0.55, echoTone: 0.3, drive: 1.1 });
    const r = T.rand, b = T.beat;
    // four bars a chord: C lydian, then its mediants, and a suspended G that falls home to C
    const chords = [
        ["C3 G3 B3 D4 F#4", "C D E F# G A B"],
        ["Ab2 Eb3 G3 C4 D4", "Ab Bb C D Eb F G"],
        ["F2 C3 Ab3 G4", "F G Ab Bb C Db Eb"],
        ["G2 D3 C4 D4", "G A B C D E F#"],
    ];
    T.add(sine(mtof(m("C2")), 64, { attack: 3, release: 3, beat: 0.35, gain: 0.18 }), 0, 0, 0.2);
    T.add(sine(mtof(m("G2")), 64, { attack: 3, release: 3, beat: 0.22, gain: 0.08 }), 0, 0, 0.3);
    T.add(wind(64, { lo: 300, hi: 1600, period: 11, gain: 0.06 }, r), 0, 0, 0.35);
    chords.forEach(([pad, scale], c) => {
        const bar = c * 4, notes = ms(pad);
        T.add(saws(notes.map(mtof), 16 * b, { attack: 3, release: 3, cutoff: 520, lfo: 180, lfoRate: 0.05, voices: 2, detune: 10, gain: 0.06 }), bar, 0, 0.8);
        // a reversed swell of the next chord breathes in over the last bar
        const next = ms(chords[(c + 1) % 4][0]).slice(-3).map(x => mtof(x + 12));
        T.add(saws(next, 3.5, { reverse: true, cutoff: 1800, voices: 2, detune: 12, gain: 0.07 }), bar + 3, 0.5, 0.9);
        // glass bells, scattered on the eighths
        const pcs = scale.split(" ").map(x => PC[x]);
        for (let k = 0; k < 32; k++) {
            if (r() > 0.17) continue;
            const pc = pcs[Math.floor(r() * pcs.length)], oct = r() < 0.6 ? 6 : 7;
            T.add(bell(mtof(pc + 12 * oct), 3, { ratio: 3.5, index: 2.2, decay: 1.4, gain: 0.05 + r() * 0.04 }), bar, k * 0.5, 0.7, 0.5);
        }
    });
    // a slow heartbeat from bar 5
    for (let bar = 4; bar < 16; bar++) {
        T.add(kick({ f0: 70, f1: 36, decay: 0.22, gain: 0.4 }), bar, 0, 0.3);
        T.add(kick({ f0: 66, f1: 36, decay: 0.18, gain: 0.25 }), bar, 0.33, 0.3);
    }
    // the theremin, over the second half
    const theremin = [["Ab4", 4], ["G4", 2], ["F4", 2], ["C5", 4], ["Eb5", 2], ["D5", 2],
        ["D5", 6], ["C5", 2], ["D5", 4], ["G4", 4]].map(([n, l]) => [m(n), l * b]);
    T.add(line(theremin, { vibrato: 0.28, vibRate: 5.5, glide: 0.12, attack: 0.4, cutoff: 1800, gain: 0.1, release: 2 }), 8, 0, 0.7, 0.35);
    return T;
}

// ── main ─────────────────────────────────────────────────────────────────────────────────────────────────
const TRACKS = { catacombs, forge, void: voidTrack };
const pick = process.argv.slice(2);
for (const [name, make] of Object.entries(TRACKS)) {
    if (pick.length && !pick.includes(name)) continue;
    const t0 = Date.now();
    const track = make();
    const { samples, rms, peak } = track.render();
    if (samples.some(v => !Number.isFinite(v))) throw new Error(name + ": bad samples");
    const path = join(OUT, "music_" + name + ".wav");
    writeWav(path, samples);
    console.log(`${name}: ${(samples.length / SR).toFixed(1)} s, RMS ${rms.toFixed(1)} dBFS, peak ${peak.toFixed(1)}, ${(samples.length * 2 / 1e6).toFixed(1)} MB, ${Date.now() - t0} ms`);
}
