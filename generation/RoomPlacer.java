package your.plugin.generation;

import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import java.util.List;

/**
 * Handles placement of rooms within the dungeon grid.
 */
public class RoomPlacer {

    private final SchematicManager schematicManager;
    private final Plugin plugin;

    public RoomPlacer(SchematicManager schematicManager, Plugin plugin) {
        this.schematicManager = schematicManager;
        this.plugin = plugin;
    }

    /**
     * Places a room at a grid position with the selected schematic.
     *
     * @param x       grid X
     * @param z       grid Z
     * @param stage   dungeon stage
     */
    public void placeRoom(int x, int z, int stage) {
        String schematicName = schematicManager.selectSchematic(stage);
        if (schematicName == null) return;

        // Convert grid position to world location
        Location worldLoc = new Location(plugin.getServer().getWorld("dungeon"), x * 16, 64, z * 16);

        // Paste schematic
        schematicManager.pasteSchematic(schematicName, worldLoc, plugin);
    }

    /**
     * Places multiple rooms in a grid.
     */
    public void placeRoomsInGrid(int gridSize, int stage) {
        for (int x = 0; x < gridSize; x++) {
            for (int z = 0; z < gridSize; z++) {
                placeRoom(x, z, stage);
            }
        }
    }
}