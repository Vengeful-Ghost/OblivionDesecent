package com.example.obliviondescent;

/**
 * מייצגת ערמת פריטים מתכלים בתיק השחקן.
 * שומרת על פריט אחד ומספר העותקים שלו.
 */
public class ConsumableStack {
    /** הפריט המתכלה בערמה זו. */
    public ShopItem item;
    /** כמות העותקים הנוכחית. */
    public int quantity;

    /**
     * יוצר ערמת פריטים מתכלים חדשה.
     * @param item הפריט המתכלה
     * @param quantity כמות התחלתית
     */
    public ConsumableStack(ShopItem item, int quantity) {
        this.item = item;
        this.quantity = quantity;
    }

    /** מגדילה את הכמות ב-1. */
    public void increment() { quantity++; }

    /**
     * מפחיתה את הכמות ב-1.
     * @return true אם הערמה ריקה (כמות אפס או פחות)
     */
    public boolean decrement() {
        quantity--;
        return quantity <= 0;
    }
}