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
// Seamless textures the game repeats across the dungeon, in greys around a mid value of 160 that the game
// scales by each biome's and room's own colour (lighter greys come out brighter than the colour, darker darker).
//   floor   flagstones, 100 x 100 px: four rows of 25 px stones (one room tile is 50 px), some split in two,
//           with bevelled edges, grout, cracks and grit
//   bricks  the front face of a north wall, 24 x 11 px: two courses of 12 px bricks, the second offset

import { rgb, Rng } from "../../core/index.js";

export const anims = {
  floor: { frames: 1, fps: 1 },
  bricks: { frames: 1, fps: 1 },
};

export const meta = {
  name: "Dungeon tiles",
  desc: "Flagstone floor and brick wall face, in tintable greys.",
  tags: ["od", "tile"],
  size: [100, 100], origin: [0, 0], duration: 1, loop: true, fps: 1, bg: "none",
  options: { anim: "floor", frame: -1, seed: 7 },
};

const grey = (v) => { v = Math.max(0, Math.min(255, Math.round(v))); return rgb(v, v, v); };

export function draw(p, t, o) {
  if (o.anim === "bricks") return bricks(p);
  const R = new Rng(o.seed), S = 25, N = 4, W = S * N;
  const v = new Float32Array(W * W).fill(-1);
  // stones: each 25 px cell, a third of them split into two half stones, each with its own value
  for (let cy = 0; cy < N; cy++)
    for (let cx = 0; cx < N; cx++) {
      const split = R.next() < 0.35 ? (R.next() < 0.5 ? "h" : "v") : null;
      const parts = split === "h" ? [[0, 0, S, 12], [0, 12, S, S - 12]] : split === "v" ? [[0, 0, 12, S], [12, 0, S - 12, S]] : [[0, 0, S, S]];
      for (const [px, py, pw, ph] of parts) {
        const base = 160 + R.range(-9, 9), x0 = cx * S + px, y0 = cy * S + py;
        for (let y = 0; y < ph; y++)
          for (let x = 0; x < pw; x++) {
            let c = base;
            if (x === 0 || y === 0) c = 98;                          // grout, shared with the stone to the left and above
            else if (x === 1 || y === 1) c = base + 22;               // the lit bevel
            else if (x === pw - 1 || y === ph - 1) c = base - 26;     // the shadowed bevel
            else if (x === 2 && y === 2) c = base + 12;
            v[(y0 + y) * W + x0 + x] = c;
          }
        // a crack across some stones, grit on most
        if (R.next() < 0.3) {
          let x = x0 + R.int(4, pw - 4), y = y0 + 3;
          for (let i = 0; i < ph - 6; i++) {
            v[y * W + x] = base - 34;
            y++;
            if (R.next() < 0.4) x += R.next() < 0.5 ? -1 : 1;
            x = Math.max(x0 + 3, Math.min(x0 + pw - 4, x));
          }
        }
        for (let i = 0, n = R.int(2, 6); i < n; i++) {
          const x = x0 + R.int(3, pw - 3), y = y0 + R.int(3, ph - 3);
          v[y * W + x] = base + (R.next() < 0.5 ? -18 : 14);
        }
      }
    }
  for (let y = 0; y < W; y++) for (let x = 0; x < W; x++) p.px(x, y, grey(v[y * W + x]));
}

function bricks(p) {
  const W = 24, H = 11, rows = [[0, 5], [5, 6]];
  rows.forEach(([y0, h], r) => {
    const shift = r ? 6 : 0;
    for (let y = y0; y < y0 + h; y++)
      for (let x = 0; x < W; x++) {
        const bx = (x + W - shift) % 12, top = y === y0, bottom = y === y0 + h - 1;
        let c = 160 + (((x + shift) / 12) & 1 ? 6 : -4);
        if (bx === 0 || top) c = 92;
        else if (y === y0 + 1 || bx === 1) c += 20;
        else if (bottom || bx === 11) c -= 24;
        p.px(x, y, grey(c));
      }
  });
}
