package ru.rooyzee.elytrixtalisman.service;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import ru.rooyzee.elytrixtalisman.config.ConfigManager;

import java.util.concurrent.ThreadLocalRandom;

public class LocationService {

    private final String worldName;
    private final int xRadius;
    private final int zRadius;
    private final int minY;
    private static final int MAX_ATTEMPTS = 500;

    public LocationService(ConfigManager configManager) {
        this.worldName = configManager.getConfig().getString("world", "world");
        this.xRadius = configManager.getConfig().getInt("search-area.x-radius", 14000);
        this.zRadius = configManager.getConfig().getInt("search-area.z-radius", 14000);
        this.minY = configManager.getConfig().getInt("search-area.min-y", 60);
    }

    public Location findSafeLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            int x = ThreadLocalRandom.current().nextInt(-xRadius, xRadius + 1);
            int z = ThreadLocalRandom.current().nextInt(-zRadius, zRadius + 1);
            int y = findSurfaceY(world, x, z);
            if (y < minY) continue;

            Location loc = new Location(world, x, y, z);
            if (isValid(loc)) return loc;
        }
        return null;
    }

    private int findSurfaceY(World world, int x, int z) {
        for (int y = 255; y >= minY; y--) {
            Location loc = new Location(world, x, y, z);
            Material type = loc.getBlock().getType();
            if ((type == Material.GRASS_BLOCK || type == Material.SAND || type == Material.STONE
                    || type == Material.DIRT || type == Material.GRAVEL)
                    && loc.clone().add(0, 1, 0).getBlock().isPassable()
                    && loc.clone().add(0, 2, 0).getBlock().isPassable()) {
                return y + 1;
            }
        }
        return minY - 1;
    }

    private boolean isValid(Location location) {
        if (!location.getBlock().isPassable()) return false;
        if (!location.clone().add(0, 1, 0).getBlock().isPassable()) return false;

        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            if (!location.getBlock().getRelative(face).isPassable()) return false;
        }

        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();
        ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(location));
        return set.getRegions().isEmpty();
    }
}