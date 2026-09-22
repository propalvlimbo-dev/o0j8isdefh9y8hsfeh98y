package ru.rooyzee.elytrixtalisman.service;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BaseBlock;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;
import ru.rooyzee.elytrixtalisman.Main;

/**
 * Постройка и снос башни талисмана.
 *
 * Перед вставкой схематики сохраняется слепок участка («как было»), и именно он потом
 * возвращается на место. Слепок пишется НЕ только в память, но и на диск:
 *
 *     plugins/ElytrixTalisman/restore/pending.schem  — блоки
 *     plugins/ElytrixTalisman/restore/pending.yml    — мир и координаты
 *
 * Без файла любое падение сервера во время ивента оставляло башню в мире навсегда:
 * слепок жил только в оперативной памяти и терялся вместе с процессом. Теперь при
 * следующем запуске плагин видит незакрытый файл и молча доигрывает уборку.
 */
public class SchematicService {

    private final Main plugin;
    private Clipboard backupClipboard;
    private BlockVector3 backupTo;
    private World backupWorld;

    /** Задача плавного сноса. Держим ссылку, чтобы доломать мгновенно при выключении. */
    private BukkitTask demolishTask;

    public SchematicService(Main plugin) {
        this.plugin = plugin;
    }

    // --- Постройка ---------------------------------------------------------------------

    public boolean paste(Location loc, String fileName, int offsetX, int offsetY, int offsetZ) {
        File file = new File(plugin.getDataFolder() + File.separator + "schematics", fileName);
        if (!file.exists()) {
            plugin.getLogger().warning("Schematic not found: " + fileName);
            return false;
        }

        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) {
            plugin.getLogger().warning("Unknown schematic format: " + fileName);
            return false;
        }

        try (FileInputStream fis = new FileInputStream(file);
             ClipboardReader reader = format.getReader(fis)) {

            Clipboard clipboard = reader.read();
            World weWorld = BukkitAdapter.adapt(loc.getWorld());

            BlockVector3 to = BlockVector3.at(
                    loc.getBlockX() + offsetX,
                    loc.getBlockY() + offsetY,
                    loc.getBlockZ() + offsetZ);

            BlockVector3 dim = clipboard.getDimensions();
            BlockVector3 origin = clipboard.getOrigin();
            BlockVector3 minPoint = clipboard.getMinimumPoint();
            BlockVector3 offset = minPoint.subtract(origin);

            BlockVector3 worldMin = to.add(offset);
            BlockVector3 worldMax = worldMin.add(dim.subtract(BlockVector3.ONE));

            CuboidRegion region = new CuboidRegion(weWorld, worldMin, worldMax);
            BlockArrayClipboard backup = new BlockArrayClipboard(region);
            backup.setOrigin(to);

            try (EditSession readSession = WorldEdit.getInstance().getEditSessionFactory().getEditSession(weWorld, -1)) {
                ForwardExtentCopy copy = new ForwardExtentCopy(readSession, region, backup, region.getMinimumPoint());
                Operations.complete(copy);
            }

            this.backupClipboard = backup;
            this.backupTo = to;
            this.backupWorld = weWorld;
            savePending(backup, weWorld, to);

            try (EditSession editSession = WorldEdit.getInstance().getEditSessionFactory().getEditSession(weWorld, -1)) {
                ClipboardHolder holder = new ClipboardHolder(clipboard);
                Operation op = holder.createPaste(editSession).to(to).ignoreAirBlocks(false).build();
                Operations.complete(op);
                editSession.flushSession();
            }
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to paste schematic: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // --- Снос --------------------------------------------------------------------------

    /** Мгновенный возврат участка. Используется при выключении и как запасной путь. */
    public void restore() {
        cancelDemolish();
        if (backupClipboard == null || backupTo == null || backupWorld == null) {
            clearPending();
            return;
        }
        try (EditSession editSession = WorldEdit.getInstance().getEditSessionFactory().getEditSession(backupWorld, -1)) {
            ClipboardHolder holder = new ClipboardHolder(backupClipboard);
            Operation op = holder.createPaste(editSession).to(backupTo).ignoreAirBlocks(false).build();
            Operations.complete(op);
            editSession.flushSession();
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to restore schematic area: " + e.getMessage());
        }
        forget();
    }

    /**
     * Плавный снос сверху вниз: каждый тик возвращается несколько слоёв по Y, поэтому
     * башня оседает, а не исчезает целиком за один кадр.
     *
     * Слои идут именно сверху вниз — так выглядит обрушением, а не проваливанием сквозь
     * землю. Возвращается при этом не воздух, а исходное содержимое участка: если башня
     * стояла на холме, холм останется на месте.
     *
     * @param layersPerTick сколько слоёв убирать за тик
     * @param onDone        что сделать после окончания (может быть null)
     */
    public void restoreAnimated(int layersPerTick, int tickInterval, Runnable onDone) {
        cancelDemolish();
        if (backupClipboard == null || backupTo == null || backupWorld == null) {
            clearPending();
            if (onDone != null) onDone.run();
            return;
        }

        final Clipboard backup = backupClipboard;
        final World world = backupWorld;
        final BlockVector3 min = backup.getRegion().getMinimumPoint();
        final BlockVector3 max = backup.getRegion().getMaximumPoint();
        final int step = Math.max(1, layersPerTick);
        final int period = Math.max(1, tickInterval);
        final int[] y = {max.getBlockY()};

        demolishTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int processed = 0;
            while (processed < step && y[0] >= min.getBlockY()) {
                restoreLayer(world, backup, min, max, y[0]);
                y[0]--;
                processed++;
            }
            if (y[0] < min.getBlockY()) {
                cancelDemolish();
                forget();
                if (onDone != null) onDone.run();
            }
        }, 0L, period);
    }

    /** Возврат одного горизонтального среза + пыль и звук по его краю. */
    private void restoreLayer(World world, Clipboard backup, BlockVector3 min, BlockVector3 max, int y) {
        org.bukkit.World bukkitWorld = BukkitAdapter.adapt(world);
        boolean brokeSomething = false;

        try (EditSession session = WorldEdit.getInstance().getEditSessionFactory().getEditSession(world, -1)) {
            for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
                for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                    BlockVector3 pos = BlockVector3.at(x, y, z);
                    BaseBlock was = backup.getFullBlock(pos);

                    // Показываем частицы только там, где реально что-то сносим:
                    // если в слепке и в мире одно и то же, слой визуально пустой.
                    if (!brokeSomething && bukkitWorld != null) {
                        Material current = bukkitWorld.getBlockAt(x, y, z).getType();
                        boolean wasAir = was.getBlockType() == null
                                || was.getBlockType().getMaterial().isAir();
                        if (current != Material.AIR && wasAir) brokeSomething = true;
                    }

                    session.setBlock(pos, was);
                }
            }
            session.flushSession();
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to restore layer " + y + ": " + e.getMessage());
        }

        if (brokeSomething && bukkitWorld != null) {
            effects(bukkitWorld, min, max, y);
        }
    }

    /** Облако пыли по периметру слоя и глухой звук осыпающегося камня. */
    private void effects(org.bukkit.World world, BlockVector3 min, BlockVector3 max, int y) {
        double cx = (min.getBlockX() + max.getBlockX()) / 2.0 + 0.5;
        double cz = (min.getBlockZ() + max.getBlockZ()) / 2.0 + 0.5;
        double rx = (max.getBlockX() - min.getBlockX()) / 2.0;
        double rz = (max.getBlockZ() - min.getBlockZ()) / 2.0;
        double radius = Math.max(1.0, Math.max(rx, rz));

        Location center = new Location(world, cx, y + 0.5, cz);
        int points = 24;
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2 / points) * i;
            Location at = center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            world.spawnParticle(Particle.BLOCK_CRACK, at, 6, 0.3, 0.3, 0.3, 0.05,
                    org.bukkit.Material.STONE.createBlockData());
            world.spawnParticle(Particle.SMOKE_NORMAL, at, 3, 0.2, 0.2, 0.2, 0.01);
        }
        world.playSound(center, Sound.BLOCK_STONE_BREAK, 1.2f, 0.6f);
    }

    public boolean isDemolishing() {
        return demolishTask != null;
    }

    private void cancelDemolish() {
        if (demolishTask != null) {
            try {
                demolishTask.cancel();
            } catch (Throwable ignored) {
            }
            demolishTask = null;
        }
    }

    /** Есть ли что сносить: нужно, чтобы не гонять анимацию впустую. */
    public boolean hasBackup() {
        return backupClipboard != null && backupTo != null && backupWorld != null;
    }

    private void forget() {
        backupClipboard = null;
        backupTo = null;
        backupWorld = null;
        clearPending();
    }

    // --- Незакрытая уборка на диске ------------------------------------------------------

    private File restoreFolder() {
        return new File(plugin.getDataFolder(), "restore");
    }

    private File pendingSchem() {
        return new File(restoreFolder(), "pending.schem");
    }

    private File pendingMeta() {
        return new File(restoreFolder(), "pending.yml");
    }

    /** Сохраняет слепок на диск, чтобы пережить перезапуск и падение сервера. */
    private void savePending(Clipboard backup, World world, BlockVector3 to) {
        try {
            File folder = restoreFolder();
            if (!folder.exists() && !folder.mkdirs()) return;

            try (FileOutputStream out = new FileOutputStream(pendingSchem());
                 ClipboardWriter writer = BuiltInClipboardFormat.SPONGE_SCHEMATIC.getWriter(out)) {
                writer.write(backup);
            }

            YamlConfiguration meta = new YamlConfiguration();
            meta.set("world", world.getName());
            meta.set("x", to.getBlockX());
            meta.set("y", to.getBlockY());
            meta.set("z", to.getBlockZ());
            meta.save(pendingMeta());
        } catch (IOException e) {
            plugin.getLogger().warning("Не удалось сохранить слепок для отката: " + e.getMessage());
        }
    }

    private void clearPending() {
        File schem = pendingSchem();
        File meta = pendingMeta();
        if (schem.exists() && !schem.delete()) schem.deleteOnExit();
        if (meta.exists() && !meta.delete()) meta.deleteOnExit();
    }

    /**
     * Доигрывает уборку, прерванную падением сервера. Зовётся один раз при включении:
     * если прошлый запуск не успел снести башню, она исчезнет сразу после старта.
     */
    public void restorePendingOnStartup() {
        File schem = pendingSchem();
        File meta = pendingMeta();
        if (!schem.exists() || !meta.exists()) return;

        try {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(meta);
            org.bukkit.World bukkitWorld = Bukkit.getWorld(cfg.getString("world", ""));
            if (bukkitWorld == null) {
                plugin.getLogger().warning("Мир для отката не найден, слепок пропущен");
                clearPending();
                return;
            }

            ClipboardFormat format = ClipboardFormats.findByFile(schem);
            if (format == null) {
                clearPending();
                return;
            }

            try (FileInputStream fis = new FileInputStream(schem);
                 ClipboardReader reader = format.getReader(fis)) {
                Clipboard clipboard = reader.read();
                World weWorld = BukkitAdapter.adapt(bukkitWorld);
                BlockVector3 to = BlockVector3.at(
                        cfg.getInt("x"), cfg.getInt("y"), cfg.getInt("z"));

                try (EditSession session = WorldEdit.getInstance().getEditSessionFactory().getEditSession(weWorld, -1)) {
                    ClipboardHolder holder = new ClipboardHolder(clipboard);
                    Operation op = holder.createPaste(session).to(to).ignoreAirBlocks(false).build();
                    Operations.complete(op);
                    session.flushSession();
                }
            }
            plugin.getLogger().info("Башня талисмана с прошлого запуска убрана");
        } catch (Exception e) {
            plugin.getLogger().warning("Не удалось доиграть откат: " + e.getMessage());
        }
        clearPending();
    }
}
