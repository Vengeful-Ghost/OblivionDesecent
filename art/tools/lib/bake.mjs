// Turns entries of art/sprites.mjs into frames the game can use: renders them, lifts the dye greys onto a
// layer of their own, cuts each frame down to what it draws, and packs everything onto sheets.

import { createFrame, clearFrame, painterFor, composite, resolveOptions } from "../../src/core/index.js";
import { DYE } from "../../src/fx/od/_kit.js";
import { loadEffect, renderFrames } from "./fxlib.mjs";

/**
 * Renders one entry: { fps, loop, pivot: [x, y], w, h, frames: [RGBA] }. The game's own sprites (files that
 * export `anims`) step their own clock, one frame per step; library effects render at their own fps, or the
 * entry's (pixel art reads better at 12 to 15 than 30, and it halves the memory).
 */
export async function bake(entry) {
  const fx = await loadEffect(entry.fx);
  const [W, H] = fx.meta.size, [OX, OY] = fx.meta.origin;
  if (fx.mod.anims) {
    const a = fx.mod.anims[entry.opts?.anim ?? Object.keys(fx.mod.anims)[0]];
    if (!a) throw new Error(`${entry.name}: ${entry.fx} has no anim ${entry.opts?.anim}`);
    const frame = createFrame(W, H), frames = [];
    for (let i = 0; i < a.frames; i++) {
      clearFrame(frame);
      const p = painterFor(frame).place(OX, OY);
      fx.mod.draw(p, i / a.fps, resolveOptions(fx.meta, { ...(entry.opts ?? {}), frame: i }));
      frames.push(composite(frame, new Uint8ClampedArray(W * H * 4), null));
    }
    return { fps: a.fps, loop: a.loop !== false, pivot: [OX, OY], w: W, h: H, frames };
  }
  const r = renderFrames(fx, { fps: entry.fps, bg: "none", options: entry.opts ?? {} });
  let frames = r.frames.map((f) => f.clear);
  if (entry.until) {
    // cut short, the last third of a second fades out instead of popping off
    frames = frames.slice(0, Math.max(1, Math.round(entry.until * r.fps)));
    const fade = Math.max(1, Math.round(r.fps / 3));
    frames.slice(-fade).forEach((f, i) => {
      const k = 1 - (i + 1) / (fade + 1);
      for (let j = 3; j < f.length; j += 4) f[j] = Math.round(f[j] * k);
    });
  }
  return { fps: r.fps, loop: !!fx.meta.loop, pivot: [OX, OY], w: r.w, h: r.h, frames };
}

const DYE_RGB = new Set(DYE.map((c) => c & 0xffffff));

/** Splits RGBA into [base, dye]: opaque pixels in the exact dye greys go to the second. */
export function splitDye(rgba) {
  const base = new Uint8ClampedArray(rgba), dye = new Uint8ClampedArray(rgba.length);
  const b32 = new Uint32Array(base.buffer), d32 = new Uint32Array(dye.buffer);
  for (let i = 0; i < b32.length; i++) {
    const c = b32[i];
    if (c >>> 24 === 255 && DYE_RGB.has(c & 0xffffff)) {
      d32[i] = c;
      b32[i] = 0;
    }
  }
  return [base, dye];
}

/** The smallest box holding every visible pixel: { x, y, w, h, data } (w = 0 when the frame is empty). */
export function trim(rgba, W, H) {
  const px = new Uint32Array(rgba.buffer, rgba.byteOffset, W * H);
  let x0 = W, y0 = H, x1 = -1, y1 = -1;
  for (let y = 0; y < H; y++)
    for (let x = 0; x < W; x++)
      if (px[y * W + x] >>> 24 > 6) {
        if (x < x0) x0 = x;
        if (x > x1) x1 = x;
        if (y < y0) y0 = y;
        if (y > y1) y1 = y;
      }
  if (x1 < 0) return { x: 0, y: 0, w: 0, h: 0, data: new Uint8ClampedArray(0) };
  const w = x1 - x0 + 1, h = y1 - y0 + 1, data = new Uint8ClampedArray(w * h * 4);
  for (let y = 0; y < h; y++) data.set(rgba.subarray(((y0 + y) * W + x0) * 4, ((y0 + y) * W + x0 + w) * 4), y * w * 4);
  return { x: x0, y: y0, w, h, data };
}

/**
 * Packs rects ({ w, h }, given an id) onto pages of `size` x `size`, tallest first, row by row with a 1 px gap.
 * Returns the page count and sets r.page, r.px, r.py on each.
 */
export function pack(rects, size = 1024) {
  const order = rects.filter((r) => r.w > 0).sort((a, b) => b.h - a.h || b.w - a.w);
  let page = 0, x = 0, y = 0, row = 0;
  for (const r of order) {
    if (r.w > size || r.h > size) throw new Error(`a ${r.w}x${r.h} frame is bigger than a ${size} page`);
    if (x + r.w > size) { x = 0; y += row + 1; row = 0; }
    if (y + r.h > size) { page++; x = 0; y = 0; row = 0; }
    r.page = page; r.px = x; r.py = y;
    x += r.w + 1;
    row = Math.max(row, r.h);
  }
  return order.length ? page + 1 : 0;
}
