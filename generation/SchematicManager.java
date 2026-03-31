package your.plugin.generation;

import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.util.*;

/**
 * Manages schematic selection for stages and room types.
 */
public class SchematicManager {

    private final Random random = new Random();

    // Preloaded schematics by stage
    private final Map<Integer, List<String>> stageSchematics = new HashMap<>();

    public SchematicManager() {
        // Stage 1
        stageSchematics.put(1, Arrays.asList("Stronghold", "DesertTemple", "TrialChamber"));
        // Stage 2
        stageSchematics.put(2, Arrays.asList("AncientCity", "NetherFortress", "WoodlandMansion"));
        // Stage 3
        stageSchematics.put(3, Arrays.asList("EndCity", "NetherBastionRemnant", "OceanMonument"));
    }

    /**
     * Returns a randomly selected schematic name for a stage.
     */
    public String selectSchematic(int stage) {
        List<String> list = stageSchematics.getOrDefault(stage, Collections.emptyList());
        if (list.isEmpty()) return null;
        return list.get(random.nextInt(list.size()));
    }

    /**
     * Placeholder for schematic pasting.
     */
    public void pasteSchematic(String schematicName, Location loc, Plugin plugin) {
        // Integrate with your existing schematic loader/paster (e.g., WorldEdit API)
        // Example:
        // WorldEditSchematicLoader.load(schematicName).paste(loc);
    }
}