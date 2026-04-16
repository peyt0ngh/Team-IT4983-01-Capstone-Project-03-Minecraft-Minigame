package com.ryan.dungeoncrawler.mobs;

import org.bukkit.entity.EntityType;

import java.util.List;
import java.util.Random;

/**
 * Defines the three dungeon levels, each containing three possible environments.
 * One environment is chosen randomly when a level starts.
 *
 * Level 1 (240 s) — Stronghold | Desert Temple | Trial Chamber
 * Level 2 (360 s) — Ancient City | Nether Fortress | Woodland Mansion
 * Level 3 (480 s) — End City | Nether Bastion | Ocean Monument
 */
public final class EncounterConfig {

    private EncounterConfig() {}

    /** An immutable pair of entity types representing one environment's mob roster. */
    public record Environment(String name, EntityType primary, EntityType secondary) {}

    // ── Level 1 environments ──────────────────────────────────────────────────

    public static final Environment STRONGHOLD = new Environment(
            "Stronghold", EntityType.SPIDER, EntityType.CREEPER);

    public static final Environment DESERT_TEMPLE = new Environment(
            "Desert Temple", EntityType.HUSK, EntityType.ZOMBIE); // Parched ≈ Zombie (no direct enum)

    public static final Environment TRIAL_CHAMBER = new Environment(
            "Trial Chamber", EntityType.BREEZE, EntityType.WITCH);

    // ── Level 2 environments ──────────────────────────────────────────────────

    public static final Environment ANCIENT_CITY = new Environment(
            "Ancient City", EntityType.CAVE_SPIDER, EntityType.SLIME);

    public static final Environment NETHER_FORTRESS = new Environment(
            "Nether Fortress", EntityType.WITHER_SKELETON, EntityType.BLAZE);

    public static final Environment WOODLAND_MANSION = new Environment(
            "Woodland Mansion", EntityType.PILLAGER, EntityType.VINDICATOR);

    // ── Level 3 environments ──────────────────────────────────────────────────

    public static final Environment END_CITY = new Environment(
            "End City", EntityType.ENDERMAN, EntityType.ENDERMITE);

    public static final Environment NETHER_BASTION = new Environment(
            "Nether Bastion", EntityType.PIGLIN_BRUTE, EntityType.MAGMA_CUBE);

    public static final Environment OCEAN_MONUMENT = new Environment(
            "Ocean Monument", EntityType.DROWNED, EntityType.BOGGED);

    // ── Environment pools per level ───────────────────────────────────────────

    private static final List<Environment> LEVEL_1_ENVS =
            List.of(STRONGHOLD, DESERT_TEMPLE, TRIAL_CHAMBER);

    private static final List<Environment> LEVEL_2_ENVS =
            List.of(ANCIENT_CITY, NETHER_FORTRESS, WOODLAND_MANSION);

    private static final List<Environment> LEVEL_3_ENVS =
            List.of(END_CITY, NETHER_BASTION, OCEAN_MONUMENT);

    // ── Seconds per level ─────────────────────────────────────────────────────

    /** Seconds allocated to level 1 (4 minutes). */
    public static final int SECONDS_LEVEL_1 = 240;

    /** Seconds allocated to level 2 (6 minutes). */
    public static final int SECONDS_LEVEL_2 = 360;

    /** Seconds allocated to level 3 (8 minutes). */
    public static final int SECONDS_LEVEL_3 = 480;

    // ── Lookup helpers ────────────────────────────────────────────────────────

    private static final Random RNG = new Random();

    /**
     * Pick a random environment for the given 1-based dungeon level.
     *
     * @param level 1, 2, or 3
     * @return a randomly chosen {@link Environment} for that level
     * @throws IllegalArgumentException if level is outside [1, 3]
     */
    public static Environment randomForLevel(int level) {
        List<Environment> pool = switch (level) {
            case 1 -> LEVEL_1_ENVS;
            case 2 -> LEVEL_2_ENVS;
            case 3 -> LEVEL_3_ENVS;
            default -> throw new IllegalArgumentException("No environments defined for level " + level);
        };
        return pool.get(RNG.nextInt(pool.size()));
    }

    /**
     * Return the pre-configured seconds for a given 1-based dungeon level.
     *
     * @param level 1, 2, or 3
     */
    public static int secondsForLevel(int level) {
        return switch (level) {
            case 1 -> SECONDS_LEVEL_1;
            case 2 -> SECONDS_LEVEL_2;
            case 3 -> SECONDS_LEVEL_3;
            default -> SECONDS_LEVEL_1; // safe fallback
        };
    }
}
