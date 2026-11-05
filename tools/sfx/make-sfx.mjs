// Synthesises the game's short sound effects as 16-bit mono WAV files into app/src/main/res/raw.
// Everything is generated from oscillators and filtered noise, so the sounds are original and
// license-free. Run: node tools/sfx/make-sfx.mjs
import { writeFileSync } from 'node:fs';

const SR = 22050;
const OUT = new URL('../../app/src/main/res/raw/', import.meta.url);

let seed = 1234567;
const rand = () => { seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0; return seed / 2147483648 - 1; };
const env = (t, attack, decay) => (t < attack ? t / attack : Math.exp(-(t - attack) / decay));
const tail = (i, n) => Math.min(1, (n - i) / 200);

function buffer(seconds) { return new Float32Array(Math.round(seconds * SR)); }

// One oscillator note; freq may be a function of progress 0..1
function tone(a, start, dur, freq, amp, { wave = 'sine', attack = 0.004, decay = dur / 3, vib = 0, vibRate = 6 } = {}) {
  const s0 = Math.round(start * SR), n = Math.round(dur * SR);
  let ph = 0;
  for (let i = 0; i < n && s0 + i < a.length; i++) {
    const t = i / SR;
    const f = (typeof freq === 'function' ? freq(i / n) : freq) * (1 + vib * Math.sin(2 * Math.PI * vibRate * t));
    ph += 2 * Math.PI * f / SR;
    const c = (ph / (2 * Math.PI)) % 1;
    const v = wave === 'square' ? (c < 0.5 ? 1 : -1)
      : wave === 'saw' ? c * 2 - 1
      : wave === 'tri' ? 1 - 4 * Math.abs(c - 0.5)
      : Math.sin(ph);
    a[s0 + i] += v * amp * env(t, attack, decay) * tail(i, n);
  }
}

// Filtered white noise (state-variable filter); center may sweep, shape overrides the envelope
function noise(a, start, dur, amp, { center = 1000, damp = 1, type = 'band', attack = 0.002, decay = dur / 3, shape } = {}) {
  const s0 = Math.round(start * SR), n = Math.round(dur * SR);
  let low = 0, band = 0;
  for (let i = 0; i < n && s0 + i < a.length; i++) {
    const p = i / n, t = i / SR;
    const c = Math.min(3500, typeof center === 'function' ? center(p) : center);
    const f = 2 * Math.sin(Math.PI * c / SR);
    low += f * band;
    const high = rand() - low - damp * band;
    band += f * high;
    const v = type === 'low' ? low : type === 'high' ? high : band;
    a[s0 + i] += v * amp * (shape ? shape(p) : env(t, attack, decay)) * tail(i, n);
  }
}

// Metallic strike: inharmonic partials that die away quickly
function metal(a, start, freqs, amp, decay) {
  freqs.forEach((f, k) => tone(a, start, decay * 6, f, amp / Math.sqrt(k + 1), { attack: 0.001, decay: decay / (1 + k * 0.3) }));
}

function bell(a, start, f, amp, decay, dur = decay * 5) {
  tone(a, start, dur, f, amp, { attack: 0.002, decay });
  tone(a, start, dur, f * 2.76, amp * 0.25, { attack: 0.002, decay: decay * 0.5 });
}

function save(name, a, peak = 0.9) {
  let max = 0;
  for (const v of a) max = Math.max(max, Math.abs(v));
  const gain = max > 0 ? peak / max : 1;
  const data = Buffer.alloc(44 + a.length * 2);
  data.write('RIFF', 0); data.writeUInt32LE(36 + a.length * 2, 4); data.write('WAVE', 8);
  data.write('fmt ', 12); data.writeUInt32LE(16, 16); data.writeUInt16LE(1, 20); data.writeUInt16LE(1, 22);
  data.writeUInt32LE(SR, 24); data.writeUInt32LE(SR * 2, 28); data.writeUInt16LE(2, 32); data.writeUInt16LE(16, 34);
  data.write('data', 36); data.writeUInt32LE(a.length * 2, 40);
  for (let i = 0; i < a.length; i++) {
    const fadeIn = Math.min(1, i / 32);
    const v = Math.tanh(a[i] * gain * 1.2) / Math.tanh(1.2) * fadeIn;
    data.writeInt16LE(Math.round(Math.max(-1, Math.min(1, v)) * 32767), 44 + i * 2);
  }
  writeFileSync(new URL(name + '.wav', OUT), data);
  console.log(`${name}.wav  ${(a.length / SR).toFixed(2)}s`);
}

const sweep = (from, to) => p => from + (to - from) * p;
const hump = p => Math.sin(Math.PI * p);

let a;

// Spin: two rushing whooshes around a low hum
a = buffer(0.55);
noise(a, 0, 0.5, 1, { center: p => 500 + 2600 * hump(p), damp: 0.7, shape: p => hump(p) ** 1.5 });
noise(a, 0.18, 0.32, 0.6, { center: p => 800 + 2400 * hump(p), damp: 0.7, shape: hump });
tone(a, 0, 0.5, sweep(150, 90), 0.3, { wave: 'tri', attack: 0.05, decay: 0.3 });
save('spin_attack', a);

// Perfect dodge: a reversed swoosh into rising chimes
a = buffer(0.75);
noise(a, 0, 0.16, 0.6, { center: sweep(1200, 3400), damp: 0.8, shape: p => p });
bell(a, 0.08, 1318.5, 0.5, 0.18);
bell(a, 0.15, 1760, 0.5, 0.18);
bell(a, 0.22, 2637, 0.4, 0.2);
save('perfect_dodge', a);

// Level up: bright major arpeggio with a sparkle
a = buffer(0.95);
[523.25, 659.25, 783.99, 1046.5].forEach((f, i) => {
  tone(a, i * 0.09, 0.55, f, 0.5, { wave: 'tri', decay: 0.2 });
  tone(a, i * 0.09, 0.55, f * 2, 0.12, { decay: 0.15 });
});
noise(a, 0.32, 0.45, 0.15, { center: 3400, damp: 0.5, decay: 0.12 });
save('level_up', a);

// Perk picked: two-note bell
a = buffer(0.65);
bell(a, 0, 783.99, 0.5, 0.25);
bell(a, 0.1, 1174.66, 0.5, 0.25);
save('perk_pick', a);

// Card flip: papery tick
a = buffer(0.14);
noise(a, 0, 0.12, 1, { center: 2600, damp: 1, decay: 0.02 });
tone(a, 0, 0.1, sweep(200, 90), 0.4, { wave: 'tri', decay: 0.03 });
save('card_flip', a);

// Key: three quick metallic pings
a = buffer(0.5);
[[0, 2200], [0.07, 2500], [0.14, 2900]].forEach(([t, f]) => metal(a, t, [f, f * 1.47, f * 2.09], 0.5, 0.08));
save('key_pickup', a);

// Vault: heavy clunk, latch click, door creak
a = buffer(0.85);
noise(a, 0, 0.2, 1, { center: 300, damp: 1, type: 'low', decay: 0.06 });
tone(a, 0, 0.25, sweep(90, 45), 0.8, { decay: 0.12 });
noise(a, 0.22, 0.05, 0.6, { center: 3000, decay: 0.01 });
metal(a, 0.22, [1800, 2600], 0.3, 0.03);
tone(a, 0.3, 0.5, sweep(210, 170), 0.2, { wave: 'tri', attack: 0.05, decay: 0.3, vib: 0.03, vibRate: 30 });
save('vault_unlock', a);

// Locked gate: dull two-step buzz
a = buffer(0.3);
tone(a, 0, 0.12, 110, 0.3, { wave: 'square', decay: 0.05 });
tone(a, 0.11, 0.16, 92, 0.3, { wave: 'square', decay: 0.06 });
noise(a, 0, 0.08, 0.6, { center: 400, type: 'low', decay: 0.03 });
save('gate_locked', a);

// Mimic: rumbling growl ending in a chomp
a = buffer(0.95);
tone(a, 0, 0.72, sweep(78, 55), 0.5, { wave: 'saw', attack: 0.08, decay: 0.5, vib: 0.08, vibRate: 22 });
noise(a, 0, 0.72, 0.5, { center: 500, type: 'low', shape: hump });
noise(a, 0.74, 0.15, 1, { center: 800, decay: 0.03 });
tone(a, 0.74, 0.18, sweep(160, 60), 0.8, { decay: 0.05 });
save('mimic_awaken', a);

// Potion: four bubbling gulps
a = buffer(0.65);
for (let k = 0; k < 4; k++) {
  const base = 300 + k * 40;
  tone(a, k * 0.14, 0.1, p => base * (1 + 1.5 * p), 0.6, { decay: 0.04 });
}
noise(a, 0, 0.6, 0.1, { center: 600, type: 'low', shape: hump });
save('potion_drink', a);

// Purchase: cha-ching
a = buffer(0.6);
noise(a, 0, 0.05, 0.7, { center: 3400, decay: 0.015 });
bell(a, 0, 1567.98, 0.5, 0.15);
bell(a, 0.08, 2093, 0.5, 0.18);
save('purchase', a);

// Sell: coins clinking into a pouch
a = buffer(0.4);
metal(a, 0, [2400, 3300], 0.5, 0.05);
metal(a, 0.09, [2000, 2900], 0.5, 0.06);
tone(a, 0.12, 0.25, 1046.5, 0.3, { decay: 0.12 });
save('sell', a);

// Equip: armour/metal clink
a = buffer(0.32);
noise(a, 0, 0.06, 0.8, { center: 2000, damp: 0.5, decay: 0.015 });
metal(a, 0, [880, 1340, 2350, 3100], 0.5, 0.06);
save('equip', a);

// UI tap
a = buffer(0.06);
tone(a, 0, 0.05, 1500, 0.6, { decay: 0.012 });
noise(a, 0, 0.02, 0.4, { center: 3000, decay: 0.004 });
save('ui_click', a, 0.7);

// Critical hit: sharp clang over a punch
a = buffer(0.38);
noise(a, 0, 0.08, 1, { center: 2500, decay: 0.02 });
metal(a, 0, [1200, 1860, 2760], 0.6, 0.07);
tone(a, 0, 0.15, sweep(220, 110), 0.7, { decay: 0.05 });
save('crit_hit', a);

// Enemy death: falling poof
a = buffer(0.48);
noise(a, 0, 0.45, 1, { center: sweep(2200, 300), damp: 0.8, type: 'low', shape: p => (1 - p) ** 2 });
tone(a, 0, 0.3, sweep(320, 70), 0.7, { decay: 0.1 });
save('enemy_death', a);

// Stagger: bonk and birdies
a = buffer(0.36);
tone(a, 0, 0.3, sweep(540, 280), 0.8, { decay: 0.1 });
tone(a, 0, 0.25, sweep(270, 140), 0.15, { wave: 'square', decay: 0.06 });
tone(a, 0.1, 0.1, 2000, 0.15, { decay: 0.04 });
tone(a, 0.17, 0.1, 2400, 0.15, { decay: 0.04 });
save('stagger', a);

// Enemy wind-up: rising warning
a = buffer(0.22);
tone(a, 0, 0.2, sweep(260, 520), 0.3, { wave: 'saw', attack: 0.08, decay: 0.2 });
noise(a, 0, 0.2, 0.2, { center: sweep(800, 2000), shape: p => p });
save('enemy_windup', a, 0.7);

// Enemy shot: low pew
a = buffer(0.2);
tone(a, 0, 0.18, sweep(620, 240), 0.35, { wave: 'square', decay: 0.06 });
noise(a, 0, 0.05, 0.3, { center: 1500, decay: 0.02 });
save('enemy_shot', a, 0.75);

// Armour break: shattering shards
a = buffer(0.6);
noise(a, 0, 0.3, 0.8, { center: 3000, damp: 0.4, decay: 0.05 });
for (let k = 0; k < 6; k++) metal(a, k * 0.045, [1500 + rand() * 1000 + k * 250], 0.3, 0.05);
tone(a, 0, 0.2, sweep(180, 90), 0.4, { decay: 0.08 });
save('armor_break', a);

// Player death: falling minor notes over a rumble
a = buffer(1.25);
tone(a, 0, 0.45, 392, 0.5, { wave: 'tri', decay: 0.2 });
tone(a, 0.25, 0.5, 311.13, 0.5, { wave: 'tri', decay: 0.25 });
tone(a, 0.5, 0.7, 261.63, 0.5, { wave: 'tri', decay: 0.4 });
noise(a, 0, 1.15, 0.4, { center: 200, type: 'low', decay: 0.5 });
save('player_death', a);

// Revive / second wind: rising swell into chimes
a = buffer(0.95);
tone(a, 0, 0.6, sweep(300, 1200), 0.4, { attack: 0.3, decay: 0.5 });
noise(a, 0, 0.55, 0.2, { center: sweep(500, 3000), shape: p => p });
bell(a, 0.45, 1046.5, 0.4, 0.15);
bell(a, 0.52, 1318.5, 0.4, 0.15);
bell(a, 0.6, 1568, 0.4, 0.18);
save('revive', a);

// Summon: eerie detuned rise and breath
a = buffer(0.75);
tone(a, 0, 0.7, sweep(200, 350), 0.4, { attack: 0.2, decay: 0.4, vib: 0.02, vibRate: 7 });
tone(a, 0, 0.7, p => (200 + 150 * p) * 1.03, 0.4, { attack: 0.2, decay: 0.4, vib: 0.02, vibRate: 5 });
noise(a, 0, 0.65, 0.3, { center: 900, damp: 0.3, shape: hump });
save('summon', a);

// Enrage: roar
a = buffer(0.85);
noise(a, 0, 0.8, 1, { center: p => 400 + 500 * hump(p), damp: 0.5, type: 'low', shape: p => hump(Math.min(1, p * 1.3)) });
tone(a, 0, 0.78, p => 95 + 30 * hump(p), 0.4, { wave: 'saw', attack: 0.1, decay: 0.5, vib: 0.05, vibRate: 25 });
save('enrage', a);

// Blink: teleport zap
a = buffer(0.3);
noise(a, 0, 0.25, 0.6, { center: sweep(3400, 1400), damp: 0.6, decay: 0.06 });
tone(a, 0, 0.2, sweep(1600, 400), 0.5, { decay: 0.05 });
save('blink', a);

// Thud: heavy landing or a boss slam
a = buffer(0.36);
tone(a, 0, 0.3, sweep(110, 50), 1, { decay: 0.1 });
noise(a, 0, 0.2, 0.7, { center: 250, type: 'low', decay: 0.05 });
save('thud', a);
