package com.ryan.dungeonexplorers;

/**
 * Computes difficulty-scaled mob stats for a given dungeon level.
 *
 * Base values (level 1):
 *   Health    10.0 hp   ×1.25 per level
 *   Damage     2.0      ×1.15 per level
 *   Mob count  3        ×1.20 per level (rounded)
 */
public class DifficultyManager {

    private final double baseHealth          = 10.0;
    private final double baseDamage          = 2.0;
    private final int    baseMobCount        = 3;

    private final double healthMultiplier    = 1.25;
    private final double damageMultiplier    = 1.15;
    private final double mobCountMultiplier  = 1.20;

    public DifficultyStats getStatsForLevel(int level) {
        double scaledHealth   = baseHealth   * Math.pow(healthMultiplier,   level - 1);
        double scaledDamage   = baseDamage   * Math.pow(damageMultiplier,   level - 1);
        int    scaledMobCount = (int) Math.round(baseMobCount * Math.pow(mobCountMultiplier, level - 1));
        return new DifficultyStats(scaledHealth, scaledDamage, scaledMobCount);
    }
}
