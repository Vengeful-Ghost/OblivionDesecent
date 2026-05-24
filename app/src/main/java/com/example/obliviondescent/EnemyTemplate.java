package com.example.obliviondescent;

public class EnemyTemplate {
    public String name;
    public String description;
    public int color;
    public float hpMultiplier;
    public float dmgMultiplier;

    public EnemyTemplate(String name, String description,
                         int color, float hpMultiplier, float dmgMultiplier) {
        this.name        = name;
        this.description = description;
        this.color       = color;
        this.hpMultiplier = hpMultiplier;
        this.dmgMultiplier = dmgMultiplier;
    }
}