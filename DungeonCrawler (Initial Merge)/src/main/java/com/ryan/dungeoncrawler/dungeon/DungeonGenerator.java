package com.ryan.dungeoncrawler.dungeon;

import com.ryan.dungeoncrawler.util.SchematicUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

/**
 * DungeonGenerator
 *
 * Fully corrected version for:
 * - SW-origin schematics
 * - 16x16 rooms
 * - Stage sizes: 6x6 / 7x7 / 8x8
 * - Path-first generation
 * - Proper rotation offsets
 *
 * Assumes:
 * Every schematic was copied from:
 *   Bottom South-West corner -> Top North-East corner
 *
 * Therefore pasted rooms naturally expand:
 *   +X (east)
 *   -Z (north)
 *
 * Folder structure:
 *
 * plugins/DungeonCrawler/rooms/stronghold/
 *   hall/
 *   corner/
 *   branch/
 *   intersection/
 *   start/
 *   end/
 */
public class DungeonGenerator {

    private final JavaPlugin plugin;
    private final Random random = new Random();

    private static final int ROOM_SIZE = 16;
    private static final int ROOM_Y = 100;

    public DungeonGenerator(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Generate dungeon for stage:
     * 1 = 6x6
     * 2 = 7x7
     * 3 = 8x8
     */
    public void generateStage(int stage) {

        int size = switch (stage) {
            case 1 -> 6;
            case 2 -> 7;
            case 3 -> 8;
            default -> 6;
        };

        World world = Bukkit.getWorlds().get(0);

        // Starting corner of dungeon in world
        int baseX = 0;
        int baseZ = 0;

        // Generate layout first
        DungeonLayoutGenerator layoutGen = new DungeonLayoutGenerator();
        List<DungeonLayoutGenerator.LayoutRoom> rooms = layoutGen.generate(size);

        for (DungeonLayoutGenerator.LayoutRoom room : rooms) {

            File schematic = getRandomSchematic(room.type);

            if (schematic == null) {
                plugin.getLogger().warning("Missing schematic for: " + room.type);
                continue;
            }

            int rotation = getRotation(room);

            Location pasteLoc = getPasteLocation(
                    world,
                    baseX,
                    baseZ,
                    room.x,
                    room.z,
                    rotation
            );

            SchematicUtil.pasteSchematic(schematic, pasteLoc, rotation);
        }

        plugin.getLogger().info("Dungeon generated for stage " + stage);
    }

    /**
     * Convert grid cell to world location.
     *
     * Because schematics are SW-origin:
     * x grows east
     * z grows north (-z)
     */
    private Location getPasteLocation(World world,
                                      int baseX,
                                      int baseZ,
                                      int gridX,
                                      int gridZ,
                                      int rotation) {

        int x = baseX + (gridX * ROOM_SIZE);
        int z = baseZ - (gridZ * ROOM_SIZE);

        // Rotation offsets for SW-origin schematics
        switch (rotation) {

            case 90 -> x += 15;

            case 180 -> {
                x += 15;
                z -= 15;
            }

            case 270 -> z -= 15;
        }

        return new Location(world, x, ROOM_Y, z);
    }

    /**
     * Determine rotation based on connections.
     */
    private int getRotation(DungeonLayoutGenerator.LayoutRoom room) {

        Set<DungeonLayoutGenerator.Direction> c = room.connections;

        switch (room.type) {

            case START:
            case END:
                if (c.contains(DungeonLayoutGenerator.Direction.NORTH)) return 0;
                if (c.contains(DungeonLayoutGenerator.Direction.EAST)) return 90;
                if (c.contains(DungeonLayoutGenerator.Direction.SOUTH)) return 180;
                if (c.contains(DungeonLayoutGenerator.Direction.WEST)) return 270;
                return 0;

            case HALL:
                if (c.contains(DungeonLayoutGenerator.Direction.NORTH)
                        && c.contains(DungeonLayoutGenerator.Direction.SOUTH))
                    return 0;

                return 90;

            case CORNER:
                if (has(c, "NORTH", "EAST")) return 0;
                if (has(c, "EAST", "SOUTH")) return 90;
                if (has(c, "SOUTH", "WEST")) return 180;
                return 270;

            case BRANCH:
                if (!c.contains(DungeonLayoutGenerator.Direction.SOUTH)) return 0;
                if (!c.contains(DungeonLayoutGenerator.Direction.WEST)) return 90;
                if (!c.contains(DungeonLayoutGenerator.Direction.NORTH)) return 180;
                return 270;

            case INTERSECTION:
            default:
                return 0;
        }
    }

    private boolean has(Set<DungeonLayoutGenerator.Direction> set,
                        String a,
                        String b) {

        return set.contains(DungeonLayoutGenerator.Direction.valueOf(a))
                && set.contains(DungeonLayoutGenerator.Direction.valueOf(b));
    }

    /**
     * Picks random schematic by room type.
     */
    private File getRandomSchematic(DungeonLayoutGenerator.RoomType type) {

        String folder = type.name().toLowerCase();

        File dir = new File(plugin.getDataFolder(),
                "rooms/stronghold/" + folder);

        if (!dir.exists()) return null;

        File[] files = dir.listFiles((d, name) ->
                name.toLowerCase().endsWith(".schem"));

        if (files == null || files.length == 0) return null;

        return files[random.nextInt(files.length)];
    }
}
