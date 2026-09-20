package ru.rooyzee.elytrixclans.database.impl;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.rooyzee.elytrixclans.Main;
import ru.rooyzee.elytrixclans.clans.Clan;
import ru.rooyzee.elytrixclans.database.IDataBase;

public class YAMLDataBase implements IDataBase {

    private File dataFile;
    private FileConfiguration dataConfig;
    private final Object ioLock = new Object();

    /**
     * Записи из clans.yml, которые не удалось прочитать. Их нельзя выбрасывать:
     * иначе первый же автосейв перезапишет файл без этих кланов, и кланы потеряются навсегда.
     */
    private final List<Object> unparsedEntries = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void connect() {
        dataFile = new File(Main.getInstance().getDataFolder(), "clans.yml");
        try {
            if (!dataFile.exists()) {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            }
            dataConfig = YamlConfiguration.loadConfiguration(dataFile);
            if (!dataConfig.contains("clans")) {
                dataConfig.set("clans", new ArrayList<>());
                writeData(new ArrayList<>());
            } else {
                List<?> clanMapsList = dataConfig.getList("clans");
                List<Clan> clans = new ArrayList<>();
                if (clanMapsList != null) {
                    for (Object obj : clanMapsList) {
                        if (!(obj instanceof Map)) {
                            if (obj != null) unparsedEntries.add(obj);
                            continue;
                        }
                        try {
                            Clan clan = Clan.deserialize((Map<String, Object>) obj);
                            if (clan != null) {
                                clans.add(clan);
                            } else {
                                unparsedEntries.add(obj);
                            }
                        } catch (Exception e) {
                            Main.getInstance().getLogger().log(Level.WARNING,
                                    "Failed to deserialize clan (сохранён в файле без изменений): " + e.getMessage());
                            unparsedEntries.add(obj);
                        }
                    }
                }
                if (Main.getInstance().getClanManager() != null) {
                    Main.getInstance().getClanManager().setClans(clans);
                }
            }
            int count = Main.getInstance().getClanManager() != null && Main.getInstance().getClanManager().getClans() != null
                    ? Main.getInstance().getClanManager().getClans().size() : 0;
            Main.getInstance().getLogger().info("Loaded " + count + " clans from YAML");
            if (!unparsedEntries.isEmpty()) {
                Main.getInstance().getLogger().warning("ElytrixClans: " + unparsedEntries.size()
                        + " записей кланов не удалось разобрать — они сохранены в clans.yml как есть.");
            }
        } catch (IOException e) {
            Main.getInstance().getLogger().log(Level.SEVERE, "Failed to create clans.yml", e);
        } catch (Exception e) {
            Main.getInstance().getLogger().log(Level.SEVERE, "Failed to load clans data", e);
        }
    }

    @Override
    public synchronized void save() {
        writeData(collectSnapshot());
    }

    /**
     * Снимок данных делаем в main-потоке (иначе асинхронная сериализация может прочитать
     * Half-обновлённые объекты и словить ConcurrentModificationException),
     * а диск пишем уже асинхронно.
     */
    @Override
    public void saveAsync() {
        Main plugin = Main.getInstance();
        if (plugin == null || !plugin.isEnabled()) return;
        try {
            Bukkit.getScheduler().runTask(plugin, () -> {
                final List<Map<String, Object>> snapshot = collectSnapshot();
                if (!plugin.isEnabled()) return;
                try {
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> writeData(snapshot));
                } catch (IllegalStateException ignored) {
                    writeData(snapshot);
                }
            });
        } catch (IllegalStateException ignored) {
            writeData(collectSnapshot());
        }
    }

    @Override
    public synchronized void disconnect() {
        save();
    }

    private List<Map<String, Object>> collectSnapshot() {
        List<Map<String, Object>> clanMaps = new ArrayList<>();
        Main main = Main.getInstance();
        if (main == null || main.getClanManager() == null || main.getClanManager().getClans() == null) {
            return clanMaps;
        }
        for (Clan clan : main.getClanManager().getClans()) {
            try {
                if (clan != null) {
                    Map<String, Object> serialized = clan.serialize();
                    if (serialized != null) {
                        clanMaps.add(serialized);
                    }
                }
            } catch (Exception e) {
                if (main.getLogger() != null) {
                    main.getLogger().log(Level.WARNING, "Failed to serialize clan: " + e.getMessage());
                }
            }
        }
        return clanMaps;
    }

    private void writeData(List<Map<String, Object>> clanMaps) {
        synchronized (ioLock) {
            try {
                if (dataConfig == null || dataFile == null) return;
                List<Object> out = new ArrayList<>(clanMaps.size() + unparsedEntries.size());
                out.addAll(clanMaps);
                synchronized (unparsedEntries) {
                    out.addAll(unparsedEntries);
                }
                dataConfig.set("clans", out);
                saveAtomically(dataConfig, dataFile);
            } catch (Exception e) {
                Main main = Main.getInstance();
                if (main != null) {
                    main.getLogger().log(Level.SEVERE, "Failed to save clans.yml", e);
                } else {
                    Bukkit.getLogger().log(Level.SEVERE, "[ElytrixClans] Failed to save clans.yml", e);
                }
            }
        }
    }

    /**
     * Запись через временный файл + атомарная замена, плюс копия clans.yml.bak.
     * Обычный FileConfiguration#save() пишет поверх файла: если сервер убьют посреди записи,
     * clans.yml остаётся обрезанным и кланы теряются.
     */
    private void saveAtomically(FileConfiguration config, File target) throws IOException {
        File folder = target.getParentFile();
        File temp = new File(folder, target.getName() + ".tmp");
        config.save(temp);

        File backup = new File(folder, target.getName() + ".bak");
        if (target.exists() && target.length() > 0) {
            try {
                Files.copy(target.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
            }
        }

        try {
            Files.move(temp.toPath(), target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException | UnsupportedOperationException e) {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            try {
                if (temp.exists()) temp.delete();
            } catch (Exception ignored) {
            }
        }
    }
}