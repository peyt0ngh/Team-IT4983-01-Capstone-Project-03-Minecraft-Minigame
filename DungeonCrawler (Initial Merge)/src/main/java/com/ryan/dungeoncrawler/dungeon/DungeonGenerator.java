package com.ryan.dungeoncrawler.dungeon;

import com.ryan.dungeoncrawler.util.SchematicUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
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

    public List<Room> generate(int size) {
        List<Room> rooms = new ArrayList<>();
        Room[][] grid = new Room[size][size];

        World world = Bukkit.getWorlds().get(0);
        File folder = new File(plugin.getDataFolder(), "rooms/Stronghold");

        if (!folder.exists()) {
            plugin.getLogger().warning("Stronghold room folder does not exist: " + folder.getPath());
            return rooms;
        }

        File[] schematics = folder.listFiles((dir, name) -> name.endsWith(".schem") && name.startsWith("stronghold_"));
        if (schematics == null || schematics.length == 0) {
            plugin.getLogger().warning("No schematics found in: " + folder.getPath());
            return rooms;
        }

        plugin.getLogger().info("Generating dungeon with " + schematics.length + " schematics...");

        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {

                List<File> candidates = new ArrayList<>();
                Collections.addAll(candidates, schematics);
                boolean placed = false;

                while (!candidates.isEmpty() && !placed) {
                    // Pick random schematic
                    File chosen = candidates.remove(random.nextInt(candidates.size()));
                    boolean[] exits = detectExits(chosen);

                    Room northRoom = (z > 0) ? grid[x][z - 1] : null;
                    Room westRoom  = (x > 0) ? grid[x - 1][z] : null;

                    // Try all rotations
                    for (Rotation r : Rotation.values()) {
                        boolean[] rotatedExits = rotateExits(exits, r);

                        boolean northMatches = northRoom == null || northRoom.hasExit(Direction.SOUTH) == rotatedExits[Direction.NORTH.ordinal()];
                        boolean westMatches  = westRoom  == null || westRoom.hasExit(Direction.EAST)  == rotatedExits[Direction.WEST.ordinal()];

                        if (northMatches && westMatches) {
                            int worldX = x * ROOM_SPACING;
                            int worldZ = z * ROOM_SPACING;
                            Location pasteLoc = new Location(world, worldX, ROOM_Y_LEVEL, worldZ);

                            // Paste schematic with rotation
                            SchematicUtil.pasteSchematic(chosen, pasteLoc, r);

                            // Create room and register
                            Room room = new Room(pasteLoc, ROOM_SPACING, ROOM_SPACING, chosen, rotatedExits);
                            RoomRegistry.register(room);
                            grid[x][z] = room;
                            rooms.add(room);

                            placed = true;
                            break;
                        }
                    }
                }

                if (!placed) {
                    plugin.getLogger().warning("Could not place a room at (" + x + ", " + z + ") with matching exits!");
                }
            }
        }

        plugin.getLogger().info("Dungeon generation complete. Rooms created: " + rooms.size());
        return rooms;
    }

    /** Detect entrances by checking edges for wooden planks */
    private boolean[] detectExits(File schematicFile) {
        Schematic schematic = SchematicUtil.loadSchematic(schematicFile);

        boolean north = edgeHasPlanks(schematic, Direction.NORTH);
        boolean east  = edgeHasPlanks(schematic, Direction.EAST);
        boolean south = edgeHasPlanks(schematic, Direction.SOUTH);
        boolean west  = edgeHasPlanks(schematic, Direction.WEST);

        return new boolean[]{north, east, south, west};
    }

    /** Check if a schematic edge has planks */
    private boolean edgeHasPlanks(Schematic schematic, Direction dir) {
        int width = schematic.getWidth();
        int length = schematic.getLength();
        int y = 0; // floor level

        switch (dir) {
            case NORTH:
                for (int x = 0; x < width; x++)
                    if (schematic.getBlock(x, y, 0).getType() == Material.OAK_PLANKS) return true;
                break;
            case SOUTH:
                for (int x = 0; x < width; x++)
                    if (schematic.getBlock(x, y, length - 1).getType() == Material.OAK_PLANKS) return true;
                break;
            case WEST:
                for (int z = 0; z < length; z++)
                    if (schematic.getBlock(0, y, z).getType() == Material.OAK_PLANKS) return true;
                break;
            case EAST:
                for (int z = 0; z < length; z++)
                    if (schematic.getBlock(width - 1, y, z).getType() == Material.OAK_PLANKS) return true;
                break;
        }
        return false;
    }

    /** Rotate the boolean exit array according to rotation */
    private boolean[] rotateExits(boolean[] exits, Rotation rotation) {
        boolean[] rotated = new boolean[4];
        switch (rotation) {
            case NONE:
                return exits;
            case CLOCKWISE_90:
                rotated[Direction.NORTH.ordinal()] = exits[Direction.WEST.ordinal()];
                rotated[Direction.EAST.ordinal()]  = exits[Direction.NORTH.ordinal()];
                rotated[Direction.SOUTH.ordinal()] = exits[Direction.EAST.ordinal()];
                rotated[Direction.WEST.ordinal()]  = exits[Direction.SOUTH.ordinal()];
                break;
            case CLOCKWISE_180:
                rotated[Direction.NORTH.ordinal()] = exits[Direction.SOUTH.ordinal()];
                rotated[Direction.EAST.ordinal()]  = exits[Direction.WEST.ordinal()];
                rotated[Direction.SOUTH.ordinal()] = exits[Direction.NORTH.ordinal()];
                rotated[Direction.WEST.ordinal()]  = exits[Direction.EAST.ordinal()];
                break;
            case COUNTER_90:
                rotated[Direction.NORTH.ordinal()] = exits[Direction.EAST.ordinal()];
                rotated[Direction.EAST.ordinal()]  = exits[Direction.SOUTH.ordinal()];
                rotated[Direction.SOUTH.ordinal()] = exits[Direction.WEST.ordinal()];
                rotated[Direction.WEST.ordinal()]  = exits[Direction.NORTH.ordinal()];
                break;
        }
        return rotated;
    }

    public enum Direction { NORTH, EAST, SOUTH, WEST }

    public enum Rotation { NONE, CLOCKWISE_90, CLOCKWISE_180, COUNTER_90 }
}
