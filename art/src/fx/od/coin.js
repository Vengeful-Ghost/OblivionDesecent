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
// A dropped gold coin at its real size beside a body (7 px across): it turns edge-on and back, a glint runs
// across its face and now and then a star of light winks off its rim. Origin: the coin's centre. Its first
// frame, face on, is also the coin icon beside prices (tools/export.mjs writes it to res/drawable-nodpi).

import { hex } from "../../core/index.js";

export const anims = { spin: { frames: 12, fps: 10 } };

export const meta = {
  name: "Coin",
  desc: "A small spinning gold coin with a glint.",
  tags: ["od", "pickup", "coin", "loop"],
  size: [16, 16], origin: [8, 8], duration: 1.2, loop: true, fps: 10, bg: "dark",
  options: { anim: "spin", frame: -1 },
};

const RIM = hex("#5c3a0c"), DEEP = hex("#9a6a16"), BASE = hex("#d9a42a"), LIGHT = hex("#f6d55a"), SHINE = hex("#fff6c8");
// half-widths through a turn: face on, narrowing to the edge and back
const HALF = [3.5, 3.5, 3.2, 2.4, 1.4, 0.6, 1.4, 2.4, 3.2, 3.5, 3.5, 3.5];

export function draw(p, t, o) {
  const f = o.frame >= 0 ? o.frame : Math.floor(t * anims.spin.fps) % anims.spin.frames;
  const hw = HALF[f], hh = 3.5, face = hw > 2;
  p.shade(-4, -4, 4, 4, (x, y) => {
    const nx = x / hw, ny = y / hh, d = nx * nx + ny * ny;
    if (d > 1) return;
    if (d > 0.62 || hw < 1) return x < 0 && y < 0 ? DEEP : RIM;    // the milled rim, lit at the top left
    if (!face) return BASE;
    if (Math.abs(nx) < 0.35 && Math.abs(ny) < 0.55) return DEEP;   // a stamped bar in the middle
    return x + y < -1 ? LIGHT : BASE;
  });
  // the glint sweeps across the face on the face-on frames
  if (face) {
    const g = [-2, -1, 0, 1, 2, -9, -9, -9, -9, -2, -1, 0][f];
    if (g > -9) { p.px(g, -2, SHINE); p.px(g - 1, -1, LIGHT); }
  }
  // a four-point wink off the rim once a turn
  if (f === 10 || f === 11) {
    const k = f === 10 ? 1 : 2;
    p.px(3, -3, SHINE);
    for (let i = 1; i <= k; i++) { p.px(3 + i, -3, LIGHT); p.px(3 - i, -3, LIGHT); p.px(3, -3 - i, LIGHT); p.px(3, -3 + i, LIGHT); }
  }
}
