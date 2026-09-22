package ru.rooyzee.elytrixtalisman.service;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import ru.rooyzee.elytrixtalisman.config.ConfigManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RegionService {

    /** Общий префикс всех регионов ивента — по нему находим мусор от прошлых запусков. */
    public static final String REGION_PREFIX = "elytrix_";

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

    /**
     * Удаляет регион ивента.
     *
     * Локация здесь только подсказка: раньше при center == null метод молча выходил и
     * регион оставался в мире навсегда. А center как раз и равен null, когда ивент падает
     * до выбора точки или сессия уже очищена. Поэтому если по локации мир не определить,
     * регион ищется во всех загруженных мирах по идентификатору.
     */
    public void remove(Location center, String regionId) {
        if (regionId == null) return;

        if (center != null && center.getWorld() != null) {
            RegionManager rm = manager(center.getWorld());
            if (rm != null && rm.hasRegion(regionId)) {
                rm.removeRegion(regionId);
                save(rm);
                return;
            }
        }

        for (org.bukkit.World world : Bukkit.getWorlds()) {
            RegionManager rm = manager(world);
            if (rm != null && rm.hasRegion(regionId)) {
                rm.removeRegion(regionId);
                save(rm);
                return;
            }
        }
    }

    /**
     * Сносит все регионы ивента, оставшиеся от прошлых запусков.
     *
     * Идентификаторы имеют вид elytrix_<случайные 8 символов>, поэтому после падения
     * сервера в мире копятся мёртвые регионы: имя каждой новой сессии другое, и обычное
     * удаление до них уже не дотянется. Зовётся при включении плагина.
     *
     * @return сколько регионов удалено
     */
    public int removeStale(String activeRegionId) {
        int removed = 0;
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            RegionManager rm = manager(world);
            if (rm == null) continue;

            List<String> stale = new ArrayList<>();
            for (Map.Entry<String, ProtectedRegion> entry : rm.getRegions().entrySet()) {
                String id = entry.getKey();
                if (id == null || !id.toLowerCase().startsWith(REGION_PREFIX)) continue;
                if (activeRegionId != null && id.equalsIgnoreCase(activeRegionId)) continue;
                stale.add(id);
            }
            for (String id : stale) {
                rm.removeRegion(id);
                removed++;
            }
            if (!stale.isEmpty()) save(rm);
        }
        return removed;
    }

    private RegionManager manager(org.bukkit.World world) {
        if (world == null) return null;
        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);
        return WorldGuard.getInstance().getPlatform().getRegionContainer().get(weWorld);
    }

    /** Сразу пишем регионы на диск: иначе падение сервера вернёт удалённый регион. */
    private void save(RegionManager rm) {
        try {
            rm.save();
        } catch (Exception e) {
            Bukkit.getLogger().warning("[ElytrixTalisman] Не удалось сохранить регионы: " + e.getMessage());
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