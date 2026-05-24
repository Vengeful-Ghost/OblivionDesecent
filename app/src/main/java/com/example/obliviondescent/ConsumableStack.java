package com.example.obliviondescent;

public class ConsumableStack {
    public ShopItem item;
    public int quantity;

    public ConsumableStack(ShopItem item, int quantity) {
        this.item = item;
        this.quantity = quantity;
    }

    public void increment() { quantity++; }

    public boolean decrement() {
        quantity--;
        return quantity <= 0;
    }
}