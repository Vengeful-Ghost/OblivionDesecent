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
// The player: a helmed wanderer in a tunic, cloak and plume dyed in the wardrobe colour, built on the kit's
// Rig so it can face five ways (o.view: e, se, s, ne, n; the game mirrors them for the other three). The game
// draws the weapon itself, so the hands stay free.

import { Pix, Rig, DYE, STEEL, LEATHER, CLOTH, GOLD, INK, stride, bob, both } from "./_kit.js";
import { hex } from "../../core/index.js";

export const anims = {
  idle: { frames: 2, fps: 3 },
  walk: { frames: 4, fps: 10 },
};

export const meta = {
  name: "Wanderer",
  desc: "The player: steel helm with a lit visor, dyed tunic, cloak and plume, in any of five views.",
  tags: ["od", "character"],
  size: [40, 40], origin: [20, 36], duration: 0.4, loop: true, fps: 10, bg: "dark",
  options: { anim: "walk", frame: -1, view: "e" },
};

const VISOR = hex("#9fe3ff");

export function draw(p, t, o) {
  const a = anims[o.anim] ?? anims.walk, walk = o.anim !== "idle";
  const f = o.frame >= 0 ? o.frame : Math.floor(t * a.fps) % a.frames;
  const s = walk ? stride(f) : 0, dy = walk ? bob(f) : f; // up on passing steps, a breath when idle
  const r = new Rig(o.view), up = -dy;                    // dy is screen-down; heights are up

  // cloak behind the body, its hem trailing a step behind the stride
  const hem = walk ? [0, -1, 0, 1][f] : 0;
  r.sheet([[-2, 17 + up], [3, 16 + up], [1, 6 + up], [-6 + hem, 3 + up], [-8 + hem, 5 + up], [-6, 14 + up]], 0, 4, DYE, { bias: -0.22, flare: [17, 3.4, 3, 4] });

  // legs: they swing along the facing; on a passing step the back foot comes off the floor
  both((side) => {
    const fo = 1 + side * (0.8 + 2 * s), lift = walk && f & 1 && side < 0 ? 1 : 0, z = side * 1.7;
    r.block(fo - 1.5, fo + 1.5, z - 1.3, z + 1.3, 2 + lift, 6 + lift, CLOTH);
    r.block(fo - 1.5, fo + 2.1, z - 1.4, z + 1.4, lift, 3 + lift, LEATHER, { round: 1 });
  });

  // the off arm hangs behind, the sword arm is held forward, a glove at its end; both swing with the stride
  r.ball(-3.2 + s, -3.8, 10 + up, 2, 1.8, 3, DYE);
  const torso = r.block(-4, 5, -3.6, 3.6, 5 + up, 16 + up, DYE, { round: 2 });
  r.block(-4, 5, -3.7, 3.7, 6 + up, 8 + up, LEATHER, { ink: 0, flat: LEATHER[1], on: torso });
  r.dots([[4.9, 0.5, 7 + up], [4.9, -0.5, 7 + up]], GOLD[2], { on: torso });
  r.ball(3.5 - s * 0.5, 3.8, 11 + up, 2, 1.8, 3, DYE, { bias: 0.05 });
  r.ball(4.5 - s, 3.6, 7.5 + up, 1.6, 1.5, 1.4, LEATHER, { bias: 0.1 });

  // helm: a rounded kettle with a cheek guard, a visor slit lit from inside, a dyed plume streaming back
  const helm = r.ball(0.5, 0, 21 + up, 6.5, 6.2, 6, STEEL);
  r.block(-5, 6, -5.6, 5.6, 16 + up, 18 + up, STEEL, { ink: 0, bias: -0.3, on: helm });
  r.glint(helm, [0.5, 0, 21 + up], [[-2.5, -4], [-1.5, -4], [-3.5, -3]], STEEL[4]);
  both((side) => {
    // the slit runs round the front of the helm; each half shows while its side faces the viewer
    r.seg([[1, side * 6.2, 21 + up], [4.5, side * 4.5, 21 + up], [6.6, 0, 21 + up]], INK, { n: [0.6, side * 0.8], on: helm });
    r.dots([[5.6, side * 2.6, 21 + up]], VISOR, { n: [0.8, side * 0.6], on: helm });
  });
  r.seg([[6.6, 0, 21 + up], [6.4, 0, 18 + up]], INK, { on: helm });
  const flow = walk ? [0, 1, 0, 1][f] : 0;
  r.sheet([[1, 28 + up], [-3, 29 + up], [-8 - flow, 26 + up], [-9 - flow, 22 + up], [-5, 24 + up], [-1, 25 + up]], 0, 0.9, DYE, { bias: 0.1, layer: 0.5 });

  const g = new Pix(-20, -36, 20, 4);
  r.draw(g);
  g.draw(p);
}
