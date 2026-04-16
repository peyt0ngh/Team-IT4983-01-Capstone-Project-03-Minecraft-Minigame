package com.ryan.dungeoncrawler.dungeon;
 
import org.bukkit.Chunk;
 
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
 
/**
 * Global registry mapping world {@link Chunk}s to the {@link Room} that
 * occupies them.
 *
 * Fixes vs. initial merge:
 *  - Stores {@link Room} (not {@link RoomEncounter}) to match
 *    {@link DungeonGenerator#generate(int)} which calls
 *    {@code RoomRegistry.register(room)}.
 *  - Adds a {@link #register(Room)} convenience overload so the generator
 *    doesn't need to compute the chunk manually.
 *  - Keeps the chunk-keyed overload for explicit registration.
 *  - {@link RoomListener} now receives a {@link Room}; callers that need a
 *    {@link RoomEncounter} should use {@link Room#toEncounter()}.
 */
public class RoomRegistry {
 
    private static final Map<Chunk, Room> rooms = new HashMap<>();
 
    /** Register a room using its paste-location chunk as the key. */
    public static void register(Room room) {
        Chunk chunk = room.getCenter().getChunk();
        rooms.put(chunk, room);
    }
 
    /** Register a room under an explicit chunk (useful when the room spans chunks). */
    public static void register(Chunk chunk, Room room) {
        rooms.put(chunk, room);
    }
 
    /** Look up the room for the chunk the player/entity is standing in. */
    public static Room getRoom(Chunk chunk) {
        return rooms.get(chunk);
    }
 
    /** All registered rooms (read-only view). */
    public static Collection<Room> allRooms() {
        return Collections.unmodifiableCollection(rooms.values());
    }
 
    /** Remove all rooms (called at dungeon teardown). */
    public static void clear() {
        rooms.clear();
    }
}
