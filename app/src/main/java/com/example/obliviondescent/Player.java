package com.example.obliviondescent;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;

/**
 * Represents the player character in the game.
 * Manages position, movement, weapon equipment and the death animation.
 * GameActivity updates the position every logic frame.
 */
public class Player {

    /** Player position in world space. */
    public float x = 1000f;
    public float y = 1000f;
    /** Player body radius used for collision detection. */
    public float radius = 50f;

    public float speed = 10f;
    private float facingAngle = 90f;
    private float aimAngle = 0f;
    private Weapon currentWeapon;

    private Paint paint;

    public long flashUntil = 0;           // red flash after taking a hit
    private boolean invulnerable = false; // drawn see-through while dashing or just hit
    private Paint outlinePaint, flashPaint, shadowPaint, highlightPaint;

    // Death animation
    private boolean isDying = false;
    private long deathStartTime = 0;
    private static final long PLAYER_DEATH_DURATION = 1000; // ms
    private Paint deathPaint;

    // Spin special: the blade sweeps a full turn around the body
    private static final long SPIN_TIME = 380;
    private volatile long spinStart = 0;
    private float spinFrom, spinReach;
    private int spinColor;
    private final Paint spinPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.RectF spinRect = new android.graphics.RectF();

    // Melee swing: the weapon sweeps across the aim (or thrusts along it), alternating sides each swing
    private volatile long swingStart = 0;
    private long swingTime = 160;
    private float swingFrom, swingTo, swingCenter;
    private boolean thrust, swingHeavy, swingFlip;
    private static final long RECOVER_TIME = 140;
    // Heavy attack charge, 0..1; the weapon is drawn back while it builds
    private volatile float charge = 0f;
    private final Paint chargePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private volatile boolean moving = false;

    // Pixel art: the sprite's feet sit this far below the centre (in radii), on the contact shadow
    private static final float FEET = 0.78f;
    private boolean faceLeft = false; // render thread only
    private int way = 0;              // which of eight ways the sprite faces (Sprites.facing); render thread only

    // Round shading: light from the top left, shadow on the far side, so the body reads as a ball
    static final RadialGradient SHADE = new RadialGradient(-0.3f, -0.35f, 1.35f,
            new int[]{0x50FFFFFF, 0x00FFFFFF, 0x78000000}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
    private final Paint shadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix shadeMatrix = new Matrix();

    public Player() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.rgb(70, 120, 235));
        paint.setStyle(Paint.Style.FILL);

        outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        outlinePaint.setColor(Color.argb(160, 10, 20, 60));
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(4f);

        flashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        flashPaint.setColor(Color.argb(170, 255, 40, 40));

        shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setColor(Color.argb(90, 0, 0, 0));

        highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        highlightPaint.setColor(Color.argb(110, 255, 255, 255));

        deathPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        deathPaint.setColor(Color.WHITE);
        deathPaint.setStyle(Paint.Style.FILL);

        spinPaint.setStyle(Paint.Style.STROKE);
        spinPaint.setStrokeCap(Paint.Cap.ROUND);
        chargePaint.setStyle(Paint.Style.STROKE);
        shadePaint.setShader(SHADE);
    }

    /**
     * Animates a melee swing: the weapon sweeps through the arc around the aim, or thrusts straight
     * along it. Consecutive swings come from alternate sides.
     */
    public void startSwing(float angle, float arc, boolean thrust, long time, boolean heavy) {
        swingFlip = !swingFlip;
        float side = swingFlip ? 1f : -1f;
        swingCenter = angle;
        swingFrom = angle - arc * side;
        swingTo = angle + arc * side;
        this.thrust = thrust;
        swingHeavy = heavy;
        swingTime = time;
        swingStart = System.currentTimeMillis();
    }

    public void setCharge(float charge) { this.charge = charge; }

    /**
     * Starts the spin animation: the weapon whips a full turn around the body.
     * @param reach distance the spin hits, from the player's centre
     * @param color colour of the blade trail
     */
    public void startSpin(float reach, int color) {
        spinFrom = aimAngle;
        spinReach = reach;
        spinColor = color;
        spinStart = System.currentTimeMillis();
    }

    /**
     * Equips a new weapon.
     * @param weapon the weapon to equip
     */
    public void equipWeapon(Weapon weapon) {
        this.currentWeapon = weapon;
    }

    /** Returns current weapon; defaults to Fists if none equipped. */
    public Weapon getWeapon() {
        if (currentWeapon == null)
            currentWeapon = new Weapon("Fists", 5, 20f, 250, Weapon.Rarity.COMMON, Color.WHITE, 0.02f);
        return currentWeapon;
    }

    /**
     * Updates the player's position based on joystick input.
     * @param joyDX normalised horizontal direction (-1 to 1)
     * @param joyDY normalised vertical direction (-1 to 1)
     */
    public void update(float joyDX, float joyDY) {
        moving = joyDX != 0 || joyDY != 0;
        if (moving) {
            x += joyDX * speed;
            y += joyDY * speed;
            facingAngle = (float) Math.toDegrees(Math.atan2(joyDY, joyDX));
        }
    }

    /**
     * Called by GameActivity when health hits zero.
     * GameActivity waits PLAYER_DEATH_DURATION ms before
     * showing the game over dialog so the animation plays first.
     */
    public void startDeathAnimation() {
        isDying = true;
        deathStartTime = System.currentTimeMillis();
    }

    public boolean isDying() { return isDying; }
    public long getDeathDuration() { return PLAYER_DEATH_DURATION; }

    /**
     * Draws the player on the canvas, including the death animation when active.
     * @param canvas the canvas to draw on
     * @param camera game camera used to translate world position to screen position
     */
    public void draw(Canvas canvas, GameCamera camera) {
        float drawX = x - camera.getOffsetX();
        float drawY = y - camera.getOffsetY();

        if (isDying) {
            // The body flashes white and collapses while GameEffects.poof() scatters it
            float progress = Math.min(1f, (System.currentTimeMillis() - deathStartTime) / 220f);
            Sprites.Anim still = Sprites.get("player.idle", way);
            if (progress < 1f && still != null) {
                float feet = drawY + radius * FEET;
                canvas.save();
                canvas.scale(1f + progress * 0.3f, 1f - progress * 0.7f, drawX, feet);
                Sprites.flash(canvas, still, 0, drawX, feet, faceLeft, 1f, Color.WHITE, (int) (255 * (1f - progress)));
                canvas.restore();
            } else if (progress < 1f) {
                deathPaint.setAlpha((int) (255 * (1f - progress)));
                canvas.drawCircle(drawX, drawY, radius * (1f - progress * 0.7f), deathPaint);
            }
            return; // Skip normal draw
        }

        Weapon weapon = getWeapon();
        long now = System.currentTimeMillis();
        long sinceSpin = now - spinStart;
        boolean spinning = sinceSpin < SPIN_TIME;
        float angle = spinning ? spinFrom : weaponAngle(now);
        if (spinning) {
            float t = (float) sinceSpin / SPIN_TIME;
            float sweep = (1f - (1f - t) * (1f - t)) * 400f; // eases out, a little over one turn
            angle = spinFrom + sweep;
            drawSpin(canvas, drawX, drawY, angle, sweep, t);
        }
        int alpha = invulnerable ? 130 : 255;

        // Contact shadow on the floor; the body bobs above it while walking
        canvas.drawOval(drawX - radius * 0.9f, drawY + radius * 0.5f, drawX + radius * 0.9f, drawY + radius * 1.05f,
                shadowPaint);
        // Faces where it aims (or walks), one of eight ways: five drawn views, the left-facing three mirrored
        double look = Math.toRadians(aimAngle);
        way = Sprites.facing(way, (float) Math.cos(look), (float) Math.sin(look));
        faceLeft = Sprites.mirrored(way);
        Sprites.Anim body = Sprites.get(moving ? "player.walk" : "player.idle", way);
        if (moving && body == null) drawY -= Math.abs((float) Math.sin(now / 95.0)) * 5f; // the sprite walks by itself

        float length = weaponLength(weapon);
        long sinceSwing = now - swingStart;
        float extend = !spinning && thrust && sinceSwing < swingTime
                ? (float) Math.sin(Math.PI * sinceSwing / swingTime) * 30f : 0f;
        if (!spinning && !thrust && sinceSwing < swingTime) drawSwingTrail(canvas, drawX, drawY, angle, length, sinceSwing);

        // A weapon pointing up the screen is behind the body, otherwise in front of it
        boolean behind = Math.sin(Math.toRadians(angle)) < -0.3;
        if (behind) drawWeapon(canvas, weapon, drawX, drawY, angle, length, extend, alpha);

        boolean squash = now < flashUntil;
        if (body != null) {
            // Pixel art: squashes and flashes red on a hit
            float feet = drawY + radius * FEET;
            int frame = body.at(now);
            if (squash) {
                canvas.save();
                canvas.scale(1.12f, 0.88f, drawX, feet);
            }
            Sprites.draw(canvas, body, frame, drawX, feet, faceLeft, paint.getColor(), alpha);
            if (squash) {
                Sprites.flash(canvas, body, frame, drawX, feet, faceLeft, 1f, flashPaint.getColor(), 170);
                canvas.restore();
            }
        } else {
            drawBall(canvas, drawX, drawY, alpha, squash);
        }

        if (!behind) drawWeapon(canvas, weapon, drawX, drawY, angle, length, extend, alpha);

        // Charging a heavy attack: a ring tightens round the body, then pulses gold when full
        float c = charge;
        if (c > 0f) {
            boolean full = c >= 1f;
            float pulse = full ? 0.75f + 0.25f * (float) Math.sin(now / 55.0) : 1f;
            chargePaint.setColor(full ? Color.rgb(255, 214, 90) : Color.rgb(255, 150, 60));
            chargePaint.setAlpha((int) ((90 + 150 * c) * pulse));
            chargePaint.setStrokeWidth(4f + 5f * c);
            canvas.drawCircle(drawX, drawY, radius + 12f + 26f * (1f - c), chargePaint);
        }
    }

    /** The shaded ball the body was before the pixel art, kept for when the sprite pack is missing. */
    private void drawBall(Canvas canvas, float drawX, float drawY, int alpha, boolean squash) {
        if (squash) {
            canvas.save();
            canvas.scale(1.12f, 0.88f, drawX, drawY + radius);
        }
        paint.setAlpha(alpha);
        canvas.drawCircle(drawX, drawY, radius, paint);
        shadeMatrix.setScale(radius, radius);
        shadeMatrix.postTranslate(drawX, drawY);
        SHADE.setLocalMatrix(shadeMatrix);
        shadePaint.setAlpha(alpha);
        canvas.drawCircle(drawX, drawY, radius, shadePaint);
        canvas.drawCircle(drawX - radius * 0.3f, drawY - radius * 0.35f, radius * 0.22f, highlightPaint);
        canvas.drawCircle(drawX, drawY, radius, outlinePaint);
        if (squash) {
            canvas.drawCircle(drawX, drawY, radius, flashPaint);
            canvas.restore();
        }
    }

    /** Where the weapon points: through a swing, recovering from it, or resting in guard (drawn back while charging). */
    private float weaponAngle(long now) {
        long since = now - swingStart;
        if (since < swingTime) {
            if (thrust) return swingCenter;
            float t = (float) since / swingTime;
            t = 1f - (1f - t) * (1f - t) * (1f - t);
            return swingFrom + (swingTo - swingFrom) * t;
        }
        float rest = aimAngle - 120f * charge; // points straight where the player faces
        if (!thrust && since < swingTime + RECOVER_TIME) {
            float t = (float) (since - swingTime) / RECOVER_TIME;
            float delta = ((rest - swingTo) % 360f + 540f) % 360f - 180f;
            return swingTo + delta * t;
        }
        return rest;
    }

    /** Drawn length of a weapon from the hand; melee weapons grow with their reach. */
    private static float weaponLength(Weapon w) {
        switch (w.kind()) {
            case FIST:  return 24f;
            case STAFF: return 64f;
            case BOW:   return 66f;
            case SPEAR: return Math.max(80f, Math.min(170f, w.range * w.kind().reach + 20f));
            default:    return Math.max(44f, Math.min(140f, w.range * 0.9f + 22f));
        }
    }

    private void drawWeapon(Canvas canvas, Weapon weapon, float cx, float cy, float angle, float length,
                            float extend, int alpha) {
        double rad = Math.toRadians(angle);
        float hand = radius * 0.7f + extend;
        Weapon.draw(canvas, weapon.kind(), weapon.color, cx + (float) Math.cos(rad) * hand,
                cy + (float) Math.sin(rad) * hand, angle, length, alpha);
    }

    /** A fading arc behind the blade's tip as it sweeps; heavy swings leave a thick gold one. */
    private void drawSwingTrail(Canvas canvas, float cx, float cy, float angle, float length, long since) {
        float t = (float) since / swingTime;
        float r = radius * 0.7f + length;
        float seg = (angle - swingFrom) / 3f;
        spinRect.set(cx - r, cy - r, cx + r, cy + r);
        spinPaint.setColor(swingHeavy ? Color.rgb(255, 210, 90) : trailColor);
        for (int i = 0; i < 3; i++) {
            spinPaint.setAlpha((int) ((1f - t) * (swingHeavy ? 200 : 130) * (1f - i * 0.3f)));
            spinPaint.setStrokeWidth((swingHeavy ? 24f : 14f) - i * 4f);
            canvas.drawArc(spinRect, angle - seg * (i + 1), seg, false, spinPaint);
        }
    }

    /** Glowing blade out to the spin's reach, with a fading trail behind it. */
    private void drawSpin(Canvas canvas, float cx, float cy, float angle, float sweep, float t) {
        float fade = 1f - t;
        spinRect.set(cx - spinReach, cy - spinReach, cx + spinReach, cy + spinReach);
        float trail = Math.min(sweep, 170f) / 5f;
        spinPaint.setColor(spinColor);
        for (int i = 0; i < 5; i++) {
            spinPaint.setAlpha((int) (fade * (210 - i * 40)));
            spinPaint.setStrokeWidth(30f - i * 5f);
            canvas.drawArc(spinRect, angle - trail * (i + 1), trail, false, spinPaint);
        }
        double rad = Math.toRadians(angle);
        float cos = (float) Math.cos(rad), sin = (float) Math.sin(rad);
        spinPaint.setAlpha((int) (fade * 255));
        spinPaint.setStrokeWidth(10f);
        canvas.drawLine(cx + cos * radius, cy + sin * radius,
                cx + cos * spinReach, cy + sin * spinReach, spinPaint);
    }

    public void setInvulnerable(boolean invulnerable) { this.invulnerable = invulnerable; }
    public volatile int trailColor = Color.WHITE; // wardrobe swing trail
    public void setColor(int color) { paint.setColor(color); }
    public int getColor() { return paint.getColor(); }
    public float getX() { return x; }
    public float getY() { return y; }

    public float getFacingAngle() { return facingAngle; }

    public void setAimAngle(float angle) { this.aimAngle = angle; }

    /**
     * Resets the death animation state so it can be triggered again.
     */
    public void resetDeathAnimation() {
        isDying = false;
        deathStartTime = 0;
    }
}