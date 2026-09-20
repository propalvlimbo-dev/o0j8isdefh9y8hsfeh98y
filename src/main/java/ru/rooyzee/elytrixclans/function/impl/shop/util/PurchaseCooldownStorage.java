package ru.rooyzee.elytrixclans.function.impl.shop.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.rooyzee.elytrixclans.Main;

/**
 * Кулдаун покупок магазина: «игрок -> позиция -> когда снова можно купить».
 *
 * Длительность считается от цены товара: чем дороже (то есть чем сильнее) предмет,
 * тем дольше ждать. Наборы считаются по отдельной, более длинной шкале.
 *
 * Хранится в shop_cooldowns.yml, поэтому перезаход и рестарт сервера кулдаун не сбрасывают —
 * иначе ограничение обходилось бы релогом.
 */
public class PurchaseCooldownStorage {

    private static final String FILE_NAME = "shop_cooldowns.yml";

    /** playerUUID -> (ключ позиции -> момент истечения, epoch millis). */
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();
    private final File file;
    private volatile boolean dirty;
    private int saveTaskId = -1;

    public PurchaseCooldownStorage(Main main) {
        this.file = new File(main.getDataFolder(), FILE_NAME);
        load();
        saveTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(main, () -> {
            purgeExpired();
            if (dirty) save();
        }, 6000L, 6000L).getTaskId();
    }

    /** Сколько миллисекунд осталось до следующей покупки этой позиции (0 — можно покупать). */
    public long remaining(UUID player, String key) {
        if (player == null || key == null) return 0L;
        Map<String, Long> entries = cooldowns.get(player);
        if (entries == null) return 0L;
        Long until = entries.get(key.toLowerCase());
        if (until == null) return 0L;
        return Math.max(0L, until - System.currentTimeMillis());
    }

    /** Взводит кулдаун после успешной покупки. */
    public void mark(UUID player, String key, long durationMillis) {
        if (player == null || key == null || durationMillis <= 0) return;
        cooldowns.computeIfAbsent(player, k -> new ConcurrentHashMap<>())
                .put(key.toLowerCase(), System.currentTimeMillis() + durationMillis);
        dirty = true;
    }

    /**
     * Длительность кулдауна для обычного товара: чем дороже предмет, тем дольше.
     * Значение из поля cooldown позиции (в секундах) имеет приоритет.
     */
    public static long itemCooldownMillis(double price, int explicitSeconds) {
        if (explicitSeconds > 0) return explicitSeconds * 1000L;
        Main main = Main.getInstance();
        int min = 30;
        int max = 1800;
        double perThousand = 3.0;
        if (main != null) {
            min = Math.max(0, main.getConfig().getInt("shop.cooldown.item-min-seconds", min));
            max = Math.max(min, main.getConfig().getInt("shop.cooldown.item-max-seconds", max));
            perThousand = main.getConfig().getDouble("shop.cooldown.seconds-per-1000-coins", perThousand);
        }
        long seconds = Math.round(Math.max(0.0, price) / 1000.0 * perThousand);
        if (seconds < min) seconds = min;
        if (seconds > max) seconds = max;
        return seconds * 1000L;
    }

    /** Длительность кулдауна для набора: отдельная, заведомо более длинная шкала. */
    public static long kitCooldownMillis(double price, int explicitSeconds) {
        if (explicitSeconds > 0) return explicitSeconds * 1000L;
        Main main = Main.getInstance();
        int min = 900;
        int max = 7200;
        double perThousand = 3.0;
        if (main != null) {
            min = Math.max(0, main.getConfig().getInt("shop.cooldown.kit-min-seconds", min));
            max = Math.max(min, main.getConfig().getInt("shop.cooldown.kit-max-seconds", max));
            perThousand = main.getConfig().getDouble("shop.cooldown.seconds-per-1000-coins", perThousand);
        }
        long seconds = Math.round(Math.max(0.0, price) / 1000.0 * perThousand);
        if (seconds < min) seconds = min;
        if (seconds > max) seconds = max;
        return seconds * 1000L;
    }

    /** Человекочитаемый остаток: «1ч 05м», «12м 30с», «8с». */
    public static String format(long millis) {
        long totalSeconds = Math.max(0L, millis + 999L) / 1000L;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) return hours + "ч " + String.format("%02dм", minutes);
        if (minutes > 0) return minutes + "м " + String.format("%02dс", seconds);
        return seconds + "с";
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<UUID, Map<String, Long>>> it = cooldowns.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Map<String, Long>> entry = it.next();
            entry.getValue().entrySet().removeIf(e -> e.getValue() == null || e.getValue() <= now);
            if (entry.getValue().isEmpty()) {
                it.remove();
                dirty = true;
            }
        }
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        long now = System.currentTimeMillis();
        for (String rawId : yaml.getKeys(false)) {
            UUID player;
            try {
                player = UUID.fromString(rawId.trim());
            } catch (IllegalArgumentException e) {
                continue;
            }
            Map<String, Long> entries = new ConcurrentHashMap<>();
            for (String line : yaml.getStringList(rawId)) {
                // Формат строки: "<ключ позиции>;<epoch millis истечения>"
                int sep = line.lastIndexOf(';');
                if (sep <= 0) continue;
                String key = line.substring(0, sep).trim().toLowerCase();
                long until;
                try {
                    until = Long.parseLong(line.substring(sep + 1).trim());
                } catch (NumberFormatException e) {
                    continue;
                }
                if (!key.isEmpty() && until > now) entries.put(key, until);
            }
            if (!entries.isEmpty()) cooldowns.put(player, entries);
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Map<String, Long>> entry : new HashMap<>(cooldowns).entrySet()) {
            List<String> list = new ArrayList<>();
            for (Map.Entry<String, Long> e : entry.getValue().entrySet()) {
                if (e.getValue() == null || e.getValue() <= now) continue;
                list.add(e.getKey() + ";" + e.getValue());
            }
            if (!list.isEmpty()) yaml.set(entry.getKey().toString(), list);
        }
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) return;
            yaml.save(file);
            dirty = false;
        } catch (IOException e) {
            Main main = Main.getInstance();
            if (main != null) {
                main.getLogger().warning("Не удалось сохранить " + FILE_NAME + ": " + e.getMessage());
            }
        }
    }

    public void shutdown() {
        if (saveTaskId != -1) {
            Bukkit.getScheduler().cancelTask(saveTaskId);
            saveTaskId = -1;
        }
        purgeExpired();
        save();
    }
}
