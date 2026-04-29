package com.ryan.dungeoncrawler.dungeon;

import com.ryan.dungeoncrawler.util.SchematicUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

/**
 * DungeonGenerator
 *
 * Theme-ready procedural generator.
 *
 * Supports:
 * - 3 stages
 * - Random environment per stage
 * - Uses room TYPES, not hardcoded schematics
 * - SW-origin schematics
 * - 16x16 rooms
 * - Auto player teleport to START room
 */
public class DungeonGenerator {

    private final JavaPlugin plugin;
    private final Random random = new Random();

    private static final int ROOM_SIZE = 16;
    private static final int ROOM_Y = 100;

    private Location stageSpawnLocation;
    private String currentTheme = "stronghold";

    public DungeonGenerator(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /* ====================================================== */
    /* PUBLIC GENERATION                                      */
    /* ====================================================== */

    public void generateStage(int stage) {

        int gridSize = getGridSize(stage);

        currentTheme = chooseTheme(stage);

        plugin.getLogger().info("Generating Stage "
                + stage + " Theme: " + currentTheme);

        World world = Bukkit.getWorlds().get(0);

        int baseX = 0;
        int baseZ = 0;

        stageSpawnLocation = null;

        DungeonLayoutGenerator layoutGen = new DungeonLayoutGenerator();
        List<DungeonLayoutGenerator.LayoutRoom> rooms =
                layoutGen.generate(gridSize);

        for (DungeonLayoutGenerator.LayoutRoom room : rooms) {

            File schematic =
                    getRandomSchematic(stage, currentTheme, room.type);

            if (schematic == null) {
                plugin.getLogger().warning(
                        "Missing schematic for "
                                + room.type + " in "
                                + currentTheme);
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

            SchematicUtil.pasteSchematic(
                    schematic,
                    pasteLoc,
                    rotation
            );

            if (room.type == DungeonLayoutGenerator.RoomType.START) {
                stageSpawnLocation = getRoomCenter(pasteLoc);
            }
        }

        if (stageSpawnLocation == null) {
            stageSpawnLocation =
                    new Location(world, 8.5, ROOM_Y + 1, -8.5);
        }

        teleportAllPlayers();
    }

    /* ====================================================== */
    /* THEMES                                                 */
    /* ====================================================== */

    private String chooseTheme(int stage) {

        List<String> themes = switch (stage) {

            case 1 -> Arrays.asList(
                    "stronghold",
                    "trial_chamber",
                    "woodland_mansion"
            );

            case 2 -> Arrays.asList(
                    "ancient_city",
                    "nether_fortress"
            );

            case 3 -> Arrays.asList(
                    "end_city",
                    "bastion_remnant",
                    "ocean_monument"
            );

            default -> Collections.singletonList("stronghold");
        };

        return themes.get(random.nextInt(themes.size()));
    }

    private int getGridSize(int stage) {
        return switch (stage) {
            case 1 -> 6;
            case 2 -> 7;
            case 3 -> 8;
            default -> 6;
        };
    }

    /* ====================================================== */
    /* SCHEMATIC LOADING                                      */
    /* ====================================================== */

    private File getRandomSchematic(int stage,
                                    String theme,
                                    DungeonLayoutGenerator.RoomType type) {

        String roomFolder = mapRoomType(type);

        File dir = new File(
                plugin.getDataFolder(),
                "rooms/stage" + stage + "/"
                        + theme + "/"
                        + roomFolder
        );

        if (!dir.exists()) return null;

        File[] files = dir.listFiles((d, name) ->
                name.toLowerCase().endsWith(".schem"));

        if (files == null || files.length == 0) return null;

        return files[random.nextInt(files.length)];
    }

    /**
     * Converts logical type to folder name.
     */
    private String mapRoomType(
            DungeonLayoutGenerator.RoomType type) {

        return switch (type) {

            case START -> "start";
            case EXIT -> "exit";
            case END -> "end";
            case HALL -> "hall";
            case CORNER -> "corner";
            case BRANCH -> "branch";
            case INTERSECTION -> "intersection";
        };
    }

    /* ====================================================== */
    /* ROOM ROTATION                                          */
    /* ====================================================== */

    private int getRotation(
            DungeonLayoutGenerator.LayoutRoom room) {

        Set<DungeonLayoutGenerator.Direction> c =
                room.connections;

        switch (room.type) {

            case START:
            case EXIT:
            case END:
                if (c.contains(
                        DungeonLayoutGenerator.Direction.NORTH))
                    return 0;

                if (c.contains(
                        DungeonLayoutGenerator.Direction.EAST))
                    return 90;

                if (c.contains(
                        DungeonLayoutGenerator.Direction.SOUTH))
                    return 180;

                if (c.contains(
                        DungeonLayoutGenerator.Direction.WEST))
                    return 270;

                return 0;

            case HALL:

                boolean ns =
                        c.contains(
                                DungeonLayoutGenerator.Direction.NORTH)
                                &&
                                c.contains(
                                        DungeonLayoutGenerator.Direction.SOUTH);

                return ns ? 0 : 90;

            case CORNER:

                if (has(c, "NORTH", "EAST")) return 0;
                if (has(c, "EAST", "SOUTH")) return 90;
                if (has(c, "SOUTH", "WEST")) return 180;
                return 270;

            case BRANCH:

                if (!c.contains(
                        DungeonLayoutGenerator.Direction.SOUTH))
                    return 0;

                if (!c.contains(
                        DungeonLayoutGenerator.Direction.WEST))
                    return 90;

                if (!c.contains(
                        DungeonLayoutGenerator.Direction.NORTH))
                    return 180;

                return 270;

            case INTERSECTION:
            default:
                return 0;
        }
    }

    private boolean has(
            Set<DungeonLayoutGenerator.Direction> set,
            String a,
            String b) {

        return set.contains(
                DungeonLayoutGenerator.Direction.valueOf(a))
                &&
                set.contains(
                        DungeonLayoutGenerator.Direction.valueOf(b));
    }

    /* ====================================================== */
    /* PASTE LOCATIONS                                        */
    /* ====================================================== */

    private Location getPasteLocation(
            World world,
            int baseX,
            int baseZ,
            int gridX,
            int gridZ,
            int rotation) {

        int x = baseX + (gridX * ROOM_SIZE);
        int z = baseZ - (gridZ * ROOM_SIZE);

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

    private Location getRoomCenter(Location corner) {

        Location loc = corner.clone().add(
                8.5,
                1,
                -8.5
        );

        loc.setYaw(180f);

        return loc;
    }

    /* ====================================================== */
    /* PLAYER TELEPORT                                        */
    /* ====================================================== */

    private void teleportAllPlayers() {

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.teleport(stageSpawnLocation);
        }
    }

    public Location getStageSpawnLocation() {
        return stageSpawnLocation;
    }

    public String getCurrentTheme() {
        return currentTheme;
    }
}
