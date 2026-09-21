package ru.rooyzee.elytrixtalisman.command.subcommand;

import org.bukkit.command.CommandSender;
import ru.rooyzee.elytrixtalisman.manager.TalismanManager;
import ru.rooyzee.elytrixtalisman.service.MessageService;

import java.util.HashMap;

public class StopSubCommand implements SubCommand {

    private final TalismanManager talismanManager;
    private final MessageService messageService;

    public StopSubCommand(TalismanManager talismanManager, MessageService messageService) {
        this.talismanManager = talismanManager;
        this.messageService = messageService;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!talismanManager.isRunning()) {
            messageService.send((org.bukkit.entity.Player) sender, "admin.event-not-running", new HashMap<>());
            return;
        }
        talismanManager.forceStop();
        messageService.send((org.bukkit.entity.Player) sender, "admin.event-stopped", new HashMap<>());
    }

    @Override
    public String getName() {
        return "stop";
    }
}