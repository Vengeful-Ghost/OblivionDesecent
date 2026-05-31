package com.example.obliviondescent;

/**
 * Represents a stack of consumable items in the player's inventory.
 * Holds one item type and a quantity count.
 */
public class ConsumableStack {
    /** The consumable item in this stack. */
    public ShopItem item;
    /** Current quantity of copies. */
    public int quantity;

    /**
     * Creates a new consumable stack.
     * @param item the consumable item
     * @param quantity initial quantity
     */
    public ConsumableStack(ShopItem item, int quantity) {
        this.item = item;
        this.quantity = quantity;
    }

    /** Increments the quantity by 1. */
    public void increment() { quantity++; }

    /**
     * Decrements the quantity by 1.
     * @return true if the stack is now empty (quantity zero or below)
     */
    public boolean decrement() {
        quantity--;
        return quantity <= 0;
    }
}