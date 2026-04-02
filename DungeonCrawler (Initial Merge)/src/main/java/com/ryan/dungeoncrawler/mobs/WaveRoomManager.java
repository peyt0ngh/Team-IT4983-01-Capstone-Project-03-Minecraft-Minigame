package com.ryan.dungeoncrawler.mobs;

import com.ryan.dungeoncrawler.dungeon.RoomEncounter;
import org.bukkit.Bukkit;
import org.bukkit.entity.*;
import org.bukkit.plugin.Plugin;

import java.util.List;

public class WaveRoomManager {

    private final Plugin plugin;

    public WaveRoomManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void start(RoomEncounter room, List<Player> players) {

        for (int i = 0; i < 5; i++) {
            room.getCenter().getWorld().spawn(room.getCenter(), Zombie.class);
        }

        // Check clear
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {

            boolean mobsAlive = room.getCenter().getWorld().getNearbyEntities(
                    room.getCenter(),10,10,10
            ).stream().anyMatch(e -> e instanceof Monster);

            if (!mobsAlive) {
                room.setCleared(true);
                task.cancel();
            }

        }, 40, 40);
    }
}