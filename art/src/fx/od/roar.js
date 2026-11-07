// ===========================================================================================================
//  >>> EXPORT AFTER EDITING <<<  From the repo root run:  node art/tools/export.mjs
//  To look first:  node art/tools/look.mjs fx.roar --zoom 3
// ===========================================================================================================
// A boss's roar as its LAST STAND begins (GameActivity's ultimate): a white flash, two shock rings racing out
// over the floor, crimson streaks screaming out in every direction, dust thrown low, grit and sparks hopping.
// Its hellfire ring after that is od/rage.js.

import { Particles, P, Rng, fit, band, sparkle, TAU, mix, hex, WHITE } from "../../core/index.js";

export const meta = {
  name: "Roar",
  desc: "A boss's roar: a white flash, two shock rings over the floor, crimson streaks screaming out all round, dust and grit thrown out.",
  tags: ["od", "boss", "roar", "shockwave", "ultimate", "one-shot"],
  size: [140, 96], origin: [70, 60], duration: 0.9, loop: false, fps: 20, bg: "ground",
  options: {
    palette: ["#1c0404", "#4a0808", "#7e1010", "#b81818", "#e8301c", "#ff5a24", "#ff8e3a", "#ffc46a", "#fff0c0"],
    dust: ["#2a2024", "#463438", "#6a5054", "#927478"],
    seed: 9,
  },
};

const RX = 30, RY = 11;

export function create(o) {
  const C = fit(o.palette, 9), D = fit(o.dust, 4), R = new Rng(o.seed + 31);
  const floor = () => 0;
  const under = new Particles({ rng: R, ground: floor, sparks: [o.palette[8], o.palette[7], o.palette[5]] });
  const over = new Particles({ rng: R, ground: floor, sparks: [o.palette[8], o.palette[7], o.palette[5]] });
  const along = (a, v) => [Math.cos(a) * v, Math.sin(a) * v * 0.5];
  // crimson streaks screaming out in every direction, from the body's height
  for (let i = 0; i < 30; i++) {
    const a = (i / 30) * TAU + R.range(-0.08, 0.08), [vx, vy] = along(a, R.range(160, 280));
    over.spawn(P.STREAK, Math.cos(a) * 6, -R.range(8, 26) + Math.sin(a) * 3, vx, vy, R.range(0.18, 0.34), C[R.int(5, 8)], 1, R.range(0, 0.1));
  }
  // dust thrown out low over the floor
  for (let i = 0; i < 34; i++) {
    const a = R.range(0, TAU), [vx, vy] = along(a, R.range(50, 140));
    under.spawn(P.DUST, Math.cos(a) * RX * 0.5, Math.sin(a) * RY * 0.5, vx, vy - R.range(2, 8), R.range(0.45, 0.8), R.chance(0.3) ? mix(hex(o.dust[2]), hex(o.palette[4]), 0.4) : D[R.int(1, 3)], R.range(2.5, 4.6), R.range(0, 0.06));
  }
  // grit hopping and sparks spat high
  for (let i = 0; i < 12; i++) {
    const a = R.range(0, TAU), [vx] = along(a, R.range(40, 110));
    over.spawn(P.GRIT, Math.cos(a) * RX * 0.6, -1, vx, -R.range(80, 160), R.range(0.45, 0.75), R.pick([D[1], D[2], D[3]]), R.chance(0.3) ? 2 : 1);
  }
  for (let i = 0; i < 14; i++) {
    const a = R.range(0, TAU), [vx, vy] = along(a, R.range(70, 160));
    over.spawn(P.SPARK, 0, -R.range(10, 24), vx, vy - R.range(60, 130), R.range(0.3, 0.55), WHITE, 1);
  }
  let t = 0;
  return {
    update(dt) { t += dt; under.update(dt); over.update(dt); },
    draw(p) {
      under.draw(p);
      // two shock rings racing out over the floor, the second a beat behind
      for (const [start, reach] of [[0, 52], [0.12, 40]]) {
        const u = (t - start) / 0.4;
        if (u < 0 || u >= 1) continue;
        const r = 4 + reach * (1 - (1 - u) * (1 - u));
        band(p, 0, 0, r, 0.42, Math.max(1, 4 * (1 - u)), (1 - u) * 0.95, o.palette.slice(3), () => true, u < 0.25);
      }
      // the flash at its heart
      if (t < 0.12) {
        const k = 1 - t / 0.12;
        sparkle(p, 0, -16, 12 * k, WHITE, C[7], 0.85 * k);
      }
      over.draw(p);
    },
  };
}
