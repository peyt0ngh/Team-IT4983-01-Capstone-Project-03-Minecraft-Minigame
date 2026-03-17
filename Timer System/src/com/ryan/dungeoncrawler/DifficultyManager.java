package com.ryan.dungeoncrawler;

public class DifficultyManager {

    // Base stats for level 1
    private final double baseHealth = 10.0;
    private final double baseDamage = 2.0;
    private final int baseMobCount = 3;

    // Multipliers applied each level
    private final double healthMultiplier = 1.25;
    private final double damageMultiplier = 1.15;
    private final double mobCountMultiplier = 1.20;

    public DifficultyStats getStatsForLevel(int level) {

        double scaledHealth = baseHealth * Math.pow(healthMultiplier, level - 1);
        double scaledDamage = baseDamage * Math.pow(damageMultiplier, level - 1);
        int scaledMobCount = (int) Math.round(baseMobCount * Math.pow(mobCountMultiplier, level - 1));

        return new DifficultyStats(scaledHealth, scaledDamage, scaledMobCount);
    }
}
