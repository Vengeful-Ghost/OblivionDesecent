#!/usr/bin/env node
// ===========================================================================================================
//  >>> RUN AFTER EDITING THE FRAMES BELOW <<<  From the repo root:
//
//      node art/tools/ui-frames.mjs
//
//  It rewrites the button, panel and card backgrounds in app/src/main/res/drawable/ listed in FRAMES.
// ===========================================================================================================
// The UI's pixel frames. Android smooths any image it scales (9-patches included), so these are built from
// plain rectangles instead, which stay sharp at every size: one "UI pixel" is U dp. Each frame is rings of
// colour from the outside in, every ring with its corners notched one pixel the way pixel art rounds a box,
// then a flat fill with a one-pixel highlight along its top and a shadow along its bottom (no gradients).

import { writeFileSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const RES = join(dirname(fileURLToPath(import.meta.url)), "..", "..", "app", "src", "main", "res", "drawable");
const U = 2; // dp per UI pixel

// name: { note, rings (outside in), fill, hi, lo, ripple (buttons), pressed (a second look while pressed) }
const FRAMES = {
  btn_login: {
    note: "Primary button: a stone slab in an iron frame, lit along its top edge",
    look: { rings: ["@color/stone_crack", "@color/border_gold"], fill: "#2A2621", hi: "#3D372F", lo: "#141210" },
    pressed: { rings: ["@color/stone_crack", "@color/torch"], fill: "#1C140D", hi: "#120D09", lo: "#2A1E14" },
    ripple: "@color/gold_translucent_strong",
  },
  btn_gothic_secondary: {
    note: "Secondary button: a darker, thinner slab",
    look: { rings: ["@color/stone_crack", "@color/border_gold_inner"], fill: "#201E1A", hi: "#2C2924", lo: "#121110" },
    ripple: "@color/gold_translucent",
  },
  btn_gothic_danger: {
    note: "Danger button (sell, delete, respawn): a blood-red iron frame on dark stone",
    look: { rings: ["@color/stone_crack", "@color/crimson_base"], fill: "#28100C", hi: "#3A1813", lo: "#140807" },
    ripple: "#66E04848",
  },
  dialog_bg: {
    note: "Secondary panel / row / badge background: a recessed stone panel",
    look: { rings: ["@color/border_gold_inner"], fill: "#1A1816", hi: "#24211D", lo: "#100F0D" },
  },
  hud_panel: {
    note: "Small HUD plate (floor / gold / kills): dark stone in a worn iron edge, a little see-through",
    look: { rings: ["@color/border_gold_inner"], fill: "#E61A1816", hi: "#E6262320", lo: "#E60C0B0A" },
  },
  rounded_dialog_bg: {
    note: "Dialog frame: a stone tablet in a worn iron band",
    look: { rings: ["@color/stone_crack", "@color/border_gold", "@color/stone_crack"], fill: "@color/bg_surface", hi: "#26231F", lo: "#121110" },
  },
};

const px = (n) => `${n * U}dp`;
// a ring of colour c at depth d (in UI pixels): two rectangles whose union is a box with its corners notched
const ring = (c, d) => [
  `        <item android:left="${px(d + 1)}" android:right="${px(d + 1)}" android:top="${px(d)}" android:bottom="${px(d)}">\n` +
  `            <shape android:shape="rectangle"><solid android:color="${c}"/></shape>\n        </item>`,
  `        <item android:left="${px(d)}" android:right="${px(d)}" android:top="${px(d + 1)}" android:bottom="${px(d + 1)}">\n` +
  `            <shape android:shape="rectangle"><solid android:color="${c}"/></shape>\n        </item>`,
].join("\n");

function layers(look) {
  const d = look.rings.length, out = look.rings.map((c, i) => ring(c, i));
  out.push(ring(look.fill, d));
  // one-pixel highlight under the top edge, shadow over the bottom edge
  for (const [c, side] of [[look.hi, "top"], [look.lo, "bottom"]])
    out.push(`        <item android:left="${px(d + 1)}" android:right="${px(d + 1)}" android:${side}="${px(d)}"` +
      ` android:height="${px(1)}" android:gravity="${side}">\n` +
      `            <shape android:shape="rectangle"><solid android:color="${c}"/></shape>\n        </item>`);
  return `    <layer-list>\n${out.join("\n")}\n    </layer-list>`;
}

for (const [name, f] of Object.entries(FRAMES)) {
  const head = `<?xml version="1.0" encoding="utf-8"?>\n<!-- ${f.note}. Pixel frame made by art/tools/ui-frames.mjs: edit it there, not here. -->\n`;
  let body;
  if (f.ripple) {
    const look = f.pressed
      ? `    <item>\n    <selector>\n        <item android:state_pressed="true">\n${layers(f.pressed).replace(/^/gm, "        ")}\n        </item>\n        <item>\n${layers(f.look).replace(/^/gm, "        ")}\n        </item>\n    </selector>\n    </item>`
      : `    <item>\n${layers(f.look).replace(/^/gm, "    ")}\n    </item>`;
    body = `<ripple xmlns:android="http://schemas.android.com/apk/res/android" android:color="${f.ripple}">\n` +
      `    <item android:id="@android:id/mask">\n        <shape android:shape="rectangle"><solid android:color="#FFFFFFFF"/></shape>\n    </item>\n` +
      `${look}\n</ripple>\n`;
  } else {
    body = layers(f.look).replace("<layer-list>", `<layer-list xmlns:android="http://schemas.android.com/apk/res/android">`).replace(/^    /gm, "") + "\n";
  }
  writeFileSync(join(RES, `${name}.xml`), head + body);
  console.log("wrote", `${name}.xml`);
}
