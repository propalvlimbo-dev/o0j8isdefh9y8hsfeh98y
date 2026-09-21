package ru.rooyzee.elytrixtalisman.command.subcommand;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.rooyzee.elytrixtalisman.manager.TalismanManager;
import ru.rooyzee.elytrixtalisman.service.MessageService;

import java.util.HashMap;

public class TpSubCommand implements SubCommand {

    private final TalismanManager talismanManager;
    private final MessageService messageService;

    public TpSubCommand(TalismanManager talismanManager, MessageService messageService) {
        this.talismanManager = talismanManager;
        this.messageService = messageService;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            messageService.send((Player) sender, "admin.console-denied", new HashMap<>());
            return;
        }
        Player player = (Player) sender;
        if (!talismanManager.isRunning() || talismanManager.getSession() == null) {
            messageService.send(player, "admin.no-active-session", new HashMap<>());
            return;
        }
        player.teleport(talismanManager.getSession().getLocation());
        messageService.send(player, "admin.teleported", new HashMap<>());
    }

    @Override
    public String getName() {
        return "tp";
    }
}