#!/usr/bin/env node
// ===========================================================================================================
//  >>> REDRAWS THE DIGITS IN THE GAME'S FONT <<<  After editing DIGITS below, from the repo root:
//
//      node art/tools/digits.mjs
//
//  It rewrites app/src/main/res/font/pixelify_sans_regular.ttf and pixelify_sans_bold.ttf in place (only the
//  ten digit glyphs change), then rebuild the app. Safe to run again: it always redraws from DIGITS.
// ===========================================================================================================
// Pixelify Sans (OFL, no Reserved Font Name, so a modified copy may keep shipping under the OFL) draws its 5
// round-topped like an S, its 0 the same as its O and its 8 close to its B. These are the classic 5 x 7 pixel
// digits instead: a flat-topped 5, a 1 with a foot, open 6 and 9. Each pixel becomes a square of the weight's
// own stroke (regular 101 x 100 font units, bold 127 x 136) on the weight's own pitch, the way Pixelify builds
// its letters, so the digits sit on the same grid, height and weight as the text round them. Widths (advances)
// stay as they were, so nothing in the layouts moves.

import { readFileSync, writeFileSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const FONTS = join(dirname(fileURLToPath(import.meta.url)), "..", "..", "app", "src", "main", "res", "font");

// '#' is a pixel; rows from the top. All 5 wide but the 1, which keeps Pixelify's narrow 3.
const DIGITS = {
  0: [".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."],
  1: [".#.", "##.", ".#.", ".#.", ".#.", ".#.", "###"],
  2: [".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"],
  3: [".###.", "#...#", "....#", "..##.", "....#", "#...#", ".###."],
  4: ["...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#."],
  5: ["#####", "#....", "####.", "....#", "....#", "#...#", ".###."],
  6: ["..##.", ".#...", "#....", "####.", "#...#", "#...#", ".###."],
  7: ["#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#..."],
  8: [".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."],
  9: [".###.", "#...#", "#...#", ".####", "....#", "...#.", ".##.."],
};

// Each weight's grid, measured from its own H, 1 and 5: left edge, baseline, a pixel's stroke and pitch
const WEIGHTS = [
  { file: "pixelify_sans_regular.ttf", x0: 60, y0: -12, sx: 101, sy: 100, px: 91, py: 90.5 },
  { file: "pixelify_sans_bold.ttf", x0: 61, y0: -11, sx: 127, sy: 136, px: 88.25, py: 85.5 },
];

for (const w of WEIGHTS) {
  const path = join(FONTS, w.file);
  writeFileSync(path, patch(readFileSync(path), w));
  console.log("redrew the digits in", w.file);
}

/** The digit's outline: every pixel a stroke-sized square, merged into clean contours. */
function contours(rows, w) {
  const rects = [];
  rows.forEach((row, r) => [...row].forEach((c, i) => {
    if (c !== "#") return;
    const x = Math.round(w.x0 + i * w.px), y = Math.round(w.y0 + (rows.length - 1 - r) * w.py);
    rects.push([x, y, x + w.sx, y + w.sy]);
  }));
  // the union on a grid cut at every edge, then its boundary walked clockwise (TrueType's outer direction)
  const xs = [...new Set(rects.flatMap((r) => [r[0], r[2]]))].sort((a, b) => a - b);
  const ys = [...new Set(rects.flatMap((r) => [r[1], r[3]]))].sort((a, b) => a - b);
  const on = (i, j) => i >= 0 && j >= 0 && i < xs.length - 1 && j < ys.length - 1 && rects.some((r) =>
    (xs[i] + xs[i + 1]) / 2 > r[0] && (xs[i] + xs[i + 1]) / 2 < r[2] && (ys[j] + ys[j + 1]) / 2 > r[1] && (ys[j] + ys[j + 1]) / 2 < r[3]);
  const next = new Map(), key = (x, y) => x + "," + y;
  const edge = (ax, ay, bx, by) => {
    const k = key(ax, ay);
    if (!next.has(k)) next.set(k, []);
    next.get(k).push([bx, by]);
  };
  for (let i = 0; i < xs.length - 1; i++)
    for (let j = 0; j < ys.length - 1; j++) {
      if (!on(i, j)) continue;
      const [l, r, b, t] = [xs[i], xs[i + 1], ys[j], ys[j + 1]];
      if (!on(i, j + 1)) edge(l, t, r, t); // top, left to right
      if (!on(i + 1, j)) edge(r, t, r, b); // right, downwards
      if (!on(i, j - 1)) edge(r, b, l, b); // bottom, right to left
      if (!on(i - 1, j)) edge(l, b, l, t); // left, upwards
    }
  const out = [];
  for (const start of [...next.keys()]) {
    while (next.get(start)?.length) {
      const pts = [];
      let [x, y] = start.split(",").map(Number);
      do {
        pts.push([x, y]);
        const k = key(x, y), list = next.get(k);
        [x, y] = list.shift();
        if (!list.length) next.delete(k);
      } while (key(x, y) !== start);
      // keep the corners only
      out.push(pts.filter((p, k) => {
        const a = pts[(k + pts.length - 1) % pts.length], c = pts[(k + 1) % pts.length];
        return (p[0] - a[0]) * (c[1] - p[1]) !== (p[1] - a[1]) * (c[0] - p[0]);
      }));
    }
  }
  return out;
}

/** A simple TrueType glyph: all points on the curve, no hinting, full 16-bit deltas. */
function glyph(cs) {
  const pts = cs.flat(), n = pts.length;
  const b = Buffer.alloc(10 + cs.length * 2 + 2 + n + n * 4);
  let o = 0;
  b.writeInt16BE(cs.length, o); o += 2;
  for (const v of [Math.min(...pts.map((p) => p[0])), Math.min(...pts.map((p) => p[1])),
    Math.max(...pts.map((p) => p[0])), Math.max(...pts.map((p) => p[1]))]) { b.writeInt16BE(v, o); o += 2; }
  let end = -1;
  for (const c of cs) { end += c.length; b.writeUInt16BE(end, o); o += 2; }
  b.writeUInt16BE(0, o); o += 2; // no instructions
  for (let i = 0; i < n; i++) b[o++] = 1; // on curve
  let last = 0;
  for (const p of pts) { b.writeInt16BE(p[0] - last, o); o += 2; last = p[0]; }
  last = 0;
  for (const p of pts) { b.writeInt16BE(p[1] - last, o); o += 2; last = p[1]; }
  return { data: b, points: n, contours: cs.length, xMin: Math.min(...pts.map((p) => p[0])) };
}

function patch(font, w) {
  const u16 = (o) => font.readUInt16BE(o), i16 = (o) => font.readInt16BE(o), u32 = (o) => font.readUInt32BE(o);
  const numTables = u16(4), recs = [];
  for (let i = 0; i < numTables; i++) {
    const o = 12 + i * 16;
    recs.push({ tag: font.toString("latin1", o, o + 4), off: u32(o + 8), len: u32(o + 12) });
  }
  const T = Object.fromEntries(recs.map((r) => [r.tag, Buffer.from(font.subarray(r.off, r.off + r.len))]));
  const head = T.head, maxp = T.maxp, numGlyphs = maxp.readUInt16BE(4), longLoca = head.readInt16BE(50) === 1;
  const loca = (g) => longLoca ? T.loca.readUInt32BE(g * 4) : T.loca.readUInt16BE(g * 2) * 2;

  // the digits' glyph ids, from the Windows Unicode cmap (format 4)
  const cmap = T.cmap;
  let f4 = -1;
  for (let i = 0; i < cmap.readUInt16BE(2); i++) {
    const platform = cmap.readUInt16BE(4 + i * 8), sub = cmap.readUInt32BE(8 + i * 8);
    if (platform === 3 && cmap.readUInt16BE(sub) === 4) f4 = sub;
  }
  if (f4 < 0) throw new Error(`${w.file} has no Unicode cmap`);
  const gid = (code) => {
    const seg2 = cmap.readUInt16BE(f4 + 6), ends = f4 + 14, starts = ends + seg2 + 2, deltas = starts + seg2, ros = deltas + seg2;
    for (let s = 0; s < seg2 / 2; s++) {
      if (code > cmap.readUInt16BE(ends + s * 2)) continue;
      const start = cmap.readUInt16BE(starts + s * 2);
      if (code < start) return 0;
      const delta = cmap.readInt16BE(deltas + s * 2), ro = cmap.readUInt16BE(ros + s * 2);
      if (!ro) return (code + delta) & 0xffff;
      const g = cmap.readUInt16BE(ros + s * 2 + ro + (code - start) * 2);
      return g ? (g + delta) & 0xffff : 0;
    }
    return 0;
  };
  const fresh = new Map();
  for (const [d, rows] of Object.entries(DIGITS)) {
    const g = gid(48 + +d);
    if (!g) throw new Error(`${w.file} has no glyph for ${d}`);
    fresh.set(g, glyph(contours(rows, w)));
  }

  // glyf and loca rebuilt with the new digits; every glyph padded to an even length
  const parts = [], offs = [];
  let at = 0;
  for (let g = 0; g < numGlyphs; g++) {
    offs.push(at);
    let data = fresh.has(g) ? fresh.get(g).data : T.glyf.subarray(loca(g), loca(g + 1));
    if (data.length % 2) data = Buffer.concat([data, Buffer.alloc(1)]);
    parts.push(data);
    at += data.length;
  }
  offs.push(at);
  T.glyf = Buffer.concat(parts);
  const long = at / 2 > 0xffff;
  T.loca = Buffer.alloc((numGlyphs + 1) * (long ? 4 : 2));
  offs.forEach((v, g) => long ? T.loca.writeUInt32BE(v, g * 4) : T.loca.writeUInt16BE(v / 2, g * 2));
  head.writeInt16BE(long ? 1 : 0, 50);

  // the left side bearing is the glyph's left edge; the advance stays
  const nHm = T.hhea.readUInt16BE(34);
  for (const [g, gl] of fresh) if (g < nHm) T.hmtx.writeInt16BE(gl.xMin, g * 4 + 2);
  for (const gl of fresh.values()) {
    if (gl.points > maxp.readUInt16BE(6)) maxp.writeUInt16BE(gl.points, 6);
    if (gl.contours > maxp.readUInt16BE(8)) maxp.writeUInt16BE(gl.contours, 8);
  }

  // the file again: same tables in the same order, fresh offsets and checksums
  head.writeUInt32BE(0, 8);
  const pad4 = (b) => b.length % 4 ? Buffer.concat([b, Buffer.alloc(4 - (b.length % 4))]) : b;
  const sum = (b) => { b = pad4(b); let s = 0; for (let i = 0; i < b.length; i += 4) s = (s + b.readUInt32BE(i)) >>> 0; return s; };
  const dir = Buffer.from(font.subarray(0, 12 + numTables * 16)), bodies = [];
  let off = dir.length;
  recs.forEach((r, i) => {
    const data = T[r.tag], o = 12 + i * 16;
    dir.writeUInt32BE(sum(data), o + 4);
    dir.writeUInt32BE(off, o + 8);
    dir.writeUInt32BE(data.length, o + 12);
    bodies.push(pad4(data));
    off += pad4(data).length;
  });
  const out = Buffer.concat([dir, ...bodies]);
  const headAt = dir.readUInt32BE(12 + recs.findIndex((r) => r.tag === "head") * 16 + 8);
  out.writeUInt32BE((0xb1b0afba - sum(out)) >>> 0, headAt + 8);
  return out;
}
