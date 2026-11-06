#!/usr/bin/env node
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
// Bakes every sprite in art/sprites.mjs for the game: cuts each frame down to what it draws, packs them onto
// 1024 px sheets and writes app/src/main/assets/px/ (px0.png, px1.png... and sprites.json) for Sprites.java.
//
//   node art/tools/export.mjs
//
// sprites.json: { pages: [file], anims: { name: { fps, loop, f: [frame], d: [frame] | absent } } }, a frame
// being [page, x, y, w, h, ox, oy]: where it sits on its page, and its top-left in art pixels from the pivot.
// `d` is the dye layer, drawn over `f` in the body's colour.

import { writeFileSync, mkdirSync, readdirSync, unlinkSync } from "node:fs";
import { join } from "node:path";
import { ROOT } from "./lib/fxlib.mjs";
import { encodePNG } from "./lib/png.mjs";
import { bake, splitDye, trim, pack } from "./lib/bake.mjs";
import { SPRITES } from "../sprites.mjs";

const OUT = join(ROOT, "..", "app", "src", "main", "assets", "px"), PAGE = 1024;

const anims = {}, rects = [];
for (const entry of SPRITES) {
  const r = await bake(entry);
  const layers = entry.dye ? r.frames.map(splitDye) : r.frames.map((f) => [f]);
  const anim = (anims[entry.name] = { fps: r.fps, loop: r.loop ? 1 : 0, f: [], ...(entry.dye ? { d: [] } : {}) });
  for (const frame of layers)
    frame.forEach((rgba, li) => {
      const t = trim(rgba, r.w, r.h);
      t.ox = t.x - r.pivot[0];
      t.oy = t.y - r.pivot[1];
      rects.push(t);
      (li === 0 ? anim.f : anim.d).push(t);
    });
  console.log(`${entry.name.padEnd(28)} ${String(r.frames.length).padStart(3)} frames  ${r.w}x${r.h}`);
}

const pages = pack(rects, PAGE);
const sheets = Array.from({ length: pages }, () => new Uint8ClampedArray(PAGE * PAGE * 4));
for (const t of rects) {
  if (!t.w) continue;
  for (let y = 0; y < t.h; y++) sheets[t.page].set(t.data.subarray(y * t.w * 4, (y + 1) * t.w * 4), ((t.py + y) * PAGE + t.px) * 4);
}

mkdirSync(OUT, { recursive: true });
for (const f of readdirSync(OUT)) if (/^px\d+\.png$/.test(f)) unlinkSync(join(OUT, f));
const files = sheets.map((data, i) => {
  // the last page only needs to be as tall as what's on it
  const used = i === pages - 1 ? Math.max(...rects.filter((t) => t.w && t.page === i).map((t) => t.py + t.h)) : PAGE;
  writeFileSync(join(OUT, `px${i}.png`), encodePNG(PAGE, used, data.subarray(0, PAGE * used * 4)));
  return `px${i}.png`;
});
const cell = (t) => (t.w ? [t.page, t.px, t.py, t.w, t.h, t.ox, t.oy] : [0, 0, 0, 0, 0, 0, 0]);
const json = { pages: files, anims: {} };
for (const [name, a] of Object.entries(anims)) json.anims[name] = { ...a, f: a.f.map(cell), ...(a.d ? { d: a.d.map(cell) } : {}) };
writeFileSync(join(OUT, "sprites.json"), JSON.stringify(json));

const bytes = sheets.length * PAGE * PAGE * 4;
console.log(`\n${Object.keys(anims).length} animations on ${pages} page(s), ${(bytes / 1048576).toFixed(1)} MB once loaded -> ${OUT}`);
