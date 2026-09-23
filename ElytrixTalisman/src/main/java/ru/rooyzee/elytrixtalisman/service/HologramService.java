package ru.rooyzee.elytrixtalisman.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;
import ru.rooyzee.elytrixtalisman.util.ColorUtil;
import ru.rooyzee.elytrixtalisman.util.PlaceholderUtil;

/**
 * Табло над талисманом.
 *
 * Строки — невидимые армор-стенды с подписью. Это настоящие сущности мира, поэтому они
 * переживают всё, что переживает мир: выгрузку чанка, падение сервера, /reload. Отсюда
 * и брались «залипшие» таблички, которые оставались висеть после ивента: ссылки на них
 * жили только в памяти, и стоило процессу умереть — убирать их было уже нечем.
 *
 * Поэтому каждая строка помечается меткой в PersistentDataContainer. Метка остаётся на
 * сущности навсегда, так что найти и снести чужие таблички можно даже после перезапуска,
 * не зная о них ничего заранее.
 */
public class HologramService {

    /** Метка «это строка табло талисмана». Переживает перезапуск вместе с сущностью. */
    private final NamespacedKey markerKey;

    private final Plugin plugin;
    private final List<ArmorStand> lines = new ArrayList<>();
    private final List<UUID> lineIds = new ArrayList<>();

    /** Чанк, удерживаемый от выгрузки, пока висит табло. */
    private Chunk heldChunk;
    private List<String> template;
    private Location base;

    public HologramService(Plugin plugin) {
        this.plugin = plugin;
        this.markerKey = new NamespacedKey(plugin, "talisman_hologram");
    }

    public void create(Location baseLocation, List<String> template) {
        destroy();
        if (baseLocation == null || baseLocation.getWorld() == null) return;

        this.template = template;
        this.base = baseLocation.clone();

        // Держим чанк загруженным на всё время ивента.
        //
        // Строки помечены setPersistent(false), чтобы не остаться в мире после падения
        // сервера. Обратная сторона: при выгрузке чанка такие сущности удаляются
        // НАСОВСЕМ. Игрок умирал, улетал на спавн, рядом с талисманом никого не
        // оставалось — чанк выгружался и уносил табло с собой. Тикет это предотвращает.
        holdChunk(baseLocation);

        double y = baseLocation.getY();
        for (int i = template.size() - 1; i >= 0; i--) {
            Location loc = new Location(baseLocation.getWorld(),
                    baseLocation.getX() + 0.5, y, baseLocation.getZ() + 0.5);
            ArmorStand as = (ArmorStand) baseLocation.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
            as.setVisible(false);
            as.setGravity(false);
            as.setMarker(true);
            as.setSmall(true);
            as.setCustomNameVisible(true);
            as.setCustomName(ColorUtil.colorize(template.get(i)));
            as.setInvulnerable(true);
            // Не сохранять в мире: даже если снос не отработает, после перезапуска
            // сервера таблички не вернутся.
            as.setPersistent(false);
            as.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
            lines.add(0, as);
            lineIds.add(0, as.getUniqueId());
            y += 0.3;
        }
    }

    /** Ставит тикет удержания чанка, чтобы непостоянные строки не выгрузились. */
    private void holdChunk(Location at) {
        try {
            Chunk chunk = at.getChunk();
            chunk.addPluginChunkTicket(plugin);
            heldChunk = chunk;
        } catch (Throwable t) {
            plugin.getLogger().warning("Не удалось удержать чанк табло: " + t.getMessage());
        }
    }

    private void releaseChunk() {
        if (heldChunk == null) return;
        try {
            heldChunk.removePluginChunkTicket(plugin);
        } catch (Throwable ignored) {
        }
        heldChunk = null;
    }

    public void update(Map<String, String> placeholders) {
        // Табло могли снести извне: выгрузка чанка, /kill @e, чужой плагин. Тогда
        // восстанавливаем его на месте, вместо того чтобы молча остаться без табло.
        if (template != null && base != null && !isAlive()) {
            List<String> saved = template;
            Location savedBase = base.clone();
            create(savedBase, saved);
        }
        if (template == null || lines.isEmpty()) return;
        for (int i = 0; i < lines.size() && i < template.size(); i++) {
            ArmorStand as = lines.get(i);
            if (as == null || as.isDead() || !as.isValid()) continue;
            as.setCustomName(ColorUtil.colorize(
                    PlaceholderUtil.replace(template.get(i), placeholders)));
        }
    }

    /**
     * Убирает табло.
     *
     * Сначала по прямым ссылкам, затем контрольным проходом по меткам вокруг точки:
     * ссылка могла протухнуть, если сущность выгружалась вместе с чанком и вернулась
     * уже другим объектом. Без второго прохода такая строка оставалась висеть навсегда.
     */
    public void destroy() {
        for (ArmorStand as : lines) {
            if (as != null && !as.isDead()) {
                try {
                    as.remove();
                } catch (Throwable ignored) {
                }
            }
        }
        lines.clear();

        releaseChunk();

        if (base != null && base.getWorld() != null) {
            sweep(base, 8);
        }
        lineIds.clear();
        template = null;
        base = null;
    }

    /**
     * Сносит все помеченные таблички рядом с точкой.
     *
     * Чанки принудительно подгружаются: в выгруженном чанке сущностей не видно, и
     * табличка пережила бы уборку, чтобы всплыть при следующем заходе игрока.
     */
    public void sweep(Location center, int radius) {
        if (center == null || center.getWorld() == null) return;

        int cx = center.getBlockX() >> 4;
        int cz = center.getBlockZ() >> 4;
        int chunkRadius = Math.max(1, radius / 16 + 1);

        for (int x = cx - chunkRadius; x <= cx + chunkRadius; x++) {
            for (int z = cz - chunkRadius; z <= cz + chunkRadius; z++) {
                try {
                    Chunk chunk = center.getWorld().getChunkAt(x, z);
                    boolean wasLoaded = chunk.isLoaded();
                    if (!wasLoaded) chunk.load();

                    for (Entity entity : chunk.getEntities()) {
                        if (!(entity instanceof ArmorStand)) continue;
                        if (!entity.getPersistentDataContainer()
                                .has(markerKey, PersistentDataType.BYTE)) continue;
                        entity.remove();
                    }
                } catch (Throwable t) {
                    plugin.getLogger().warning("Не удалось прибрать табло в чанке "
                            + x + ";" + z + ": " + t.getMessage());
                }
            }
        }
    }

    /** Есть ли живое табло: если строки пропали, ивент должен уметь это заметить. */
    public boolean isAlive() {
        if (lines.isEmpty()) return false;
        for (ArmorStand as : lines) {
            if (as != null && !as.isDead() && as.isValid()) return true;
        }
        return false;
    }

    public Location getBase() {
        return base == null ? null : base.clone();
    }
}
