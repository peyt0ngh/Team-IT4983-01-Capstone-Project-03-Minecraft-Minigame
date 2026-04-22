package com.ryan.dungeoncrawler.dungeon;

import com.ryan.dungeoncrawler.util.SchematicUtil;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

public class DungeonGenerator {

    private final JavaPlugin plugin;
    private final Random random = new Random();

    private static final int ROOM_SPACING = 25;
    private static final int ROOM_Y = 100;

    public DungeonGenerator(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public List<Room> generate(int size) {

        List<Room> rooms = new ArrayList<>();
        World world = Bukkit.getWorlds().get(0);

        // ── Generate Layout ──
        DungeonLayoutGenerator layoutGen = new DungeonLayoutGenerator();
        List<DungeonLayoutGenerator.LayoutRoom> layout = layoutGen.generate(size);

        // ── Load schematics by type ──
        Map<DungeonLayoutGenerator.RoomType, List<File>> pool = loadRoomPools();

        for (DungeonLayoutGenerator.LayoutRoom r : layout) {

            List<File> options = pool.get(r.type);

            if (options == null || options.isEmpty()) {
                plugin.getLogger().warning("No schematics for type: " + r.type);
                continue;
            }

            File chosen = options.get(random.nextInt(options.size()));

            Location baseLoc = new Location(
                    world,
                    r.x * ROOM_SPACING,
                    ROOM_Y,
                    r.z * ROOM_SPACING
            );

            // ── Find correct rotation based on door markers ──
            int rotation = findBestRotation(baseLoc, r.connections, chosen);

            // ── Paste schematic ──
            SchematicUtil.pasteSchematic(chosen, baseLoc, rotation);

            // ── Register room ──
            Room room = new Room(baseLoc, ROOM_SPACING, ROOM_SPACING);
            RoomRegistry.register(room);
            rooms.add(room);
        }

        return rooms;
    }

    // ─────────────────────────────────────────────
    // LOAD SCHEMATIC POOLS
    // ─────────────────────────────────────────────

    private Map<DungeonLayoutGenerator.RoomType, List<File>> loadRoomPools() {

        Map<DungeonLayoutGenerator.RoomType, List<File>> map = new HashMap<>();

        File base = new File(plugin.getDataFolder(), "rooms/stronghold");

        for (DungeonLayoutGenerator.RoomType type : DungeonLayoutGenerator.RoomType.values()) {

            File folder = new File(base, type.name().toLowerCase());

            if (!folder.exists()) continue;

            File[] files = folder.listFiles((d, name) -> name.endsWith(".schem"));

            if (files != null) {
                map.put(type, Arrays.asList(files));
            }
        }

        return map;
    }

    // ─────────────────────────────────────────────
    // ROTATION LOGIC USING RED WOOL DOORS
    // ─────────────────────────────────────────────

    private int findBestRotation(Location loc,
                                 Set<DungeonLayoutGenerator.Direction> needed,
                                 File schematic) {

        for (int rotation : new int[]{0, 90, 180, 270}) {

            Set<DungeonLayoutGenerator.Direction> doors =
                    detectDoors(loc, schematic, rotation);

            if (doors.equals(needed)) {
                return rotation;
            }
        }

        // fallback
        return 0;
    }

    // ─────────────────────────────────────────────
    // DETECT DOORS FROM RED WOOL
    // ─────────────────────────────────────────────

    private Set<DungeonLayoutGenerator.Direction> detectDoors(Location loc,
                                                              File schematic,
                                                              int rotation) {

        Set<DungeonLayoutGenerator.Direction> doors = new HashSet<>();

        // TEMP paste (could be optimized later)
        SchematicUtil.pasteSchematic(schematic, loc, rotation);

        int topY = loc.getBlockY() + 5; // adjust based on room height

        for (int x = -ROOM_SPACING / 2; x <= ROOM_SPACING / 2; x++) {
            for (int z = -ROOM_SPACING / 2; z <= ROOM_SPACING / 2; z++) {

                Block b = loc.clone().add(x, 5, z).getBlock();

                if (b.getType() == Material.RED_WOOL) {

                    if (x > 5) doors.add(DungeonLayoutGenerator.Direction.EAST);
                    if (x < -5) doors.add(DungeonLayoutGenerator.Direction.WEST);
                    if (z > 5) doors.add(DungeonLayoutGenerator.Direction.SOUTH);
                    if (z < -5) doors.add(DungeonLayoutGenerator.Direction.NORTH);
                }
            }
        }

        return doors;
    }
}
