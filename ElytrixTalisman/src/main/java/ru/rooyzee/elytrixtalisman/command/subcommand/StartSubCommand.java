package ru.rooyzee.elytrixtalisman.command.subcommand;

import org.bukkit.command.CommandSender;
import ru.rooyzee.elytrixtalisman.manager.TalismanManager;
import ru.rooyzee.elytrixtalisman.service.MessageService;

import java.util.HashMap;

public class StartSubCommand implements SubCommand {

    private final TalismanManager talismanManager;
    private final MessageService messageService;

    public StartSubCommand(TalismanManager talismanManager, MessageService messageService) {
        this.talismanManager = talismanManager;
        this.messageService = messageService;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (talismanManager.isRunning()) {
            messageService.send((org.bukkit.entity.Player) sender, "admin.event-already-running", new HashMap<>());
            return;
        }
        talismanManager.start();
        messageService.send((org.bukkit.entity.Player) sender, "admin.event-started", new HashMap<>());
    }

    @Override
    public String getName() {
        return "start";
    }
}