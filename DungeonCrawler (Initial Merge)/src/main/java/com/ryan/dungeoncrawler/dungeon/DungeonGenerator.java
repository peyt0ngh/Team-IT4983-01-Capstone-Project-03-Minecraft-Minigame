package com.ryan.dungeoncrawler.dungeon;

import com.ryan.dungeoncrawler.util.SchematicUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * DungeonGenerator
 *
 * Supports:
 * - SW-origin schematics
 * - 16x16 rooms
 * - Stage sizes:
 *     Stage 1 = 6x6
 *     Stage 2 = 7x7
 *     Stage 3 = 8x8
 * - Path-first generation
 * - Room rotation
 * - Auto teleport to center of START room
 */
public class DungeonGenerator {

    private final JavaPlugin plugin;
    private final Random random = new Random();

    private static final int ROOM_SIZE = 16;
    private static final int ROOM_Y = 100;

    private Location stageSpawnLocation;

    public DungeonGenerator(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Generate dungeon for stage.
     */
    public void generateStage(int stage) {

        int size = switch (stage) {
            case 1 -> 6;
            case 2 -> 7;
            case 3 -> 8;
            default -> 6;
        };

        World world = Bukkit.getWorlds().get(0);

        // Base corner of dungeon
        int baseX = 0;
        int baseZ = 0;

        stageSpawnLocation = null;

        // Generate layout
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

            // Paste room
            SchematicUtil.pasteSchematic(schematic, pasteLoc, rotation);

            // Save spawn location from START room
            if (room.type == DungeonLayoutGenerator.RoomType.START) {
                stageSpawnLocation = getRoomCenter(pasteLoc);
            }
        }

        // Fallback spawn
        if (stageSpawnLocation == null) {
            stageSpawnLocation = new Location(world, 8.5, ROOM_Y + 1, -8.5);
        }

        // Teleport players
        teleportAllPlayers();

        plugin.getLogger().info("Dungeon generated for stage " + stage);
    }

    /**
     * Teleports all online players to stage spawn.
     */
    private void teleportAllPlayers() {

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.teleport(stageSpawnLocation);
        }
    }

    /**
     * Center of 16x16 SW-origin room.
     */
    private Location getRoomCenter(Location corner) {

        Location loc = corner.clone().add(
                8.5,
                1.0,
                -8.5
        );

        loc.setYaw(180f);
        loc.setPitch(0f);

        return loc;
    }

    /**
     * Grid location -> world location
     *
     * SW-origin paste:
     * +X east
     * -Z north
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
     * Determine room rotation from connections.
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
     * Picks schematic by room type.
     */
    private File getRandomSchematic(DungeonLayoutGenerator.RoomType type) {

        String folder = type.name().toLowerCase();

        File dir = new File(
                plugin.getDataFolder(),
                "rooms/stronghold/" + folder
        );

        if (!dir.exists()) return null;

        File[] files = dir.listFiles((d, name) ->
                name.toLowerCase().endsWith(".schem"));

        if (files == null || files.length == 0) return null;

        return files[random.nextInt(files.length)];
    }

    public Location getStageSpawnLocation() {
        return stageSpawnLocation;
    }
}
