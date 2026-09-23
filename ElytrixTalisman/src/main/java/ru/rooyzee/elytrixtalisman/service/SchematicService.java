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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
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
     * Рваное обрушение сверху вниз.
     *
     * Ровными слоями башня «утопала» в землю — это читалось как лифт, а не как
     * разрушение. Теперь у каждой колонки (x, z) своя высота фронта, заданная шумом от
     * координат: кромка осыпается неровно, где-то остаются зубцы, где-то провалы.
     *
     * Вдобавок за один шаг колонка съедает случайную глубину, поэтому куски отваливаются
     * кусками, а не ровным рядом. Возвращается не воздух, а исходное содержимое участка:
     * если башня стояла на холме, холм останется.
     *
     * @param layersPerTick средняя глубина обрушения за шаг
     * @param tickInterval  пауза между шагами в тиках
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

        final int minX = min.getBlockX(), maxX = max.getBlockX();
        final int minZ = min.getBlockZ(), maxZ = max.getBlockZ();
        final int minY = min.getBlockY(), maxY = max.getBlockY();
        final int width = maxX - minX + 1;
        final int depth = maxZ - minZ + 1;

        // Высота фронта для каждой колонки. Стартовые значения разные, поэтому
        // обрушение начинается рваной кромкой, а не единым срезом.
        final int[] front = new int[width * depth];
        final long seed = System.nanoTime();
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                front[x * depth + z] = maxY - (int) (noise(seed, x, z) * jitter);
            }
        }

        demolishTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            boolean anything = false;
            try (EditSession session = WorldEdit.getInstance().getEditSessionFactory().getEditSession(world, -1)) {
                org.bukkit.World bukkitWorld = BukkitAdapter.adapt(world);
                int hitX = Integer.MIN_VALUE, hitY = 0, hitZ = 0, hits = 0;

                for (int x = 0; x < width; x++) {
                    for (int z = 0; z < depth; z++) {
                        int idx = x * depth + z;
                        if (front[idx] < minY) continue;
                        anything = true;

                        // Глубина укуса за шаг: разброс делает край неровным от кадра к кадру.
                        int bite = 1 + (int) (noise(seed + front[idx], x, z) * step * 2);
                        int wx = minX + x, wz = minZ + z;

                        for (int i = 0; i < bite && front[idx] >= minY; i++) {
                            int y = front[idx];
                            BlockVector3 pos = BlockVector3.at(wx, y, wz);
                            boolean had = bukkitWorld != null
                                    && !bukkitWorld.getBlockAt(wx, y, wz).getType().isAir();
                            session.setBlock(pos, backup.getFullBlock(pos));
                            front[idx]--;
                            if (had) {
                                hits++;
                                if (hitX == Integer.MIN_VALUE || (hits & 7) == 0) {
                                    hitX = wx; hitY = y; hitZ = wz;
                                }
                            }
                        }
                    }
                }
                session.flushSession();

                if (hitX != Integer.MIN_VALUE && bukkitWorld != null) {
                    rubble(bukkitWorld, hitX, hitY, hitZ, hits);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Ошибка при обрушении: " + e.getMessage());
                anything = false;
            }

            if (!anything) {
                cancelDemolish();
                forget();
                if (onDone != null) onDone.run();
            }
        }, 0L, period);
    }

    /** Насколько рваная стартовая кромка, в блоках. */
    private int jitter = 5;

    public void setJitter(int jitter) {
        this.jitter = Math.max(0, jitter);
    }

    /**
     * Детерминированный шум 0..1 от координат.
     *
     * Обычный Random не подходит: колонка должна получать одно и то же значение при
     * повторном обращении, иначе кромка дёргалась бы каждый кадр вместо устойчивого
     * осыпания.
     */
    private static double noise(long seed, int x, int z) {
        long h = seed + x * 374761393L + z * 668265263L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        h = h ^ (h >>> 16);
        return (h & 0xFFFF) / 65535.0;
    }

    /**
     * Пыль в месте обвала.
     *
     * Звуков намеренно нет: снос идёт десятки секунд, и постоянный грохот камня
     * забивал всё вокруг. Разрушение показывается только визуально.
     */
    private void rubble(org.bukkit.World world, int x, int y, int z, int intensity) {
        Location at = new Location(world, x + 0.5, y + 0.5, z + 0.5);
        int count = Math.min(30, 4 + intensity / 2);
        world.spawnParticle(Particle.BLOCK_CRACK, at, count, 1.2, 0.8, 1.2, 0.08,
                Material.STONE.createBlockData());
        world.spawnParticle(Particle.SMOKE_LARGE, at, Math.min(12, 2 + count / 3), 0.9, 0.6, 0.9, 0.02);
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
     * Точка последнего ивента, если уборка ещё не закрыта.
     *
     * Читается из того же файла, что и слепок для отката, поэтому переживает
     * перезапуск. По ней ищутся таблички, оставшиеся висеть после падения сервера.
     */
    public Location getPendingCenter() {
        File meta = pendingMeta();
        if (!meta.exists()) return null;
        try {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(meta);
            org.bukkit.World world = Bukkit.getWorld(cfg.getString("world", ""));
            if (world == null) return null;
            return new Location(world, cfg.getInt("x"), cfg.getInt("y"), cfg.getInt("z"));
        } catch (Throwable t) {
            return null;
        }
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
