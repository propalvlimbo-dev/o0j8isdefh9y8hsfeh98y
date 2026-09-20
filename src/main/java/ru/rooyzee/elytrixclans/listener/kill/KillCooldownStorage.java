package ru.rooyzee.elytrixclans.listener.kill;

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
 * Кулдаун повторного убийства «убийца -> жертва».
 *
 * Раньше пары хранились в обычной HashMap внутри листенера и жили только до рестарта:
 * перезапуск сервера обнулял кулдаун, и опыт можно было фармить убийством одного и того же
 * игрока. Теперь пары пишутся в kill_cooldowns.yml и переживают перезапуск.
 */
public class KillCooldownStorage {

    private static final String FILE_NAME = "kill_cooldowns.yml";

    /** killerUUID -> (victimUUID -> момент истечения, epoch millis). */
    private final Map<UUID, Map<UUID, Long>> cooldowns = new ConcurrentHashMap<>();
    private final File file;
    private volatile boolean dirty;
    private int saveTaskId = -1;

    public KillCooldownStorage(Main main) {
        this.file = new File(main.getDataFolder(), FILE_NAME);
        load();
        // Периодический сброс на диск + чистка протухших записей, чтобы файл не рос вечно.
        saveTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(main, () -> {
            purgeExpired();
            if (dirty) save();
        }, 6000L, 6000L).getTaskId();
    }

    /**
     * @return true, если убийство засчитывается (кулдаун не активен). В этом случае кулдаун
     *         сразу же взводится заново.
     */
    public boolean tryRegisterKill(UUID killer, UUID victim, long cooldownMillis) {
        if (killer == null || victim == null) return false;
        long now = System.currentTimeMillis();
        Map<UUID, Long> victims = cooldowns.computeIfAbsent(killer, k -> new ConcurrentHashMap<>());
        Long until = victims.get(victim);
        if (until != null && until > now) {
            return false;
        }
        victims.put(victim, now + Math.max(0L, cooldownMillis));
        dirty = true;
        return true;
    }

    /** Сколько миллисекунд осталось до повторного засчитывания убийства (0 — можно уже сейчас). */
    public long remaining(UUID killer, UUID victim) {
        Map<UUID, Long> victims = cooldowns.get(killer);
        if (victims == null) return 0L;
        Long until = victims.get(victim);
        if (until == null) return 0L;
        return Math.max(0L, until - System.currentTimeMillis());
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<UUID, Map<UUID, Long>>> it = cooldowns.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Map<UUID, Long>> entry = it.next();
            Map<UUID, Long> victims = entry.getValue();
            victims.entrySet().removeIf(e -> e.getValue() == null || e.getValue() <= now);
            if (victims.isEmpty()) {
                it.remove();
                dirty = true;
            }
        }
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        long now = System.currentTimeMillis();
        for (String killerRaw : yaml.getKeys(false)) {
            UUID killer = parseUuid(killerRaw);
            if (killer == null) continue;
            List<String> entries = yaml.getStringList(killerRaw);
            Map<UUID, Long> victims = new ConcurrentHashMap<>();
            for (String entry : entries) {
                // Формат строки: "<uuid жертвы>;<epoch millis истечения>"
                int sep = entry.lastIndexOf(';');
                if (sep <= 0) continue;
                UUID victim = parseUuid(entry.substring(0, sep));
                if (victim == null) continue;
                long until;
                try {
                    until = Long.parseLong(entry.substring(sep + 1).trim());
                } catch (NumberFormatException e) {
                    continue;
                }
                if (until > now) victims.put(victim, until);
            }
            if (!victims.isEmpty()) cooldowns.put(killer, victims);
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Map<UUID, Long>> entry : new HashMap<>(cooldowns).entrySet()) {
            List<String> list = new ArrayList<>();
            for (Map.Entry<UUID, Long> victim : entry.getValue().entrySet()) {
                if (victim.getValue() == null || victim.getValue() <= now) continue;
                list.add(victim.getKey() + ";" + victim.getValue());
            }
            if (!list.isEmpty()) yaml.set(entry.getKey().toString(), list);
        }
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                return;
            }
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

    private static UUID parseUuid(String raw) {
        if (raw == null) return null;
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
