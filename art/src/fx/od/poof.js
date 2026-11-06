// ===========================================================================================================
//  >>> EXPORT AFTER EDITING <<<  The game only sees sprite changes once they are exported. From the repo
//  root (the OblivionDesecent folder with app/ and art/ in it), run:
//
//      node art/tools/export.mjs
//
//  That rebuilds app/src/main/assets/px/ (the sheets and sprites.json the game loads); commit those too.
//  Needs Node 18 or newer, nothing else. To look before you export:
//
//      node art/tools/look.mjs player        (any name prefix from art/sprites.mjs, e.g. enemy. boss. prop.)
//
//  which writes art/records/look/<prefix>.png.
// ===========================================================================================================
// A small death puff for a body this size: a white glint where it was, a ring of soft dust that rolls out
// low along the floor and thins, a few flecks thrown up. Built from pixelfx's sim (DUST, FLAKE, sparkle);
// pixelfx's spawn/poof is a showpiece three times the size. One-shot; ends empty.

import { Particles, P, Rng, fit, sparkle, TAU } from "../../core/index.js";

export const meta = {
  name: "Small poof",
  desc: "A small puff of dust and flecks where a body vanished.",
  tags: ["od", "spawn", "death", "poof"],
  size: [56, 40], origin: [28, 30], duration: 0.9, loop: false, fps: 15, bg: "ground",
  options: { palette: ["#2a2440", "#5e5878", "#9a94b4", "#cdc8e0", "#f2f0fa", "#ffffff"], seed: 21 },
};

export function create(o) {
  const C = fit(o.palette, 6), R = new Rng(o.seed), ps = new Particles({ rng: R, ground: () => 0 });
  for (let i = 0; i < 10; i++) {
    const a = (i / 10) * TAU + R.range(-0.2, 0.2), sp = R.range(22, 38);
    ps.spawn(P.DUST, Math.cos(a) * 3, -5 + Math.sin(a) * 2, Math.cos(a) * sp, Math.sin(a) * sp * 0.45 - 6,
      R.range(0.45, 0.75), C[R.int(2, 4)], R.range(2, 3.2));
  }
  for (let i = 0; i < 5; i++) ps.spawn(P.FLAKE, R.range(-2, 2), -8, R.range(-26, 26), R.range(-60, -25), R.range(0.3, 0.55), C[5]);
  let t = 0;
  return {
    update(dt) { t += dt; ps.update(dt); },
    draw(p) {
      if (t < 0.1) sparkle(p, 0, -8, 5 * (1 - t / 0.1), C[5], C[4]);
      ps.draw(p);
    },
  };
}
