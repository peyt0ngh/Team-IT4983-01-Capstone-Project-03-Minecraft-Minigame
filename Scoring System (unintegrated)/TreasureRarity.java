package com.minigame.scorer.model;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/**
 * Represents a treasure rarity tier.
 * Each tier has a display name, chat colour, and point value.
 */
public enum TreasureRarity {

    COMMON("Common", NamedTextColor.GRAY, 10),
    UNCOMMON("Uncommon", NamedTextColor.GREEN, 20),
    RARE("Rare", NamedTextColor.BLUE, 30),
    SUPER_RARE("Super Rare", NamedTextColor.GOLD, 40);

    private final String displayName;
    private final TextColor color;
    private final int points;

    TreasureRarity(String displayName, TextColor color, int points) {
        this.displayName = displayName;
        this.color = color;
        this.points = points;
    }

    public String getDisplayName() { return displayName; }
    public TextColor getColor()    { return color; }
    public int getPoints()         { return points; }

    /**
     * Parse a rarity from a user-supplied string (case-insensitive).
     * Accepts "superrare", "super_rare", "super rare", etc.
     */
    public static TreasureRarity fromString(String input) {
        String normalised = input.trim().toLowerCase().replace(" ", "").replace("_", "");
        return switch (normalised) {
            case "common"    -> COMMON;
            case "uncommon"  -> UNCOMMON;
            case "rare"      -> RARE;
            case "superrare" -> SUPER_RARE;
            default          -> null;
        };
    }
}
