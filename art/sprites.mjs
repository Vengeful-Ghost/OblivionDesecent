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
// Everything the game draws from sprite sheets. Each entry becomes one animation in sprites.json, which
// Sprites.java loads by name. Run `node art/tools/export.mjs` after changing anything here or in src/fx.
//
//   name    what the game asks for ("player.walk")
//   fx      the effect file under src/fx ("od/player", "impacts/hit-spark")
//   opts    options passed to it ({ anim: "walk" } picks one of an od/ sprite's anims)
//   fps     library effects only: render at this rate instead of their own (pixel art reads well at 12 to 15)
//   until   library effects only: stop after this many seconds
//   dye     the sprite uses the DYE greys, which the game tints with the body's colour
//   tint    the colour tools/look.mjs previews the dye in
//   icon    also write the first frame, trimmed, to this path under app/src/main/res (for Android views)

import { draw as drawPlayer } from "./src/fx/od/player.js";

// Bodies for pixelfx effects written round its stand-in character ("actor": a pixel mask, and colours):
//   BLOB(w, h, lift)  a plain oval body, for effects that only need somewhere on a body to happen
//   PLAYER            our own player, front on, so the revive, level-up and respawn light trace our knight
const BLOB = (w, h, lift = 0) => {
  const data = new Uint8Array(w * h);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++)
    if (((x + 0.5 - w / 2) / (w / 2)) ** 2 + ((y + 0.5 - h / 2) / (h / 2)) ** 2 <= 1) data[y * w + x] = 1;
  return { mask: { w, h, data }, pixels: null, x: -(w >> 1), y: -h - lift, w, h, k: 2, head: [0, -h - lift], chest: [0, -Math.round(h * 0.6) - lift] };
};
const PLAYER = (() => {
  const px = new Map();
  drawPlayer({ px: (x, y, c) => px.set(x + "," + y, c) }, 0, { anim: "idle", frame: 0, view: "s" });
  const pts = [...px.keys()].map((k) => k.split(",").map(Number));
  const x0 = Math.min(...pts.map((p) => p[0])), y0 = Math.min(...pts.map((p) => p[1]));
  const w = Math.max(...pts.map((p) => p[0])) - x0 + 1, h = Math.max(...pts.map((p) => p[1])) - y0 + 1;
  const data = new Uint8Array(w * h), pixels = new Uint32Array(w * h);
  for (const [k, c] of px) { const [x, y] = k.split(",").map(Number), i = (y - y0) * w + (x - x0); data[i] = 1; pixels[i] = c; }
  return { mask: { w, h, data }, pixels, x: x0, y: y0, w, h, k: 2, head: [0, y0], chest: [0, Math.round(y0 * 0.6)] };
})();

// A character's idle and walk, in each view it can face: "player.walk" is side on facing right (e), and
// "player.walk.se", ".s", ".ne", ".n" turn it towards and away from the viewer. The game mirrors these for
// the other three of its eight ways (Sprites.facing). Bodies that look the same every way pass views: ["e"].
const VIEWS = ["e", "se", "s", "ne", "n"];
const body = (name, fx, anims, opts = {}, extra = {}, views = VIEWS) =>
  anims.flatMap((anim) => views.map((view) => ({
    name: view === "e" ? `${name}.${anim}` : `${name}.${anim}.${view}`,
    fx, opts: { anim, view, ...opts }, dye: true, ...extra,
  })));
const WALKERS = ["idle", "walk"];
// each type's own colour in Enemy.java, so previews show them as the game does (tools/look.mjs)
// each biome's fire, dark to white-hot, around the flame colours in Biome.java
const FIRE = {
  crypt: ["#4a1712", "#8a1f17", "#c9281f", "#f2561d", "#ff8a2a", "#ffc23d", "#ffe26b", "#fff3a6", "#ffffff"],
  catacombs: ["#0b2420", "#124a3e", "#1d6e5c", "#36a084", "#6ec8aa", "#9ce6c8", "#d2ffe6", "#eefff6", "#ffffff"],
  forge: ["#4a1408", "#8c2610", "#cf3c12", "#f56a1a", "#ff9a30", "#ffc04a", "#ffe080", "#fff3b0", "#ffffff"],
  void: ["#1a0e30", "#32195a", "#4c2a88", "#6e42b8", "#9a6ae0", "#bf98f4", "#ddc8ff", "#f0e6ff", "#ffffff"],
};
const TINTS = { grunt: "#c83232", speeder: "#ffa000", tank: "#7800b4", brute: "#3ca03c", archer: "#28aaa0",
  bomber: "#c4561e", bat: "#604876", charger: "#84563a", necro: "#305c3c" };

export const SPRITES = [
  // textures the game repeats and tints: the floor everywhere, the brick face of north walls
  { name: "tile.floor", fx: "od/tiles", opts: { anim: "floor" } },
  { name: "tile.bricks", fx: "od/tiles", opts: { anim: "bricks" } },
  ...body("player", "od/player", WALKERS),
  ...["grunt", "speeder", "tank", "brute", "archer", "bomber", "bat", "charger", "necro"]
    .flatMap((kind) => body(`enemy.${kind}`, "od/enemy", WALKERS, { kind }, { tint: TINTS[kind] }, kind === "bat" ? ["e"] : VIEWS)),
  ...body("enemy.mimic", "od/enemy", WALKERS, { kind: "mimic" }, { dye: false }, ["e"]),
  ...Object.entries({ brute: "#b40000", summoner: "#2e1a3a", sorcerer: "#405cbe", hunter: "#70522e" })
    .flatMap(([kind, tint]) => body(`boss.${kind}`, "od/boss", WALKERS, { kind }, { tint })),

  // one-shot effects straight from the pixelfx library, played by GameEffects.play
  { name: "fx.hit", fx: "impacts/hit-spark", fps: 20 },
  { name: "fx.crit", fx: "impacts/crit-hit", fps: 20 },
  { name: "fx.poof", fx: "od/poof" },
  // the exit portal in arcane violet and the shop portal in gold: pixelfx's standing portal, a little
  // taller than the player (width / height are the rift's radii in art pixels; 17 x 29 is pixelfx's own size)
  { name: "fx.portal.exit", fx: "arcane/portal", fps: 10, opts: { width: 8, height: 14 } },
  { name: "fx.portal.shop", fx: "arcane/portal", fps: 10, opts: { width: 8, height: 14, palette: ["#1e1206", "#4a2c08", "#86520c", "#c48412", "#f0b424", "#ffe066", "#fff6c4", "#ffffff"] } },
  { name: "fx.blast", fx: "explosions/small-explosion", fps: 12, until: 2.6, opts: { size: 0.8 } },
  { name: "fx.heal", fx: "holy/heal", fps: 15 },
  // status on a body, drawn by Enemy.draw while it lasts: flames (the body itself left alone), stars round the head
  { name: "fx.burn", fx: "status/burning", fps: 12, opts: { actor: BLOB(12, 16), bodyless: true } },
  { name: "fx.stun", fx: "status/stunned", fps: 9, opts: { actor: BLOB(14, 2) } },
  // the Warden's Aegis catching a hit; an enemy's raised guard catching one of yours
  { name: "fx.ward", fx: "holy/divine-shield", fps: 9, opts: { radius: 21 } },
  { name: "fx.block", fx: "impacts/shield-block", fps: 15 },
  // pickups: a key over "+1 KEY"; a pillar of light over an unopened vault (gold) or boss chest (violet)
  { name: "fx.key", fx: "pickups/key-glint", fps: 9 },
  // a healing draught bobbing where a barrel broke open (GameActivity.dropBarrelPotion), red like the heal
  { name: "fx.potion", fx: "pickups/potion", fps: 9, opts: { palette: ["#1a0508", "#4a0c14", "#8a1622", "#c8283a", "#f04a5a", "#ff8a94", "#ffd0d4", "#ffffff"] } },
  { name: "fx.beam.vault", fx: "pickups/loot-beam", fps: 9, opts: { palette: ["#1e1206", "#4a2c08", "#86520c", "#c48412", "#f0b424", "#ffe066", "#fff6c4", "#ffffff"] } },
  { name: "fx.beam.boss", fx: "pickups/loot-beam", fps: 9 },
  // arrivals: the rune circle and burst where the dead rise (cut before pixelfx's own brute climbs out),
  // a rift for a boss or a Trial wave, the light that writes the player back in at a checkpoint
  { name: "fx.summon", fx: "spawn/summon-rise", fps: 12, until: 1.3 },
  { name: "fx.rift", fx: "spawn/spawn-portal", fps: 9, opts: { width: 16 } },
  { name: "fx.respawn", fx: "spawn/respawn-beam", fps: 10, opts: { actor: PLAYER } },
  // dark magic: the Summoner's sigil; skulls circling the Cursed Crypt's chest
  { name: "fx.hex", fx: "shadow/hex-sigil", fps: 7 },
  { name: "fx.skulls", fx: "shadow/necro-skulls", fps: 9 },
  // the Healing Spring's ring; a revive (ad, Second Wind); a level up; a daily quest done mid-run
  { name: "fx.spring", fx: "holy/heal-aoe", fps: 9, opts: { reach: 60 } },
  { name: "fx.revive", fx: "holy/resurrection", fps: 8, until: 3.6, opts: { actor: PLAYER } },
  { name: "fx.levelup", fx: "ui/level-up", fps: 9, until: 2.6, opts: { actor: PLAYER } },
  { name: "fx.quest", fx: "ui/quest-complete", fps: 8 },
  // heavy blows on the floor: a boss's slam, and smaller landings (the Hunter's leap, a Tank's hammer)
  { name: "fx.slam", fx: "impacts/ground-slam", fps: 9, until: 1.7, opts: { reach: 58 } },
  { name: "fx.land", fx: "impacts/heavy-landing", fps: 15 },
  // water: drops falling from the dark into the Flooded Catacombs' pools, the Healing Spring's fountain
  { name: "prop.drip", fx: "water/drip", fps: 12, opts: { source: false } },
  { name: "prop.fountain", fx: "water/fountain", fps: 9 },
  // the dropped coin, small and spinning; its first frame is also the coin icon beside every price
  { name: "item.coin", fx: "od/coin", opts: { anim: "spin" }, icon: "drawable-nodpi/px_coin.png" },

  // fire props, one set per biome in its own flame colours (Biome.java's flame and flameCore): wall torches
  // for north walls and side walls (east walls mirror the side one), floor candles, and pixelfx's brazier
  ...Object.entries(FIRE).flatMap(([biome, palette]) => [
    { name: `prop.torch.north.${biome}`, fx: "od/torch", opts: { anim: "north", palette } },
    { name: `prop.torch.side.${biome}`, fx: "od/torch", opts: { anim: "side", palette } },
    { name: `prop.candle.${biome}`, fx: "od/candles", opts: { anim: "burn", palette } },
    { name: `prop.brazier.${biome}`, fx: "fire/brazier", fps: 6, opts: { palette, light: 0.7 } },
  ]),

  // rugs at the sizes Props.java lays them (its half sizes in world units / 4)
  ...[[130, 95], [110, 80], [170, 115], [150, 105]].map(([a, b]) => ({ name: `prop.rug.${a}x${b}`, fx: "od/rug", opts: { anim: "still", hw: Math.round(a / 4), hh: Math.round(b / 4) } })),

  // furniture drawn for the game (sizes in art pixels)
  ...[["barrel.l", "barrel", 7], ["barrel.s", "barrel", 6], ["crate.l", "crate", 8], ["crate.s", "crate", 6],
    ["pillar.l", "pillar", 9], ["pillar.m", "pillar", 7], ["bones.0", "bones", 0, 0], ["bones.1", "bones", 0, 1]]
    .map(([name, kind, size, variant = 0]) => ({ name: `prop.${name}`, fx: "od/props", opts: { anim: "still", kind, size, variant } })),
];
