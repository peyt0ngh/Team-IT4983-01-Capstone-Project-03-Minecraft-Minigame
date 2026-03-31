package your.plugin.generation;

import your.plugin.mobs.RoomEncounter;
import your.plugin.mobs.RoomCategory;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.util.*;

/**
 * Generates a full dungeon layout per stage.
 */
public class DungeonGenerator {

    private final RoomPlacer roomPlacer;
    private final Plugin plugin;
    private final Random random = new Random();

    public DungeonGenerator(RoomPlacer roomPlacer, Plugin plugin) {
        this.roomPlacer = roomPlacer;
        this.plugin = plugin;
    }

    /**
     * Generates the dungeon for a given stage.
     *
     * @param stage Stage number (1-3)
     * @return list of RoomEncounter objects for the stage
     */
    public List<RoomEncounter> generateDungeon(int stage) {
        int gridSize = 5 + stage + 1; // stage 1 = 6x6, etc.
        List<RoomEncounter> rooms = new ArrayList<>();

        // Place rooms
        roomPlacer.placeRoomsInGrid(gridSize, stage);

        // Build RoomEncounter objects (center locations)
        for (int x = 0; x < gridSize; x++) {
            for (int z = 0; z < gridSize; z++) {
                Location loc = new Location(plugin.getServer().getWorld("dungeon"), x * 16 + 8, 65, z * 16 + 8);
                RoomCategory category = randomRoomCategory(x, z, gridSize);
                rooms.add(new RoomEncounter("room_" + x + "_" + z, loc, category));
            }
        }

        // Assign entrance/exit on opposite sides
        rooms.get(0).setCleared(false); // entrance
        rooms.get(rooms.size() - 1).setCleared(false); // exit

        return rooms;
    }

    private RoomCategory randomRoomCategory(int x, int z, int gridSize) {
        if ((x == 0 && z == 0) || (x == gridSize - 1 && z == gridSize - 1)) return RoomCategory.BOSS;
        int roll = random.nextInt(100);
        if (roll < 10) return RoomCategory.SMALL;
        if (roll < 70) return RoomCategory.NORMAL;
        return RoomCategory.LARGE;
    }
}