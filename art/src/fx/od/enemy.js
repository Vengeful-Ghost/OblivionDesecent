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
// The dungeon's regulars, each dyed in its type's colour (or a content-pool template's), built on the kit's
// Rig so each faces five ways (o.view: e, se, s, ne, n; the game mirrors them for the other three). The bat
// and the mimic look the same from every side and are drawn flat. Weapons are drawn by the game, so hands
// stay empty. o.kind picks the body:
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

import { Pix, Rig, DYE, IRON, LEATHER, CLOTH, GOLD, BONE, WOOD, STEEL, INK, WHITE, EYE_RED, EYE_GREEN, EYE_GOLD, EMBER, stride, bob, both } from "./_kit.js";
import { hex } from "../../core/index.js";

export const anims = {
  idle: { frames: 2, fps: 3 },
  walk: { frames: 4, fps: 9 },
};

export const meta = {
  name: "Enemies",
  desc: "Every regular enemy type, dyed in its colour, in any of five views.",
  tags: ["od", "character"],
  size: [64, 56], origin: [32, 50], duration: 0.44, loop: true, fps: 9, bg: "dark",
  options: { anim: "walk", kind: "grunt", frame: -1, view: "e" },
};

export function draw(p, t, o) {
  const a = anims[o.anim] ?? anims.walk, walk = o.anim !== "idle";
  const f = o.frame >= 0 ? o.frame : Math.floor(t * a.fps) % a.frames;
  const g = new Pix(-32, -50, 32, 6);
  const s = walk ? stride(f) : 0, dy = walk ? bob(f) : f, lift = walk && f & 1 ? 1 : 0;
  if (FLAT[o.kind]) FLAT[o.kind](g, { f, walk });
  else {
    const r = new Rig(o.view);
    (BODIES[o.kind] ?? BODIES.grunt)(r, { f, s, up: -dy, lift, walk });
    r.draw(g);
  }
  g.draw(p);
}

const DARK = hex("#0c0a10");

/** Two legs w wide and h tall, swinging along the facing round fc; the back one lifts on a passing step. */
const legs = (r, fc, w, h, s, lift, ramp = CLOTH, boot = LEATHER, gap = 0.8) => both((side) => {
  const fo = fc + side * (gap + 2 * s), l = side < 0 ? lift : 0, z = side * (w / 2 + 0.3), hw = w / 2 - 0.1;
  r.block(fo - w / 2, fo + w / 2, z - hw, z + hw, 2 + l, h + l, ramp);
  r.block(fo - w / 2, fo + w / 2 + 0.6, z - hw - 0.1, z + hw + 0.1, l, 3 + l, boot, { round: 1 });
});
/** A pair of eyes on a head's front, each shown while its side faces the viewer. */
const eyes = (r, f, z, h, c, on) => both((side) => r.dots([[f, side * z, h]], c, { n: [0.8, side * 0.6], on }));
/** A rectangle [z0..z1] x [h0..h1] across the body at f. */
const rect = (z0, z1, h0, h1) => [[z0, h1], [z1, h1], [z1, h0], [z0, h0]];

const BODIES = {
  grunt(r, { s, up, lift }) {
    legs(r, 0.5, 3, 5, s, lift);
    r.ball(-1.5 + s, -4.2, 9 + up, 2, 1.8, 2.8, DYE);
    const torso = r.block(-4, 4, -3.8, 3.8, 4 + up, 12 + up, LEATHER, { round: 2 });
    r.block(-4, 4, -3.9, 3.9, 5.5 + up, 6.5 + up, LEATHER, { ink: 0, flat: LEATHER[0], on: torso });
    r.dots([[4, 0.5, 6 + up]], GOLD[2], { on: torso });
    r.ball(2.5 - s * 0.5, 4.2, 9 + up, 2, 1.8, 2.8, DYE, { bias: 0.05 });
    // a big head, long ears swept back and out
    const head = r.ball(1, 0, 16 + up, 6, 5.6, 5, DYE);
    both((side) => r.fin([[-1, side * 4.4, 17.5 + up], [-9, side * 7.5, 20.5 + up], [-8, side * 7, 18.5 + up], [-3.5, side * 4.2, 14.5 + up]], 0.6, DYE,
      { bias: 0.1 }));
    both((side) => r.seg([[4.2, side * 3.6, 18 + up], [6.2, side * 1.2, 17.2 + up]], INK, { n: [0.8, side * 0.6], on: head }));
    eyes(r, 6, 2.2, 16.5 + up, EYE_GOLD, head);
    r.dots([[6.9, 0, 14 + up]], DYE[1], { on: head });
    r.dots([[6.2, 1.1, 13 + up], [6.2, 1.1, 12 + up], [6.2, -1.1, 13 + up], [6.2, -1.1, 12 + up]], BONE[3], { on: head });
  },

  speeder(r, { f, s, up, lift, walk }) {
    // a tail and wings behind, a lean body, a horned head thrust forward
    const flick = walk ? [0, 1, 0, -1][f] : 0;
    r.limb([-3, 0, 6 + up], [-9, -0.5, 9 - flick + up], 1.6, DYE, { bias: -0.2 });
    r.sheet([[-8, 10 - flick + up], [-11, 12 - flick + up], [-9, 8 - flick + up]], -0.5, 0.6, DYE, { bias: -0.2 });
    both((side) => r.fin([[-1, side * 1.5, 11 + up], [-6, side * 6, 16 + flick + up], [-8.5, side * 5, 11 + up], [-6, side * 3.5, 12 + up], [-4, side * 2, 8 + up]], 0.5, DYE,
      { bias: -0.3 }));
    legs(r, 0, 2, 4, s, lift, DYE, IRON);
    r.ball(0.5, 0, 7.5 + up, 3.5, 3.2, 3.5, DYE);
    both((side) => r.ball(2.6 - side * s * 0.6, side * 3, 7 + up, 1.4, 1.3, 2, DYE, { bias: 0.1 }));
    const head = r.ball(2.5, 0, 12 + up, 4, 3.6, 3.5, DYE, { bias: 0.05 });
    both((side) => r.limb([1.2, side * 2, 14.6 + up], [-0.6, side * 3.4, 18 + up], 1.4, BONE));
    eyes(r, 5.8, 1.6, 13 + up, EYE_GOLD, head);
    both((side) => r.seg([[3.6, side * 2.8, 10 + up], [6.3, side * 0.4, 10.8 + up]], INK, { n: [0.6, side * 0.8], on: head }));
    r.dots([[6.2, 0.7, 10 + up]], WHITE, { on: head });
  },

  tank(r, { s, up, lift }) {
    legs(r, -1, 5, 7, s, lift, IRON, IRON, 1.2);
    // broad iron body with a dyed tabard front and back, huge pauldrons, a great helm
    const body = r.block(-6, 6, -8, 8, 6 + up, 22 + up, STEEL, { round: 3, bias: -0.1 });
    for (const fo of [6.4, -6.4])
      r.block(fo - 0.4, fo + 0.4, -3.5, 3.5, 4 + up, 19 + up, DYE, { round: 1, on: body, noFade: true });
    r.block(-6.1, 6.1, -8.1, 8.1, 6 + up, 8 + up, LEATHER, { ink: 0, flat: LEATHER[1], on: body });
    r.dots([[6.6, 0.5, 7 + up], [6.6, -0.5, 7 + up]], GOLD[2], { on: body });
    both((side) => r.ball(-side * s * 0.5, side * 8, 19 + up, 4.5, 4.5, 3.5, STEEL, { bias: side > 0 ? 0.1 : -0.15 }));
    both((side) => r.ball(1.5 - side * s, side * 9, 12 + up, 2.6, 2.6, 3, IRON, { bias: 0.2 }));
    const helm = r.block(-4, 5, -4.5, 4.5, 21 + up, 31 + up, STEEL, { round: 2 });
    r.seg([[5.1, -3.6, 26 + up], [5.1, 3.6, 26 + up]], INK, { on: helm });
    both((side) => r.seg([[5, side * 4.6, 26 + up], [-1, side * 4.6, 26 + up]], INK, { n: [0, side], on: helm }));
    r.seg([[5.1, 0, 27 + up], [5.1, 0, 23 + up]], INK, { on: helm });
    eyes(r, 5.1, 2.2, 26 + up, EYE_RED, helm);
    r.glint(helm, [0.5, 0, 26 + up], [[-2.5, -4], [-3.5, -3]], STEEL[4]);
    r.sheet([[-1, 31 + up], [3, 35 + up], [2, 31 + up]], 0, 0.8, DYE, { bias: 0.1, layer: 0.5 });
  },

  brute(r, { s, up, lift }) {
    legs(r, -0.5, 4, 6, s, lift, DYE, LEATHER, 1);
    // a belly that sags over a leather loincloth, a small head sunk into the shoulders, thick arms
    r.ball(-1 + s, -8, 14 + up, 3, 3, 4, DYE);
    const belly = r.ball(0, 0, 13 + up, 9, 8, 8.5, DYE);
    r.ball(3, 0, 9 + up, 5, 5, 4, DYE, { bias: 0.15, ink: 0, on: belly });
    r.block(-6, 6, -6.5, 6.5, 4 + up, 7 + up, LEATHER, { round: 1, on: belly });
    r.dots([[6.2, 1.5, 6 + up], [6.2, -1.5, 6 + up]], LEATHER[3], { on: belly });
    r.ball(1 - s * 0.7, 8, 14 + up, 3, 3, 4, DYE, { bias: 0.1 });
    const head = r.ball(3, 0, 23 + up, 4.5, 4.2, 4, DYE);
    both((side) => r.seg([[3.5, side * 3.4, 25 + up], [7, side * 1.2, 24 + up]], INK, { n: [0.7, side * 0.7], on: head }));
    eyes(r, 6.6, 1.6, 24 + up, EYE_RED, head);
    both((side) => r.dots([[6.6, side * 1.6, 21 + up], [6.6, side * 1.6, 20 + up]], BONE[3], { n: [0.8, side * 0.6], on: head }));
    r.glint(head, [3, 0, 23 + up], [[-3, -3], [-2, -3]], DYE[4]);
  },

  archer(r, { s, up, lift }) {
    // a quiver on the back, bony legs under a dyed cloak, a skull in a deep hood
    const quiver = r.block(-6, -3.5, -1.5, 1.5, 8 + up, 18 + up, LEATHER, { round: 1 });
    r.dots([[-5.5, 0, 19 + up], [-5, 0.5, 20 + up], [-4.5, -0.5, 19 + up]], BONE[2], { n: [0, 0], min: -2, on: quiver });
    legs(r, 0.5, 2, 5, s, lift, BONE, IRON, 0.9);
    r.block(-4.5, 4.5, -4, 4, 4 + up, 16 + up, DYE, { round: 2, bias: -0.05 });
    both((side) => r.ball(3 - side * s * 0.5, side * 4.2, 10 + up, 1.6, 1.5, 2.6, BONE, { bias: 0.1 }));
    const hood = r.ball(0.5, 0, 19 + up, 5.5, 5, 5, DYE);
    // the hood's dark opening and the skull in it
    r.plate(rect(-3, 3, 16.5 + up, 21 + up), 4.8, 0.6, [DARK], { ink: 0, flat: DARK, on: hood, n: [1, 0] });
    r.plate(rect(-2.2, 2.2, 17.2 + up, 20.5 + up), 5.3, 0.4, BONE, { ink: 0, on: hood, n: [1, 0] });
    eyes(r, 5.8, 1.2, 19 + up, EYE_RED, hood);
    r.dots([[5.8, 0.8, 17 + up], [5.8, -0.8, 17 + up]], DARK, { on: hood });
  },

  bomber(r, { s, up, lift }) {
    legs(r, 0, 2, 3, s, lift, IRON, IRON, 1);
    const bomb = r.ball(0, 0, 8 + up, 7, 7, 6.5, DYE);
    r.block(-2, 2, -2, 2, 13 + up, 16 + up, IRON, { round: 1 });
    r.glint(bomb, [0, 0, 8 + up], [[-3, -3], [-2, -4]], DYE[4]);
    both((side) => r.seg([[5.2, side * 3.8, 10.3 + up], [6.6, side * 1, 9.3 + up]], INK, { n: [0.8, side * 0.6], on: bomb }));
    both((side) => r.dots([[6.4, side * 2.4, 8 + up]], WHITE, { n: [0.8, side * 0.6], on: bomb }));
    both((side) => r.dots([[6.4, side * 2.4, 7 + up]], INK, { n: [0.8, side * 0.6], on: bomb }));
    r.seg([[6.4, -2, 4.6 + up], [6.7, 0, 4.2 + up], [6.4, 2, 4.6 + up]], INK, { on: bomb });
  },

  charger(r, { f, up, walk }) {
    // four legs at a trot: diagonal pairs move together
    const k = walk ? [1, 0, -1, 0][f] : 0;
    for (const [fo, side, ph] of [[-6, -1, k], [5, -1, -k], [-6, 1, -k], [5, 1, k]]) {
      const l = walk && f & 1 && side < 0 ? 1 : 0;
      r.block(fo + ph - 1.2, fo + ph + 1.2, side * 3.4 - 1.1, side * 3.4 + 1.1, l, 6 + l, DYE, { bias: -0.1 });
    }
    const body = r.ball(-1, 0, 10 + up, 9, 5, 5.5, DYE);
    // a bristling mane down the back, a heavy head with curving tusks
    for (let i = 0; i < 5; i++) r.dots([[-6 + i * 2, 0, 16 - (i & 1) + up], [-6 + i * 2, 0, 15 + up]], DYE[0], { n: [0, 0], min: -2, on: body });
    both((side) => r.fin([[4, side * 2, 12 + up], [2, side * 3.5, 16 + up], [6, side * 2.5, 13 + up]], 0.5, DYE, { bias: -0.1 }));
    const head = r.ball(8, 0, 9 + up, 4.5, 4, 4, DYE, { bias: 0.05 });
    r.ball(11, 0, 7 + up, 2, 2.2, 1.6, DYE, { bias: 0.25, on: head });
    both((side) => r.limb([10, side * 2, 6 + up], [12.5, side * 3.4, 10 + up], 1.4, BONE));
    eyes(r, 10, 2.4, 11 + up, EYE_RED, head);
    both((side) => r.dots([[12.9, side * 0.8, 7.5 + up]], INK, { n: [0.9, side * 0.4], on: head }));
  },

  necro(r, { f, up, walk }) {
    // glides: the robe hem ripples instead of legs
    const hem = walk ? [0, 1, 0, -1][f] : f;
    const robe = r.sheet([[-3, 18 + up], [4, 18 + up], [7, 0], [3 + hem, 1], [0, 0], [-3 - hem, 1], [-7, 0]], 0, 6.5, DYE, { flare: [18, 3.8, 0, 6.5] });
    r.seg([[4.2, 0, 12 + up], [5.5, 0, 2]], DYE[0], { on: robe });
    both((side) => r.ball(4.5, side * 4.2, 11 + up, 1.6, 1.5, 2.2, BONE, { bias: 0.1 }));
    const hood = r.ball(0.5, 0, 21 + up, 5.5, 5, 5, DYE, { bias: -0.05 });
    r.plate(rect(-2.6, 2.6, 17.5 + up, 23.5 + up), 3.6, 1.2, BONE, { ink: DARK, on: hood, n: [1, 0] });
    eyes(r, 5.1, 1.3, 21 + up, EYE_GREEN, hood);
    r.dots([[5.1, 0, 18.6 + up]], INK, { on: hood });
    r.glint(hood, [0.5, 0, 21 + up], [[-3.5, -4], [-2.5, -5]], DYE[4]);
  },
};

// The bat and the mimic: the same from every side, so one flat drawing (the game flips it to face left)
const FLAT = {
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
