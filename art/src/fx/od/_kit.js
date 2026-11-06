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
// The game's sprite kit: a small pixel grid that bodies are built on part by part, back to front. Each part
// gets a 1 px ink ring, so a part in front cuts a clean line across the one behind it, and is shaded with
// light from the top left in a few hard ramp steps. Files starting with _ are helpers, not effects.

import { hex, clamp } from "../../core/index.js";

export const INK = hex("#140f1a");
// The dye ramp, dark to light: exact greys that tools/export.mjs lifts onto a layer of their own, which the
// game multiplies by each body's colour (the wardrobe, an enemy's template). Nothing else may use these greys.
export const DYE = ["#4c4c4c", "#727272", "#9a9a9a", "#c3c3c3", "#ececec"].map(hex);

// Shared ramps, dark to light
export const STEEL = ["#262a40", "#454e6b", "#7a86a3", "#b8c4d8", "#eef3ff"].map(hex);
export const IRON = ["#1f1d26", "#36333f", "#57535f", "#7d7886"].map(hex);
export const LEATHER = ["#341f18", "#5a3627", "#87553a", "#b27c50"].map(hex);
export const CLOTH = ["#1c1929", "#2f2b42", "#45405e"].map(hex);
export const GOLD = ["#6b4512", "#b2832a", "#efc95a", "#fff3b0"].map(hex);
export const SKIN = ["#6e3a30", "#ac624a", "#df9a74", "#ffcfa8"].map(hex);
export const BONE = ["#4f463f", "#8b7d6c", "#cbbd9f", "#f3ead4"].map(hex);
export const WOOD = ["#2c1a12", "#4d2f1f", "#74482d", "#9a6740"].map(hex);
export const STONE = ["#25232b", "#3d3a45", "#5d5966", "#857f8c"].map(hex);
export const WHITE = hex("#fffaf0");
export const EMBER = hex("#ff9a3a");
export const EYE_RED = hex("#ff4a3a");
export const EYE_GREEN = hex("#8dffb0");
export const EYE_GOLD = hex("#ffd35a");

const LX = -0.6, LY = -0.8; // light from the top left

/** Ramp step for a normalised point on a round part: lit top left, dark bottom right, darker at the rim. */
function roundTone(ramp, nx, ny, bias = 0) {
  const facing = nx * LX + ny * LY, rim = nx * nx + ny * ny > 0.72 && facing < 0.3 ? 0.18 : 0;
  const v = 0.5 + 0.45 * facing + bias - rim;
  return ramp[clamp(Math.floor(v * ramp.length), 0, ramp.length - 1)];
}

export class Pix {
  /** A grid over local space x0..x1, y0..y1 (feet at 0, 0; y up is negative). */
  constructor(x0 = -32, y0 = -64, x1 = 32, y1 = 8) {
    this.x0 = x0; this.y0 = y0; this.w = x1 - x0; this.h = y1 - y0;
    this.c = new Uint32Array(this.w * this.h);
  }
  at(x, y) {
    x = Math.floor(x) - this.x0; y = Math.floor(y) - this.y0;
    return x >= 0 && y >= 0 && x < this.w && y < this.h ? y * this.w + x : -1;
  }
  set(x, y, c) { const i = this.at(x, y); if (i >= 0 && c) this.c[i] = c; }
  get(x, y) { const i = this.at(x, y); return i >= 0 ? this.c[i] : 0; }
  /** Several single pixels: [[x, y], ...] in one colour. */
  dots(list, c) { for (const [x, y] of list) this.set(x, y, c); }

  /**
   * The core of the kit: every pixel whose centre passes `test` is filled with tone(x, y); with ink, the
   * pixels just outside it (4-neighbours) are inked first, so the part reads as its own piece.
   */
  shape(test, tone, ink = INK) {
    const cells = [];
    for (let y = this.y0; y < this.y0 + this.h; y++)
      for (let x = this.x0; x < this.x0 + this.w; x++) if (test(x + 0.5, y + 0.5)) cells.push(x, y);
    if (ink)
      for (let i = 0; i < cells.length; i += 2) {
        const x = cells[i], y = cells[i + 1];
        for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]])
          if (!test(x + dx + 0.5, y + dy + 0.5)) this.set(x + dx, y + dy, ink);
      }
    for (let i = 0; i < cells.length; i += 2) this.set(cells[i], cells[i + 1], tone(cells[i] + 0.5, cells[i + 1] + 0.5));
  }

  /** A shaded ellipse. o: { ink, bias (brighter +, darker -), flat (one colour) }. */
  blob(cx, cy, rx, ry, ramp, o = {}) {
    this.shape(
      (x, y) => ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1,
      (x, y) => (o.flat !== undefined ? o.flat : roundTone(ramp, (x - cx) / rx, (y - cy) / ry, o.bias ?? 0)),
      o.ink === undefined ? INK : o.ink,
    );
  }
  /** A shaded box from (x0, y0) to (x1, y1), corners optionally cut by `round` px. */
  box(x0, y0, x1, y1, ramp, o = {}) {
    const cut = o.round ?? 0, cx = (x0 + x1) / 2, cy = (y0 + y1) / 2, rx = (x1 - x0) / 2, ry = (y1 - y0) / 2;
    this.shape(
      (x, y) => {
        if (x < x0 || x > x1 || y < y0 || y > y1) return false;
        if (!cut) return true;
        const ex = Math.max(0, Math.max(x0 + cut - x, x - (x1 - cut))), ey = Math.max(0, Math.max(y0 + cut - y, y - (y1 - cut)));
        return ex + ey <= cut;
      },
      (x, y) => (o.flat !== undefined ? o.flat : roundTone(ramp, ((x - cx) / (rx || 1)) * 0.85, ((y - cy) / (ry || 1)) * 0.85, o.bias ?? 0)),
      o.ink === undefined ? INK : o.ink,
    );
  }
  /** A shaded polygon [[x, y], ...]; tone follows the polygon's bounding box like a box. */
  poly(pts, ramp, o = {}) {
    let x0 = Infinity, y0 = Infinity, x1 = -Infinity, y1 = -Infinity;
    for (const [x, y] of pts) { x0 = Math.min(x0, x); y0 = Math.min(y0, y); x1 = Math.max(x1, x); y1 = Math.max(y1, y); }
    const cx = (x0 + x1) / 2, cy = (y0 + y1) / 2, rx = (x1 - x0) / 2 || 1, ry = (y1 - y0) / 2 || 1;
    const inside = (x, y) => {
      let c = false;
      for (let i = 0, j = pts.length - 1; i < pts.length; j = i++) {
        const [xi, yi] = pts[i], [xj, yj] = pts[j];
        if (yi > y !== yj > y && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi) c = !c;
      }
      return c;
    };
    this.shape(inside,
      (x, y) => (o.flat !== undefined ? o.flat : roundTone(ramp, ((x - cx) / rx) * 0.85, ((y - cy) / ry) * 0.85, o.bias ?? 0)),
      o.ink === undefined ? INK : o.ink);
  }
  /** A 1 px line in one colour, no ink. */
  line(x0, y0, x1, y1, c) {
    const n = Math.max(1, Math.ceil(Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0))));
    for (let i = 0; i <= n; i++) this.set(x0 + ((x1 - x0) * i) / n, y0 + ((y1 - y0) * i) / n, c);
  }
  /** A line with an ink ring round it: a thin limb, a strap, a horn. */
  stroke(x0, y0, x1, y1, w, ramp, o = {}) {
    const dx = x1 - x0, dy = y1 - y0, L2 = dx * dx + dy * dy || 1;
    this.shape((x, y) => {
      const u = clamp(((x - x0) * dx + (y - y0) * dy) / L2, 0, 1), px = x0 + dx * u - x, py = y0 + dy * u - y;
      return px * px + py * py <= (w / 2) * (w / 2);
    }, (x, y) => {
      if (o.flat !== undefined) return o.flat;
      const u = clamp(((x - x0) * dx + (y - y0) * dy) / L2, 0, 1), side = ((x - x0) * -dy + (y - y0) * dx) / Math.sqrt(L2);
      return ramp[clamp(Math.floor((0.5 - side / w * 0.9 - u * 0.15 + (o.bias ?? 0)) * ramp.length), 0, ramp.length - 1)];
    }, o.ink === undefined ? INK : o.ink);
  }

  /** Copy the grid into the painter. */
  draw(p) {
    for (let y = 0; y < this.h; y++)
      for (let x = 0; x < this.w; x++) {
        const c = this.c[y * this.w + x];
        if (c) p.px(x + this.x0, y + this.y0, c);
      }
  }
}

/** Walk-cycle helpers: frame 0..3 is contact, pass, contact (other foot), pass. */
export const stride = (f) => [1, 0, -1, 0][f & 3];       // which way the front foot is out
export const bob = (f) => (f & 1 ? -1 : 0);                // the body rises on the passing frames
