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
// A woven rug lying on the floor, at the size Props.java gives it: o.hw and o.hh are its half width and half
// height in art pixels. A dark selvedge, a gold border woven with a running pattern, a crimson field with a
// faint weave, a diamond medallion, and tasselled fringes on the short ends. Origin: its centre.

import { hex } from "../../core/index.js";

export const anims = { still: { frames: 1, fps: 1 } };

export const meta = {
  name: "Rug",
  desc: "A woven crimson rug with a gold border, medallion and fringes.",
  tags: ["od", "prop", "rug"],
  size: [112, 80], origin: [56, 40], duration: 1, loop: true, fps: 1, bg: "ground",
  options: { anim: "still", frame: -1, hw: 32, hh: 24 },
};

const EDGE = hex("#1e0a0a"), FIELD = [hex("#5e1414"), hex("#7a1c1a"), hex("#8f2420")];
const GOLD = [hex("#6b4512"), hex("#a8782a"), hex("#d8b04a")], FRINGE = [hex("#b8a27a"), hex("#e0d0a6")];
const DEEP = hex("#3e0c0e");

export function draw(p, t, o) {
  const W = o.hw, H = o.hh;
  for (let y = -H; y < H; y++)
    for (let x = -W; x < W; x++) {
      const ex = Math.min(x + W, W - 1 - x), ey = Math.min(y + H, H - 1 - y), e = Math.min(ex, ey);
      let c;
      if (e === 0) c = EDGE;                                            // the selvedge
      else if (e <= 3) {
        // the border: a gold band with a running notch every few threads, dark lines either side
        const along = ex < ey ? y : x;
        c = e === 2 ? ((along & 3) === 0 ? GOLD[0] : GOLD[2]) : e === 1 ? GOLD[1] : DEEP;
      } else {
        // the field: a faint diagonal weave; a diamond medallion with a gold ring and a dark heart
        const d = Math.abs(x + 0.5) / (W * 0.42) + Math.abs(y + 0.5) / (H * 0.55);
        if (d < 0.32) c = DEEP;
        else if (d < 0.5) c = FIELD[2];
        else if (d < 0.62) c = GOLD[1];
        else if (d < 0.7) c = DEEP;
        else c = FIELD[(x + y) % 4 === 0 ? 0 : (x - y) % 7 === 0 ? 2 : 1];
        // small gold diamonds in the four corners of the field
        const cx = W - 7, cy = H - 7, k = Math.abs(Math.abs(x + 0.5) - cx) + Math.abs(Math.abs(y + 0.5) - cy);
        if (k < 2.2) c = k < 1 ? GOLD[2] : GOLD[0];
      }
      p.px(x, y, c);
    }
  // tasselled fringes off the short ends
  for (let y = -H + 1; y < H - 1; y += 2)
    for (const s of [-1, 1]) {
      const x = s < 0 ? -W - 1 : W;
      p.px(x, y, FRINGE[1]);
      p.px(x + s, y, FRINGE[0]);
      if ((y & 3) === 1) p.px(x + 2 * s, y, FRINGE[0]);
    }
}
