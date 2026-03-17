package com.ryan.dungeoncrawler;

public class DifficultyStats {

    private final double mobHealth;
    private final double mobDamage;
    private final int mobCount;

    public DifficultyStats(double mobHealth, double mobDamage, int mobCount) {
        this.mobHealth = mobHealth;
        this.mobDamage = mobDamage;
        this.mobCount = mobCount;
    }

    public double getMobHealth() { return mobHealth; }
    public double getMobDamage() { return mobDamage; }
    public int getMobCount() { return mobCount; }
}