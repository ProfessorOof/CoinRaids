package com.william.prisongems;

import com.william.prisongems.command.PrisonCommand;
import com.william.prisongems.listener.PrisonBlockListener;
import com.william.prisongems.service.GemsService;
import com.william.prisongems.service.PrisonManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class PrisonGemsPlugin extends JavaPlugin {
    private GemsService gemsService;
    private PrisonManager prisonManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        gemsService = new GemsService(this);
        prisonManager = new PrisonManager(this);
        prisonManager.loadAreas();
        prisonManager.startIntegrityChecks();

        PrisonCommand prisonCommand = new PrisonCommand(this, prisonManager);
        PluginCommand command = getCommand("prison");
        if (command == null) {
            throw new IllegalStateException("The prison command is missing from plugin.yml");
        }
        command.setExecutor(prisonCommand);
        command.setTabCompleter(prisonCommand);

        getServer().getPluginManager().registerEvents(new PrisonBlockListener(this, prisonManager, gemsService), this);
        getLogger().info("PrisonGems enabled with " + prisonManager.getAreas().size() + " prison area(s).");
    }

    @Override
    public void onDisable() {
        if (prisonManager != null) {
            prisonManager.stopIntegrityChecks();
        }
    }

    public GemsService getGemsService() {
        return gemsService;
    }
}
