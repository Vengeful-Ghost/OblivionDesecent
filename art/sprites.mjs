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

const body = (name, fx, anims, opts = {}, extra = {}) =>
  anims.map((anim) => ({ name: `${name}.${anim}`, fx, opts: { anim, ...opts }, dye: true, ...extra }));
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
    .flatMap((kind) => body(`enemy.${kind}`, "od/enemy", WALKERS, { kind }, { tint: TINTS[kind] })),
  ...body("enemy.mimic", "od/enemy", WALKERS, { kind: "mimic" }, { dye: false }),
  ...Object.entries({ brute: "#b40000", summoner: "#2e1a3a", sorcerer: "#405cbe", hunter: "#70522e" })
    .flatMap(([kind, tint]) => body(`boss.${kind}`, "od/boss", WALKERS, { kind }, { tint })),

  // one-shot effects straight from the pixelfx library, played by GameEffects.play
  { name: "fx.hit", fx: "impacts/hit-spark", fps: 20 },
  { name: "fx.crit", fx: "impacts/crit-hit", fps: 20 },
  { name: "fx.poof", fx: "od/poof" },
  // the exit portal in arcane violet and the shop portal in gold: pixelfx's standing portal
  { name: "fx.portal.exit", fx: "arcane/portal", fps: 10 },
  { name: "fx.portal.shop", fx: "arcane/portal", fps: 10, opts: { palette: ["#1e1206", "#4a2c08", "#86520c", "#c48412", "#f0b424", "#ffe066", "#fff6c4", "#ffffff"] } },
  { name: "fx.blast", fx: "explosions/small-explosion", fps: 12, until: 2.6, opts: { size: 0.8 } },
  { name: "fx.heal", fx: "holy/heal", fps: 15 },
  // the dropped coin, small and spinning; its first frame is also the coin icon beside every price
  { name: "item.coin", fx: "od/coin", opts: { anim: "spin" }, icon: "drawable-nodpi/px_coin.png" },

  // fire props, one set per biome in its own flame colours (Biome.java's flame and flameCore): wall torches
  // for north walls and side walls (east walls mirror the side one), floor candles, and pixelfx's brazier
  ...Object.entries(FIRE).flatMap(([biome, palette]) => [
    { name: `prop.torch.north.${biome}`, fx: "od/torch", opts: { anim: "north", palette } },
    { name: `prop.torch.side.${biome}`, fx: "od/torch", opts: { anim: "side", palette } },
    { name: `prop.candle.${biome}`, fx: "od/candles", opts: { anim: "burn", palette } },
    { name: `prop.brazier.${biome}`, fx: "fire/brazier", fps: 8, opts: { palette, light: 0.7 } },
  ]),

  // rugs at the sizes Props.java lays them (its half sizes in world units / 4)
  ...[[130, 95], [110, 80], [170, 115], [150, 105]].map(([a, b]) => ({ name: `prop.rug.${a}x${b}`, fx: "od/rug", opts: { anim: "still", hw: Math.round(a / 4), hh: Math.round(b / 4) } })),

  // furniture drawn for the game (sizes in art pixels)
  ...[["barrel.l", "barrel", 7], ["barrel.s", "barrel", 6], ["crate.l", "crate", 8], ["crate.s", "crate", 6],
    ["pillar.l", "pillar", 9], ["pillar.m", "pillar", 7], ["bones.0", "bones", 0, 0], ["bones.1", "bones", 0, 1]]
    .map(([name, kind, size, variant = 0]) => ({ name: `prop.${name}`, fx: "od/props", opts: { anim: "still", kind, size, variant } })),
];
