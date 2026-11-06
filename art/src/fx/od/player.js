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
// The player: a helmed wanderer facing right, in a tunic, cloak and plume dyed in the wardrobe colour.
// The game mirrors it to face left and draws the weapon itself, so the hands stay free.

import { Pix, DYE, STEEL, LEATHER, CLOTH, GOLD, INK, stride, bob } from "./_kit.js";
import { hex } from "../../core/index.js";

export const anims = {
  idle: { frames: 2, fps: 3 },
  walk: { frames: 4, fps: 10 },
};

export const meta = {
  name: "Wanderer",
  desc: "The player, facing right: steel helm with a lit visor, dyed tunic, cloak and plume.",
  tags: ["od", "character"],
  size: [40, 40], origin: [20, 36], duration: 0.4, loop: true, fps: 10, bg: "dark",
  options: { anim: "walk", frame: -1 },
};

const VISOR = hex("#9fe3ff");

export function draw(p, t, o) {
  const a = anims[o.anim] ?? anims.walk, walk = o.anim !== "idle";
  const f = o.frame >= 0 ? o.frame : Math.floor(t * a.fps) % a.frames;
  const s = walk ? stride(f) : 0, dy = walk ? bob(f) : f; // body offset: up on passing steps, a breath when idle
  const g = new Pix(-20, -36, 20, 4);

  // legs: the back one darker; on a passing step the back foot comes off the floor
  const lift = walk && f & 1 ? -1 : 0;
  leg(g, -2 - s * 2, lift, -0.22);
  leg(g, 1 + s * 2, 0, 0);

  // cloak behind the body, its hem trailing a step behind the stride
  const hem = walk ? [0, -1, 0, 1][f] : 0;
  g.poly([[-2, -17 + dy], [3, -16 + dy], [1, -6 + dy], [-6 + hem, -3 + dy], [-8 + hem, -5 + dy], [-6, -14 + dy]], DYE, { bias: -0.22 });

  // back arm, tunic, belt
  g.blob(-4.5, -10 + dy, 2, 3, DYE, { bias: -0.25 });
  g.box(-4, -16 + dy, 5, -5 + dy, DYE, { round: 2 });
  g.box(-4, -8 + dy, 5, -6 + dy, LEATHER, { ink: 0, flat: LEATHER[1] });
  g.dots([[2, -7 + dy], [3, -7 + dy]], GOLD[2]);
  g.dots([[-3, -7 + dy], [-1, -7 + dy], [1, -7 + dy]], LEATHER[2]);

  // front arm swings against the stride, a glove at its end
  const arm = -s;
  g.blob(4 + arm * 0.5, -11 + dy, 2, 3, DYE, { bias: 0.05 });
  g.blob(5 + arm, -7.5 + dy, 1.6, 1.4, LEATHER, { bias: 0.1 });

  // helm: a rounded kettle with a cheek guard, a visor slit lit from inside, a dyed plume streaming back
  g.blob(0.5, -21 + dy, 6.5, 6, STEEL);
  g.box(-5, -18 + dy, 6, -16 + dy, STEEL, { ink: 0, bias: -0.3 });
  g.line(1, -21 + dy, 6, -21 + dy, INK);
  g.line(3, -21 + dy, 3, -18 + dy, INK);
  g.dots([[4, -21 + dy], [5, -21 + dy]], VISOR);
  g.dots([[-2, -25 + dy], [-1, -25 + dy], [-3, -24 + dy]], STEEL[4]);
  const flow = walk ? [0, 1, 0, 1][f] : 0;
  g.poly([[1, -28 + dy], [-3, -29 + dy], [-8 - flow, -26 + dy], [-9 - flow, -22 + dy], [-5, -24 + dy], [-1, -25 + dy]], DYE, { bias: 0.1 });

  g.draw(p);
}

function leg(g, x, lift, bias) {
  g.box(x, -6 + lift, x + 3, -2 + lift, CLOTH, { bias });
  g.box(x, -3 + lift, x + 3.6, 0 + lift, LEATHER, { bias, round: 1 });
}
