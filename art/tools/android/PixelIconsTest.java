package com.example.obliviondescent;

// ===========================================================================================================
//  >>> REGENERATES THE PIXEL ICONS <<<  Run after adding or changing a res/drawable/vec_*.xml vector:
//
//   1. copy this file to app/src/test/java/com/example/obliviondescent/
//   2. add to app/build.gradle.kts:  testImplementation("org.robolectric:robolectric:4.14.1")
//                                    testOptions { unitTests.isIncludeAndroidResources = true }
//   3. ./gradlew :app:testDebugUnitTest --tests "com.example.obliviondescent.PixelIconsTest"
//   4. node art/tools/pixel-icons.mjs        (writes the res/drawable/<name>.xml wrappers)
//   5. remove the test file and the two gradle lines again
// ===========================================================================================================

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;

/**
 * Redraws every icon vector (res/drawable/vec_*.xml, game-icons.net and Material shapes) as pixel art: drawn
 * white at 4x, then each 4 x 4 block becomes one pixel, solid where the shape covers enough of it, so the
 * icons get the same hard pixel edges as the rest of the game. Writes res/drawable-nodpi/px_<name>.png.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34)
public class PixelIconsTest {
    private static final int GRID = 16, SUPER = 4;

    @Test
    public void pixelIcons() throws Exception {
        Context ctx = RuntimeEnvironment.getApplication();
        File out = new File("src/main/res/drawable-nodpi");
        out.mkdirs();
        for (Field f : R.drawable.class.getFields()) {
            if (!f.getName().startsWith("vec_")) continue;
            Drawable d = ctx.getDrawable(f.getInt(null));
            d.setTint(Color.WHITE);
            int n = GRID * SUPER;
            Bitmap hi = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
            d.setBounds(0, 0, n, n);
            d.draw(new Canvas(hi));
            Bitmap lo = Bitmap.createBitmap(GRID, GRID, Bitmap.Config.ARGB_8888);
            for (int y = 0; y < GRID; y++)
                for (int x = 0; x < GRID; x++) {
                    int sum = 0;
                    for (int j = 0; j < SUPER; j++)
                        for (int i = 0; i < SUPER; i++) sum += Color.alpha(hi.getPixel(x * SUPER + i, y * SUPER + j));
                    lo.setPixel(x, y, sum >= 0.42f * 255 * SUPER * SUPER ? Color.WHITE : Color.TRANSPARENT);
                }
            String name = "px_" + f.getName().substring(4);
            try (FileOutputStream o = new FileOutputStream(new File(out, name + ".png"))) {
                lo.compress(Bitmap.CompressFormat.PNG, 100, o);
            }
        }
    }
}
