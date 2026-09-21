package ru.rooyzee.elytrixtalisman.service;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import ru.rooyzee.elytrixtalisman.config.ConfigManager;

import java.util.List;

public class RegionService {

    private final int radius;
    private final List<String> allowFlags;
    private final List<String> denyFlags;

    public RegionService(ConfigManager configManager) {
        this.radius = configManager.getRegion().getInt("radius", 35);
        this.allowFlags = configManager.getRegion().getStringList("allow-flags");
        this.denyFlags = configManager.getRegion().getStringList("deny-flags");
    }

    public void create(Location center, String regionId) {
        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(center.getWorld());
        BlockVector3 min = BlockVector3.at(center.getBlockX() - radius, 0, center.getBlockZ() - radius);
        BlockVector3 max = BlockVector3.at(center.getBlockX() + radius, 255, center.getBlockZ() + radius);
        ProtectedCuboidRegion region = new ProtectedCuboidRegion(regionId, min, max);
        RegionManager rm = WorldGuard.getInstance().getPlatform().getRegionContainer().get(weWorld);
        if (rm == null) return;
        rm.addRegion(region);

        String worldName = center.getWorld().getName();
        for (String flag : allowFlags) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "region flag -w " + worldName + " " + regionId + " " + flag + " allow");
        }
        for (String flag : denyFlags) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "region flag -w " + worldName + " " + regionId + " " + flag + " deny");
        }
    }

    public void remove(Location center, String regionId) {
        if (center == null || center.getWorld() == null) return;
        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(center.getWorld());
        RegionManager rm = WorldGuard.getInstance().getPlatform().getRegionContainer().get(weWorld);
        if (rm != null) {
            rm.removeRegion(regionId);
        }
    }

    public boolean isInRegion(Player player, String regionId) {
        Location loc = player.getLocation();
        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(loc.getWorld());
        RegionManager rm = WorldGuard.getInstance().getPlatform().getRegionContainer().get(weWorld);
        if (rm == null) return false;
        ApplicableRegionSet set = rm.getApplicableRegions(
                BlockVector3.at(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
        return set.getRegions().stream().anyMatch(r -> r.getId().equalsIgnoreCase(regionId));
    }
}