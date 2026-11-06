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
// The dungeon's regulars, facing right, each dyed in its type's colour (or a content-pool template's).
// Weapons are drawn by the game, so hands stay empty. o.kind picks the body:
//   grunt     a goblin footsoldier in a leather vest
//   speeder   a hunched imp with horns, wings and a tail
//   tank      an iron juggernaut in a dyed tabard, its visor lit
//   brute     a pot-bellied ogre with tusks
//   archer    a hooded skeleton with a quiver
//   bomber    a walking bomb with angry eyes (the game lights its fuse)
//   bat       a bat; its walk is a flap
//   charger   a tusked boar-beast with a bristling mane
//   necro     a robed lich with green eyes
//   mimic     a treasure chest with teeth; its walk is a hop (not dyed)

import { Pix, DYE, IRON, LEATHER, CLOTH, GOLD, BONE, WOOD, STEEL, INK, WHITE, EYE_RED, EYE_GREEN, EYE_GOLD, EMBER, stride, bob } from "./_kit.js";
import { hex } from "../../core/index.js";

export const anims = {
  idle: { frames: 2, fps: 3 },
  walk: { frames: 4, fps: 9 },
};

export const meta = {
  name: "Enemies",
  desc: "Every regular enemy type, facing right, dyed in its colour.",
  tags: ["od", "character"],
  size: [64, 56], origin: [32, 50], duration: 0.44, loop: true, fps: 9, bg: "dark",
  options: { anim: "walk", kind: "grunt", frame: -1 },
};

export function draw(p, t, o) {
  const a = anims[o.anim] ?? anims.walk, walk = o.anim !== "idle";
  const f = o.frame >= 0 ? o.frame : Math.floor(t * a.fps) % a.frames;
  const g = new Pix(-32, -50, 32, 6);
  const s = walk ? stride(f) : 0, dy = walk ? bob(f) : f, lift = walk && f & 1 ? -1 : 0;
  (BODIES[o.kind] ?? BODIES.grunt)(g, { f, s, dy, lift, walk });
  g.draw(p);
}

const DARK = hex("#0c0a10");
const legs = (g, x0, x1, w, h, s, lift, ramp = CLOTH, boot = LEATHER) => {
  for (const [x, l, b] of [[x0 - s * 2, lift, -0.22], [x1 + s * 2, 0, 0]]) {
    g.box(x, -h + l, x + w, -2 + l, ramp, { bias: b });
    g.box(x, -3 + l, x + w + 0.6, l, boot, { bias: b, round: 1 });
  }
};

const BODIES = {
  grunt(g, { s, dy, lift }) {
    legs(g, -3, 1, 3, 5, s, lift);
    g.blob(-4.5, -9 + dy, 2, 2.8, DYE, { bias: -0.25 });
    g.box(-4, -12 + dy, 4, -4 + dy, LEATHER, { round: 2 });
    g.line(-3, -6 + dy, 3, -6 + dy, LEATHER[0]);
    g.dots([[1, -6 + dy]], GOLD[2]);
    g.blob(4 - s * 0.5, -9 + dy, 2, 2.8, DYE, { bias: 0.05 });
    // big head, a long ear swept back, a small one forward
    g.poly([[3, -18 + dy], [7, -22 + dy], [5, -16 + dy]], DYE, { bias: -0.2 });
    g.blob(1, -16 + dy, 6, 5, DYE);
    g.poly([[-3, -17 + dy], [-10, -20 + dy], [-9, -18 + dy], [-4, -14 + dy]], DYE, { bias: 0.1 });
    g.line(2, -18 + dy, 5, -17 + dy, INK);
    g.dots([[3, -17 + dy], [5, -16 + dy]], EYE_GOLD);
    g.dots([[6, -14 + dy]], DYE[1]);
    g.dots([[4, -13 + dy], [4, -12 + dy]], BONE[3]);
  },

  speeder(g, { f, s, dy, lift, walk }) {
    // tail and a wing behind, a lean body, a horned head thrust forward
    const flick = walk ? [0, 1, 0, -1][f] : 0;
    g.stroke(-3, -6 + dy, -9, -9 + flick + dy, 1.6, DYE, { bias: -0.2 });
    g.poly([[-8, -10 + flick + dy], [-11, -12 + flick + dy], [-9, -8 + flick + dy]], DYE, { bias: -0.2 });
    g.poly([[-1, -11 + dy], [-7, -16 - flick + dy], [-9, -11 + dy], [-6, -12 + dy], [-4, -8 + dy]], DYE, { bias: -0.35 });
    legs(g, -2, 0, 2, 4, s, lift, DYE, IRON);
    g.blob(0.5, -7.5 + dy, 3.5, 3.5, DYE);
    g.blob(3 - s * 0.6, -7 + dy, 1.4, 2, DYE, { bias: 0.1 });
    g.blob(2.5, -12 + dy, 4, 3.5, DYE, { bias: 0.05 });
    g.stroke(0, -15 + dy, -2, -18 + dy, 1.4, BONE);
    g.stroke(3, -15 + dy, 4, -18 + dy, 1.4, BONE);
    g.dots([[4, -13 + dy]], EYE_GOLD);
    g.line(3, -10 + dy, 6, -11 + dy, INK);
    g.dots([[5, -10 + dy]], WHITE);
  },

  tank(g, { s, dy, lift }) {
    legs(g, -6, 1, 5, 7, s, lift, IRON, IRON);
    // broad iron body with a dyed tabard, huge pauldrons, a great helm
    g.box(-9, -22 + dy, 9, -6 + dy, STEEL, { round: 3, bias: -0.1 });
    g.box(-3, -19 + dy, 4, -4 + dy, DYE, { round: 1 });
    g.line(-3, -12 + dy, 4, -12 + dy, DYE[1]);
    g.box(-9, -8 + dy, 9, -6 + dy, LEATHER, { ink: 0, flat: LEATHER[1] });
    g.dots([[0, -7 + dy], [1, -7 + dy]], GOLD[2]);
    g.blob(-9, -19 + dy, 4.5, 3.5, STEEL, { bias: -0.15 });
    g.blob(8 - s * 0.5, -19 + dy, 4.5, 3.5, STEEL, { bias: 0.1 });
    g.blob(9 - s, -12 + dy, 2.6, 3, IRON, { bias: 0.2 });
    g.box(-4, -31 + dy, 5, -21 + dy, STEEL, { round: 2 });
    g.line(-1, -26 + dy, 5, -26 + dy, INK);
    g.line(1, -27 + dy, 1, -23 + dy, INK);
    g.dots([[3, -26 + dy], [4, -26 + dy]], EYE_RED);
    g.dots([[-2, -30 + dy], [-3, -29 + dy]], STEEL[4]);
    g.poly([[-1, -31 + dy], [3, -35 + dy], [2, -31 + dy]], DYE, { bias: 0.1 });
  },

  brute(g, { s, dy, lift }) {
    legs(g, -5, 1, 4, 6, s, lift, DYE, LEATHER);
    g.blob(-8, -14 + dy, 3, 4, DYE, { bias: -0.25 });
    // a belly that sags over a leather loincloth, a small head sunk into the shoulders
    g.blob(0, -13 + dy, 9, 8.5, DYE);
    g.blob(3, -9 + dy, 5, 4, DYE, { bias: 0.15, ink: 0 });
    g.box(-6, -7 + dy, 6, -4 + dy, LEATHER, { round: 1 });
    g.dots([[-1, -6 + dy], [2, -6 + dy]], LEATHER[3]);
    g.blob(8 - s * 0.7, -13 + dy, 3, 4, DYE, { bias: 0.1 });
    g.blob(3, -23 + dy, 4.5, 4, DYE);
    g.line(3, -25 + dy, 7, -24 + dy, INK);
    g.dots([[5, -24 + dy]], EYE_RED);
    g.dots([[6, -21 + dy], [6, -20 + dy], [3, -21 + dy]], BONE[3]);
    g.dots([[0, -26 + dy], [1, -26 + dy]], DYE[4]);
  },

  archer(g, { s, dy, lift }) {
    // a quiver on the back, bony legs under a dyed cloak, a skull in a deep hood
    g.box(-6, -18 + dy, -3, -8 + dy, LEATHER, { round: 1 });
    g.dots([[-6, -19 + dy], [-5, -20 + dy], [-4, -19 + dy]], BONE[2]);
    legs(g, -2, 1, 2, 5, s, lift, BONE, IRON);
    g.poly([[-4, -16 + dy], [4, -16 + dy], [5, -5 + dy], [-6, -4 + dy]], DYE, { bias: -0.05 });
    g.blob(4 - s * 0.5, -10 + dy, 1.6, 2.6, BONE, { bias: 0.1 });
    g.blob(0.5, -19 + dy, 5.5, 5, DYE);
    g.box(0, -21 + dy, 5, -16 + dy, [DARK], { ink: 0, flat: DARK, round: 1 });
    g.box(1, -21 + dy, 4.6, -17 + dy, BONE, { ink: 0, round: 1 });
    g.dots([[3, -19 + dy], [4, -19 + dy]], DARK);
    g.dots([[3, -19 + dy]], EYE_RED);
    g.dots([[2, -17 + dy], [4, -17 + dy]], DARK);
  },

  bomber(g, { s, dy, lift }) {
    legs(g, -3, 1, 2, 3, s, lift, IRON, IRON);
    g.blob(0, -8 + dy, 7, 6.5, DYE);
    g.box(-2, -16 + dy, 2, -13 + dy, IRON, { round: 1 });
    g.dots([[-3, -11 + dy], [-2, -12 + dy]], DYE[4]);
    g.line(1, -10 + dy, 4, -9 + dy, INK);
    g.dots([[2, -8 + dy], [5, -8 + dy]], WHITE);
    g.dots([[2, -7 + dy], [5, -7 + dy]], INK);
    g.line(1, -4 + dy, 5, -5 + dy, INK);
  },

  bat(g, { f, walk }) {
    // flies: no feet on the floor; the wings beat up, out, down, out
    const beat = walk ? [-1, 0, 1, 0][f] : [0, 1][f];
    const y = -10 + (walk ? [0, -1, 0, 1][f] : f);
    for (const side of [-1, 1]) {
      const tip = side * 9, up = beat * -4;
      g.poly([[side * 1.5, y - 1], [tip, y - 2 + up], [tip + side, y + 2 + up * 0.4], [side * 6, y + 1], [side * 4, y + 3], [side * 1.5, y + 2]], DYE, { bias: side < 0 ? -0.25 : 0 });
    }
    g.blob(0, y, 3, 3, DYE);
    g.dots([[-1, y - 3], [1, y - 3]], DYE[3]);
    g.dots([[-1, y - 1], [1, y - 1]], EYE_RED);
    g.dots([[0, y + 2]], WHITE);
  },

  charger(g, { f, dy, walk }) {
    // four legs at a trot: diagonal pairs move together
    const k = walk ? [1, 0, -1, 0][f] : 0;
    for (const [x, ph, b] of [[-7, k, -0.25], [4, -k, -0.25], [-5, -k, 0], [6, k, 0]])
      g.box(x + ph, -6, x + ph + 2.4, 0 - (walk && f & 1 && b < 0 ? 1 : 0), DYE, { bias: b - 0.1 });
    g.blob(-1, -10 + dy, 9, 5.5, DYE);
    // a bristling mane down the back, a heavy head with curving tusks
    for (let i = 0; i < 5; i++) g.dots([[-6 + i * 2, -16 + dy + (i & 1)], [-6 + i * 2, -15 + dy]], DYE[0]);
    g.blob(8, -9 + dy, 4.5, 4, DYE, { bias: 0.05 });
    g.blob(11, -7 + dy, 2, 1.6, DYE, { bias: 0.25 });
    g.stroke(10, -6 + dy, 13, -10 + dy, 1.4, BONE);
    g.dots([[9, -11 + dy]], EYE_RED);
    g.dots([[12, -8 + dy]], INK);
    g.poly([[5, -12 + dy], [3, -16 + dy], [7, -13 + dy]], DYE, { bias: -0.1 });
  },

  necro(g, { f, dy, walk }) {
    // glides: the robe hem ripples instead of legs
    const hem = walk ? [0, 1, 0, -1][f] : f;
    g.poly([[-3, -18 + dy], [4, -18 + dy], [7, 0], [3 + hem, -1], [0, 0], [-3 - hem, -1], [-7, 0]], DYE);
    g.line(0, -12 + dy, 1, -2, DYE[0]);
    g.blob(5, -11 + dy, 1.6, 2.2, BONE, { bias: 0.1 });
    g.blob(0.5, -21 + dy, 5.5, 5, DYE, { bias: -0.05 });
    g.blob(2, -20 + dy, 3, 3, BONE, { ink: DARK });
    g.dots([[2, -21 + dy], [4, -21 + dy]], EYE_GREEN);
    g.dots([[3, -18 + dy]], INK);
    g.dots([[-3, -25 + dy], [-2, -26 + dy]], DYE[4]);
  },

  mimic(g, { f, walk }) {
    // hops: up on the passing frames, the lid gaping wider in the air
    const hop = walk ? [0, -2, 0, -2][f] : 0, gape = walk ? [2, 4, 2, 4][f] : 2 + f;
    const lip = -9 + hop, lid = lip - gape; // the body's rim, and the bottom edge of the raised lid
    g.box(-8, lid, 8, lip, [DARK], { ink: INK, flat: hex("#3a0e14") });
    g.blob(3, lip - 1, 3, 1.2, [hex("#c0405a")], { ink: 0 });
    g.box(-8, lip, 8, hop, WOOD, { round: 1 });
    g.box(-8, -5 + hop, 8, -4 + hop, GOLD, { ink: 0, flat: GOLD[1] });
    g.box(-8, lid - 4, 8, lid, WOOD, { round: 1, bias: 0.15 });
    g.box(-8, lid - 2, 8, lid - 1, GOLD, { ink: 0, flat: GOLD[2] });
    for (let x = -6; x <= 6; x += 3) {
      g.dots([[x, lid + 1], [x + 1, lid + 1]], BONE[3]);
      g.dots([[x + 1, lip - 1]], BONE[3]);
    }
    g.dots([[-3, lid - 3], [3, lid - 3]], EMBER);
  },
};
