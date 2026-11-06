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
// Wall torches sized for the game's low walls, burning with pixelfx's fire (fire/_lib.js burn: tongues, licks,
// puffs, smoke, embers, light). The flame always rises straight up the screen; what changes is the mount:
//   north   on the brick face of a north wall, seen from the front: an iron plate low on the face, a stub of
//           handle and the pitch-soaked wrap, so the flame burns against the bricks. Origin: the middle of the wall face.
//   side    on a west wall, seen edge-on: a bar on the wall, an arm, and the torch leaning out into the room.
//           Origin: the wall line at the floor. The game mirrors it for east walls.

import { fit, mix, rand, clamp } from "../../core/index.js";
import { noiseFor, hotFirst, burn } from "../fire/_lib.js";

const D = 2;

export const anims = {
  north: { frames: 24, fps: 12 },
  side: { frames: 24, fps: 12 },
};

export const meta = {
  name: "Wall torch",
  desc: "A wall torch for the game's low walls: front-mounted on north walls, leaning out of side walls.",
  tags: ["od", "prop", "fire", "torch", "loop"],
  size: [48, 64], origin: [24, 44], duration: D, loop: true, fps: 12, bg: "dark",
  options: {
    anim: "north", frame: -1,
    palette: ["#4a1712", "#8a1f17", "#c9281f", "#f2561d", "#ff8a2a", "#ffc23d", "#ffe26b", "#fff3a6", "#ffffff"],
    wood: ["#0e080a", "#24130f", "#3b2216", "#5a3520", "#8a5a34", "#b9884f"],
    iron: ["#0d0b12", "#1e1b26", "#363242", "#5b5566", "#8a8396"],
    seed: 4,
  },
};

const NL = noiseFor(D);
// a small flame: [x, y from the wrap's top, height share, half-width, back row?]
const TONGUES = [
  [-1.2, -0.5, 0.72, 1.4, 1],
  [1.3, -0.5, 0.68, 1.4, 1],
  [0, 0, 1, 2.0, 0],
];

export function draw(p, t, o) {
  const F = fit(o.palette, 9), H = hotFirst(F), W = fit(o.wood, 6), I = fit(o.iron, 5), S = o.seed;
  const fl = 0.74 + 0.16 * NL(t, 7.3, S + 1) + 0.1 * NL(t, 19, S + 2);
  const side = o.anim === "side";
  const fire = side ? { x: 7, y: -16 } : { x: 0, y: -6 };
  if (side) sideMount(p, W, I, H, fl);
  else frontMount(p, W, I, H, fl);
  burn(p, t, TONGUES, {
    H, D, NL, S, x: fire.x, y: fire.y, height: 8, lean: 0.1, spread: 2.5,
    puffs: 6, licks: 3, smoke: 4, smokeSize: 1.5, smokeLife: [0.9, 1.5], embers: 4, glowR: 18, glow: 0.8, wind: 1.5,
    mid: () => wrapAt(p, fire.x, fire.y, side ? 0.35 : 0, W, I, H, t),
  });
}

// a bevelled iron plate low on the face, two rivets, the handle rising out of a collar
function frontMount(p, W, I, H, fl) {
  p.shade(-3, -2, 3, 5, (x, y) => {
    if (x < -2.5 || x > 2.5 || y < -1.5 || y > 4.5) return;
    if (x < -2 || x > 2 || y < -1 || y > 4) return I[0];
    if (Math.abs(x) < 0.6 && (Math.abs(y - 0) < 0.6 || Math.abs(y - 3) < 0.6)) return mix(I[4], H[3], 0.35 * fl);
    if (y < 0 || x < -1) return mix(I[3], H[4], 0.25 * fl);
    return x > 1 || y > 3 ? I[1] : I[2];
  });
  p.shade(-2, -5, 2, 1, (x, y) => {
    if (Math.abs(x) > 1.5 || y < -4.5 || y > 0.5) return;
    if (Math.abs(x) > 0.9) return W[0];
    return x < 0 ? W[3] : W[1];
  });
  p.rect(-2, -2, 4, 1, I[3]);
  p.rect(-2, -1, 4, 1, I[1]);
}

// a bar on the wall line, an arm out to a ring, the handle leaning into the room
function sideMount(p, W, I, H, fl) {
  p.rect(0, -12, 2, 7, I[0]);
  p.rect(1, -11, 1, 5, mix(I[3], H[4], 0.25 * fl));
  p.line(2, -9, 4, -10, I[0]);
  p.line(2, -8, 4, -9, I[2]);
  // the handle from its foot by the wall up to the wrap, two pixels thick, lit on the side facing the flame
  for (let i = 0; i <= 7; i++) {
    const x = 3 + i * 0.5, y = -6 - i;
    p.px(x - 1, y, W[0]);
    p.px(x, y, i < 2 ? W[1] : W[3]);
    p.px(x + 1, y, W[0]);
  }
  p.rect(4, -10, 3, 1, I[3]);
}

// the head: rags soaked in pitch, charred and breathing embers where they burn
function wrapAt(p, X, Y, lean, W, I, H, t) {
  p.shade(X - 4, Y - 1, X + 4, Y + 4, (x, y, out) => {
    const dx = x - X - (y - Y) * lean, v = (y - Y) / 3.5, half = 2.6 - v * 0.9;
    if (Math.abs(dx) > half + 0.5 || y < Y - 0.5 || y > Y + 3.5) return;
    if (Math.abs(dx) > half - 0.5 || y > Y + 2.5) return W[0];
    const h = rand(Math.floor(x), Math.floor(y), 31);
    if (v < 0.5 && h < 0.5) {
      const g = 0.5 + 0.5 * Math.sin((t / D) * Math.PI * 4 + h * 40);
      out.k = 0.3 * g;
      return g > 0.5 ? H[2] : H[4];
    }
    return h < 0.4 ? W[0] : dx < 0 ? W[2] : W[1];
  });
  p.px(X - 2 + Math.round(lean * 2), Y + 2, I[2]);
  p.px(X + 1 + Math.round(lean * 2), Y + 2, I[2]);
}
