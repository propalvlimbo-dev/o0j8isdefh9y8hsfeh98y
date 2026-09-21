package ru.rooyzee.elytrixtalisman.command.subcommand;

import org.bukkit.command.CommandSender;
import ru.rooyzee.elytrixtalisman.config.ConfigManager;
import ru.rooyzee.elytrixtalisman.manager.ScheduleManager;
import ru.rooyzee.elytrixtalisman.service.MessageService;

import java.util.HashMap;

public class ReloadSubCommand implements SubCommand {

    private final ConfigManager configManager;
    private final MessageService messageService;
    private final ScheduleManager scheduleManager;

    public ReloadSubCommand(ConfigManager configManager, MessageService messageService, ScheduleManager scheduleManager) {
        this.configManager = configManager;
        this.messageService = messageService;
        this.scheduleManager = scheduleManager;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        configManager.loadAll();
        scheduleManager.scheduleAll();
        messageService.send((org.bukkit.entity.Player) sender, "admin.reloaded", new HashMap<>());
    }

    @Override
    public String getName() {
        return "reload";
    }
}