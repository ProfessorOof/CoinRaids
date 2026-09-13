package com.william.prisongems.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Locale;
import java.util.Objects;

public final class PrisonArea {
    private final String name;
    private final String worldName;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;
    private Location spawn;
    private boolean resetting;

    public PrisonArea(String name, World world, int x1, int y1, int z1, int x2, int y2, int z2, Location spawn) {
        this(name, world.getName(), x1, y1, z1, x2, y2, z2, spawn);
    }

    public PrisonArea(String name, String worldName, int x1, int y1, int z1, int x2, int y2, int z2, Location spawn) {
        this.name = name.toLowerCase(Locale.ROOT);
        this.worldName = worldName;
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
        this.spawn = spawn.clone();
    }

    public static PrisonArea fromConfig(String name, ConfigurationSection section) {
        World world = Bukkit.getWorld(Objects.requireNonNull(section.getString("world"), "world"));
        if (world == null) {
            return null;
        }
        Location spawn = new Location(
                world,
                section.getDouble("spawn.x"),
                section.getDouble("spawn.y"),
                section.getDouble("spawn.z"),
                (float) section.getDouble("spawn.yaw"),
                (float) section.getDouble("spawn.pitch")
        );
        return new PrisonArea(
                name,
                world,
                section.getInt("min.x"), section.getInt("min.y"), section.getInt("min.z"),
                section.getInt("max.x"), section.getInt("max.y"), section.getInt("max.z"),
                spawn
        );
    }

    public void save(FileConfiguration config) {
        String path = "areas." + name;
        config.set(path + ".world", worldName);
        config.set(path + ".min.x", minX);
        config.set(path + ".min.y", minY);
        config.set(path + ".min.z", minZ);
        config.set(path + ".max.x", maxX);
        config.set(path + ".max.y", maxY);
        config.set(path + ".max.z", maxZ);
        config.set(path + ".spawn.x", spawn.getX());
        config.set(path + ".spawn.y", spawn.getY());
        config.set(path + ".spawn.z", spawn.getZ());
        config.set(path + ".spawn.yaw", spawn.getYaw());
        config.set(path + ".spawn.pitch", spawn.getPitch());
    }

    public boolean contains(Location location) {
        return location.getWorld() != null
                && location.getWorld().getName().equals(worldName)
                && location.getBlockX() >= minX && location.getBlockX() <= maxX
                && location.getBlockY() >= minY && location.getBlockY() <= maxY
                && location.getBlockZ() >= minZ && location.getBlockZ() <= maxZ;
    }

    public String getName() {
        return name;
    }

    public World getWorld() {
        return Bukkit.getWorld(worldName);
    }

    public Location getSpawn() {
        return spawn.clone();
    }

    public void setSpawn(Location spawn) {
        this.spawn = spawn.clone();
    }

    public int getMinX() { return minX; }
    public int getMinY() { return minY; }
    public int getMinZ() { return minZ; }
    public int getMaxX() { return maxX; }
    public int getMaxY() { return maxY; }
    public int getMaxZ() { return maxZ; }
    public boolean isResetting() { return resetting; }
    public void setResetting(boolean resetting) { this.resetting = resetting; }

    public long getVolume() {
        return ((long) maxX - minX + 1L)
                * ((long) maxY - minY + 1L)
                * ((long) maxZ - minZ + 1L);
    }
}
