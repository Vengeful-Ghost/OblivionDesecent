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
// Dungeon furniture that isn't on fire (the torches, candles and braziers are pixelfx's own fire). Anchored
// at the prop's centre on the floor, as Props.java places them. o.kind and o.size (art pixels) pick one:
//   barrel   an oak barrel with iron hoops, seen from a little above; size = radius
//   crate    a plank crate with a lid and corner irons; size = half its width
//   pillar   a stone column stub with a capital and cracks; size = radius
//   bones    a skull and a scatter of bones (o.variant 0 or 1)

import { Pix, IRON, WOOD, STONE, BONE, INK } from "./_kit.js";
import { hex } from "../../core/index.js";

export const anims = { still: { frames: 1, fps: 1 } };

export const meta = {
  name: "Props",
  desc: "Barrels, crates, pillar stubs and bones.",
  tags: ["od", "prop"],
  size: [48, 64], origin: [24, 40], duration: 1, loop: true, fps: 1, bg: "ground",
  options: { anim: "still", kind: "barrel", size: 7, variant: 0, frame: -1 },
};

const DARK = hex("#0c0a10");

export function draw(p, t, o) {
  const g = new Pix(-24, -40, 24, 24);
  KINDS[o.kind](g, o.size, o.variant);
  g.draw(p);
}

const KINDS = {
  barrel(g, r) {
    // a body as tall as it is wide, staves, two hoops, a lid ellipse on top
    const h = Math.round(r * 1.7), top = -Math.round(r * 0.9);
    g.blob(0, top + h * 0.55, r + 0.5, h * 0.55, WOOD);
    for (let x = -r + 2; x < r - 1; x += 3) g.line(x, top + 2, x, top + h - 1, WOOD[0]);
    for (const y of [top + 3, top + h - 3]) {
      g.line(-r, y, r, y, IRON[0]);
      g.line(-r + 1, y - 1, r - 1, y - 1, IRON[2]);
    }
    g.blob(0, top, r - 0.5, Math.max(2, r * 0.38), WOOD, { bias: 0.25 });
    g.blob(0, top, r - 2.5, Math.max(1, r * 0.2), WOOD, { ink: 0, bias: 0.05 });
  },

  crate(g, s) {
    // front face below, lid above, planks and corner irons
    const lid = Math.round(s * 0.55);
    g.box(-s, -s, s, -s + lid, WOOD, { bias: 0.2 });
    g.box(-s, -s + lid, s, s - lid, WOOD, { bias: -0.15 });
    for (let x = -s + 3; x < s - 1; x += 4) g.line(x, -s + 1, x, -s + lid - 1, WOOD[1]);
    g.line(-s + 1, -s + lid + 1, s - 1, s - lid - 1, WOOD[1]);
    g.line(-s + 1, s - lid - 1, s - 1, -s + lid + 1, WOOD[1]);
    for (const [x, y] of [[-s + 1, -s + lid + 1], [s - 1, -s + lid + 1], [-s + 1, s - lid - 1], [s - 1, s - lid - 1]]) g.set(x, y, IRON[3]);
  },

  pillar(g, r) {
    // a squat column: base, shaft and a capital ring, lit from the left, a crack down the shaft
    const h = Math.round(r * 2.2);
    g.blob(0, 0, r + 1, r * 0.5, STONE, { bias: -0.2 });
    g.box(-r + 1, -h, r - 1, 0, STONE);
    for (let y = -h + 4; y < -1; y += 5) g.line(-r + 2, y, r - 2, y, STONE[1]);
    g.blob(0, -h, r + 1, r * 0.45, STONE, { bias: 0.25 });
    g.blob(0, -h, r - 1.5, r * 0.25, STONE, { ink: 0, bias: 0.05 });
    let x = Math.round(r * 0.3);
    for (let y = -h + 3; y < -3; y++) { g.set(x, y, STONE[0]); if (y % 3 === 0) x += y % 2 ? 1 : -1; }
  },

  bones(g, s, variant) {
    const B = BONE;
    const bone = (x0, y0, x1, y1) => {
      g.stroke(x0, y0, x1, y1, 1.6, B, { ink: DARK });
      g.dots([[x0 - 1, y0], [x0, y0 - 1], [x1 + 1, y1], [x1, y1 + 1]], B[3]);
    };
    if (variant) {
      bone(-8, 1, 6, 4);
      bone(-3, 5, 7, -2);
      g.blob(-4, -2, 3.5, 3, B);
      g.dots([[-5, -2], [-3, -2]], DARK);
    } else {
      bone(-7, -2, 7, 3);
      g.blob(4, -3, 3.5, 3, B);
      g.dots([[3, -3], [5, -3]], DARK);
      g.set(4, -1, DARK);
      bone(-6, 4, -1, 6);
    }
  },
};
