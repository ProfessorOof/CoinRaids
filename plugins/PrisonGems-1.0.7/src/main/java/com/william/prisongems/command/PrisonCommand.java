package com.william.prisongems.command;

import com.william.prisongems.PrisonGemsPlugin;
import com.william.prisongems.model.PrisonArea;
import com.william.prisongems.service.PrisonManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class PrisonCommand implements CommandExecutor, TabCompleter {
    private final PrisonGemsPlugin plugin;
    private final PrisonManager prisonManager;

    public PrisonCommand(PrisonGemsPlugin plugin, PrisonManager prisonManager) {
        this.plugin = plugin;
        this.prisonManager = prisonManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "list" -> list(sender);
            case "status" -> status(sender, args);
            case "create" -> create(sender, args);
            case "setspawn" -> setSpawn(sender, args);
            case "capture" -> capture(sender, args);
            case "delete" -> delete(sender, args);
            case "reload" -> reload(sender);
            default -> teleport(sender, action);
        }
        return true;
    }

    private void teleport(CommandSender sender, String areaName) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly a player can teleport to a prison area.");
            return;
        }
        if (!sender.hasPermission("prisongems.use")) {
            noPermission(sender);
            return;
        }
        PrisonArea area = prisonManager.getArea(areaName);
        if (area == null) {
            sender.sendMessage("§cNo prison area named '" + areaName + "' exists.");
            return;
        }
        player.teleportAsync(area.getSpawn()).thenAccept(success -> {
            if (success) {
                player.sendMessage("§aTeleported to the " + area.getName() + " prison.");
            }
        });
    }

    private void list(CommandSender sender) {
        if (!sender.hasPermission("prisongems.use")) {
            noPermission(sender);
            return;
        }
        String areas = prisonManager.getAreas().stream().map(PrisonArea::getName).collect(Collectors.joining(", "));
        sender.sendMessage("§bPrison areas: §f" + (areas.isEmpty() ? "none yet" : areas));
    }

    private void status(CommandSender sender, String[] args) {
        if (!sender.hasPermission("prisongems.use")) {
            noPermission(sender);
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("§eUsage: /prison status <area>");
            return;
        }
        PrisonArea area = prisonManager.getArea(args[1]);
        if (area == null) {
            sender.sendMessage("§cThat prison area does not exist.");
            return;
        }
        if (!prisonManager.hasSnapshot(area)) {
            sender.sendMessage("§e" + area.getName() + " has no template yet. Use /prison capture " + area.getName() + ".");
            return;
        }
        sender.sendMessage("§b" + area.getName() + " integrity: §f" + String.format(Locale.ROOT, "%.1f", prisonManager.getIntegrityPercent(area))
                + "% §7(" + prisonManager.countCurrentSolidBlocks(area) + "/" + prisonManager.getTemplateSolidCount(area) + ")"
                + (area.isResetting() ? " §eRegenerating" : ""));
    }

    private void create(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly a player can create a prison area.");
            return;
        }
        if (!sender.hasPermission("prisongems.admin")) {
            noPermission(sender);
            return;
        }
        if (args.length < 5 || args.length > 7) {
            sender.sendMessage("§eUsage: /prison create <name> <width> <height> <depth> [block] [terraform]");
            return;
        }
        String name = args[1].toLowerCase(Locale.ROOT);
        if (!name.matches("[a-z0-9_-]{1,32}")) {
            sender.sendMessage("§cArea names may contain only letters, numbers, underscores, and hyphens.");
            return;
        }
        if (prisonManager.getArea(name) != null) {
            sender.sendMessage("§cA prison area with that name already exists.");
            return;
        }
        try {
            int width = Integer.parseInt(args[2]);
            int height = Integer.parseInt(args[3]);
            int depth = Integer.parseInt(args[4]);
            if (width < 1 || height < 1 || depth < 1) {
                sender.sendMessage("§cWidth, height, and depth must each be at least 1.");
                return;
            }
            long volume = Math.multiplyExact(Math.multiplyExact((long) width, height), depth);
            if (volume > PrisonManager.MAX_AREA_BLOCKS) {
                sender.sendMessage("§cThat region is too large (" + volume + " blocks). Keep a prison area at " + PrisonManager.MAX_AREA_BLOCKS + " blocks or fewer.");
                return;
            }

            Location origin = player.getLocation();
            int x1 = Math.subtractExact(origin.getBlockX(), width / 2);
            int y1 = Math.subtractExact(origin.getBlockY(), height / 2);
            int z1 = Math.subtractExact(origin.getBlockZ(), depth / 2);
            int x2 = Math.addExact(x1, width - 1);
            int y2 = Math.addExact(y1, height - 1);
            int z2 = Math.addExact(z1, depth - 1);
            int minY = y1;
            int maxY = y2;
            if (minY < player.getWorld().getMinHeight() || maxY >= player.getWorld().getMaxHeight()) {
                sender.sendMessage("§cThat height would extend outside this world's build height.");
                return;
            }
            List<Material> fillPalette = null;
            boolean terraform = false;
            if (args.length >= 6) {
                if (isBoolean(args[5])) {
                    terraform = Boolean.parseBoolean(args[5]);
                } else {
                    fillPalette = parseFillPalette(args[5]);
                }
            }
            if (args.length == 7) {
                if (fillPalette == null || !isBoolean(args[6])) {
                    sender.sendMessage("§eTerraform must be true or false. Usage: /prison create <name> <width> <height> <depth> [block] [terraform]");
                    return;
                }
                terraform = Boolean.parseBoolean(args[6]);
            }
            if (terraform && fillPalette == null) {
                sender.sendMessage("§cTerraforming requires a block or percentage palette to fill the prison first.");
                return;
            }
            PrisonArea area = new PrisonArea(name, player.getWorld(), x1, y1, z1, x2, y2, z2, player.getLocation());
            prisonManager.addArea(area);
            if (fillPalette != null) {
                prisonManager.fill(area, fillPalette, terraform);
                sender.sendMessage("§aCreated and filled " + name + (terraform ? " with a terraformed surface" : "") + ". Run /prison capture " + name + " when it is ready.");
            } else {
                sender.sendMessage("§aCreated " + name + ". Build the mine, then run /prison capture " + name + ".");
            }
        } catch (NumberFormatException | ArithmeticException exception) {
            sender.sendMessage("§cWidth, height, and depth must be whole numbers within the world range.");
        } catch (IllegalArgumentException exception) {
            sender.sendMessage("§c" + exception.getMessage());
        }
    }

    private void setSpawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly a player can set a prison spawn.");
            return;
        }
        if (!sender.hasPermission("prisongems.admin")) {
            noPermission(sender);
            return;
        }
        if (args.length != 2) {
            sender.sendMessage("§eUsage: /prison setspawn <area>");
            return;
        }
        PrisonArea area = prisonManager.getArea(args[1]);
        if (area == null) {
            sender.sendMessage("§cThat prison area does not exist.");
            return;
        }
        if (!area.getWorld().equals(player.getWorld())) {
            sender.sendMessage("§cThe spawn must be in the same world as the prison area.");
            return;
        }
        area.setSpawn(player.getLocation());
        prisonManager.saveArea(area);
        sender.sendMessage("§aUpdated the spawn for " + area.getName() + ".");
    }

    private void capture(CommandSender sender, String[] args) {
        if (!sender.hasPermission("prisongems.admin")) {
            noPermission(sender);
            return;
        }
        if (args.length != 2) {
            sender.sendMessage("§eUsage: /prison capture <area>");
            return;
        }
        PrisonArea area = prisonManager.getArea(args[1]);
        if (area == null) {
            sender.sendMessage("§cThat prison area does not exist.");
            return;
        }
        if (prisonManager.capture(area)) {
            sender.sendMessage("§aCaptured the " + area.getName() + " template with " + prisonManager.getTemplateSolidCount(area) + " non-air blocks.");
        } else {
            sender.sendMessage("§cCould not capture this area. It may be empty, regenerating, or in an unloaded world.");
        }
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("prisongems.admin")) {
            noPermission(sender);
            return;
        }
        if (prisonManager.reload()) {
            sender.sendMessage("§aPrisonGems reloaded.");
        } else {
            sender.sendMessage("§ePrisonGems cannot reload while a mine is regenerating. Try again in a moment.");
        }
    }

    private void delete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("prisongems.admin")) {
            noPermission(sender);
            return;
        }
        if (args.length != 2) {
            sender.sendMessage("§eUsage: /prison delete <area>");
            return;
        }
        if (prisonManager.deleteArea(args[1])) {
            sender.sendMessage("§aDeleted prison area " + args[1].toLowerCase(Locale.ROOT) + ".");
        } else {
            sender.sendMessage("§cCould not delete that prison area. It may not exist or may be regenerating.");
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + "PrisonGems: /prison <area>, /prison list, /prison status <area>");
        if (sender.hasPermission("prisongems.admin")) {
            sender.sendMessage(ChatColor.GRAY + "Admin: create <name> <width> <height> <depth> [block] [terraform], setspawn, capture, delete, reload");
        }
    }

    private void noPermission(CommandSender sender) {
        sender.sendMessage("§cYou do not have permission to do that.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(Arrays.asList("list", "status"));
            prisonManager.getAreas().forEach(area -> options.add(area.getName()));
            if (sender.hasPermission("prisongems.admin")) {
                options.addAll(List.of("create", "setspawn", "capture", "delete", "reload"));
            }
            return filter(options, args[0]);
        }
        if (args.length == 2 && List.of("status", "setspawn", "capture", "delete").contains(args[0].toLowerCase(Locale.ROOT))) {
            return filter(prisonManager.getAreas().stream().map(PrisonArea::getName).toList(), args[1]);
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.startsWith(lower)).sorted().toList();
    }

    private boolean isBoolean(String value) {
        return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
    }

    /** Expands a percentage palette into 100 weighted material slots. */
    private List<Material> parseFillPalette(String specification) {
        if (!specification.contains("%")) {
            return List.of(parseBlock(specification));
        }
        List<Material> palette = new ArrayList<>(100);
        int total = 0;
        for (String entry : specification.split(",", -1)) {
            int separator = entry.indexOf('%');
            if (separator <= 0 || separator != entry.lastIndexOf('%') || separator == entry.length() - 1) {
                throw new IllegalArgumentException("Use percentage blocks like 20%andesite,40%stone,40%cobblestone.");
            }
            int percentage;
            try {
                percentage = Integer.parseInt(entry.substring(0, separator));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Each block percentage must be a whole number.");
            }
            if (percentage < 1 || percentage > 100) {
                throw new IllegalArgumentException("Each block percentage must be between 1 and 100.");
            }
            Material material = parseBlock(entry.substring(separator + 1));
            total += percentage;
            for (int index = 0; index < percentage; index++) {
                palette.add(material);
            }
        }
        if (total != 100) {
            throw new IllegalArgumentException("Block percentages must add up to exactly 100.");
        }
        return List.copyOf(palette);
    }

    private Material parseBlock(String value) {
        Material material = Material.matchMaterial(value);
        if (material == null || !material.isBlock() || material.isAir()) {
            throw new IllegalArgumentException("'" + value + "' is not a valid non-air block material.");
        }
        return material;
    }
}
