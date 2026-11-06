#!/usr/bin/env node
// ===========================================================================================================
//  >>> RUN AFTER art/tools/android/PixelIconsTest.java HAS MADE THE PIXEL ICONS <<<  From the repo root:
//
//      node art/tools/pixel-icons.mjs
//
// ===========================================================================================================
// For every icon vector res/drawable/vec_<name>.xml, writes res/drawable/<name>.xml: the pixel icon
// res/drawable-nodpi/px_<name>.png at the vector's own size and tint, scaled up without smoothing. Layouts and
// code keep using @drawable/<name>; a view that tints an icon still tints the pixel one.

import { readdirSync, readFileSync, writeFileSync, existsSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const RES = join(dirname(fileURLToPath(import.meta.url)), "..", "..", "app", "src", "main", "res");
const attr = (xml, name) => (xml.match(new RegExp(`android:${name}="([^"]*)"`)) || [])[1];

for (const file of readdirSync(join(RES, "drawable")).filter((f) => f.startsWith("vec_") && f.endsWith(".xml"))) {
  const name = file.slice(4, -4), xml = readFileSync(join(RES, "drawable", file), "utf8");
  if (!existsSync(join(RES, "drawable-nodpi", `px_${name}.png`))) { console.warn("no pixel icon for", name); continue; }
  const w = attr(xml, "width") ?? "24dp", h = attr(xml, "height") ?? "24dp", tint = attr(xml, "tint");
  writeFileSync(join(RES, "drawable", `${name}.xml`), `<?xml version="1.0" encoding="utf-8"?>
<!-- Pixel icon made from vec_${name}.xml by art/tools/pixel-icons.mjs: edit the vector, then regenerate. -->
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:width="${w}" android:height="${h}">
        <bitmap
            android:src="@drawable/px_${name}"
            android:filter="false"
            android:antialias="false"
            android:dither="false"${tint ? `\n            android:tint="${tint}"` : ""}/>
    </item>
</layer-list>
`);
  console.log("wrote", `${name}.xml`);
}
