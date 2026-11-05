// Fills the shared content pool: one Gemini call per 5-floor range, stored in Firestore
// content_pool/r<rangeStart>.variants (newest KEEP replies). The app parses and clamps every
// reply itself (BatchGenerator.parseBatch), so this only has to ask well and drop broken JSON.
// Keep the prompt and budget numbers in sync with BatchGenerator.java.
// Env: GEMINI_API_KEY, FIREBASE_SERVICE_ACCOUNT (service account JSON).
import { initializeApp, cert } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';

const MODEL = 'gemini-3.1-flash-lite';
const MAX_FLOOR = 100; // deeper floors use the app's offline content
const KEEP = 6;        // variants kept per range; each run adds one
const PAUSE_MS = 6000; // stay well under the free-tier request rate

const TIERS = ['COMMON', 'RARE', 'EPIC', 'LEGENDARY'];
const STAT_MULT = [[0.85, 1.15], [1.15, 1.4], [1.4, 1.75], [1.75, 2.2]];
const PRICE_MULT = [[1.0, 1.4], [2.0, 2.8], [3.5, 5.0], [7.0, 10.0]];
const CONSUMABLE_PRICE = [0.4, 0.7];
const POTION_PCT = [[15, 20], [20, 25], [25, 30], [30, 35]]; // percent of max HP / armor, by rarity
const weaponBase = f => 6 + f * 2.5;
const armorBase = f => 3 + f * 1.5;
const priceBase = f => 13 + f * 8.5;
const enemyHpMin = r => 1.0 + r * 0.02;
const enemyHpMax = r => 1.5 + r * 0.03;
const enemyDmgMin = r => 1.0 + r * 0.03;
const enemyDmgMax = r => 1.4 + r * 0.04;
const bossHp = f => 2.0 + f * 0.12;
const bossDmg = f => 1.1 + f * 0.05;
const span = (base, lo, hi) => `${Math.max(1, Math.round(base * lo))}-${Math.max(1, Math.round(base * hi))}`;
const fmt = v => v.toFixed(2);

function buildPrompt(start, end) {
  const L = [];
  L.push('You are the content designer for Oblivion Descent, a hard dark fantasy roguelike for mobile.');
  L.push(`Generate content for floors ${start} to ${end}.`, '');
  L.push('DESIGN GOALS (most important):');
  L.push('- The run must feel dangerous. Progress is earned through fights, levels and careful shopping,');
  L.push('  never handed out by the shop. Every floor should be a little harder than the one before.');
  L.push('- The player starts with 100 HP and fists dealing 7 damage every 250 ms. Levels give small perks,');
  L.push('  not raw HP or damage, so gear is the main power source. Gold is scarce: about 40-55 gold per floor.');
  L.push('- Shop items are modest upgrades, never a power spike. A COMMON is a small step up; a LEGENDARY');
  L.push('  is strong but costs several floors of saved gold. Never exceed the budget below.');
  L.push('- Itemise like the classics of the genre (Diablo, Dead Cells, Hades, Enter the Gungeon): every item');
  L.push('  has one clear job, rarity adds a little power and a lot of flavour, and healing is a scarce sip,');
  L.push('  not a refill. A full armor set is about four pieces, so one piece is a quarter of the protection.', '');
  L.push('PER-FLOOR BUDGET (pick values inside these ranges and vary them so no two items match):');
  for (let f = start; f <= end; f++) {
    L.push(`floor ${f}:`);
    L.push('  weapon damage at 600 ms cooldown:' + TIERS.map((t, i) => ` ${t} ${span(weaponBase(f), ...STAT_MULT[i])}`).join(''));
    L.push('  armor defense per piece:' + TIERS.map((t, i) => ` ${t} ${span(armorBase(f), ...STAT_MULT[i])}`).join(''));
    L.push('  price (gold):' + TIERS.map((t, i) => ` ${t} ${span(priceBase(f), ...PRICE_MULT[i])}`).join(''));
    L.push(`  consumables: price ${span(priceBase(f), ...CONSUMABLE_PRICE)}`);
  }
  L.push('');
  L.push('WEAPONS: final damage = budget value * cooldown / 600, so fast weapons (250-400 ms) hit soft');
  L.push('  and slow ones (900-1400 ms) hit hard. Projectile weapons: damage x0.8, range 450-900.');
  L.push('  Melee range 40-140. knockback 0.05-0.4 (heavier weapons push more).');
  L.push('  The name MUST contain its weapon type, because the type decides how it swings:');
  L.push('  Dagger/Knife (fast, short, cooldown 250-400), Sword/Blade (balanced), Axe/Cleaver (wide, slow),');
  L.push('  Spear/Glaive/Halberd (long reach, range 110-140), Hammer/Mace/Maul (slowest, 1000-1400, most knockback),');
  L.push('  Staff/Wand (projectile) or Bow/Crossbow (projectile). Mix the types across the range.');
  L.push('ARMOR: maxDurability 40-120 hits (rarer lasts longer). armorType HEAD, CHEST, LEGS or BOOTS; vary them.');
  L.push('CONSUMABLES: effectValue is a PERCENT of the maximum, not a flat number. "heal" restores that share of');
  L.push('  max HP over two seconds; "armor_restore" restores that share of max armor. All potions share an');
  L.push('  8 second cooldown. Percent by rarity: ' + TIERS.map((t, i) => `${t} ${POTION_PCT[i][0]}-${POTION_PCT[i][1]}`).join(', ') + '.');
  L.push('  Consumables are mostly COMMON or RARE. Give them potion-like names (Draught, Tonic, Elixir, Salve).');
  L.push('SHOP: exactly 6 items per floor: 2 WEAPON, 2 ARMOR, 2 CONSUMABLE (one "heal", one "armor_restore").');
  L.push('  Rarity: mostly COMMON and RARE; at most ONE item EPIC or better per floor; LEGENDARY only on');
  L.push('  floor 4 or deeper and at most one LEGENDARY in this whole response.');
  L.push('ENEMIES: exactly 4 types shared by every floor in this range, each with a clear role:');
  L.push('  one glass cannon (low hp, high damage), one juggernaut (high hp, lower damage), two skirmishers.');
  L.push(`  hpMultiplier ${fmt(enemyHpMin(start))}-${fmt(enemyHpMax(start))}, dmgMultiplier ${fmt(enemyDmgMin(start))}-${fmt(enemyDmgMax(start))}. Distinct colours that stand out on a dark stone floor.`);
  L.push(`BOSS for floor ${end}: the hardest fight of the range, with a name and a title.`);
  L.push('  kind: exactly one of BRUTE (armoured horned juggernaut that slams and charges),');
  L.push('  SUMMONER (hooded necromancer that keeps its distance and raises thralls), SORCERER');
  L.push('  (floating arcane crystal that blinks around firing bolts) or HUNTER (masked beast-hunter');
  L.push('  that leaps onto its prey, hurls spears and calls hounds). Pick the kind that fits the name.');
  L.push(`  hpMultiplier ${fmt(bossHp(end) * 0.9)}-${fmt(bossHp(end) * 1.1)}, dmgMultiplier ${fmt(bossDmg(end) * 0.9)}-${fmt(bossDmg(end) * 1.1)}.`, '');
  L.push('IMPORTANT: Return ONLY a valid JSON object. No markdown fences, no explanation.');
  L.push('The "enemies" field MUST be a JSON array with EXACTLY 4 objects.', '');
  const enemy = '    {"name": "string", "colorHex": "#RRGGBB", "hpMultiplier": 1.0, "dmgMultiplier": 1.0}';
  L.push('{', '  "enemies": [', [enemy, enemy, enemy, enemy].join(',\n'), '  ],', '  "shop": {');
  for (let f = start; f <= end; f++) {
    L.push(`    "floor${f}": [`);
    L.push('      {"name": "string", "type": "WEAPON", "price": 0, "rarity": "COMMON",');
    L.push('       "colorHex": "#FFFFFF", "description": "string",');
    L.push('       "damage": 0, "range": 80.0, "cooldown": 500, "knockback": 0.1, "isProjectile": false,');
    L.push('       "defense": 0, "maxDurability": 100.0, "armorType": "CHEST",');
    L.push('       "effect": "heal", "effectValue": 20}');
    L.push('    ]' + (f < end ? ',' : ''));
  }
  L.push('  },', '  "boss": {', '    "name": "string",', '    "title": "string",', '    "kind": "BRUTE",',
    '    "colorHex": "#FF0000",', '    "hpMultiplier": 3.0,', '    "dmgMultiplier": 1.4', '  }', '}');
  return L.join('\n') + '\n';
}

async function generate(start, end) {
  const res = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${MODEL}:generateContent`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'x-goog-api-key': process.env.GEMINI_API_KEY },
    body: JSON.stringify({
      contents: [{ parts: [{ text: buildPrompt(start, end) }] }],
      // Thinking tokens count toward maxOutputTokens, so leave plenty of room for the JSON itself
      generationConfig: { maxOutputTokens: 32768, temperature: 0.9, responseMimeType: 'application/json' },
    }),
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}: ${(await res.text()).slice(0, 300)}`);
  const reply = await res.json();
  const candidate = reply.candidates?.[0];
  if (!candidate) throw new Error('no reply ' + (reply.promptFeedback?.blockReason ?? ''));
  if (candidate.finishReason === 'MAX_TOKENS') throw new Error('reply cut off');
  let text = (candidate.content?.parts ?? []).filter(p => !p.thought).map(p => p.text ?? '').join('').trim();
  text = text.replace(/```json/g, '').replace(/```/g, '').trim();

  const data = JSON.parse(text); // throws on broken JSON
  if (!Array.isArray(data.enemies) || data.enemies.length < 4) throw new Error('missing enemies');
  for (let f = start; f <= end; f++) {
    if (!Array.isArray(data.shop?.[`floor${f}`])) throw new Error(`missing shop for floor ${f}`);
  }
  if (!data.boss?.name) throw new Error('missing boss');
  return text;
}

const db = getFirestore(initializeApp({ credential: cert(JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT)) }));
let added = 0;
for (let start = 1; start <= MAX_FLOOR; start += 5) {
  try {
    const text = await generate(start, start + 4);
    const ref = db.collection('content_pool').doc(`r${start}`);
    const old = (await ref.get()).data()?.variants ?? [];
    await ref.set({ variants: [...old, text].slice(-KEEP), updated: new Date() });
    added++;
    console.log(`floors ${start}-${start + 4}: ok (${Math.min(old.length + 1, KEEP)} variants)`);
  } catch (e) {
    console.error(`floors ${start}-${start + 4}: ${e.message}`);
  }
  await new Promise(r => setTimeout(r, PAUSE_MS));
}
console.log(`${added} ranges updated`);
if (added === 0) process.exit(1);
