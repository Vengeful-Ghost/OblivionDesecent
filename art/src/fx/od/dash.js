// ===========================================================================================================
//  >>> EXPORT AFTER EDITING <<<  The game only sees sprite changes once they are exported. From the repo
//  root (the OblivionDesecent folder with app/ and art/ in it), run:
//
//      node art/tools/export.mjs
//
//  That rebuilds app/src/main/assets/px/ (the sheets and sprites.json the game loads); commit those too.
//  Needs Node 18 or newer, nothing else. To look before you export:
//
//      node art/tools/look.mjs fx.dash        (writes art/records/look/fx.dash.png)
//
// ===========================================================================================================
// The dash's two ends, drawn for the game (GameActivity.dash): the KICK where the player pushes off and the
// SKID where they stop. The middle of the dash is drawn live by GameEffects (afterimages of the player's own
// frame and pixel speed lines), since only the game knows how far it goes.
//
//   kick  a white flash at the heel, a half ring of air thrown back the way you came, speed streaks and
//         arcane motes shooting back, floor dust blown out low behind and to the sides, a little grit hopping
//   skid  a ring of air at the feet, dust shoved on ahead and out to the sides, sparks skittering forward
//
// dir is the dash's way in art pixels (x right, y down the screen); art/sprites.mjs bakes five of them
// (e, se, s, ne, n) and the game mirrors those for the three that face left, as it does with bodies. Motion
// along y is squashed by the floor's tilt (FLAT), so dust rolls along the floor instead of rising off it.

import { Particles, P, Rng, fit, sparkle, band, TAU, mix, hex, WHITE } from "../../core/index.js";

export const meta = {
  name: "Dash",
  desc: "The two ends of a dash: the kick of air and dust where you push off, the skid where you stop.",
  tags: ["od", "dash", "motion", "dust", "speed", "one-shot"],
  size: [112, 72], origin: [56, 42], duration: 0.6, loop: false, fps: 20, bg: "ground",
  options: {
    anim: "kick", dir: [1, 0],
    palette: ["#101a3a", "#1e3670", "#2f5cb0", "#4a8ae8", "#7ab8ff", "#b4dcff", "#e6f4ff", "#ffffff"],
    dust: ["#2e2a36", "#4a4554", "#6e6878", "#9a94a4", "#c4bece"],
    seed: 5,
  },
};

const FLAT = 0.5; // floor tilt: how much of a move along y shows on screen

export function create(o) {
  const C = fit(o.palette, 8), D = fit(o.dust, 5), R = new Rng(o.seed + (o.anim === "skid" ? 50 : 0));
  const len = Math.hypot(o.dir[0], o.dir[1]) || 1, dx = o.dir[0] / len, dy = o.dir[1] / len;
  const back = Math.atan2(-dy, -dx), ahead = Math.atan2(dy, dx);
  const floor = () => 0;
  const under = new Particles({ rng: R, ground: floor, sparks: [o.palette[7], o.palette[5], o.palette[3]] });
  const over = new Particles({ rng: R, ground: floor, sparks: [o.palette[7], o.palette[5], o.palette[3]] });
  const AIR = mix(hex(o.palette[5]), WHITE, 0.25), LIT = mix(hex(o.dust[3]), hex(o.palette[5]), 0.35);
  // a vector at angle a, speed v, laid on the floor
  const along = (a, v) => [Math.cos(a) * v, Math.sin(a) * v * FLAT];

  if (o.anim === "skid") {
    // dust shoved on ahead and out to the sides, low along the floor
    for (let i = 0; i < 20; i++) {
      const a = ahead + R.range(-1.6, 1.6), [vx, vy] = along(a, R.range(40, 105));
      under.spawn(P.DUST, R.range(-2, 2), R.range(-2, 0), vx, vy - R.range(2, 9), R.range(0.4, 0.65), R.chance(0.4) ? LIT : D[R.int(2, 4)], R.range(2.2, 4), R.range(0, 0.04));
    }
    // sparks skittering on, and a few motes left hanging in the air
    for (let i = 0; i < 6; i++) {
      const a = ahead + R.range(-0.7, 0.7), [vx, vy] = along(a, R.range(90, 170));
      over.spawn(P.SPARK, R.range(-2, 2), -1, vx, vy - R.range(30, 70), R.range(0.22, 0.4), WHITE, 1);
    }
    for (let i = 0; i < 6; i++) {
      const [vx, vy] = along(R.range(0, TAU), R.range(8, 22));
      over.spawn(P.MOTE, R.range(-6, 6), -R.range(3, 14), vx, vy - R.range(4, 12), R.range(0.35, 0.55), C[R.int(4, 6)], 0.6);
    }
  } else {
    // speed streaks shooting back the way you came, from the body's height
    for (let i = 0; i < 14; i++) {
      const a = back + R.range(-0.3, 0.3), [vx, vy] = along(a, R.range(150, 260));
      over.spawn(P.STREAK, -dx * 3 + R.range(-3, 3), -R.range(2, 15), vx, vy, R.range(0.14, 0.28), C[R.int(4, 7)], 1, R.range(0, 0.06));
    }
    // floor dust blown out low behind and to the sides
    for (let i = 0; i < 26; i++) {
      const a = back + R.range(-1.25, 1.25), [vx, vy] = along(a, R.range(45, 130));
      under.spawn(P.DUST, R.range(-2, 2), R.range(-2, 0), vx, vy - R.range(3, 10), R.range(0.42, 0.7), R.chance(0.35) ? LIT : D[R.int(2, 4)], R.range(2.4, 4.4), R.range(0, 0.05));
    }
    // arcane motes trailing off, and grit kicked up off the floor
    for (let i = 0; i < 8; i++) {
      const a = back + R.range(-0.8, 0.8), [vx, vy] = along(a, R.range(40, 90));
      over.spawn(P.MOTE, R.range(-3, 3), -R.range(3, 12), vx, vy - R.range(5, 15), R.range(0.3, 0.5), C[R.int(4, 6)], 0.7, R.range(0, 0.08));
    }
    for (let i = 0; i < 7; i++) {
      const a = back + R.range(-0.6, 0.6), [vx] = along(a, R.range(50, 110));
      over.spawn(P.GRIT, R.range(-2, 2), -1, vx, -R.range(70, 140), R.range(0.45, 0.7), R.pick([D[2], D[3], D[4]]), R.chance(0.3) ? 2 : 1);
    }
  }

  let t = 0;
  // the ring of air on the floor: half of one thrown back on the kick, a whole small one on the skid
  const ring = (p) => {
    const skid = o.anim === "skid", dur = skid ? 0.26 : 0.22, u = t / dur;
    if (u >= 1) return;
    const r = (skid ? 3 : 2) + (skid ? 15 : 26) * (1 - (1 - u) * (1 - u));
    const keep = skid ? () => true : (g) => Math.cos(g) * dx + Math.sin(g) * dy < 0.15;
    band(p, skid ? 0 : -dx * 2, -1, r, 0.42, skid ? 2 : 3, (1 - u) * 0.95, o.palette.slice(2), keep, u < 0.3);
  };

  return {
    update(dt) { t += dt; under.update(dt); over.update(dt); },
    draw(p) {
      under.draw(p);
      ring(p);
      // the flash at the heel (kick) or the feet (skid)
      const f = o.anim === "skid" ? 0.07 : 0.1;
      if (t < f) {
        const k = 1 - t / f;
        sparkle(p, o.anim === "skid" ? 0 : -dx * 2, -7, (o.anim === "skid" ? 4 : 6) * k, AIR, C[5], 0.7 * k);
      }
      over.draw(p);
    },
  };
}
