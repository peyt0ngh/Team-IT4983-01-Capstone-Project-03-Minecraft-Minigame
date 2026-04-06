package com.ryan.dungeoncrawler.mobs;

import com.ryan.dungeoncrawler.dungeon.RoomEncounter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Random;

public class WaveRoomManager {

    private final Plugin plugin;
    private final Random random = new Random();

    private static final int SPAWN_Y = 65;

    public WaveRoomManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void start(RoomEncounter room, List<Player> players) {

        World world = room.getCenter().getWorld();

        Location center = room.getCenter();

        // Spawn 5 mobs near the center (slight spread)
        for (int i = 0; i < 5; i++) {

            double offsetX = (random.nextDouble() - 0.5) * 2; // -1 to 1
            double offsetZ = (random.nextDouble() - 0.5) * 2;

            Location spawnLoc = new Location(
                    world,
                    center.getX() + offsetX,
                    SPAWN_Y,
                    center.getZ() + offsetZ
            );

            world.spawn(spawnLoc, Zombie.class);
        }

        // Check if all monsters are dead
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {

            boolean mobsAlive = world.getNearbyEntities(
                    room.getCenter(), 10, 10, 10
            ).stream().anyMatch(e -> e instanceof Monster);

            if (!mobsAlive) {
                room.setCleared(true);
                task.cancel();
            }

        }, 40, 40);
    }
}
