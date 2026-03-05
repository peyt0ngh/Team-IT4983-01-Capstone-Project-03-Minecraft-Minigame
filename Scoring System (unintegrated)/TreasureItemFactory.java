package com.minigame.scorer.util;

import com.minigame.scorer.model.TreasureRarity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Creates treasure {@link ItemStack}s with custom metadata and
 * provides a method to identify/read rarity from an item.
 *
 * Treasures use a {@link org.bukkit.persistence.PersistentDataContainer} tag
 * to store their rarity key, making identification reliable across restarts.
 */
public class TreasureItemFactory {

    /** PDC key stored on every treasure item. */
    private static NamespacedKey RARITY_KEY;

    /**
     * Must be called once during plugin enable to register the NamespacedKey.
     */
    public static void init(JavaPlugin plugin) {
        RARITY_KEY = new NamespacedKey(plugin, "treasure_rarity");
    }

    /**
     * Create an {@link ItemStack} that represents a treasure of the given rarity.
     * The item has maxStackSize = 1 via PDC flag (non-stackable by design).
     */
    public static ItemStack createTreasureItem(TreasureRarity rarity) {
        // Choose a visually distinct material per rarity tier.
        Material mat = switch (rarity) {
            case COMMON     -> Material.SUNFLOWER;
            case UNCOMMON   -> Material.EMERALD;
            case RARE       -> Material.DIAMOND;
            case SUPER_RARE -> Material.NETHER_STAR;
        };

        ItemStack item = new ItemStack(mat, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // Display name coloured by rarity (remove italics added by default).
        meta.displayName(
            Component.text("✦ " + rarity.getDisplayName() + " Treasure")
                     .color(rarity.getColor())
                     .decoration(TextDecoration.ITALIC, false)
                     .decoration(TextDecoration.BOLD, true)
        );

        // Lore line.
        meta.lore(List.of(
            Component.text("Worth " + rarity.getPoints() + " points")
                     .color(rarity.getColor())
                     .decoration(TextDecoration.ITALIC, false),
            Component.text("Cannot be stacked.")
                     .color(net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY)
                     .decoration(TextDecoration.ITALIC, false)
        ));

        // Store rarity in PDC so we can read it back reliably.
        meta.getPersistentDataContainer()
            .set(RARITY_KEY, PersistentDataType.STRING, rarity.name());

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Returns the {@link TreasureRarity} stored in an item's PDC, or {@code null}
     * if the item is not a treasure.
     */
    public static TreasureRarity getRarityFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        String stored = meta.getPersistentDataContainer()
                            .get(RARITY_KEY, PersistentDataType.STRING);
        if (stored == null) return null;

        try {
            return TreasureRarity.valueOf(stored);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
