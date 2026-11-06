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
// The four boss archetypes, about twice a regular's size, dyed in the boss's colour, built on the kit's Rig so
// each faces five ways (o.view: e, se, s, ne, n; the game mirrors them for the other three). The game draws
// their weapons and phase-two aura. o.kind picks one:
//   brute      a horned warlord in iron pauldrons
//   summoner   a crowned lich in long robes, floating
//   sorcerer   a floating crystal with an eye, shards circling it (the same from every side but the eye)
//   hunter     a hunched beast-hunter with ears, claws and a long snout

import { Pix, Rig, DYE, IRON, STEEL, LEATHER, GOLD, BONE, INK, WHITE, EYE_GOLD, EYE_RED, stride, bob, both } from "./_kit.js";
import { hex } from "../../core/index.js";

export const anims = {
  idle: { frames: 2, fps: 3 },
  walk: { frames: 4, fps: 7 },
};

export const meta = {
  name: "Bosses",
  desc: "The four boss archetypes, dyed in the boss's colour, in any of five views.",
  tags: ["od", "character", "boss"],
  size: [96, 84], origin: [48, 76], duration: 0.57, loop: true, fps: 7, bg: "dark",
  options: { anim: "walk", kind: "brute", frame: -1, view: "e" },
};

const DARK = hex("#0c0a10"), VIOLET = hex("#d48cff"), CORE = hex("#fff3c0");

export function draw(p, t, o) {
  const a = anims[o.anim] ?? anims.walk, walk = o.anim !== "idle";
  const f = o.frame >= 0 ? o.frame : Math.floor(t * a.fps) % a.frames;
  const g = new Pix(-48, -76, 48, 8);
  const s = walk ? stride(f) : 0, dy = walk ? bob(f) : f, lift = walk && f & 1 ? 1 : 0;
  if (o.kind === "sorcerer") sorcerer(g, { f, walk, back: o.view === "ne" || o.view === "n" });
  else {
    const r = new Rig(o.view);
    (BODIES[o.kind] ?? BODIES.brute)(r, { f, s, up: -dy, lift, walk });
    r.draw(g);
  }
  g.draw(p);
}

const rect = (z0, z1, h0, h1) => [[z0, h1], [z1, h1], [z1, h0], [z0, h0]];

const BODIES = {
  brute(r, { s, up, lift }) {
    both((side) => {
      const fo = -2 + side * (1.5 + 3 * s), l = side < 0 ? lift : 0, z = side * 4.5;
      r.block(fo - 3.5, fo + 3.5, z - 3.3, z + 3.3, 4 + l, 14 + l, DYE, { bias: -0.1 });
      r.block(fo - 4.5, fo + 4.5, z - 3.6, z + 3.6, l, 5 + l, IRON, { round: 1 });
    });
    r.ball(-2 + s * 1.5, -14, 25 + up, 5, 4.5, 7, DYE);
    const body = r.ball(0, 0, 27 + up, 15, 12, 14, DYE);
    r.ball(4, 0, 22 + up, 8, 7, 7, DYE, { bias: 0.15, ink: 0, on: body });
    // a belt with a skull buckle, iron pauldrons, heavy fists
    r.block(-13, 13, -11, 11, 13 + up, 17 + up, LEATHER, { round: 1, on: body });
    const buckle = r.plate(rect(-2.6, 2.6, 13.2 + up, 17.8 + up), 12.4, 0.6, BONE, { ink: INK, on: body, n: [1, 0] });
    r.dots([[13, -1, 16 + up], [13, 1, 16 + up]], DARK, { on: buckle });
    both((side) => r.ball(-side * s, side * 11, 38 + up, 7, 7, 5.5, STEEL, { bias: side > 0 ? 0.05 : -0.15 }));
    r.ball(4 - s * 1.5, 14, 24 + up, 5, 4.5, 6, DYE, { bias: 0.1 });
    r.ball(6 - s * 1.5, 14.5, 18 + up, 4, 3.6, 3, IRON, { bias: 0.2 });
    // the head low between the shoulders, curling horns, a fanged jaw
    const head = r.ball(3, 0, 43 + up, 8, 7.5, 7, DYE, { bias: 0.05 });
    both((side) => {
      r.limb([1, side * 6, 47 + up], [-4, side * 12, 53 + up], 3, BONE, { bias: -0.1 });
      r.limb([-4, side * 12, 53 + up], [-3, side * 11, 58 + up], 2, BONE, { bias: -0.1 });
      r.seg([[5, side * 6.5, 46 + up], [10.5, side * 2, 44.5 + up]], INK, { n: [0.7, side * 0.7], on: head });
      r.dots([[9.6, side * 3, 44 + up], [10, side * 2, 44 + up]], EYE_GOLD, { n: [0.8, side * 0.6], on: head });
    });
    const jaw = r.plate(rect(-3.5, 3.5, 37 + up, 40 + up), 10, 0.8, [DARK], { ink: 0, flat: DARK, on: head, n: [1, 0] });
    r.dots([[10.6, -2.5, 40 + up], [10.6, 0, 40 + up], [10.6, 2.5, 40 + up], [10.6, -1.2, 37 + up], [10.6, 1.2, 37 + up]], BONE[3], { on: jaw });
    for (const z of [-14, -9, 9, 14]) r.glint(body, [0, z, 41 + up], [[0, 0]], STEEL[4]);
  },

  summoner(r, { f, walk }) {
    // floats: the whole robe drifts up and down, the hem ripples
    const hem = walk ? [0, 1, 0, -1][f] : f, up = 3 - (walk ? [0, -1, -1, 0][f] : f);
    const robe = r.sheet([[-8, 42 + up], [9, 42 + up], [16, 4 + up], [9 + hem, 6 + up], [3, 3 + up], [-3 - hem, 6 + up], [-10, 3 + up], [-17, 5 + up]], 0, 14, DYE, { flare: [42, 9, 3, 14] });
    r.seg([[9.5, 0, 36 + up], [12, 0, 6 + up]], DYE[0], { on: robe });
    // a mantle of bone across the shoulders, skeletal hands
    const mantle = r.block(-10, 11, -11, 11, 37 + up, 42 + up, BONE, { round: 2, bias: -0.1 });
    for (const z of [-8, -4, 0, 4, 8]) r.dots([[11.2, z, 38 + up]], BONE[0], { on: mantle });
    both((side) => r.ball(8, side * 12, 26 + up, 3, 3, 4, BONE, { bias: 0.1 }));
    // hood, a skull lit purple from inside, a crown of gold spikes
    const hood = r.ball(1, 0, 50 + up, 10, 9.5, 9.5, DYE, { bias: -0.05 });
    const face = r.plate(rect(-5, 5, 43 + up, 54 + up), 7.5, 1, [DARK], { ink: 0, flat: DARK, round: 2, on: hood, n: [1, 0] });
    const skull = r.plate(rect(-3.5, 3.5, 45 + up, 53 + up), 8.6, 0.6, BONE, { ink: 0, on: face, n: [1, 0] });
    both((side) => r.dots([[9.2, side * 1.6, 50 + up]], VIOLET, { on: skull }));
    r.seg([[9.2, -2, 46 + up], [9.2, 2, 46 + up]], DARK, { on: skull });
    const crown = r.block(-7, 9, -8, 8, 58 + up, 61 + up, GOLD);
    for (const [fo, z] of [[8, -5], [8, 5], [-6, -5], [-6, 5], [8.5, 0], [-6.5, 0]])
      r.sheet([[fo - 1.5, 61 + up], [fo, 65 + up], [fo + 1.5, 61 + up]], z, 0.6, GOLD, { on: crown });
    r.dots([[9.2, 0, 60 + up]], EYE_RED, { on: crown });
  },

  hunter(r, { f, s, up, lift, walk }) {
    // a long tail behind, digitigrade legs, a hunched back
    const sw = walk ? [0, 1, 0, -1][f] : 0;
    r.limb([-10, 0, 24 + up], [-22, sw * 1.5, 18 - sw + up], 4, DYE, { bias: -0.2 });
    both((side) => {
      const fo = -2 + side * (2 + 3 * s), l = side < 0 ? lift : 0, z = side * 5;
      r.limb([fo, z, 22 - l + up], [fo + 3, z, 10 + l], 5, DYE);
      r.limb([fo + 3, z, 10 + l], [fo, z, 2 + l], 3.5, DYE, { bias: -0.1 });
      r.block(fo - 1, fo + 5, z - 2, z + 2, l, 3 + l, LEATHER, { round: 1 });
    });
    const body = r.ball(-2, 0, 31 + up, 13, 9, 10, DYE);
    r.ball(-5, 0, 38 + up, 9, 7, 5, DYE, { bias: 0.15, ink: 0, on: body });
    // a leather harness, arms reaching with claws
    both((side) => r.seg([[-12, side * 8.6, 36 + up], [6, side * 8.6, 24 + up]], LEATHER[1], { n: [0, side], on: body }));
    r.dots([[-3, 9, 30 + up]], GOLD[2], { n: [0, 1], on: body });
    both((side) => {
      const reach = side > 0 ? -s : s;
      r.limb([7, side * 8, 30 + up], [14 + reach, side * 9, 20 + up], 4, DYE, { bias: 0.05 });
      for (let i = 0; i < 3; i++) r.seg([[14 + reach, side * (8 + i), 19 + up], [15 + reach, side * (8 + i), 16 + up]], BONE[3], { n: [1, 0], min: -1 });
    });
    // the head thrust forward: ears back, a long snout, red eyes
    const head = r.ball(11, 0, 42 + up, 7, 6, 6, DYE, { bias: 0.05 });
    both((side) => r.fin([[8, side * 3.5, 46 + up], [5, side * 6.5, 55 + up], [11, side * 4.5, 47 + up]], 0.6, DYE, { bias: 0.1 }));
    const snout = r.block(13, 22, -2.6, 2.6, 36 + up, 43 + up, DYE, { round: 2, bias: 0.1 });
    r.dots([[22.2, 0, 42 + up]], INK, { on: snout });
    both((side) => {
      r.seg([[14, side * 4, 44 + up], [17, side * 2.6, 43 + up]], INK, { n: [0.7, side * 0.7], on: head });
      r.dots([[15.5, side * 3.4, 42 + up]], EYE_RED, { n: [0.7, side * 0.7], on: head });
      r.dots([[16, side * 2.7, 36 + up], [19, side * 2.7, 36 + up], [21, side * 2.7, 38 + up]], BONE[3], { n: [0, side], on: snout });
    });
  },
};

// A crystal that bobs over the floor, an eye in its heart, three shards wheeling round it; turned away, the
// eye looks the other way, so only its back faces shine
function sorcerer(g, { f, walk, back }) {
  const up = -16 + (walk ? [0, -1, -2, -1][f] : -f);
  const LEFT = DYE.slice(2), RIGHT = DYE.slice(0, 3);
  const behind = [], front = [];
  for (let i = 0; i < 3; i++) {
    const ang = (f / 4 + i / 3) * Math.PI * 2, x = Math.cos(ang) * 22, y = up - 20 + Math.sin(ang) * 6;
    (Math.sin(ang) < 0 ? behind : front).push([x, y]);
  }
  const shard = ([x, y]) => g.poly([[x, y - 5], [x + 2.5, y], [x, y + 4], [x - 2.5, y]], DYE, { bias: 0.1 });
  behind.forEach(shard);
  g.poly([[0, up - 44], [12, up - 22], [0, up + 2], [-12, up - 22]], DYE);
  g.poly([[0, up - 44], [0, up + 2], [-12, up - 22]], LEFT, { ink: 0 });
  g.poly([[0, up - 44], [12, up - 22], [0, up + 2]], RIGHT, { ink: 0 });
  g.line(-12, up - 22, 12, up - 22, DYE[1]);
  g.line(0, up - 43, 0, up + 1, DYE[4]);
  if (!back) {
    g.blob(0, up - 22, 5, 3.5, [DARK], { ink: 0, flat: DARK });
    g.blob(1, up - 22, 2.4, 2.4, [CORE], { ink: 0, flat: CORE });
    g.dots([[1, up - 22], [2, up - 22]], EYE_RED);
  }
  front.forEach(shard);
  g.dots([[-4, up - 34], [-5, up - 31], [-3, up - 37]], WHITE);
}
