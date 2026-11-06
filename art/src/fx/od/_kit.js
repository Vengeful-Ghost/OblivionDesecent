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

// -----------------------------------------------------------------------------------------------------------
// Rig: bodies built once in 3D and drawn facing any of eight ways. The game needs five views (the other three
// are mirrors): e (side on, facing right), se (three-quarter, towards the viewer), s (facing the viewer),
// ne (three-quarter, away) and n (back to the viewer). A part is placed by
//   f  forward, along the way the body faces        z  across, + is the body's own right
//   h  height above the floor
// and is projected for the view: its x on screen, its depth (what is in front of what) and, seen from the
// top-down camera, a little lower on screen the nearer the viewer it stands. Side on (e) nothing is
// foreshortened, so side views keep their crisp profile. Parts are drawn far to near; parts at the same depth
// in the order they were added (so add them back to front as for a side view). The far side of a body is a
// shade darker. Marks (eyes, buckles, slits) face a way (n, default forward) and vanish when it faces away.
// -----------------------------------------------------------------------------------------------------------

export const VIEWS = { e: 0, se: 45, s: 90, ne: -45, n: -90 };

export class Rig {
  constructor(view = "e") {
    const a = ((VIEWS[view] ?? 0) * Math.PI) / 180, r = (v) => Math.round(v * 1e6) / 1e6;
    this.view = view;
    this.c = r(Math.cos(a));
    this.s = r(Math.sin(a));
    this.K = 0.25 * Math.abs(this.s); // ground foreshortening: none side on
    this.parts = [];
  }

  /** Screen [x, y] and depth of a point (feet at 0, 0; y up is negative, as on a Pix). */
  P(f, z, h) {
    const d = f * this.s + z * this.c;
    return [f * this.c - z * this.s, -h + d * this.K, d];
  }
  /** How much of direction (nf, nz) faces the viewer: 1 straight at them, -1 straight away. */
  facing(nf, nz) { return nf * this.s + nz * this.c; }
  get front() { return this.s > 0.3; }   // towards the viewer
  get back() { return this.s < -0.3; }   // away from the viewer

  add(d, o, fn) {
    const part = { d: o.on ? o.on.d + 1e-3 * (this.parts.length + 1) : d + (o.layer ?? 0), i: this.parts.length, fn };
    part.fade = o.on ? o.on.fade : clamp(d * 0.1, -0.25, 0.06);
    // a part that faces one way (o.n, e.g. a face plate) is left out while that way faces off from the viewer
    if (o.n && this.facing(o.n[0], o.n[1]) < (o.min ?? -0.2)) return part;
    this.parts.push(part);
    return part;
  }
  tone(o, fade) { return { ...o, bias: (o.bias ?? 0) + (o.noFade ? 0 : fade) }; }

  /** An ellipsoid: rf along the facing, rz across, rh up. */
  ball(f, z, h, rf, rz, rh, ramp, o = {}) {
    const [x, y, d] = this.P(f, z, h), rx = Math.max(0.7, Math.hypot(rf * this.c, rz * this.s));
    return this.add(d, o, (g, fade) => g.blob(x, y, rx, rh, ramp, this.tone(o, fade)));
  }
  /** A box f0..f1 along, z0..z1 across, h0..h1 up. */
  block(f0, f1, z0, z1, h0, h1, ramp, o = {}) {
    const xs = [[f0, z0], [f0, z1], [f1, z0], [f1, z1]].map(([f, z]) => f * this.c - z * this.s);
    const d = ((f0 + f1) / 2) * this.s + ((z0 + z1) / 2) * this.c;
    return this.add(d, o, (g, fade) => g.box(Math.min(...xs), -h1 + d * this.K, Math.max(...xs), -h0 + d * this.K, ramp, this.tone(o, fade)));
  }
  /** A limb, a horn, a tail: a line w wide from a to b, each [f, z, h]. */
  limb(a, b, w, ramp, o = {}) {
    const [x0, y0, d0] = this.P(...a), [x1, y1, d1] = this.P(...b);
    return this.add((d0 + d1) / 2, o, (g, fade) => g.stroke(x0, y0, x1, y1, w, ramp, this.tone(o, fade)));
  }
  /**
   * A flat piece, outlined [[f, h], ...] in the body's side plane at z and w thick to each side: a cloak, an
   * ear, a plume, a wing. Seen from the front it shows its thickness, swept across.
   */
  sheet(pts, z, w, ramp, o = {}) {
    return this.flat(pts.map(([f, h]) => [f, z, h]), [0, 1, 0], w, ramp, o);
  }
  /** A flat piece outlined [[z, h], ...] across the body at f, w thick fore and aft: a tabard, a mask, a lid. */
  plate(pts, f, w, ramp, o = {}) {
    return this.flat(pts.map(([z, h]) => [f, z, h]), [1, 0, 0], w, ramp, o);
  }
  /** A flat piece through any [f, z, h] points, w thick up and down: an ear or a wing that juts out sideways. */
  fin(pts3, w, ramp, o = {}) {
    return this.flat(pts3, [0, 0, 1], w, ramp, o);
  }
  flat(pts3, [tf, tz, th], w, ramp, o) {
    const pr = pts3.map(([f, z, h]) => this.P(f, z, h)), d = pr.reduce((a, p) => a + p[2], 0) / pr.length;
    // the outline swept through its thickness: step along the screen shift of one unit of thickness
    const sx = tf * this.c - tz * this.s, sy = -th + (tf * this.s + tz * this.c) * this.K;
    const n = Math.max(1, Math.ceil((2 * w * Math.hypot(sx, sy)) / 0.5)), offs = [];
    for (let i = 0; i <= n; i++) { const k = -w + (2 * w * i) / n; offs.push([k * sx, k * sy]); }
    const xy = pr.map((p) => [p[0], p[1]]);
    let x0 = Infinity, y0 = Infinity, x1 = -Infinity, y1 = -Infinity;
    for (const [x, y] of xy) for (const [ox, oy] of offs) {
      x0 = Math.min(x0, x + ox); y0 = Math.min(y0, y + oy); x1 = Math.max(x1, x + ox); y1 = Math.max(y1, y + oy);
    }
    const cx = (x0 + x1) / 2, cy = (y0 + y1) / 2, rx = (x1 - x0) / 2 || 1, ry = (y1 - y0) / 2 || 1;
    // the outline at each step of its thickness (and its edges, so a piece seen edge on, its outline a line,
    // still shows as the band it is). o.flare = [h0, w0, h1, w1] tapers the thickness with height: a robe
    // narrow at the shoulders and wide at the hem.
    const fl = o.flare, wAt = (y) => {
      if (!fl) return w;
      const h = -y, u = clamp((h - fl[0]) / (fl[2] - fl[0] || 1), 0, 1);
      return fl[1] + (fl[3] - fl[1]) * u;
    };
    const ks = offs.map(([ox, oy], i) => [ox, oy, -w + (2 * w * i) / (offs.length - 1 || 1)]);
    const sweeping = 2 * w * Math.hypot(sx, sy) > 0.3;
    const test = (x, y) => !sweeping ? insidePoly(xy, x, y) : ks.some(([ox, oy, k]) => {
      const px = x - ox, py = y - oy;
      if (Math.abs(k) > wAt(py) + 1e-6) return false;
      return insidePoly(xy, px, py) || (sweeping && nearOutline(xy, px, py, 0.55));
    });
    return this.add(d, o, (g, fade) => {
      const t = this.tone(o, fade);
      g.shape(test, (x, y) => (t.flat !== undefined ? t.flat : roundTone(ramp, ((x - cx) / rx) * 0.85, ((y - cy) / ry) * 0.85, t.bias)),
        t.ink === undefined ? INK : t.ink);
    });
  }

  /** Single pixels [[f, z, h], ...] in one colour, shown while their side (o.n = [nf, nz]) faces the viewer. */
  dots(list, c, o = {}) {
    const [nf, nz] = o.n ?? [1, 0];
    if (this.facing(nf, nz) < (o.min ?? -0.2)) return null;
    const pts = list.map((p) => this.P(...p)), d = pts.reduce((a, p) => a + p[2], 0) / pts.length;
    return this.add(d, o, (g) => { for (const [x, y] of pts) g.set(x, y, c); });
  }
  /** A 1 px line in one colour through [f, z, h] points, shown while it faces the viewer (as dots). */
  seg(list, c, o = {}) {
    const [nf, nz] = o.n ?? [1, 0];
    if (this.facing(nf, nz) < (o.min ?? -0.2)) return null;
    const pts = list.map((p) => this.P(...p)), d = pts.reduce((a, p) => a + p[2], 0) / pts.length;
    return this.add(d, o, (g) => { for (let i = 1; i < pts.length; i++) g.line(pts[i - 1][0], pts[i - 1][1], pts[i][0], pts[i][1], c); });
  }
  /** Pixels at fixed screen offsets [[dx, dy], ...] from a part's point: a glint that stays top left. */
  glint(on, [f, z, h], list, c) {
    const [x, y] = this.P(f, z, h);
    return this.add(0, { on }, (g) => { for (const [dx, dy] of list) g.set(x + dx, y + dy, c); });
  }

  /** Draws every part far to near onto the grid g. */
  draw(g) {
    for (const p of [...this.parts].sort((a, b) => a.d - b.d || a.i - b.i)) p.fn(g, p.fade);
  }
}

/** True when (x, y) lies within r of the polygon's outline. */
function nearOutline(pts, x, y, r) {
  for (let i = 0; i < pts.length; i++) {
    const [ax, ay] = pts[i], [bx, by] = pts[(i + 1) % pts.length], dx = bx - ax, dy = by - ay;
    const u = clamp(((x - ax) * dx + (y - ay) * dy) / (dx * dx + dy * dy || 1), 0, 1);
    if (Math.hypot(ax + dx * u - x, ay + dy * u - y) <= r) return true;
  }
  return false;
}

function insidePoly(pts, x, y) {
  let c = false;
  for (let i = 0, j = pts.length - 1; i < pts.length; j = i++) {
    const [xi, yi] = pts[i], [xj, yj] = pts[j];
    if (yi > y !== yj > y && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi) c = !c;
  }
  return c;
}

/** Calls fn(+1) for the body's right and fn(-1) for its left: paired eyes, arms, legs, ears. */
export const both = (fn) => { fn(1); fn(-1); };

/** Walk-cycle helpers: frame 0..3 is contact, pass, contact (other foot), pass. */
export const stride = (f) => [1, 0, -1, 0][f & 3];       // which way the front foot is out
export const bob = (f) => (f & 1 ? -1 : 0);                // the body rises on the passing frames
