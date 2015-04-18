package net.minelink.redstonelimiter;

import com.google.common.collect.ImmutableSet;
import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Iterator;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class RedstoneLimiter extends JavaPlugin implements Listener {

    private static final Random random = new Random();

    private static final Set<Material> materials = ImmutableSet.<Material>builder()
            .add(Material.DIODE_BLOCK_OFF)
            .add(Material.DIODE_BLOCK_ON)
            .add(Material.LEVER)
            .add(Material.REDSTONE_BLOCK)
            .add(Material.REDSTONE_LAMP_OFF)
            .add(Material.REDSTONE_LAMP_ON)
            .add(Material.REDSTONE_TORCH_OFF)
            .add(Material.REDSTONE_TORCH_ON)
            .add(Material.REDSTONE_WIRE)
            .build();

    private ConcurrentMap<BlockPosition, AtomicInteger> scores = new ConcurrentHashMap<>(20000, 0.8F, 2);

    private boolean isProcessing;

    @Override
    public void onEnable() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, new Runnable() {
            @Override
            public void run() {
                if (isProcessing) return;

                isProcessing = true;
                Iterator<AtomicInteger> iterator = scores.values().iterator();
                int threshold = getConfig().getInt("threshold");

                while (iterator.hasNext()) {
                    AtomicInteger score = iterator.next();
                    int newScore = score.decrementAndGet();

                    if (newScore <= 0) {
                        iterator.remove();
                    } else if (newScore > threshold) {
                        score.compareAndSet(newScore, threshold);
                    }
                }

                isProcessing = false;
            }
        }, 60, 60);

        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void limitRedstone(BlockRedstoneEvent event) {
        Block block = event.getBlock();
        if (!materials.contains(block.getType()) || event.getOldCurrent() != 0) {
            return;
        }

        Location loc = block.getLocation();
        BlockPosition blockPos = new BlockPosition(loc);
        AtomicInteger score = scores.putIfAbsent(blockPos, new AtomicInteger());

        if (score == null) {
            score = scores.get(blockPos);
            if (score == null) {
                limitRedstone(event);
                return;
            }
        }

        int newScore = score.incrementAndGet();
        int threshold = getConfig().getInt("threshold");

        if (newScore < threshold) {
            return;
        }

        if (newScore > threshold) {
            score.compareAndSet(newScore, threshold);
        } else {
            Bukkit.broadcast("§cToo much redstone @ §e" + loc.getWorld().getName() +
                    " §b" + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ() +
                    " §8[§7" + block.getType().name() + "§8]", "redstonelimiter.notify");
        }

        event.setNewCurrent(0);

        for (int i = 0; i < 3; ++i) {
            Location l = loc.clone().add(random.nextDouble() * 1.1, random.nextDouble() - 0.5, random.nextDouble() * 1.1);
            loc.getWorld().playEffect(l, Effect.SMOKE, 64);
        }
    }

}
