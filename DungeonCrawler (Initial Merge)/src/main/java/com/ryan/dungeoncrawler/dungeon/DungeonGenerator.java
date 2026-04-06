package com.ryan.dungeoncrawler.dungeon;

import com.ryan.dungeoncrawler.util.SchematicUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class DungeonGenerator {

    private final JavaPlugin plugin;
    private final Random random = new Random();

    private static final int ROOM_SPACING = 16;
    private static final int ROOM_Y_LEVEL = 64;

    public DungeonGenerator(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Generates a grid of dungeon rooms using stronghold schematics.
     *
     * @param size grid size (e.g., 6 = 6x6)
     * @return list of generated rooms
     */
    public List<Room> generate(int size) {

        List<Room> rooms = new ArrayList<>();

        // Get world (main world)
        World world = Bukkit.getWorlds().get(0);

        // Load schematics folder
        File folder = new File(plugin.getDataFolder(), "rooms/Stronghold");

        if (!folder.exists()) {
            plugin.getLogger().warning("Stronghold room folder does not exist: " + folder.getPath());
            return rooms;
        }

        File[] schematics = folder.listFiles((dir, name) -> name.endsWith(".schem"));

        if (schematics == null || schematics.length == 0) {
            plugin.getLogger().warning("No schematics found in: " + folder.getPath());
            return rooms;
        }

        plugin.getLogger().info("Generating dungeon with " + schematics.length + " schematics...");

        // Generate grid
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {

                // Pick random schematic
                File chosen = schematics[random.nextInt(schematics.length)];

                // Calculate paste location
                int worldX = x * ROOM_SPACING;
                int worldZ = z * ROOM_SPACING;

                Location pasteLoc = new Location(world, worldX, ROOM_Y_LEVEL, worldZ);

                // Paste schematic
                SchematicUtil.pasteSchematic(chosen, pasteLoc);

                // Create and register room
                Room room = new Room(pasteLoc, ROOM_SPACING, ROOM_SPACING);
                RoomRegistry.register(room);

                rooms.add(room);
            }
        }

        plugin.getLogger().info("Dungeon generation complete. Rooms created: " + rooms.size());

        return rooms;
    }
}
