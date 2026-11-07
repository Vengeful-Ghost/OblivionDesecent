// ===========================================================================================================
//  >>> EXPORT AFTER EDITING <<<  The game only sees sprite changes once they are exported. From the repo
//  root (the OblivionDesecent folder with app/ and art/ in it), run:
//
//      node art/tools/export.mjs
//
//  That rebuilds app/src/main/assets/px/ (the sheets and sprites.json the game loads); commit those too.
//  To look before you export:   node art/tools/look.mjs fx.rage --zoom 3
//
// ===========================================================================================================
// A boss's LAST STAND (Enemy.draw, GameActivity's ultimate): the boss stands in a ring of crimson hellfire
// while it rages, and its roar blasts out when the rage begins.
//
//   back   (loop) the far half of the fire ring and the tall tongues behind the body, a dark red glow pooled
//          on the floor, heat and embers going up. Drawn before the body.
//   front  (loop) the near half of the ring, low and licking, and embers spat out at the front. Drawn after.
//   (the roar that starts it is od/roar.js)
//
// The ring is an ellipse RX x RY art px round the feet (the floor's tilt flattens it), sized for a boss.

import { fit, TAU } from "../../core/index.js";
import { noiseFor, flameTongue, flicker, hotFirst, looped, ember, floorLight, BIG_LAYERS, BACK_LAYERS, LAYERS } from "../fire/_lib.js";

export const meta = {
  name: "Rage",
  desc: "A boss's last stand: a ring of crimson hellfire round its feet, the far half and tall tongues behind it (back), the near half licking in front (front), embers and heat rising.",
  tags: ["od", "boss", "rage", "fire", "ultimate", "loop"],
  size: [112, 96], origin: [56, 70], duration: 1.2, loop: true, fps: 24, bg: "ground",
  options: {
    anim: "back",
    palette: ["#1c0404", "#4a0808", "#7e1010", "#b81818", "#e8301c", "#ff5a24", "#ff8e3a", "#ffc46a", "#fff0c0"],
    seed: 9,
  },
};

const RX = 30, RY = 11, TONGUES = 16;

export function draw(p, t, o) {
  const F = fit(o.palette, 9), H = hotFirst(F), D = meta.duration;
  const N0 = noiseFor(D), N = (rate, seed = 0, off = 0) => N0(t, rate, seed, off);
  const front = o.anim === "front";
  if (!front) {
    // the floor under it glows like a forge, breathing with the fire
    floorLight(p, 0, 0, RX + 10, RY + 5, F[4], 0.55 * flicker(N));
    floorLight(p, 0, 0, RX - 6, RY - 3, F[6], 0.35 * flicker(N, 3));
  }
  // the tongues round the ring: the far half (and tall ones) behind the body, the near half in front
  for (let i = 0; i < TONGUES; i++) {
    const a = (i / TONGUES) * TAU + 0.2, s = Math.sin(a), behind = s < 0;
    if (behind === front) continue;
    const x = Math.cos(a) * RX, y = s * RY;
    const breath = 0.7 + 0.3 * N(1.6, i, i * 0.37);
    const tall = behind ? (i % 3 === 0 ? 30 : 20) : 11;
    const h = tall * breath * (0.85 + 0.15 * flicker(N, i));
    const w = behind ? 3.6 : 3;
    // tips swept outward, and a little by the heat's draught
    const sx = Math.cos(a) * 4 + N(2.2, i + 40) * 3;
    flameTongue(p, Math.round(x), Math.round(y), h, w, sx, N, i * 13 + 5, H, behind ? (i % 3 === 0 ? BIG_LAYERS : BACK_LAYERS) : LAYERS, 0.6, 1.3);
  }
  // embers going up off the ring, the front ones spat out towards you
  looped(t, D, front ? 10 : 18, o.seed + (front ? 7 : 0), (r) => {
    const a = r.next() * TAU, s = Math.sin(a);
    return { life: 0.7 + r.next() * 0.7, x: Math.cos(a) * RX * (0.7 + r.next() * 0.4), y: s * RY, vy: 30 + r.next() * 40, vx: (r.next() - 0.5) * 16, ph: r.next(), size: r.next() < 0.3 ? 2 : 1, back: s < 0 };
  }, (q, age) => {
    if (q.back === front) return;
    const k = 1 - age / q.life;
    ember(p, q.x + q.vx * age + Math.sin(age * 9 + q.ph * 6) * 1.2, q.y - q.vy * age - 4, k, q.size, [F[8], F[7], F[5], F[3]], q.ph);
  });
}
