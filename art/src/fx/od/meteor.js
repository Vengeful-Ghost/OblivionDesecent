// ===========================================================================================================
//  >>> EXPORT AFTER EDITING <<<  From the repo root run:  node art/tools/export.mjs
//  To look first:  node art/tools/look.mjs fx.meteor --zoom 3
// ===========================================================================================================
// A burning rock falling out of the dark during a boss's LAST STAND (GameActivity's blasts): a cracked, glowing
// stone at the pivot with a crimson comet tail streaming up and back behind it, the way it came. The game slides
// it down onto its warning mark and plays fx.blast where it lands.

import { fit, hex, TAU } from "../../core/index.js";
import { noiseFor, flameBlade, hotFirst, looped, ember, solid, BIG_LAYERS } from "../fire/_lib.js";

export const meta = {
  name: "Meteor",
  desc: "A burning rock falling: a cracked glowing stone with a crimson comet tail streaming up and back behind it.",
  tags: ["od", "boss", "meteor", "fire", "projectile", "loop"],
  size: [64, 80], origin: [20, 66], duration: 0.6, loop: true, fps: 20, bg: "ground",
  options: {
    palette: ["#1c0404", "#4a0808", "#7e1010", "#b81818", "#e8301c", "#ff5a24", "#ff8e3a", "#ffc46a", "#fff0c0"],
    rock: ["#160e10", "#2c1e20", "#463034"],
    seed: 4,
  },
};

// the way it falls: down and a little to the left, so the tail streams up and to the right
const UX = 0.42, UY = -0.91;

export function draw(p, t, o) {
  const F = fit(o.palette, 9), H = hotFirst(F), Rk = o.rock, D = meta.duration;
  const N0 = noiseFor(D), N = (rate, seed = 0, off = 0) => N0(t, rate, seed, off);
  // the tail: a long outer blade and a hotter inner one, crackling
  flameBlade(p, 0, -4, UX, UY, 52 + 4 * N(3, 1), 7, 3 * N(2, 2), N, 11, H, BIG_LAYERS, 0.8, 1.6);
  flameBlade(p, 0, -4, UX, UY, 30 + 3 * N(4, 3), 4, -2 * N(2.5, 4), N, 23, H, undefined, 0.5, 1);
  // embers shed off the tail
  looped(t, D, 10, o.seed, (r) => ({ life: 0.3 + r.next() * 0.3, d: 6 + r.next() * 30, s: (r.next() - 0.5) * 8, ph: r.next() }), (q, age) => {
    const k = 1 - age / q.life, d = q.d + age * 40;
    ember(p, UX * d - UY * q.s, -4 + UY * d + UX * q.s, k, 1, [F[8], F[7], F[5], F[3]], q.ph);
  });
  // the rock itself: dark stone, lit cracks, a hot face where it meets the air
  solid(p, 0, -4, 6.5, hex(Rk[0]));
  solid(p, -0.5, -4.5, 5.5, hex(Rk[1]));
  solid(p, 1.2, -5.5, 3.2, hex(Rk[2]));
  for (let i = 0; i < 5; i++) {
    const a = (i / 5) * TAU + t * 3, r = 2 + (i % 2) * 2;
    p.px(Math.round(Math.cos(a) * r), Math.round(-4 + Math.sin(a) * r), F[6 + (i % 3)]);
  }
  solid(p, -2.5, -1.5, 2, F[6]);
  p.glow(0, -4, 14, F[5], 0.45);
}
