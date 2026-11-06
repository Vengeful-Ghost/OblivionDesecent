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
// Look at the game's sprites the way the game will draw them: every frame of every matching entry in
// art/sprites.mjs, on the dungeon floor colour, dye tinted, blown up so each pixel shows.
//
//   node art/tools/look.mjs player                 -> art/records/look/player.png
//   node art/tools/look.mjs enemy. --zoom 6 --dye "#3c9a4a,#c04040"
//
// Writes one PNG per call; open it with an image viewer (or the Read tool).

import { join } from "node:path";
import { ROOT, canvas, save, upscale } from "./lib/fxlib.mjs";
import { bake, splitDye } from "./lib/bake.mjs";
import { SPRITES } from "../sprites.mjs";

const args = process.argv.slice(2), flag = (k, d) => { const i = args.indexOf(k); return i >= 0 ? args.splice(i, 2)[1] : d; };
const zoom = +flag("--zoom", 8), dyeFlag = flag("--dye", null);
const dyesFor = (e) => (dyeFlag ?? e.tint ?? "#4678eb,#b8402e,#3f9a4c").split(",");
const bgHex = flag("--bg", "#2a2421");
const prefixes = args.length ? args : [""];
const entries = SPRITES.filter((e) => prefixes.some((p) => e.name.startsWith(p)));
if (!entries.length) { console.error("no sprite matches", prefixes.join(" ")); process.exit(1); }

const rgbOf = (h) => [1, 3, 5].map((i) => parseInt(h.replace("#", "").padEnd(6, "0").slice(i - 1, i + 1), 16));
const bg = [...rgbOf(bgHex), 255];
const rows = [];
for (const e of entries) {
  const r = await bake(e);
  for (const tint of e.dye ? dyesFor(e) : [null]) {
    const tc = tint && rgbOf(tint);
    rows.push({ label: `${e.name}${tint ? "  " + tint : ""}  ${r.frames.length}F @${r.fps}`, w: r.w, h: r.h, frames: r.frames.map((f) => {
      const [base, dye] = e.dye ? splitDye(f) : [f, null], out = new Uint8ClampedArray(f.length);
      for (let i = 0; i < f.length; i += 4) {
        const src = dye && dye[i + 3] ? [dye[i] * tc[0] / 255, dye[i + 1] * tc[1] / 255, dye[i + 2] * tc[2] / 255, dye[i + 3]] : [base[i], base[i + 1], base[i + 2], base[i + 3]];
        const a = src[3] / 255;
        for (let c = 0; c < 3; c++) out[i + c] = src[c] * a + bg[c] * (1 - a);
        out[i + 3] = 255;
      }
      return out;
    }) });
  }
}

const pad = 10, label = 22;
const W = Math.min(2400, pad + Math.max(...rows.map((r) => r.frames.length * (r.w * zoom + pad))));
const H = pad + rows.reduce((s, r) => s + label + r.h * zoom + pad, 0);
const img = canvas(W, H, [14, 12, 18, 255]);
let y = pad;
for (const r of rows) {
  img.text(r.label.toUpperCase(), pad, y + 4, 3);
  y += label;
  r.frames.forEach((f, i) => img.paste(upscale(f, r.w, r.h, zoom), r.w * zoom, r.h * zoom, pad + i * (r.w * zoom + pad), y));
  y += r.h * zoom + pad;
}
const file = save(join(ROOT, "records", "look", `${(prefixes[0] || "all").replace(/[^a-z0-9.-]/gi, "_")}.png`), img.png());
console.log(file);
