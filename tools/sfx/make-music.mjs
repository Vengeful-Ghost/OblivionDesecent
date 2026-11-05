// Composes the game's music from synthesised instruments and writes MP3s into app/src/main/res/raw:
// the dungeon theme (game_music), the boss theme (boss_music) and the game-over lament (game_over_music).
// Everything is original and license-free. Needs the lamejs encoder:
//   npm i --no-save lamejs   (or set LAMEJS to the path of lame.all.js), then: node tools/sfx/make-music.mjs
import { readFileSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
// lamejs's module entry fails in Node; its prebuilt bundle works when evaluated on its own
const lamejs = new Function(readFileSync(process.env.LAMEJS || require.resolve('lamejs/lame.all.js'), 'utf8')
  + '\nreturn lamejs;')();

const SR = 44100;
const OUT = new URL('../../app/src/main/res/raw/', import.meta.url);

let seed = 20261105;
const rand = () => { seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0; return seed / 2147483648 - 1; };
const mtof = m => 440 * 2 ** ((m - 69) / 12);
const TAU = 2 * Math.PI;

/* ---------- mixing ---------- */

// A loop of the given length, plus a tail for decays and reverb that gets folded back onto the start
function track(seconds, tail = 6) {
  const n = Math.ceil((seconds + tail) * SR);
  return { loop: Math.round(seconds * SR), L: new Float32Array(n), R: new Float32Array(n),
    sL: new Float32Array(n), sR: new Float32Array(n) };
}

// Adds a rendered mono note at time t (seconds), panned -1..1, with part of it sent to the reverb
function mixIn(m, t, a, pan = 0, send = 0.3) {
  const s0 = Math.round(t * SR), ang = (pan + 1) * Math.PI / 4, gl = Math.cos(ang), gr = Math.sin(ang);
  for (let i = 0; i < a.length && s0 + i < m.L.length; i++) {
    const l = a[i] * gl, r = a[i] * gr;
    m.L[s0 + i] += l; m.R[s0 + i] += r;
    m.sL[s0 + i] += l * send; m.sR[s0 + i] += r * send;
  }
}

/* ---------- filters and envelopes ---------- */

function svf(damp = 1) {
  let low = 0, band = 0;
  return (x, cutoff, mode = 'low') => {
    const f = 2 * Math.sin(Math.PI * Math.min(cutoff, 6000) / SR);
    low += f * band;
    const high = x - low - damp * band;
    band += f * high;
    return mode === 'band' ? band : mode === 'high' ? high : low;
  };
}

// Linear attack, hold until `hold` samples, linear release
const adsr = (i, hold, a, r) => (i < a ? i / a : i < hold ? 1 : Math.max(0, 1 - (i - hold) / r));

/* ---------- instruments (each returns a mono Float32Array) ---------- */

// Plucked string (Karplus-Strong): harp and lute
function pluck(midi, amp, decay = 2.4, bright = 0.5) {
  const f = mtof(midi), N = Math.max(2, Math.round(SR / f)), len = Math.round(decay * SR);
  const ring = new Float32Array(N);
  let lp = 0;
  for (let k = 0; k < N; k++) { lp += (rand() - lp) * bright; ring[k] = lp; }
  const g = Math.pow(0.001, N / len), out = new Float32Array(len);
  let idx = 0, warm = 0;
  for (let i = 0; i < len; i++) {
    const next = (idx + 1) % N, v = ring[idx];
    ring[idx] = g * 0.5 * (v + ring[next]);
    idx = next;
    warm += (v - warm) * 0.55;
    out[i] = warm * amp * 2.2 * Math.min(1, (len - i) / 600);
  }
  return out;
}

// Ensemble of three detuned oscillators through a low-pass: strings, cello, drones
function pad(midi, dur, amp, { cutoff = 1200, attack = 0.5, release = 1.2, detune = 0.005, vib = 0.003,
  vibRate = 4.6, wave = 'saw' } = {}) {
  const f = mtof(midi), hold = Math.round(dur * SR), a = Math.max(1, Math.round(attack * SR));
  const r = Math.round(release * SR), out = new Float32Array(hold + r), lp = svf(1.1);
  const ph = [0.5 + rand() / 2, 0.5 + rand() / 2, 0.5 + rand() / 2], det = [1 - detune, 1, 1 + detune];
  for (let i = 0; i < out.length; i++) {
    const v = 1 + vib * Math.sin(TAU * vibRate * i / SR);
    let s = 0;
    for (let k = 0; k < 3; k++) {
      ph[k] = (ph[k] + f * det[k] * v / SR) % 1;
      s += wave === 'saw' ? 2 * ph[k] - 1 : 1 - 4 * Math.abs(ph[k] - 0.5);
    }
    out[i] = lp(s / 3, cutoff) * amp * adsr(i, hold, a, r);
  }
  return out;
}

// Voices singing "ah": saws through two vowel formants
function choir(midi, dur, amp, { attack = 0.6, release = 1.4, vowel = [730, 1090] } = {}) {
  const f = mtof(midi), hold = Math.round(dur * SR), a = Math.round(attack * SR), r = Math.round(release * SR);
  const out = new Float32Array(hold + r), f1 = svf(0.22), f2 = svf(0.22), lp = svf(1);
  const ph = [rand() / 2 + 0.5, rand() / 2 + 0.5, rand() / 2 + 0.5, rand() / 2 + 0.5];
  const det = [0.994, 0.998, 1.002, 1.006];
  for (let i = 0; i < out.length; i++) {
    const v = 1 + 0.004 * Math.sin(TAU * 5.2 * i / SR + ph[0] * 6);
    let s = 0;
    for (let k = 0; k < 4; k++) { ph[k] = (ph[k] + f * det[k] * v / SR) % 1; s += 2 * ph[k] - 1; }
    s /= 4;
    out[i] = lp(f1(s, vowel[0], 'band') + 0.6 * f2(s, vowel[1], 'band'), 3200) * amp * 3 * adsr(i, hold, a, r);
  }
  return out;
}

// Brass: fat saws whose filter opens on the attack
function brass(midi, dur, amp, { bright = 2600, attack = 0.03, release = 0.18 } = {}) {
  const f = mtof(midi), hold = Math.round(dur * SR), a = Math.round(attack * SR), r = Math.round(release * SR);
  const out = new Float32Array(hold + r), lp = svf(0.9);
  let p1 = rand() / 2 + 0.5, p2 = rand() / 2 + 0.5, p3 = 0;
  for (let i = 0; i < out.length; i++) {
    const t = i / SR;
    p1 = (p1 + f * 0.996 / SR) % 1; p2 = (p2 + f * 1.004 / SR) % 1; p3 = (p3 + f * 0.5 / SR) % 1;
    const s = (2 * p1 - 1) + (2 * p2 - 1) + 0.5 * (p3 < 0.5 ? 1 : -1);
    const swell = Math.min(1, t / 0.07);
    const cutoff = 350 + bright * swell * (0.65 + 0.35 * Math.exp(-t / 0.25));
    out[i] = lp(s / 2.5, cutoff) * amp * adsr(i, hold, a, r);
  }
  return out;
}

// Bowed short note for driving ostinatos
function spiccato(midi, amp, len = 0.32) {
  const f = mtof(midi), n = Math.round(len * SR), out = new Float32Array(n), lp = svf(0.8);
  let p1 = rand() / 2 + 0.5, p2 = rand() / 2 + 0.5;
  for (let i = 0; i < n; i++) {
    const t = i / SR;
    p1 = (p1 + f * 0.997 / SR) % 1; p2 = (p2 + f * 1.003 / SR) % 1;
    const s = (2 * p1 - 1) + (2 * p2 - 1);
    out[i] = lp(s / 2, 300 + 2200 * Math.exp(-t / 0.07)) * amp * Math.min(1, t / 0.004) * Math.exp(-t / 0.16)
      * Math.min(1, (n - i) / 200);
  }
  return out;
}

// Soft flute with breath and a vibrato that blooms after the attack
function flute(midi, dur, amp) {
  const f = mtof(midi), hold = Math.round(dur * SR), a = Math.round(0.07 * SR), r = Math.round(0.25 * SR);
  const out = new Float32Array(hold + r), breath = svf(0.4);
  let ph = 0;
  for (let i = 0; i < out.length; i++) {
    const t = i / SR, depth = 0.007 * Math.min(1, Math.max(0, t - 0.25) / 0.4);
    ph += TAU * f * (1 + depth * Math.sin(TAU * 5.1 * t)) / SR;
    const s = Math.sin(ph) + 0.18 * Math.sin(2 * ph) + 0.06 * Math.sin(3 * ph) + 0.08 * breath(rand(), f * 2, 'band');
    out[i] = s * amp * adsr(i, hold, a, r);
  }
  return out;
}

// Piano: stretched harmonics that fade faster the higher they are, plus a hammer tick
function piano(midi, dur, amp) {
  const f = mtof(midi), base = 3.2 * 2 ** ((60 - midi) / 24);
  const hold = Math.round(dur * SR), r = Math.round(0.45 * SR), out = new Float32Array(hold + r);
  for (let k = 1; k <= 8; k++) {
    const fk = k * f * Math.sqrt(1 + 0.0003 * k * k);
    if (fk > 9000) break;
    const ak = (k === 1 ? 1 : 0.7) / k ** 1.3, dk = base / k ** 0.7;
    for (let i = 0; i < out.length; i++) {
      const t = i / SR;
      out[i] += ak * Math.sin(TAU * fk * t) * Math.exp(-t / dk);
    }
  }
  for (let i = 0; i < out.length; i++) {
    const t = i / SR;
    out[i] = (out[i] + 0.15 * rand() * Math.exp(-t / 0.004)) * amp * Math.min(1, t / 0.002) * adsr(i, hold, 1, r);
  }
  return out;
}

// Music box: a sweet bell with a couple of quick overtones
function musicBox(midi, amp) {
  const f = mtof(midi), n = Math.round(2.2 * SR), out = new Float32Array(n);
  for (let i = 0; i < n; i++) {
    const t = i / SR;
    out[i] = (Math.sin(TAU * f * t) * Math.exp(-t / 0.9) + 0.25 * Math.sin(TAU * 2.01 * f * t) * Math.exp(-t / 0.35)
      + 0.08 * Math.sin(TAU * 4.97 * f * t) * Math.exp(-t / 0.12)) * amp * Math.min(1, t / 0.002);
  }
  return out;
}

// Taiko: a deep skin whose pitch drops as it rings
function taiko(amp, pitch = 1, decay = 0.38) {
  const n = Math.round(1.1 * SR), out = new Float32Array(n), lp = svf(1);
  let ph = 0;
  for (let i = 0; i < n; i++) {
    const t = i / SR;
    ph += TAU * (52 + 75 * Math.exp(-t / 0.035)) * pitch / SR;
    out[i] = (Math.sin(ph) * Math.exp(-t / decay) + 0.6 * lp(rand(), 380) * Math.exp(-t / 0.03)) * amp;
  }
  return out;
}

// Snare-like rim: noise crack over a short tone
function rim(amp) {
  const n = Math.round(0.3 * SR), out = new Float32Array(n), bp = svf(0.5);
  for (let i = 0; i < n; i++) {
    const t = i / SR;
    out[i] = (1.6 * bp(rand(), 2300, 'band') * Math.exp(-t / 0.09) + 0.5 * Math.sin(TAU * 185 * t) * Math.exp(-t / 0.04)) * amp;
  }
  return out;
}

// Shaker / hat: a whisper of high noise
function shaker(amp) {
  const n = Math.round(0.08 * SR), out = new Float32Array(n), hp = svf(0.7);
  for (let i = 0; i < n; i++) {
    const t = i / SR;
    out[i] = hp(rand(), 5000, 'high') * Math.min(1, t / 0.006) * Math.exp(-t / 0.025) * amp;
  }
  return out;
}

// Gong: inharmonic partials with long, beating decays
function gong(amp, base = 98) {
  const parts = [[1, 1, 4.5], [1.42, 0.6, 3.2], [2.09, 0.5, 2.4], [2.76, 0.35, 1.8], [3.41, 0.25, 1.3], [4.07, 0.2, 1]];
  const n = Math.round(5 * SR), out = new Float32Array(n);
  for (const [ratio, a, d] of parts) {
    const fr = base * ratio, beat = 0.6 + ratio * 0.3;
    for (let i = 0; i < n; i++) {
      const t = i / SR;
      out[i] += a * Math.sin(TAU * fr * t) * Math.exp(-t / d) * (1 + 0.25 * Math.sin(TAU * beat * t));
    }
  }
  for (let i = 0; i < n; i++) out[i] *= amp * Math.min(1, i / (0.015 * SR));
  return out;
}

/* ---------- master ---------- */

// Freeverb-style reverb on the send bus, scaled to a share of the dry level
function reverb(m, wetShare, size = 0.86, damp = 0.3) {
  const combs = [1557, 1617, 1491, 1422, 1277, 1356], aps = [556, 441, 341];
  const run = (input, spread) => {
    const out = new Float32Array(input.length);
    for (const d0 of combs) {
      const d = d0 + spread, buf = new Float32Array(d);
      let idx = 0, store = 0;
      for (let i = 0; i < input.length; i++) {
        const y = buf[idx];
        store = y * (1 - damp) + store * damp;
        buf[idx] = input[i] + store * size;
        out[i] += y;
        idx = (idx + 1) % d;
      }
    }
    for (const d0 of aps) {
      const d = d0 + spread, buf = new Float32Array(d);
      let idx = 0;
      for (let i = 0; i < out.length; i++) {
        const b = buf[idx], y = -out[i] + b;
        buf[idx] = out[i] + b * 0.5;
        out[i] = y;
        idx = (idx + 1) % d;
      }
    }
    return out;
  };
  const rms = a => Math.sqrt(a.reduce((s, v) => s + v * v, 0) / a.length) || 1;
  const wl = run(m.sL, 0), wr = run(m.sR, 23);
  const gain = wetShare * (rms(m.L) + rms(m.R)) / (rms(wl) + rms(wr));
  for (let i = 0; i < m.L.length; i++) { m.L[i] += wl[i] * gain; m.R[i] += wr[i] * gain; }
}

function finish(m, name, kbps, peak = 0.89, wet = 0.45) {
  reverb(m, wet);
  // Fold the tail onto the start so the loop has no seam
  const n = m.loop, L = m.L.slice(0, n), R = m.R.slice(0, n);
  for (let i = n; i < m.L.length; i++) { L[i % n] += m.L[i]; R[i % n] += m.R[i]; }
  // Remove DC, then a gentle soft limiter
  let pl = 0, pr = 0, xl = 0, xr = 0, max = 0;
  for (let i = 0; i < n; i++) {
    const yl = L[i] - xl + 0.997 * pl, yr = R[i] - xr + 0.997 * pr;
    xl = L[i]; xr = R[i]; pl = yl; pr = yr; L[i] = yl; R[i] = yr;
    max = Math.max(max, Math.abs(yl), Math.abs(yr));
  }
  const k = 1.6, norm = Math.tanh(k);
  const l16 = new Int16Array(n), r16 = new Int16Array(n);
  for (let i = 0; i < n; i++) {
    l16[i] = Math.round(Math.tanh(k * L[i] / max) / norm * peak * 32767);
    r16[i] = Math.round(Math.tanh(k * R[i] / max) / norm * peak * 32767);
  }
  const enc = new lamejs.Mp3Encoder(2, SR, kbps), parts = [];
  for (let i = 0; i < n; i += 1152) {
    const b = enc.encodeBuffer(l16.subarray(i, i + 1152), r16.subarray(i, i + 1152));
    if (b.length) parts.push(Buffer.from(b));
  }
  parts.push(Buffer.from(enc.flush()));
  const mp3 = Buffer.concat(parts);
  writeFileSync(new URL(name + '.mp3', OUT), mp3);
  // WAV_DIR=<folder> also writes the raw mix for checking levels
  if (process.env.WAV_DIR) {
    const wav = Buffer.alloc(44 + n * 4);
    wav.write('RIFF', 0); wav.writeUInt32LE(36 + n * 4, 4); wav.write('WAVEfmt ', 8); wav.writeUInt32LE(16, 16);
    wav.writeUInt16LE(1, 20); wav.writeUInt16LE(2, 22); wav.writeUInt32LE(SR, 24); wav.writeUInt32LE(SR * 4, 28);
    wav.writeUInt16LE(4, 32); wav.writeUInt16LE(16, 34); wav.write('data', 36); wav.writeUInt32LE(n * 4, 40);
    for (let i = 0; i < n; i++) { wav.writeInt16LE(l16[i], 44 + i * 4); wav.writeInt16LE(r16[i], 46 + i * 4); }
    writeFileSync(`${process.env.WAV_DIR}/${name}.wav`, wav);
  }
  console.log(`${name}.mp3  ${(n / SR).toFixed(1)}s  ${Math.round(mp3.length / 1024)}KB`);
}

/* ---------- harmony helpers ---------- */

const triad = (root, minor) => [root, root + (minor ? 3 : 4), root + 7];
// Melody notes: [bar, beat, beats long, midi]
function play(notes, firstBar, bar, beat, fn) {
  for (const [b, at, len, midi] of notes) fn((firstBar + b) * bar + at * beat, len * beat, midi);
}

/* =========================================================
   DUNGEON THEME: D minor, 80 BPM. Harp arpeggios over a drone, a frame drum like distant footsteps,
   then strings and a lonely flute; a second verse climbs higher before it all thins out again.
   ========================================================= */
{
  const beat = 60 / 80, bar = beat * 4, bars = 32;
  const m = track(bars * bar);
  const D = [50, true], C = [48, false], Bb = [46, false], A = [45, false], Gm = [55, true], F = [53, false];
  const verse = [D, D, Bb, Bb, Gm, Gm, A, A], climb = [D, C, Bb, A, D, F, Gm, A];
  const melody1 = [[0, 0, 2, 69], [0, 2, 1, 74], [0, 3, 1, 76], [1, 0, 3, 77], [1, 3, 1, 76],
    [2, 0, 2, 74], [2, 2, 1, 72], [2, 3, 1, 70], [3, 0, 4, 65],
    [4, 0, 1.5, 67], [4, 1.5, 0.5, 69], [4, 2, 2, 70], [5, 0, 2, 74], [5, 2, 1, 72], [5, 3, 1, 70],
    [6, 0, 2, 69], [6, 2, 1, 64], [6, 3, 1, 73], [7, 0, 4, 69]];
  const melody2 = [[0, 0, 1, 74], [0, 1, 1, 77], [0, 2, 2, 81], [1, 0, 2, 79], [1, 2, 1, 76], [1, 3, 1, 72],
    [2, 0, 2, 77], [2, 2, 2, 74], [3, 0, 3, 76], [3, 3, 1, 73],
    [4, 0, 2, 74], [4, 2, 2, 69], [5, 0, 1.5, 72], [5, 1.5, 0.5, 69], [5, 2, 2, 65],
    [6, 0, 2, 70], [6, 2, 1, 69], [6, 3, 1, 67], [7, 0, 4, 69]];

  // Drone under everything: D and A, breathing slowly
  mixIn(m, 0, pad(38, bars * bar, 0.16, { wave: 'tri', cutoff: 320, attack: 3, release: 3, vib: 0.001 }), 0, 0.3);
  mixIn(m, 0, pad(45, bars * bar, 0.08, { wave: 'tri', cutoff: 400, attack: 5, release: 3, vib: 0.001 }), 0, 0.3);

  for (let b = 0; b < bars; b++) {
    const section = Math.floor(b / 8), [root, minor] = (section === 2 ? climb : verse)[b % 8];
    const t0 = b * bar, tones = triad(root, minor), ladder = [...tones, ...tones.map(n => n + 12)];
    // Harp: up and back down the chord in eighths; the last verse mirrors it
    const order = section === 3 ? [5, 3, 2, 1, 0, 1, 2, 3] : [0, 2, 3, 4, 5, 4, 3, 2];
    order.forEach((k, i) => mixIn(m, t0 + i * beat / 2, pluck(ladder[k], i === 0 ? 0.3 : 0.22, 2.4),
      i % 2 ? 0.25 : -0.25, 0.35));
    // Cello on the root
    mixIn(m, t0, pad(root - 12, bar * 0.97, section === 0 ? 0.13 : 0.2,
      { cutoff: 520, attack: 0.25, release: 0.9, vib: 0.004 }), -0.1, 0.3);
    // Frame drum like footsteps, busier in the middle sections
    mixIn(m, t0, taiko(section === 0 ? 0.35 : 0.5, 1.5, 0.22), 0, 0.25);
    mixIn(m, t0 + 2.5 * beat, taiko(0.22, 1.7, 0.18), 0.1, 0.25);
    if (section === 1 || section === 2) {
      mixIn(m, t0 + 1.5 * beat, taiko(0.16, 1.8, 0.15), -0.1, 0.25);
      for (let e = 0; e < 8; e++) mixIn(m, t0 + e * beat / 2, shaker(e % 2 ? 0.035 : 0.055), 0.4, 0.15);
      // Strings hold the chord
      tones.forEach((n, i) => mixIn(m, t0, pad(n + 12, bar, 0.05, { cutoff: 1500, attack: 0.9, release: 1.6 }),
        -0.4 + 0.4 * i, 0.5));
    }
    if (b % 8 === 0) mixIn(m, t0, gong(section === 0 ? 0.12 : 0.2), 0, 0.5);
  }
  play(melody1, 8, bar, beat, (t, len, n) => mixIn(m, t, flute(n, len * 0.95, 0.2), 0.15, 0.5));
  play(melody2, 16, bar, beat, (t, len, n) => mixIn(m, t, flute(n, len * 0.95, 0.2), 0.15, 0.5));
  // The last verse: the tune echoes on the harp an octave up
  play(melody1, 24, bar, beat, (t, len, n) => mixIn(m, t, pluck(n + 12, 0.16, 2.8, 0.7), 0.3, 0.55));
  finish(m, 'game_music', 112, 0.85, 0.5);
}

/* =========================================================
   BOSS THEME: D minor with a Phrygian E-flat, 150 BPM. Taiko and a driving low-string ostinato,
   brass stabs, a choir, and a brass theme that returns an octave higher.
   ========================================================= */
{
  const beat = 60 / 150, bar = beat * 4, bars = 32;
  const m = track(bars * bar, 5);
  const prog = [[38, true], [39, false], [38, true], [36, false], [34, false], [39, false], [33, false], [33, false]];
  const theme = [[0, 0, 1.5, 74], [0, 1.5, 0.5, 74], [0, 2, 1, 77], [0, 3, 1, 81], [1, 0, 3, 79], [1, 3, 0.5, 77],
    [1, 3.5, 0.5, 75], [2, 0, 2, 74], [2, 2, 2, 69], [3, 0, 1.5, 72], [3, 1.5, 0.5, 74], [3, 2, 2, 76],
    [4, 0, 2, 77], [4, 2, 1, 74], [4, 3, 1, 70], [5, 0, 2, 75], [5, 2, 2, 79],
    [6, 0, 2, 81], [6, 2, 1, 79], [6, 3, 0.5, 77], [6, 3.5, 0.5, 76], [7, 0, 2, 73], [7, 2, 2, 69]];

  for (let b = 0; b < bars; b++) {
    const section = Math.floor(b / 8), [root, minor] = prog[b % 8], t0 = b * bar;
    // Ostinato in eighths
    [0, 0, 12, 0, 0, 12, 7, 12].forEach((step, i) =>
      mixIn(m, t0 + i * beat / 2, spiccato(root + step, i === 0 ? 0.5 : 0.38), i % 2 ? 0.2 : -0.2, 0.15));
    // Drums
    [[0, 1, 0.75], [1.5, 1, 0.5], [2, 1, 0.65], [3, 1.4, 0.4], [3.5, 1.4, 0.4]].forEach(([at, p, a]) =>
      mixIn(m, t0 + at * beat, taiko(a, p), 0, 0.2));
    if (section >= 1) [1, 3].forEach(at => mixIn(m, t0 + at * beat, rim(0.35), 0.15, 0.2));
    if (section >= 2) for (let e = 0; e < 8; e++) mixIn(m, t0 + e * beat / 2, shaker(0.07), 0.35, 0.1);
    if (b % 8 === 7) {
      // Tom fill into the next phrase
      for (let s = 0; s < 8; s++) mixIn(m, t0 + 2 * beat + s * beat / 4, taiko(0.45, 1.9 - s * 0.1, 0.15), -0.3 + s * 0.08, 0.2);
    }
    // Choir: a low hum at first, full chords later
    const tones = triad(root + 24, minor);
    if (section === 0 || section === 1) mixIn(m, t0, choir(root + 24, bar, 0.08), 0, 0.5);
    else tones.forEach((n, i) => mixIn(m, t0, choir(n, bar, 0.07), -0.3 + 0.3 * i, 0.5));
    // Brass stabs on the downbeat
    if (section >= 2) tones.forEach((n, i) => mixIn(m, t0, brass(n - 12, beat * 0.8, 0.09), -0.2 + 0.2 * i, 0.3));
    if (b % 8 === 0) mixIn(m, t0, gong(0.3, 82), 0, 0.4);
  }
  play(theme, 8, bar, beat, (t, len, n) => {
    mixIn(m, t, brass(n, len * 0.92, 0.21), 0.1, 0.35);
    mixIn(m, t, brass(n - 12, len * 0.92, 0.1), -0.1, 0.35);
  });
  play(theme, 24, bar, beat, (t, len, n) => {
    mixIn(m, t, brass(n, len * 0.92, 0.21, { bright: 3200 }), 0.1, 0.35);
    mixIn(m, t, choir(n + 12, len, 0.05, { attack: 0.05, release: 0.3 }), -0.1, 0.4);
  });
  finish(m, 'boss_music', 128, 0.9, 0.35);
}

/* =========================================================
   GAME OVER: A minor, 60 BPM. A lone piano lament over a cello, a tolling bell to begin.
   ========================================================= */
{
  const beat = 1, bar = beat * 4, bars = 8;
  const m = track(bars * bar, 6);
  const prog = [[45, true], [41, false], [48, false], [40, false], [45, true], [50, true], [40, false], [45, true]];
  const tune = [[0, 0, 2, 76], [0, 2, 1, 74], [0, 3, 1, 72], [1, 0, 2, 72], [1, 2, 1, 71], [1, 3, 1, 69],
    [2, 0, 2, 67], [2, 2, 1, 64], [2, 3, 1, 67], [3, 0, 3, 68], [3, 3, 1, 71],
    [4, 0, 2, 69], [4, 2, 1, 72], [4, 3, 1, 76], [5, 0, 2, 77], [5, 2, 1, 76], [5, 3, 1, 74],
    [6, 0, 2, 71], [6, 2, 1, 68], [6, 3, 1, 64], [7, 0, 4, 69]];

  mixIn(m, 0, gong(0.35, 73), 0, 0.6);
  for (let b = 0; b < bars; b++) {
    const [root, minor] = prog[b], t0 = b * bar, tones = triad(root, minor);
    // Left hand: root, fifth, octave, tenth
    [tones[0] - 12, tones[2] - 12, tones[0], tones[1] + 12].forEach((n, i) =>
      mixIn(m, t0 + i * beat, piano(n, beat * 1.6, 0.16), -0.25, 0.45));
    mixIn(m, t0, pad(root - 12, bar, 0.12, { cutoff: 600, attack: 0.6, release: 1.5, vib: 0.004 }), -0.1, 0.4);
    tones.forEach((n, i) => mixIn(m, t0, pad(n + 12, bar, 0.03, { cutoff: 1300, attack: 1.2, release: 2 }),
      -0.3 + 0.3 * i, 0.6));
  }
  play(tune, 0, bar, beat, (t, len, n) => {
    mixIn(m, t, piano(n, len, 0.3), 0.2, 0.5);
    mixIn(m, t + 0.02, musicBox(n + 12, 0.05), 0.4, 0.6);
  });
  finish(m, 'game_over_music', 96, 0.8, 0.6);
}
