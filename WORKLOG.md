# Work log: pixel art and UI pass

A handoff for whoever picks this up next (another session, another computer). Newest requests at the top
of "Open requests"; everything else is what's already in the code and how it fits together.

## How the pixel art works (read this first)

- The art is drawn **in code** with a copy of [pixelfx](https://github.com/patch-gg/pixelfx) (MIT) in `art/`.
  Our own sprites are in `art/src/fx/od/` (player, enemy, boss, props, tiles, torch, candles); library effects
  we use are copied into `art/src/fx/<category>/`. Every sprite the game loads is listed in `art/sprites.mjs`.
- **After changing any sprite, export** from the repo root (needs Node 18+, nothing else):

      node art/tools/export.mjs

  That rewrites `app/src/main/assets/px/` (`px0.png`, `px1.png`... and `sprites.json`). Commit those too:
  CI builds the APK without Node. Preview without exporting: `node art/tools/look.mjs <name prefix>` writes
  `art/records/look/<prefix>.png` (`art/records/` is git-ignored).
- In the game: `Sprites.java` loads the pack; `PixelCanvas.java` is the low-res buffer the whole world is drawn
  into (1 art pixel = `Sprites.PX` = 4 world units), scaled up without smoothing. World text is held back and
  drawn sharp at full resolution. The camera snaps to whole art pixels (`GameCamera`).
- Sprites with a `dye` layer (player, enemies, bosses) are tinted at runtime by the body's colour (wardrobe,
  enemy template), so content-pool colours still work.
- Fire props come in one set per biome (`FIRE` palettes in `art/sprites.mjs`); `Props.setBiome` picks the set.
- North walls have a brick face `Props.WALL_FACE` = 88 units tall (shared by `GameDisplay`); torches mount on
  the wall they were placed by (`Props.drawSprite`, `TORCH_DROP`, `TORCH_INSET`).
- Checking visuals without a phone: the emulator can't run on the main PC (no hypervisor). I render real game
  frames on the JVM with Robolectric (`@GraphicsMode(NATIVE)`), added only temporarily: add
  `testImplementation("org.robolectric:robolectric:4.14.1")` and `testOptions { unitTests.isIncludeAndroidResources = true }`
  to `app/build.gradle.kts`, write a test that builds `GameDisplay` and calls `draw()` into a Bitmap, then
  remove both again. Renders kept in `art/records/scenes/` and `art/records/dialogs/`.
- Build env on the main PC: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`,
  `ANDROID_HOME="$LOCALAPPDATA/Android/Sdk"`, `./gradlew :app:assembleRelease`.

## Done (uncommitted unless noted)

- Biomes + dedicated event rooms (committed and pushed by the user, `0941dee`).
- Pixel pipeline, sprite pack, player / 10 enemies / 4 bosses / mimic sprites, flagstone floor and brick
  textures, barrels / crates / pillars / bones, pixelfx fire (torch, brazier), pixelfx effects (hit spark,
  crit, death poof, bomber blast, heal), spinning coin.
- Wall torches per wall (north face / side walls mirrored), biome fire colours, taller north walls, corner
  pillars and library shelves moved in front of the wall, floor decals kept off it, small candle clusters,
  shackles only on walls you can see.
- Shop + inventory weapon detail: 2x3 stat grid, trait box, no scroll at 732x360dp; green/red deltas and
  cell edges, red Sell button, rarity-coloured badge and name.
- Export banner comments in every art file; `npm run export` / `npm run look` in `art/`.

## Open requests (2026-11-06), in the order I'm doing them

| # | Request | Status |
|---|---|---|
| 1 | Remove the "Stronger than ..." line over the weapon stats | done (`addWeaponGrid`) |
| 2 | A coin icon on the line showing how many coins something costs | done: `res/drawable/ic_coin_px.xml` (pixel coin, never tint it) on shop prices, shop gold, HUD gold, Sell |
| 3 | Elites: a golden outline around the sprite (about 2 px), not a gold circle | done: gold silhouette 1 art px (4 screen px) all round, pulsing (`Enemy.OUTLINE`) |
| 4 | Dropped coins are too big | done: `art/src/fx/od/coin.js`, 7 px across, spins with a glint |
| 5 | Coin drop animation: coins fly off the enemy, bounce on the floor, spin / glint | done: `GoldCoin` flies 700 ms (arc + 2 bounces, shadow), can't be picked up mid-air |
| 6 | Enemy death smoke is far too big | done: `art/src/fx/od/poof.js`, about a body's size |
| 7 | Use pixelfx's portals for the exit and shop portals | done: `arcane/portal`, violet exit, gold shop (`Props.drawPortal`) |
| 8 | The starting room's rug isn't pixel art | done: `art/src/fx/od/rug.js`, baked at every rug size rooms use |
| 9 | Walk animations for player and enemies | already there: 4-frame walks (stride, bob, arm swing, cloak/plume/hem) and 2-frame idles, played while moving. Not yet seen on a phone; could go to 6 frames if they read too stiff |
| 10 | Make the whole game pixel art: text, strings, buttons, dialogs | done: Pixelify Sans everywhere (`res/font/pixel.xml`, `pixel_bold.xml`, static regular + bold files), Cinzel / Crimson removed; pixel-frame buttons, panels, cards from `art/tools/ui-frames.mjs`; square corners in code-made cells. Pixelify's 5 reads a little like an S and B / 2 are easy to misread (it is the font's design, checked in Robolectric and Windows GDI); **the user chose to keep it** (2026-11-06) |
| 11 | Room variety | done: `FloorLayout.shapeRooms` walls off parts of ordinary rooms (clipped corners, L, cross, pillared halls; `Room.blocks`), `growWinding` turns corridors through small JUNCTION landings (L-bends, staircases that read as diagonals, crossroads). Checked over 600 floors: doors, door-to-centre walks and props clear, every room reachable. True curves are not done |

Last checked (2026-11-06 evening): release APK builds with everything above; (`assembleRelease`); entry room render with rug, portal, elite outline,
coins in flight and the small poof in `art/records/scenes/ENTRY.png`. Not played on a phone.

### Round 3 (2026-11-06, later)

- Menu icons, cracks and moss were never gone in the app: my renders inflated layouts bare, skipping
  AppCompat (the title's autosize) and `Wear.apply` (the cracks). Real-menu render now in
  `art/records/dialogs/menu_pixel.png`.
- **Pixel icons:** every icon vector is now `res/drawable/vec_<name>.xml` (the source); `<name>.xml` is a
  wrapper around `res/drawable-nodpi/px_<name>.png`, a 16x16 pixel version with the vector's tint.
  Regenerate: follow the banner in `art/tools/android/PixelIconsTest.java`, then `node art/tools/pixel-icons.mjs`.
- **Wear** (`Wear.Stone`): cracks, broken corners and moss are drawn into a small bitmap one pixel per 2dp and
  scaled up without smoothing, so they match the pixel frames.
- **Room sizes** (`FloorLayout.sizeForType`): small 3-4 tiles, medium 4-6, large 6-8 x 5-7, hallways 4-6 long,
  boss arena 11 (were 4-6, 5-8, 8-12 x 7-11, 6-10, 16). A large room fits one 1080p screen
  (`art/records/scenes/ROOM_LARGE.png`). Event rooms keep their sizes (their props are laid out for them).

### Notes for the big ones

- **Pixel font (10).** pixelfx's own display font (Patch Pixel, in its gallery) is NOT MIT, so it can't ship.
  pixelfx's MIT `src/core/font.js` has only capitals, digits and ~20 symbols (3x5 and 5x7), too little for
  descriptions and the how-to-play pages. So: an OFL font from Google Fonts with lowercase (Pixelify Sans is
  the readable pick; Silkscreen / Press Start 2P / Tiny5 are blockier) in `res/font/`, credited in Settings.
  Then swap `@font/cinzel` / `cinzel_bold` / `crimson_pro` in `res/values/styles.xml` and the
  `getFont(R.font....)` calls, and redraw button / card backgrounds (`res/drawable/btn_gothic*.xml`,
  `dialog_bg.xml`) as pixel 9-patches.
- **Room shapes (11).** Everything assumes axis-aligned rectangles: `FloorLayout` (generation, `isFloor`,
  `resolveWallCollision`, navigation grid), `Room` (`getBounds`, door sides), `GameDisplay` (floor, walls,
  minimap), `Props.decorate` (corners, wall points), enemy spawning. A safe path: keep collision on a tile
  grid and let rooms be unions of rectangles (L, T, plus, cut corners, pillared halls) and corridors chains
  of segments (L-bends, zig-zag "diagonals" in 1-tile steps), drawn with the same textures. Must stay
  seeded so co-op phones build the same floor.

### Round 4 (2026-11-06, night)

- **Nothing animated (walks, idles, portals, torches, coins):** `Sprites.Anim.at()` turned the wall clock (~1.7e12 ms)
  times fps into an `int`, which always clamped to the same frame. Now counted in `long`. Effects were fine
  (they pass their own age).
- **Portals:** the rift is now about the player's height (`art/sprites.mjs` opts `width: 8, height: 14`;
  `art/src/fx/arcane/portal.js` scales its sparks, glows and floor line with that size), the floor glow is
  smaller and the label sits above the sparks (`Props.drawPortal`). `FloorManager.setExitRoom(room)`: a doorway
  on the north or south side puts the two portals side by side (next floor left, shop right); east or west
  stacks them (next floor up, shop down).
- **Digits:** `node art/tools/digits.mjs` redraws 0-9 in both Pixelify TTFs as classic 5 x 7 pixel digits on
  each weight's own grid (Pixelify's 5 looked like an S). Pixelify has no Reserved Font Name, so the modified
  font ships under the OFL; noted in `assets/licenses/PixelifySans-OFL.txt` and the Settings credits.
- **"So close" death card** (`GameActivity.fillSoClose`): "Slain by <what hit last> on floor N" (hits carry
  their source through `hitPlayer` / `resolveStrike` / `Projectile.owner`; `Enemy.killerName()`), how many floors
  short of your best (or New best / Matched; `bestFloorBefore` is read when the run loads), and the Soul Altar
  upgrade you're nearest to with this life's Souls counted in (redrawn when an ad doubles them). The two lines
  sit under the run summary. Daily / weekly runs skip the best-floor line.
- **Treasure thief** (`Enemy.makeThief/updateThief`, `GameActivity.pickThiefRoom/spawnThief/tickThief`): on 25% of
  floors from 2 on (never boss floors; seeded on daily runs) it waits in one ordinary room and bolts when the
  player first walks in. A gold speeder, 85% of the player's speed, runs the open way that leads most directly
  away; 12 s, then "IT GOT AWAY". Each hit spills 2 coins; caught: 20 + 2 x floor coins (max 60), a key, triple
  XP. Doesn't count toward the floor; clearing the floor leaves it running. Bestiary entry THIEF. Co-op: state
  bit 16, guests mirror it.
- **Tutorial crash on its last page:** 7 pages, 6 icons (the biome round added a page without one). Icon added
  (`ic_map`) and the lookup clamps.
- **Coins and chests in walls:** `FloorLayout.clearForItem / itemSpot` (on floor with room to spare, below a north
  wall's brick face, off solid props; else back along the throw, else the nearest clear spot round the thrower).
  Every coin drop and dropped chest goes through `GameActivity.landing`.
- **Eight-way facing:** `_kit.js` has a `Rig`: parts placed in 3D (forward, across, up) and projected for five views
  (e, se, s, ne, n); the game mirrors three of them. Player, all regular enemies and the bosses are rebuilt on it
  (bat and mimic stay one flat drawing). `art/sprites.mjs` `body()` exports `<name>.<anim>` (side) plus `.se .s .ne .n`.
  In the game `Sprites.facing(way, dx, dy)` picks one of 8 ways with a 30 degree dead zone, `Sprites.get(base, way)`
  and `Sprites.mirrored(way)` give the view. Player faces where it aims / walks, enemies where they look or run.
- Renders (Robolectric, removed again): `art/records/scenes/EXIT_FROM_TOP.png`, `EXIT_FROM_SIDE.png`, `FACINGS.png`,
  `GAME_OVER.png`. Note: Robolectric doesn't draw a TextView's compound drawables (even plain vectors), so icons
  beside text are missing from those renders only.
- **pixelfx gallery** (https://patch-gg.github.io/pixelfx/): only the effects we use are vendored. Shortlist that
  fits, not downloaded yet: pickups/chest-open, key-glint, loot-beam, potion; spawn/summon-rise, spawn-portal,
  soul-rise, respawn-beam; lightning/chain-lightning; status/burning, stunned, cursed; shadow/life-steal, hex-sigil,
  necro-skulls; holy/divine-shield, healing-ring, resurrection; impacts/ground-slam, heavy-landing, shield-block;
  water/ripples, drip, fountain; weather/fog, fireflies; ui/level-up, quest-complete. Effects "drawn on the body"
  paint pixelfx's grey dummy, so only effect-only ones drop straight in.

Last checked: release APK builds (15.6 MB). Not played on a phone.

### The pixelfx shortlist (planned 2026-11-06, done in Round 5 below)

The user approved downloading them. pixelfx's latest `src/core` is identical to ours, so these are copied in
as-is but **not yet listed in `art/sprites.mjs` or used by the game** (the game is unchanged by them):
pickups/key-glint, loot-beam · spawn/summon-rise, spawn-portal, respawn-beam · status/burning, stunned, cursed ·
shadow/hex-sigil, necro-skulls · holy/divine-shield, heal-aoe, resurrection · impacts/ground-slam, heavy-landing,
shield-block · water/ripples, drip, fountain · ui/level-up, quest-complete (plus each category's `_lib.js`).
Dropped after previews: chest-open (draws its own chest), potion, soul-rise and life-steal (draw pixelfx's figure),
chain-lightning (fixed three targets), fog and fireflies (full-screen meadow backdrops).

Plan for each (previews: `node <scratchpad>/strips.mjs`-style bake of the effect alone):
- stunned: loop over a stunned enemy's head. burning: loop on enemies the Burning trait lit; pass a small
  custom `actor` (mask { w, h, data }) so its body flash isn't pixelfx's figure. cursed: on the player under the
  tome curse (custom actor, no eyes).
- divine-shield: the Aegis block ("WARDED"). shield-block: an enemy's guard stopping a hit (flip to face).
- key-glint: over "+1 KEY". loot-beam: behind unopened vault (gold palette) and boss chests.
- summon-rise with `until` ~1.2 s (before its own brute climbs out): where thralls rise, a mimic wakes.
  spawn-portal (smaller `width`): boss arrival, Trial waves. respawn-beam with `until` ~0.8 s: checkpoint respawn.
- hex-sigil: the Summoner's summon. necro-skulls: over the Cursed Crypt's chest.
- heal-aoe (smaller `reach`): the Healing Spring. resurrection (custom empty actor): ad revive, Second Wind.
- ground-slam (smaller `reach`): boss slams. heavy-landing: Hunter's leap, other slams.
- level-up (custom empty actor): player level up. quest-complete: a daily quest finished mid-run.
- ripples / drip: Flooded Catacombs props. fountain: the Healing Spring's centrepiece.
Looping effects need a timed play in `GameEffects` (play for N ms) and per-body loops drawn in `Enemy.draw`.

### Round 5 (2026-11-07): pixelfx effects in the game

All in `art/sprites.mjs` (section after `fx.heal`) and exported; the pack is now 7 sheets, about 28 MB once
loaded (long effects run at 7 to 10 fps and some stop early with `until` to keep it there; braziers went to 6 fps).
- **Our own bodies for pixelfx's stand-in** (`BLOB(w, h)` and `PLAYER` at the top of `art/sprites.mjs`): effects
  that trace "the body" trace an oval or our knight (front view) instead of pixelfx's grey figure. Burning got a
  `bodyless` option in our copy (`status/burning.js`): flames, smoke and sparks only.
- **Where each plays** (GameActivity unless noted):
  - fx.burn: flames on an enemy the Burning trait lit (`Enemy.burningUntil`, drawn in `Enemy.draw`).
  - fx.stun: stars round the head while stunned (`Enemy.draw`; the old dots are the fallback).
  - fx.ward: the Aegis catching a hit. fx.block: an enemy's guard stopping your hit (`showBlocked`).
  - fx.key: over "+1 KEY" (`gainKey`).
  - fx.beam.vault / fx.beam.boss / fx.skulls: behind a waiting vault, boss or cursed chest (`TreasureChest.draw`).
  - fx.summon: where the Necromancer and the Summoner raise the dead, each Trial wave foe, a mimic waking.
  - fx.rift: the boss coming up (`spawnBoss`). fx.hex: the Summoner's sigil when it raises its thralls.
  - fx.respawn: at a checkpoint respawn. fx.revive: the ad revive and Second Wind. fx.levelup: each level.
  - fx.quest: a daily quest finished mid-run, a ribbon above the player (the old text banner is the fallback).
  - fx.spring: the Healing Spring's ring when you drink.
  - fx.slam: a boss's slam. fx.land: other enemies' slams and the hammer finisher.
  - prop.fountain: the Healing Spring until it's drunk dry. prop.drip: drops falling into the Catacombs'
    pools (`Props.drawSprite`, PUDDLE / FOUNTAIN).
- `GameEffects.play(name, x, y, flip, holdMs)`: a looping effect plays for holdMs and fades out.
- Not used after previews: chest-open, potion, soul-rise, life-steal, chain-lightning, fog, fireflies, ripples,
  cursed (removed from `art/src/fx`).
- Renders: `art/records/scenes/FX_ROOM.png` (chests, ward, key, stun, burn, block), `FX_SPRING.png`,
  `FX_POOLS.png`, `FX_SHOTS.png`. Co-op guests don't see the host-only ones (rift, hex, summon) yet.

Last checked: release APK builds (18.5 MB). Not played on a phone.

### Round 6 (2026-11-07): the ideas list

Working from `../OblivionDesecentIdeasAndBugFixes.txt` (the user's list; done items are moved to its DONE
section with what changed). In code:
- Co-op fallen player invisible: `prepareNextFloor` revives anyone dying, level ups and Vigor don't heal the
  fallen (`gainXp`, `spendStatPoint`), `Player.isDying` is volatile.
- Barrel potions: `dropBarrelPotion` (6%), `fx.potion` in `art/sprites.mjs` (pixelfx pickups/potion, red).
- RTL: `android:supportsRtl="false"`. Tutorial: `tutorialKey()` ("guest" when signed out), hint brought to
  front; How to Play opens on the first menu visit (`MenuActivity`, prefs `guide_<uid>`).
- Walls: `FloorLayout.isOnFloor` reaches past the north brick face for small bodies and checks diagonals.
  Thief: `Enemy.updateThief` scores ways by how far its body fits, turns when stuck.
- Altar: `MenuActivity.renderAltar(dialog, data, bought)` updates cells in place.
- Bosses: `loadBossIdentity` / `bossDataOrOffline` / `bossKindFor` (offline content when a range hasn't
  arrived, never the same kind twice running); new runs skip the local content cache.
- Spawns: bomber / charger / necromancer rows in `SPAWN_TABLE`, elites 11% up to 3, thief 33%,
  biome favour x2.2 (`Biome` lists widened).
- Perks: values in `Perk` (share()), all formulas read them. Level up is a stat sheet (`showPerkDialog`,
  `fillStatSheet`, `spendStatPoint`); perks renamed as stats; ad reroll removed.
- Stagger / perfect dodge: `skillHeal()` +2 HP.
- Weapon traits on the held weapon: `Player.drawTrait`.
- Boss ultimates: `BossMove.ULTIMATE` (at 10% HP, once, 1.4 s crimson tell), announced "LAST STAND".
- Shop cards: `dialog_shop_item.xml`, `bindShopCard`, `shopIcon`, `shopStatLine`, `applyFloorSale`
  (`ShopItem.fullPrice`). Render: `art/records/dialogs/SHOP_NEW.png`.
- Biome gear: `biomeGear` (`ShopItem.biomeTilted`), `withAdjective` / `withNoun`.

Last checked: release APK builds. Not played on a phone.

### Round 7 (2026-11-07): the second ideas list

- Wardrobe (`MenuActivity.showWardrobe(onClose)` / `loadWardrobe` / `renderWardrobe`): opens once and redraws
  its rows in place after a purchase; its Souls count is the altar's purple one (`styleSoulCount`); closing it
  reloads the altar's count.
- Boss rooms seal: `FloorLayout.bossCorridors` / `bossGates` / `bossSealed`, `GameActivity.tickBossSeal`
  (per phone: shuts once you're all the way inside with the boss alive, lifts when it falls), drawn with the
  Trial's portcullis (`GameDisplay.drawPortcullis`).
- Biome music: `art/tools/music.mjs` composes and renders `res/raw/music_catacombs.wav`, `music_forge.wav`,
  `music_void.wav` (22050 Hz mono, about 55 s loops, RMS -16 dBFS, original, synthesised). `Biome.music()`;
  the Crypt keeps `game_music.mp3`, bosses `boss_music.mp3`. Not listened to: only checked as spectrograms.
- Dash: `art/src/fx/od/dash.js` (kick and skid, five ways, `fx.dash.kick[.se/.s/.ne/.n]`, `fx.dash.skid...`),
  `GameEffects.ghost` (afterimages of the player's own frame), `dashStart` / `dashMove` / `dashEnd` (pixel speed
  lines), `dashSpark` (pixel squares); `GameActivity.dash` / `dashTrail` / `endDash`; gold after a perfect dodge.
  Renders: `art/records/scenes/DASH_E.png`, `DASH_SE.png`, `DASH_N.png`.
- Guided tutorial: the menu's ? asks, then starts `GameActivity` with `EXTRA_TUTORIAL`. `FloorLayout.tutorial()`
  is a fixed six-room floor (doors `stageCorridors` / `stageGates` / `stagesOpen`, a shrine side room, a vault);
  the coach (`coachEnter` / `tickCoach`, 25 steps in `COACH`) teaches moving, dash, attack, heavy, spin,
  telegraphs and perfect dodges, levelling and stats, smashing barrels, potions, event rooms, elites and keys,
  the vault, chests, the bag, mimics, portals and the shop; `coachFinale` covers bosses, biomes, Souls, the
  Altar, daily modes, the Bestiary, co-op and every Settings section. Offline content, nothing saved, kills
  don't count, no quests, you can't fall. Map check: `art/records/scenes/TUTORIAL_FLOOR.png`.

Last checked: release APK builds (26.2 MB). Not played on a phone, and the emulator here can't start (no
hypervisor driver), so the tutorial has only been traced by hand.

### Round 8 (2026-11-07): stats in the bag, the boss's last stand, secret rooms

- Stats: no level-up badge or dialog (`dialog_level_up.xml` deleted). `gainXp` adds `STAT_POINTS_PER_LEVEL` (3)
  points a level to `pendingPerks`; `updateBagPip` shows them on the bag (`txt_bag_points`). The bag's
  `filter_stats` tab opens `stats_panel` over the whole dialog (`showStatSheet` / `buildStatSheet` /
  `refreshStatSheet` / `tapStat`, tiles `item_stat_tile.xml`); with no points (or a stat at its `cap`) a tap
  plays `GATE_LOCKED`, buzzes and shakes. `Perk` gained `name` (its own name), `cap` and `amount()`; steps
  were cut for three points a level; synergies need `SYNERGY_POINTS` (6). The Perks filter is gone.
- Boss last stand (`Enemy`): `ULTIMATE_TELL` 1800 ms, then CAST for `ULT_TIME` 7 s driven by `rage()` (spiral
  arms, `gapRing`, meteors into `pendingBlasts`, the hunter's volleys), then STUNNED for `EXHAUST_TIME`.
  `isRaging()` blocks `takeDamage` (and `damageEnemy` shows IMMUNE); `isExhausted()` takes 1.5x. Drawn with
  `fx.rage.back` / `fx.rage.front` (`art/src/fx/od/rage.js`), `Sprites.rage` (a crimson colour matrix), a
  throbbing outline, a shudder and a swell. `GameActivity` plays `fx.roar` (`od/roar.js`), `GameDisplay.roar()`
  and `setRaging` (burning edges), `tickRage` shakes on its heartbeat. Meteors: `GameEffects.mark` / `due`
  (marks on the floor, `fx.meteor` from `od/meteor.js` falling), `tickBlasts`; co-op guests get them as "m"
  events. Renders: `art/records/scenes/BOSS_RAGE.png`, `BOSS_LAST_STAND.png`.
- Secret rooms (`FloorLayout.placeSecretRoom` / `sealSecret`, solo, 40% of floors, after `shapeRooms`): a 4 x 4
  room off a free side of an ordinary room, its corridor gated while `secretSealed`; `hidden()` / `hiddenAt()`
  keep it, its chest, coins and props off the screen and the map. `GameDisplay.drawCrackedWall` draws the
  host's wall as loose bricks with lit cracks, and `drawDarkness` lets a glimmer through.
  `GameActivity.breakSecretWall` (from `heavyAttack`) opens it on a full charge. `spawnRoomChest` puts a vault
  chest (`TreasureChest.secret`) and coins inside. 203 of 480 generated solo floors had one, none overlapping.
  Renders: `SECRET_SEALED.png`, `SECRET_OPEN.png`, `SECRET_NORTH_WALL.png`.
- Chest beam: `pickups/loot-beam` takes `opts.height` (92 for `fx.beam.vault` / `fx.beam.boss`, was 150).
- Tutorial: `C_SECRET` (a secret room south of the store room in `FloorLayout.tutorial`), an event-rooms card
  at `C_EVENT`, the Level Up cards and `C_SPEND` hint for the STATS tab, the last stand in `coachFinale`; 26 steps.
- Stat sheet render: `art/records/scenes/STATS_SHEET.png`.

Last checked: release APK builds (26.2 MB). Not played on a phone or the emulator.

Round 8 follow-up (same day, from Steven's review):
- No exhausted phase: after `rage()` the boss goes straight back to APPROACH, frenzied (`justFrenzied` for the
  summoner too), its next move almost at once. `isExhausted`, `justRageOver` and the 1.5x damage are gone.
- No red circles on bosses: the frenzied aura and the ultimate's tell field are gone; a frenzied boss gets a red
  outline traced round its sprite (like the elite's gold one). Render: `art/records/scenes/BOSS_FRENZIED.png`.
- The secret wall is secret: `drawCrackedWall` draws only two faint hairline cracks and one chip; no light
  through the darkness; a weak heavy blow only drops grit. Renders: `SECRET_SUBTLE_N/E/S/W.png`.
- Tutorial: no secret room or step (back to 25 steps), the event card no longer lists the events, the stats
  card no longer lists the stats, the finale only warns that a boss isn't done when it's nearly dead.

Last checked: release APK builds (26.2 MB). Not played on a phone.
- Secret wall, a notch bolder: one dark crack (`crackDot`), two pixels wide in places with a lit lip and a
  branch, from the top of a north wall's brick face to its foot, or zigzagging along a side or south wall for
  about a tile. Renders: `art/records/scenes/SECRET_CRACK_N/E/S/W.png`.
