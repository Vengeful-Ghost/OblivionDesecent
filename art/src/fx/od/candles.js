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
// A cluster of three candles standing on the floor, at a candle's real size beside a body (pixelfx's own
// candle is a close-up, as tall as a torso here). Wax with drips and a wick, each burning a flame a few
// pixels tall: at this size pixelfx's tongues and puffs turn to noise, so the flame is placed pixel by pixel,
// swaying and stretching on pixelfx's looping noise, with a glow on the light buffer.
// Laid out like Props.java's old candles; origin: the cluster's middle on the floor.

import { fit, hex } from "../../core/index.js";
import { noiseFor } from "../fire/_lib.js";

const D = 2;

export const anims = { burn: { frames: 24, fps: 12 } };

export const meta = {
  name: "Candles",
  desc: "Three floor candles of different heights, each with its own small flame.",
  tags: ["od", "prop", "fire", "candle", "loop"],
  size: [32, 40], origin: [16, 28], duration: D, loop: true, fps: 12, bg: "dark",
  options: {
    anim: "burn", frame: -1,
    palette: ["#4a1712", "#8a1f17", "#c9281f", "#f2561d", "#ff8a2a", "#ffc23d", "#ffe26b", "#fff3a6", "#ffffff"],
    seed: 11,
  },
};

const NL = noiseFor(D);
const WAX = ["#4a3f33", "#8a7a60", "#c2ae88", "#e2d1aa"].map(hex), WICK = hex("#1a1210");
// back to front: [x, foot y, height in px, seed]
const CANDLES = [[2, -1, 4, 3], [-3, 1, 6, 7], [0, 3, 5, 13]];

// a flame over the wick at (x, y): a hot yellow base, set off from the wax by the dark wick below it, a bright
// body two or three pixels tall, a darker tip that sways, now and then a wisp of smoke breaking off the top
function flame(p, x, y, t, s, C) {
  const fl = 0.74 + 0.16 * NL(t, 7.3, s + 1) + 0.1 * NL(t, 19, s + 2);
  const tall = NL(t, 3.1, s + 5) > 0.15 ? 3 : 2, sway = Math.round(NL(t, 2.2, s + 9) * 1.4);
  p.px(x, y, C[7]);
  p.px(x - 1, y, C[5]);
  for (let i = 1; i < tall; i++) p.px(x + (i === tall - 1 ? sway : 0), y - i, i === 1 ? C[6] : C[5]);
  p.px(x + sway, y - tall, C[3]);
  if (NL(t, 1.3, s + 21) > 0.55) p.dot(x + sway, y - tall - 2, C[1], 0.6);
  p.glow(x, y - 1, 7, C[5], 0.3 * fl);
  p.glow(x, y, 2.5, C[7], 0.35 * fl);
}

export function draw(p, t, o) {
  const C = fit(o.palette, 9);
  for (const [x, y, h, s] of CANDLES) {
    // the stick: lit on the left, a drip running down the front, a dark wick at the top
    for (let yy = y - h; yy <= y; yy++) {
      p.px(x - 1, yy, WAX[yy === y ? 1 : 3]);
      p.px(x, yy, WAX[yy === y ? 0 : 2]);
    }
    p.px(x - 1, y - h + 2, WAX[2]);
    p.px(x, y - h + 1, WAX[3]);
    p.px(x - 1, y + 1, WAX[0]);
    p.px(x, y + 1, WAX[0]);
    p.px(x, y - h - 1, WICK);
    flame(p, x, y - h - 2, t, o.seed + s, C);
  }
}
