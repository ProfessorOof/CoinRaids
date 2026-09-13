package com.william.prisongems.listener;

import com.william.prisongems.PrisonGemsPlugin;
import com.william.prisongems.model.PrisonArea;
import com.william.prisongems.service.GemsService;
import com.william.prisongems.service.PrisonManager;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public final class PrisonBlockListener implements Listener {
    private final PrisonGemsPlugin plugin;
    private final PrisonManager prisonManager;
    private final GemsService gemsService;

    public PrisonBlockListener(PrisonGemsPlugin plugin, PrisonManager prisonManager, GemsService gemsService) {
        this.plugin = plugin;
        this.prisonManager = prisonManager;
        this.gemsService = gemsService;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() == Material.AIR) {
            return;
        }
        PrisonArea area = prisonManager.getAreaAt(event.getBlock().getLocation());
        if (area == null) {
            return;
        }
        if (area.isResetting()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§eThis mine is regenerating. Please wait a moment.");
            return;
        }
        if (!prisonManager.hasSnapshot(area)) {
            return;
        }
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE
                && !plugin.getConfig().getBoolean("reward-creative-mode", false)) {
            prisonManager.checkAreaSoon(area);
            return;
        }

        int reward = Math.max(0, plugin.getConfig().getInt("gems-per-block", 1));
        gemsService.addGems(event.getPlayer(), reward);
        if (reward > 0) {
            event.getPlayer().sendMessage("§b+" + reward + " Gems");
        }
        prisonManager.checkAreaSoon(area);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        PrisonArea area = prisonManager.getAreaAt(event.getBlockPlaced().getLocation());
        if (area == null) {
            return;
        }
        if (!prisonManager.hasSnapshot(area)) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage("§eBlocks cannot be placed inside a captured prison mine.");
    }
}
