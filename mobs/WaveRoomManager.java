package your.plugin.mobs;

import your.plugin.difficulty.DifficultyAdapter;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * Handles spawning waves, room locking/unlocking, and multi-player support.
 */
public class WaveRoomManager {

    private final DifficultyAdapter difficulty;
    private final Random random = new Random();
    private final int currentStage;
    private final Plugin plugin;

    private final Map<String, List<Block>> lockedFenceBlocks = new HashMap<>();
    private final Map<String, List<Block>> lockedBarrierBlocks = new HashMap<>();
    private final Map<String, List<Player>> playersInRooms = new HashMap<>();

    public WaveRoomManager(DifficultyAdapter difficulty, int currentStage, Plugin plugin) {
        this.difficulty = difficulty;
        this.currentStage = currentStage;
        this.plugin = plugin;
    }

    /**
     * Starts a full room encounter: locks room, spawns waves, monitors players.
     */
    public void startRoom(RoomEncounter room, List<Block> fenceBlocks, List<Block> barrierBlocks, List<Player> playersInRoom) {
        lockRoom(room.getRoomId(), fenceBlocks, barrierBlocks, playersInRoom);

        int totalWaves = getWaveCount(room.getCategory());
        runWave(room, totalWaves, 0);
    }

    private int getWaveCount(RoomCategory category) {
        int base = switch (category) {
            case SMALL -> 2;
            case NORMAL -> 3;
            case LARGE -> 4;
            case BOSS -> 5;
        };
        int bonus = difficulty.getWaveBonus(currentStage);
        return Math.min(base + bonus, 8);
    }

    private void runWave(RoomEncounter room, int total, int current) {
        if (current >= total) {
            finishRoom(room);
            return;
        }

        int mobCount = getMobCount(room.getCategory(), current);
        spawnWave(room, mobCount, () -> runWave(room, total, current + 1));
    }

    private int getMobCount(RoomCategory category, int wave) {
        int base = difficulty.getStats(currentStage).getMobCount();
        int modifier = switch (category) {
            case SMALL -> -1;
            case NORMAL -> 0;
            case LARGE -> 2;
            case BOSS -> 3;
        };
        return Math.max(1, base + modifier + wave);
    }

    private void spawnWave(RoomEncounter room, int count, Runnable onComplete) {
        List<LivingEntity> mobs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Location loc = randomLocation(room.getCenter());
            LivingEntity mob = spawnMob(loc);
            scaleMob(mob);
            mobs.add(mob);
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                mobs.removeIf(Entity::isDead);
                if (mobs.isEmpty()) {
                    cancel();
                    onComplete.run();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private LivingEntity spawnMob(Location loc) {
        EntityType type;
        switch (currentStage) {
            case 1 -> type = random.nextBoolean() ? EntityType.ZOMBIE : EntityType.SKELETON;
            case 2 -> type = switch (random.nextInt(3)) {
                case 0 -> EntityType.BLAZE;
                case 1 -> EntityType.WITHER_SKELETON;
                default -> EntityType.PIGLIN;
            };
            case 3 -> type = switch (random.nextInt(3)) {
                case 0 -> EntityType.ENDERMAN;
                case 1 -> EntityType.SHULKER;
                default -> EntityType.GUARDIAN;
            };
            default -> type = EntityType.ZOMBIE;
        }
        return (LivingEntity) loc.getWorld().spawnEntity(loc, type);
    }

    private void scaleMob(LivingEntity mob) {
        double health = difficulty.getStats(currentStage).getHealth();
        double damage = difficulty.getStats(currentStage).getDamage();

        mob.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(health);
        mob.setHealth(health);

        if (mob.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE) != null) {
            mob.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(damage);
        }
    }

    private void finishRoom(RoomEncounter room) {
        room.setCleared(true);
        unlockRoom(room.getRoomId());
    }

    // =========================
    // ROOM LOCK / UNLOCK SYSTEM
    // =========================

    private void lockRoom(String roomId, List<Block> fences, List<Block> barriers, List<Player> players) {
        if (lockedFenceBlocks.containsKey(roomId)) return;

        lockedFenceBlocks.put(roomId, new ArrayList<>(fences));
        lockedBarrierBlocks.put(roomId, new ArrayList<>(barriers));
        playersInRooms.put(roomId, new ArrayList<>(players));

        for (Block b : fences) b.setType(Material.OAK_FENCE);
        for (Block b : barriers) b.setType(Material.BARRIER);

        for (Player p : players) {
            p.sendMessage(ChatColor.RED + "The room is locked! Clear all enemies or die to proceed.");
            p.playSound(p.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 1f, 1f);
            spawnLockParticles(p.getLocation());
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
                spawnUnlockParticles(p.getLocation());
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
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void spawnLockParticles(Location loc) {
        loc.getWorld().spawnParticle(Particle.SMOKE_LARGE, loc.add(0.5,1,0.5), 10, 0.5,0.5,0.5, 0.05);
    }

    private void spawnUnlockParticles(Location loc) {
        loc.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, loc.add(0.5,1,0.5), 10, 0.5,0.5,0.5, 0.05);
    }

    private Location randomLocation(Location center) {
        double dx = (random.nextDouble() - 0.5) * 8;
        double dz = (random.nextDouble() - 0.5) * 8;
        Location loc = center.clone().add(dx, 0, dz);
        loc.setY(center.getWorld().getHighestBlockYAt(loc) + 1);
        return loc;
    }
}