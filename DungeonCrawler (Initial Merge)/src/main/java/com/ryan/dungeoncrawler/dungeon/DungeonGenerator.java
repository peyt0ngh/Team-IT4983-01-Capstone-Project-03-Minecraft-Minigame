package com.ryan.dungeoncrawler.dungeon;

import org.bukkit.*;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

public class DungeonGenerator {

    private final Plugin plugin;

    public DungeonGenerator(Plugin plugin) {
        this.plugin = plugin;
    }

    public List<RoomEncounter> generate(int size) {

        List<RoomEncounter> rooms = new ArrayList<>();
        World world = Bukkit.getWorlds().get(0);

        int spacing = 32;

        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {

                Location loc = new Location(world, x * spacing, 70, z * spacing);

                // Build simple test platform
                for (int dx = -4; dx <= 4; dx++) {
                    for (int dz = -4; dz <= 4; dz++) {
                        loc.clone().add(dx, 0, dz).getBlock().setType(Material.STONE);
                    }
                }

                RoomEncounter room = new RoomEncounter(loc);

                // Fake door (edge of room)
                room.addDoor(loc.clone().add(4, 1, 0).getBlock());

                RoomRegistry.register(loc.getChunk(), room);
                rooms.add(room);
            }
        }

        return rooms;
    }
}