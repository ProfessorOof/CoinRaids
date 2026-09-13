package com.william.prisongems.service;

import com.william.prisongems.PrisonGemsPlugin;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

public final class GemsService {
    private static final String OBJECTIVE_NAME = "Gems";
    private final PrisonGemsPlugin plugin;
    private final Objective objective;

    public GemsService(PrisonGemsPlugin plugin) {
        this.plugin = plugin;
        ScoreboardManager manager = plugin.getServer().getScoreboardManager();
        if (manager == null) {
            throw new IllegalStateException("The server scoreboard manager is unavailable.");
        }
        Scoreboard scoreboard = manager.getMainScoreboard();
        Objective existing = scoreboard.getObjective(OBJECTIVE_NAME);
        objective = existing != null
                ? existing
                : scoreboard.registerNewObjective(OBJECTIVE_NAME, Criteria.DUMMY, ChatColor.AQUA + "Gems");

        if (plugin.getConfig().getBoolean("show-gems-in-sidebar", true)) {
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
    }

    public void addGems(OfflinePlayer player, int amount) {
        if (amount <= 0) {
            return;
        }
        objective.getScore(player.getName() == null ? player.getUniqueId().toString() : player.getName())
                .setScore(getGems(player) + amount);
    }

    public int getGems(OfflinePlayer player) {
        return objective.getScore(player.getName() == null ? player.getUniqueId().toString() : player.getName()).getScore();
    }
}
