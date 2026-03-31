package your.plugin.rooms;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * Optional separate room lock system if needed outside WaveRoomManager.
 */
public class RoomLockSystem {

    private final Map<String, List<Block>> lockedFenceBlocks = new HashMap<>();
    private final Map<String, List<Block>> lockedBarrierBlocks = new HashMap<>();
    private final Map<String, List<Player>> playersInRooms = new HashMap<>();

    private final Material fenceMaterial = Material.OAK_FENCE;
    private final Material barrierMaterial = Material.BARRIER;

    public void lockRoom(String roomId, List<Block> fences, List<Block> barriers, List<Player> players) {
        if (lockedFenceBlocks.containsKey(roomId)) return;

        lockedFenceBlocks.put(roomId, new ArrayList<>(fences));
        lockedBarrierBlocks.put(roomId, new ArrayList<>(barriers));
        playersInRooms.put(roomId, new ArrayList<>(players));

        for (Block b : fences) b.setType(fenceMaterial);
        for (Block b : barriers) b.setType(barrierMaterial);

        for (Player p : players) {
            p.sendMessage(ChatColor.RED + "The room is locked! Clear all enemies or die to proceed.");
            p.playSound(p.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 1f, 1f);
        }

        monitorRoom(roomId);
    }

    private void unlockRoom(String roomId) {
        List<Block> fences = lockedFenceBlocks.remove(roomId);
        List<Block> barriers = lockedBarrierBlocks.remove(roomId);
        List<Player> players = playersInRooms.remove(roomId);

        if (fences != null) fences.forEach(b -> b.setType(Material.AIR));
        if (barriers != null) barriers.forEach(b -> b.setType(Material.AIR));

        if (players != null) {
            for (Player p : players) {
                p.sendMessage(ChatColor.GREEN + "The room is now unlocked!");
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
            }
        }
    }

    private void monitorRoom(String roomId) {
        new BukkitRunnable() {
            @Override
            public void run() {
                List<Player> players = playersInRooms.get(roomId);
                if (players == null) { cancel(); return; }

                boolean allDead = true;
                for (Player p : players) {
                    if (p.isOnline() && p.getHealth() > 0) allDead = false;
                }

                if (allDead) {
                    unlockRoom(roomId);
                    cancel();
                }
            }
        }.runTaskTimer(/* plugin instance */, 20L, 20L);
    }
}