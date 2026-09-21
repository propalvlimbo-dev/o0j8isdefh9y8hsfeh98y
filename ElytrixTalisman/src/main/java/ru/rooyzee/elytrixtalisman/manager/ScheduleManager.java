package ru.rooyzee.elytrixtalisman.manager;

import org.bukkit.Bukkit;
import ru.rooyzee.elytrixtalisman.Main;
import ru.rooyzee.elytrixtalisman.config.ConfigManager;
import ru.rooyzee.elytrixtalisman.model.ScheduleEntry;
import ru.rooyzee.elytrixtalisman.service.MessageService;
import ru.rooyzee.elytrixtalisman.util.TimeUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScheduleManager {

    private final Main plugin;
    private final ConfigManager configManager;
    private final TalismanManager talismanManager;
    private final MessageService messageService;
    private final List<Integer> taskIds = new ArrayList<>();

    public ScheduleManager(Main plugin, ConfigManager configManager, TalismanManager talismanManager, MessageService messageService) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.talismanManager = talismanManager;
        this.messageService = messageService;
    }

    public void scheduleAll() {
        cancelAll();
        List<?> list = configManager.getSchedules().getList("schedules");
        if (list == null) return;

        for (Object obj : list) {
            if (obj instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) obj;
                int hour = ((Number) map.get("hour")).intValue();
                int minute = ((Number) map.get("minute")).intValue();
                scheduleOne(new ScheduleEntry(hour, minute));
            }
        }
    }

    private void scheduleOne(ScheduleEntry entry) {
        long delay = TimeUtil.secondsUntil(entry.getHour(), entry.getMinute()) * 20L;
        int id = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> tryStart(entry), delay);
        taskIds.add(id);
    }

    private void tryStart(ScheduleEntry entry) {
        int minOnline = configManager.getConfig().getInt("min-online", 10);
        int postponeMinutes = configManager.getConfig().getInt("postpone-minutes", 30);
        int online = Bukkit.getOnlinePlayers().size();

        if (talismanManager.isRunning()) {
            scheduleOne(entry);
            return;
        }

        if (online < minOnline) {
            Map<String, String> ph = new HashMap<>();
            ph.put("%online%", String.valueOf(online));
            ph.put("%required%", String.valueOf(minOnline));
            ph.put("%minutes%", String.valueOf(postponeMinutes));
            messageService.broadcast("not-enough-online", ph);

            int id = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin,
                    () -> tryStart(entry),
                    postponeMinutes * 60L * 20L);
            taskIds.add(id);
            return;
        }

        talismanManager.start();
        scheduleOne(entry);
    }

    public void cancelAll() {
        taskIds.forEach(id -> Bukkit.getScheduler().cancelTask(id));
        taskIds.clear();
    }
}