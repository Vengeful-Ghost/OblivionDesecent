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
