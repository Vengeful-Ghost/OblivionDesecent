package com.example.obliviondescent;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

/**
 * מייצגת ארגז אוצר שמופיע לאחר הריגת בוס.
 * השחקן מתקרב מספיק כדי לפתוח אותו.
 * פריט הפרס (שנקבע על ידי GameActivity) ניתן לשחקן בפתיחה.
 */
public class TreasureChest {

    public float x, y;
    public boolean opened = false;

    public static final float OPEN_RADIUS = 80f;

    private static final float WIDTH  = 60f;
    private static final float HEIGHT = 50f;

    // Reward item — set by GameActivity after construction
    public ShopItem rewardItem = null;
    public String   rewardItemName  = null;
    public int      rewardItemColor = Color.WHITE;

    private Paint bodyPaint;
    private Paint lidPaint;
    private Paint lockPaint;
    private Paint openedPaint;
    private Paint labelPaint;

    /**
     * יוצר ארגז אוצר חדש במיקום הנתון.
     * @param spawnX מיקום X בעולם המשחק
     * @param spawnY מיקום Y בעולם המשחק
     */
    public TreasureChest(float spawnX, float spawnY) {
        this.x = spawnX;
        this.y = spawnY;

        bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bodyPaint.setColor(Color.rgb(139, 90, 43));
        bodyPaint.setStyle(Paint.Style.FILL);

        lidPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        lidPaint.setColor(Color.rgb(160, 110, 60));
        lidPaint.setStyle(Paint.Style.FILL);

        lockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        lockPaint.setColor(Color.rgb(255, 215, 0));
        lockPaint.setStyle(Paint.Style.FILL);

        openedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        openedPaint.setColor(Color.rgb(60, 40, 20));
        openedPaint.setStyle(Paint.Style.FILL);

        labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(Color.WHITE);
        labelPaint.setTextSize(22f);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setFakeBoldText(true);
    }

    /**
     * מצייר את ארגז האוצר — סגור עם מנעול או פתוח עם שם הפרס.
     * @param canvas הקנבס לציור
     * @param camera מצלמת המשחק לחישוב מיקום מסך
     */
    public void draw(Canvas canvas, GameCamera camera) {
        float drawX = x - camera.getOffsetX();
        float drawY = y - camera.getOffsetY();

        float left   = drawX - WIDTH / 2f;
        float top    = drawY - HEIGHT / 2f;
        float right  = drawX + WIDTH / 2f;
        float bottom = drawY + HEIGHT / 2f;

        if (opened) {
            canvas.drawRoundRect(new RectF(left, top, right, bottom), 8f, 8f, openedPaint);
            if (rewardItemName != null) {
                labelPaint.setColor(rewardItemColor);
                labelPaint.setTextSize(18f);
                canvas.drawText(rewardItemName, drawX, top - 8f, labelPaint);
                labelPaint.setTextSize(22f);
            } else {
                labelPaint.setColor(Color.rgb(150, 150, 150));
                canvas.drawText("OPEN", drawX, top - 8f, labelPaint);
            }
        } else {
            canvas.drawRoundRect(new RectF(left, top + HEIGHT * 0.35f, right, bottom),
                    8f, 8f, bodyPaint);
            canvas.drawRoundRect(new RectF(left, top, right, top + HEIGHT * 0.45f),
                    8f, 8f, lidPaint);
            canvas.drawCircle(drawX, drawY, 8f, lockPaint);
            labelPaint.setColor(Color.rgb(255, 215, 0));
            canvas.drawText("!", drawX, top - 8f, labelPaint);
        }
    }
}
