package com.ryan.dungeoncrawler.dungeon;

import org.bukkit.Chunk;

import java.util.HashMap;
import java.util.Map;

public class RoomRegistry {

    private static final Map<Chunk, RoomEncounter> rooms = new HashMap<>();

    public static void register(Chunk chunk, RoomEncounter room) {
        rooms.put(chunk, room);
    }

    public static RoomEncounter getRoom(Chunk chunk) {
        return rooms.get(chunk);
    }

    public static void clear() {
        rooms.clear();
    }
}