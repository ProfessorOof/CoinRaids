package com.william.prisongems.service;

import com.william.prisongems.PrisonGemsPlugin;
import com.william.prisongems.model.BlockSnapshot;
import com.william.prisongems.model.PrisonArea;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class PrisonManager {
    public static final long MAX_AREA_BLOCKS = 50_000L;
    private final PrisonGemsPlugin plugin;
    private final Map<String, PrisonArea> areas = new HashMap<>();
    private final Map<String, Map<String, BlockSnapshot>> snapshots = new HashMap<>();
    private final Map<String, Integer> templateSolidCounts = new HashMap<>();
    private final Set<String> pendingIntegrityChecks = new HashSet<>();
    private final Set<String> activeResets = new HashSet<>();
    private BukkitTask integrityTask;
    private BukkitTask borderTask;
    private long areaRevision;

    public PrisonManager(PrisonGemsPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadAreas() {
        areas.clear();
        snapshots.clear();
        templateSolidCounts.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("areas");
        if (section == null) {
            return;
        }
        for (String name : section.getKeys(false)) {
            ConfigurationSection areaSection = section.getConfigurationSection(name);
            if (areaSection == null) {
                continue;
            }
            PrisonArea area = PrisonArea.fromConfig(name, areaSection);
            if (area == null) {
                plugin.getLogger().warning("Skipped prison '" + name + "' because its world is not loaded.");
                continue;
            }
            areas.put(area.getName(), area);
            loadSnapshot(area);
        }
    }

    public boolean reload() {
        if (!activeResets.isEmpty()) {
            return false;
        }
        areaRevision++;
        pendingIntegrityChecks.clear();
        plugin.reloadConfig();
        loadAreas();
        stopIntegrityChecks();
        startIntegrityChecks();
        return true;
    }

    public void startIntegrityChecks() {
        long interval = Math.max(1L, plugin.getConfig().getLong("integrity-check-interval-seconds", 15L)) * 20L;
        integrityTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkAllAreas, interval, interval);
        long borderInterval = Math.max(1L, plugin.getConfig().getLong("border-particle-interval-ticks", 10L));
        borderTask = Bukkit.getScheduler().runTaskTimer(plugin, this::showAllBorders, 1L, borderInterval);
    }

    public void stopIntegrityChecks() {
        if (integrityTask != null) {
            integrityTask.cancel();
            integrityTask = null;
        }
        if (borderTask != null) {
            borderTask.cancel();
            borderTask = null;
        }
    }

    public Collection<PrisonArea> getAreas() {
        return areas.values().stream().sorted(Comparator.comparing(PrisonArea::getName)).toList();
    }

    public PrisonArea getArea(String name) {
        return areas.get(name.toLowerCase(Locale.ROOT));
    }

    public PrisonArea getAreaAt(org.bukkit.Location location) {
        return areas.values().stream().filter(area -> area.contains(location)).findFirst().orElse(null);
    }

    public void addArea(PrisonArea area) {
        areas.put(area.getName(), area);
        area.save(plugin.getConfig());
        plugin.saveConfig();
    }

    public void saveArea(PrisonArea area) {
        area.save(plugin.getConfig());
        plugin.saveConfig();
    }

    public boolean deleteArea(String name) {
        PrisonArea area = getArea(name);
        if (area == null || area.isResetting() || activeResets.contains(area.getName())) {
            return false;
        }
        File snapshot = snapshotFile(area);
        if (snapshot.exists() && !snapshot.delete()) {
            return false;
        }
        areas.remove(area.getName());
        snapshots.remove(area.getName());
        templateSolidCounts.remove(area.getName());
        pendingIntegrityChecks.remove(area.getName());
        plugin.getConfig().set("areas." + area.getName(), null);
        plugin.saveConfig();
        return true;
    }

    public boolean hasSnapshot(PrisonArea area) {
        return snapshots.containsKey(area.getName()) && templateSolidCounts.getOrDefault(area.getName(), 0) > 0;
    }

    public int getTemplateSolidCount(PrisonArea area) {
        return templateSolidCounts.getOrDefault(area.getName(), 0);
    }

    public int countCurrentSolidBlocks(PrisonArea area) {
        World world = area.getWorld();
        Map<String, BlockSnapshot> snapshot = snapshots.get(area.getName());
        if (world == null || snapshot == null) {
            return 0;
        }
        int solid = 0;
        for (Map.Entry<String, BlockSnapshot> entry : snapshot.entrySet()) {
            BlockSnapshot expected = entry.getValue();
            if (Material.valueOf(expected.material()).isAir()) {
                continue;
            }
            int[] coordinates = parseKey(entry.getKey());
            if (world.getBlockAt(coordinates[0], coordinates[1], coordinates[2]).getType().name().equals(expected.material())) {
                solid++;
            }
        }
        return solid;
    }

    public double getIntegrityPercent(PrisonArea area) {
        int template = getTemplateSolidCount(area);
        return template == 0 ? 0.0 : (countCurrentSolidBlocks(area) * 100.0) / template;
    }

    public boolean capture(PrisonArea area) {
        World world = area.getWorld();
        if (world == null || area.isResetting() || area.getVolume() > MAX_AREA_BLOCKS) {
            return false;
        }

        Map<String, BlockSnapshot> snapshot = new HashMap<>((int) area.getVolume());
        int solids = 0;
        for (int x = area.getMinX(); x <= area.getMaxX(); x++) {
            for (int y = area.getMinY(); y <= area.getMaxY(); y++) {
                for (int z = area.getMinZ(); z <= area.getMaxZ(); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    snapshot.put(key(x, y, z), new BlockSnapshot(block.getType().name(), block.getBlockData().getAsString()));
                    if (!block.getType().isAir()) {
                        solids++;
                    }
                }
            }
        }
        if (solids == 0) {
            return false;
        }
        snapshots.put(area.getName(), snapshot);
        templateSolidCounts.put(area.getName(), solids);
        saveSnapshot(area);
        return true;
    }

    /** Fills an uncaptured prison area during its setup phase. */
    public void fill(PrisonArea area, List<Material> palette, boolean terraform) {
        World world = area.getWorld();
        if (world == null) {
            throw new IllegalStateException("The prison world is not loaded.");
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int x = area.getMinX(); x <= area.getMaxX(); x++) {
            for (int y = area.getMinY(); y <= area.getMaxY(); y++) {
                for (int z = area.getMinZ(); z <= area.getMaxZ(); z++) {
                    world.getBlockAt(x, y, z).setType(palette.get(random.nextInt(palette.size())), false);
                }
            }
        }
        if (terraform) {
            applyNaturalSurface(area, world);
        }
    }

    /**
     * Carves a shallow, irregular surface without introducing materials outside the fill palette.
     * The exposed blocks are therefore the same blocks that were generated throughout the prison.
     */
    private void applyNaturalSurface(PrisonArea area, World world) {
        int maxCarveDepth = (int) Math.min(3L, (long) area.getMaxY() - area.getMinY());
        if (maxCarveDepth == 0) {
            return;
        }
        long seed = world.getSeed() ^ ((long) area.getName().hashCode() << 32) ^ area.getName().hashCode();
        for (int x = area.getMinX(); x <= area.getMaxX(); x++) {
            for (int z = area.getMinZ(); z <= area.getMaxZ(); z++) {
                double broadShape = smoothNoise(x, z, 11, seed);
                double detailShape = smoothNoise(x, z, 5, seed ^ 0x9E3779B97F4A7C15L);
                double shape = broadShape * 0.82 + detailShape * 0.18;
                int carveDepth = terrainDepth(shape, maxCarveDepth);
                for (int offset = 0; offset < carveDepth; offset++) {
                    world.getBlockAt(x, area.getMaxY() - offset, z).setType(Material.AIR, false);
                }
            }
        }
    }

    /** Converts continuous terrain noise into shallow plateaus with gentle transitions. */
    private int terrainDepth(double shape, int maxCarveDepth) {
        if (shape < -0.50) {
            return 0;
        }
        if (shape < -0.05) {
            return Math.min(1, maxCarveDepth);
        }
        if (shape < 0.45) {
            return Math.min(2, maxCarveDepth);
        }
        return maxCarveDepth;
    }

    /** Bilinearly interpolated value noise avoids isolated single-block terrain changes. */
    private double smoothNoise(int x, int z, int cellSize, long seed) {
        int cellX = Math.floorDiv(x, cellSize);
        int cellZ = Math.floorDiv(z, cellSize);
        double localX = (double) Math.floorMod(x, cellSize) / cellSize;
        double localZ = (double) Math.floorMod(z, cellSize) / cellSize;
        double blendX = smoothStep(localX);
        double blendZ = smoothStep(localZ);
        double north = lerp(valueAt(cellX, cellZ, seed), valueAt(cellX + 1, cellZ, seed), blendX);
        double south = lerp(valueAt(cellX, cellZ + 1, seed), valueAt(cellX + 1, cellZ + 1, seed), blendX);
        return lerp(north, south, blendZ);
    }

    private double valueAt(int x, int z, long seed) {
        long value = seed + x * 341873128712L + z * 132897987541L;
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return ((value >>> 11) * 0x1.0p-53) * 2.0 - 1.0;
    }

    private double smoothStep(double value) {
        return value * value * (3.0 - 2.0 * value);
    }

    private double lerp(double first, double second, double amount) {
        return first + (second - first) * amount;
    }

    private void checkAllAreas() {
        for (PrisonArea area : areas.values()) {
            checkArea(area);
        }
    }

    private void showAllBorders() {
        if (!plugin.getConfig().getBoolean("show-border-particles", true)) {
            return;
        }
        for (PrisonArea area : areas.values()) {
            showBorder(area);
        }
    }

    private void showBorder(PrisonArea area) {
        World world = area.getWorld();
        if (world == null) {
            return;
        }
        Particle particle;
        try {
            particle = Particle.valueOf(plugin.getConfig().getString("border-particle", "END_ROD").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            particle = Particle.END_ROD;
        }
        int spacing = Math.max(1, plugin.getConfig().getInt("border-particle-spacing", 2));
        int minX = area.getMinX();
        int minY = area.getMinY();
        int minZ = area.getMinZ();
        int maxX = area.getMaxX();
        int maxY = area.getMaxY();
        int maxZ = area.getMaxZ();

        for (int x = minX; x <= maxX; x += spacing) {
            spawnBorderParticle(world, particle, x + 0.5, minY - 0.05, minZ - 0.05);
            spawnBorderParticle(world, particle, x + 0.5, minY - 0.05, maxZ + 1.05);
            spawnBorderParticle(world, particle, x + 0.5, maxY + 1.05, minZ - 0.05);
            spawnBorderParticle(world, particle, x + 0.5, maxY + 1.05, maxZ + 1.05);
        }
        for (int z = minZ; z <= maxZ; z += spacing) {
            spawnBorderParticle(world, particle, minX - 0.05, minY - 0.05, z + 0.5);
            spawnBorderParticle(world, particle, maxX + 1.05, minY - 0.05, z + 0.5);
            spawnBorderParticle(world, particle, minX - 0.05, maxY + 1.05, z + 0.5);
            spawnBorderParticle(world, particle, maxX + 1.05, maxY + 1.05, z + 0.5);
        }
        for (int y = minY; y <= maxY; y += spacing) {
            spawnBorderParticle(world, particle, minX - 0.05, y + 0.5, minZ - 0.05);
            spawnBorderParticle(world, particle, minX - 0.05, y + 0.5, maxZ + 1.05);
            spawnBorderParticle(world, particle, maxX + 1.05, y + 0.5, minZ - 0.05);
            spawnBorderParticle(world, particle, maxX + 1.05, y + 0.5, maxZ + 1.05);
        }
    }

    private void spawnBorderParticle(World world, Particle particle, double x, double y, double z) {
        world.spawnParticle(particle, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
    }

    public void checkAreaSoon(PrisonArea area) {
        if (!pendingIntegrityChecks.add(area.getName())) {
            return;
        }
        long taskRevision = areaRevision;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingIntegrityChecks.remove(area.getName());
            if (taskRevision == areaRevision) {
                checkArea(area);
            }
        }, 2L);
    }

    private void checkArea(PrisonArea area) {
        if (area.isResetting() || !hasSnapshot(area)) {
            return;
        }
        int current = countCurrentSolidBlocks(area);
        int template = getTemplateSolidCount(area);
        double threshold = plugin.getConfig().getDouble("reset-threshold-percent", 40.0);
        if (current * 100.0 < template * threshold) {
            startReset(area);
        }
    }

    private void startReset(PrisonArea area) {
        Map<String, BlockSnapshot> snapshot = snapshots.get(area.getName());
        World world = area.getWorld();
        if (snapshot == null || world == null) {
            return;
        }
        if (!activeResets.add(area.getName())) {
            return;
        }
        area.setResetting(true);
        List<Map.Entry<String, BlockSnapshot>> entries = new ArrayList<>(snapshot.entrySet());
        int blocksPerTick = Math.max(1, plugin.getConfig().getInt("reset-blocks-per-tick", 500));

        new BukkitRunnable() {
            private int index;

            @Override
            public void run() {
                int limit = Math.min(index + blocksPerTick, entries.size());
                for (; index < limit; index++) {
                    Map.Entry<String, BlockSnapshot> entry = entries.get(index);
                    int[] coordinates = parseKey(entry.getKey());
                    Block block = world.getBlockAt(coordinates[0], coordinates[1], coordinates[2]);
                    BlockSnapshot state = entry.getValue();
                    try {
                        block.setType(Material.valueOf(state.material()), false);
                        block.setBlockData(Bukkit.createBlockData(state.blockData()), false);
                    } catch (IllegalArgumentException exception) {
                        plugin.getLogger().warning("Could not restore a block in prison '" + area.getName() + "': " + exception.getMessage());
                    }
                }
                if (index >= entries.size()) {
                    area.setResetting(false);
                    activeResets.remove(area.getName());
                    cancel();
                    world.getPlayers().stream()
                            .filter(player -> area.contains(player.getLocation()))
                            .forEach(player -> player.sendMessage("§aThe " + area.getName() + " mine has regenerated."));
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void saveSnapshot(PrisonArea area) {
        File directory = new File(plugin.getDataFolder(), "snapshots");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Could not create the snapshots directory.");
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("solid-blocks", getTemplateSolidCount(area));
        for (Map.Entry<String, BlockSnapshot> entry : snapshots.get(area.getName()).entrySet()) {
            String path = "blocks." + entry.getKey();
            yaml.set(path + ".material", entry.getValue().material());
            yaml.set(path + ".data", entry.getValue().blockData());
        }
        try {
            yaml.save(snapshotFile(area));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save the snapshot for prison '" + area.getName() + "'.", exception);
        }
    }

    private void loadSnapshot(PrisonArea area) {
        File file = snapshotFile(area);
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection blocks = yaml.getConfigurationSection("blocks");
        if (blocks == null) {
            return;
        }
        Map<String, BlockSnapshot> snapshot = new HashMap<>(blocks.getKeys(false).size());
        for (String key : blocks.getKeys(false)) {
            String material = blocks.getString(key + ".material");
            String data = blocks.getString(key + ".data");
            if (material != null && data != null) {
                snapshot.put(key, new BlockSnapshot(material, data));
            }
        }
        snapshots.put(area.getName(), snapshot);
        templateSolidCounts.put(area.getName(), yaml.getInt("solid-blocks", 0));
    }

    private File snapshotFile(PrisonArea area) {
        return new File(new File(plugin.getDataFolder(), "snapshots"), area.getName() + ".yml");
    }

    private String key(int x, int y, int z) {
        return x + "_" + y + "_" + z;
    }

    private int[] parseKey(String key) {
        String[] split = key.split("_");
        return new int[]{Integer.parseInt(split[0]), Integer.parseInt(split[1]), Integer.parseInt(split[2])};
    }
}
