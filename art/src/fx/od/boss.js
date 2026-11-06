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
// The four boss archetypes, facing right, about twice a regular's size, dyed in the boss's colour.
// The game draws their weapons and phase-two aura. o.kind picks one:
//   brute      a horned warlord in iron pauldrons
//   summoner   a crowned lich in long robes, floating
//   sorcerer   a floating crystal with an eye, shards circling it
//   hunter     a hunched beast-hunter with ears, claws and a long snout

import { Pix, DYE, IRON, STEEL, LEATHER, GOLD, BONE, INK, WHITE, EYE_GOLD, EYE_RED, stride, bob } from "./_kit.js";
import { hex } from "../../core/index.js";

export const anims = {
  idle: { frames: 2, fps: 3 },
  walk: { frames: 4, fps: 7 },
};

export const meta = {
  name: "Bosses",
  desc: "The four boss archetypes, facing right, dyed in the boss's colour.",
  tags: ["od", "character", "boss"],
  size: [96, 84], origin: [48, 76], duration: 0.57, loop: true, fps: 7, bg: "dark",
  options: { anim: "walk", kind: "brute", frame: -1 },
};

const DARK = hex("#0c0a10"), VIOLET = hex("#d48cff"), CORE = hex("#fff3c0");

export function draw(p, t, o) {
  const a = anims[o.anim] ?? anims.walk, walk = o.anim !== "idle";
  const f = o.frame >= 0 ? o.frame : Math.floor(t * a.fps) % a.frames;
  const g = new Pix(-48, -76, 48, 8);
  const s = walk ? stride(f) : 0, dy = walk ? bob(f) : f, lift = walk && f & 1 ? -1 : 0;
  (BODIES[o.kind] ?? BODIES.brute)(g, { f, s, dy, lift, walk });
  g.draw(p);
}

const BODIES = {
  brute(g, { s, dy, lift }) {
    for (const [x, l, b] of [[-9 - s * 3, lift, -0.22], [3 + s * 3, 0, 0]]) {
      g.box(x, -14 + l, x + 7, -4 + l, DYE, { bias: b - 0.1 });
      g.box(x - 1, -5 + l, x + 8, l, IRON, { bias: b, round: 1 });
    }
    g.blob(-15, -27 + dy, 5, 7, DYE, { bias: -0.25 });
    g.blob(0, -27 + dy, 15, 14, DYE);
    g.blob(4, -22 + dy, 8, 7, DYE, { bias: 0.15, ink: 0 });
    // a belt with a skull buckle, iron pauldrons, a heavy fist
    g.box(-13, -17 + dy, 13, -13 + dy, LEATHER, { round: 1 });
    g.blob(1, -15 + dy, 3, 2.6, BONE, { ink: INK });
    g.dots([[0, -16 + dy], [2, -16 + dy]], DARK);
    g.blob(-12, -38 + dy, 8, 5.5, STEEL, { bias: -0.15 });
    g.blob(11 - s, -38 + dy, 8, 5.5, STEEL, { bias: 0.05 });
    for (const x of [-15, -10, 8, 13]) g.dots([[x, -41 + dy]], STEEL[4]);
    g.blob(15 - s * 1.5, -24 + dy, 5, 6, DYE, { bias: 0.1 });
    g.blob(16 - s * 1.5, -18 + dy, 4, 3, IRON, { bias: 0.2 });
    // the head low between the shoulders, curling horns, a fanged jaw
    g.stroke(-2, -46 + dy, -9, -53 + dy, 3, BONE, { bias: -0.15 });
    g.stroke(-9, -53 + dy, -7, -58 + dy, 2, BONE, { bias: -0.15 });
    g.blob(3, -43 + dy, 8, 7, DYE, { bias: 0.05 });
    g.stroke(7, -48 + dy, 13, -54 + dy, 3, BONE);
    g.stroke(13, -54 + dy, 12, -59 + dy, 2, BONE);
    g.line(4, -46 + dy, 11, -44 + dy, INK);
    g.dots([[7, -44 + dy], [8, -44 + dy], [10, -43 + dy]], EYE_GOLD);
    g.box(5, -40 + dy, 11, -37 + dy, [DARK], { ink: 0, flat: DARK });
    g.dots([[6, -40 + dy], [8, -40 + dy], [10, -40 + dy], [7, -37 + dy], [9, -37 + dy]], BONE[3]);
  },

  summoner(g, { f, dy, walk }) {
    // floats: the whole robe drifts up and down, the hem ripples
    const hem = walk ? [0, 1, 0, -1][f] : f, up = -3 + (walk ? [0, -1, -1, 0][f] : f);
    g.poly([[-8, -42 + up], [9, -42 + up], [16, -4 + up], [9 + hem, -6 + up], [3, -3 + up], [-3 - hem, -6 + up], [-10, -3 + up], [-17, -5 + up]], DYE);
    g.line(0, -36 + up, 2, -5 + up, DYE[0]);
    g.line(-6, -34 + up, -9, -7 + up, DYE[0]);
    // a mantle of bone across the shoulders, skeletal hands
    g.box(-10, -42 + up, 11, -37 + up, BONE, { round: 2, bias: -0.1 });
    for (const x of [-7, -3, 1, 5, 9]) g.dots([[x, -38 + up]], BONE[0]);
    g.blob(13, -26 + up, 3, 4, BONE, { bias: 0.1 });
    g.blob(-13, -27 + up, 3, 4, BONE, { bias: -0.2 });
    // hood, a skull lit purple from inside, a crown of gold spikes
    g.blob(1, -50 + up, 10, 9.5, DYE, { bias: -0.05 });
    g.box(-1, -54 + up, 9, -43 + up, [DARK], { ink: 0, flat: DARK, round: 2 });
    g.blob(5, -49 + up, 4, 4.5, BONE, { ink: 0 });
    g.dots([[4, -50 + up], [7, -50 + up]], VIOLET);
    g.dots([[3, -50 + up], [6, -50 + up], [5, -46 + up]], DARK);
    g.line(3, -45 + up, 7, -45 + up, DARK);
    g.box(-7, -61 + up, 9, -58 + up, GOLD, { round: 0 });
    for (const x of [-6, -1, 4, 8]) g.poly([[x - 1.5, -61 + up], [x, -65 + up], [x + 1.5, -61 + up]], GOLD, { ink: INK });
    g.dots([[1, -60 + up]], EYE_RED);
  },

  sorcerer(g, { f, walk }) {
    // a crystal that bobs over the floor, an eye in its heart, three shards wheeling round it
    const up = -16 + (walk ? [0, -1, -2, -1][f] : -f);
    const LEFT = DYE.slice(2), RIGHT = DYE.slice(0, 3);
    const back = [], front = [];
    for (let i = 0; i < 3; i++) {
      const ang = (f / 4 + i / 3) * Math.PI * 2, x = Math.cos(ang) * 22, y = up - 20 + Math.sin(ang) * 6;
      (Math.sin(ang) < 0 ? back : front).push([x, y]);
    }
    const shard = ([x, y]) => g.poly([[x, y - 5], [x + 2.5, y], [x, y + 4], [x - 2.5, y]], DYE, { bias: 0.1 });
    back.forEach(shard);
    g.poly([[0, up - 44], [12, up - 22], [0, up + 2], [-12, up - 22]], DYE);
    g.poly([[0, up - 44], [0, up + 2], [-12, up - 22]], LEFT, { ink: 0 });
    g.poly([[0, up - 44], [12, up - 22], [0, up + 2]], RIGHT, { ink: 0 });
    g.line(-12, up - 22, 12, up - 22, DYE[1]);
    g.line(0, up - 43, 0, up + 1, DYE[4]);
    g.blob(0, up - 22, 5, 3.5, [DARK], { ink: 0, flat: DARK });
    g.blob(1, up - 22, 2.4, 2.4, [CORE], { ink: 0, flat: CORE });
    g.dots([[1, up - 22], [2, up - 22]], EYE_RED);
    front.forEach(shard);
    g.dots([[-4, up - 34], [-5, up - 31], [-3, up - 37]], WHITE);
  },

  hunter(g, { f, s, dy, lift, walk }) {
    // a long tail behind, digitigrade legs, a hunched back
    const sw = walk ? [0, 1, 0, -1][f] : 0;
    g.stroke(-10, -24 + dy, -22, -18 + sw + dy, 4, DYE, { bias: -0.2 });
    for (const [x, l, b] of [[-7 - s * 3, lift, -0.25], [3 + s * 3, 0, 0]]) {
      g.stroke(x, -22 + l + dy, x + 3, -10 + l, 5, DYE, { bias: b });
      g.stroke(x + 3, -10 + l, x, -2 + l, 3.5, DYE, { bias: b - 0.1 });
      g.box(x - 1, -3 + l, x + 5, l, LEATHER, { bias: b, round: 1 });
    }
    g.blob(-2, -31 + dy, 13, 10, DYE);
    g.blob(-5, -38 + dy, 9, 5, DYE, { bias: 0.15, ink: 0 });
    // a leather harness, an arm reaching with claws
    g.line(-12, -36 + dy, 6, -24 + dy, LEATHER[1]);
    g.line(-12, -35 + dy, 6, -23 + dy, LEATHER[2]);
    g.dots([[-3, -30 + dy]], GOLD[2]);
    g.stroke(9, -30 + dy, 16 - s, -20 + dy, 4, DYE, { bias: 0.05 });
    for (let i = 0; i < 3; i++) g.line(16 - s + i - 1, -19 + dy, 17 - s + i, -16 + dy, BONE[3]);
    // the head thrust forward: ears back, a long snout, a red eye
    g.poly([[6, -45 + dy], [3, -54 + dy], [10, -46 + dy]], DYE, { bias: -0.15 });
    g.blob(11, -42 + dy, 7, 6, DYE, { bias: 0.05 });
    g.poly([[13, -44 + dy], [23, -40 + dy], [22, -37 + dy], [13, -36 + dy]], DYE, { bias: 0.1 });
    g.poly([[8, -47 + dy], [8, -55 + dy], [13, -47 + dy]], DYE, { bias: 0.1 });
    g.dots([[23, -41 + dy]], INK);
    g.line(14, -43 + dy, 17, -42 + dy, INK);
    g.dots([[15, -42 + dy]], EYE_RED);
    g.line(15, -37 + dy, 22, -37 + dy, DARK);
    g.dots([[16, -36 + dy], [19, -36 + dy], [21, -38 + dy]], BONE[3]);
  },
};
