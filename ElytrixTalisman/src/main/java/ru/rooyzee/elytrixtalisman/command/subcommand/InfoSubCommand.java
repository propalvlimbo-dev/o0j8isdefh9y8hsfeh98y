package ru.rooyzee.elytrixtalisman.command.subcommand;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.rooyzee.elytrixtalisman.manager.TalismanManager;
import ru.rooyzee.elytrixtalisman.model.TalismanSession;
import ru.rooyzee.elytrixtalisman.service.MessageService;

import java.util.HashMap;
import java.util.Map;

public class InfoSubCommand implements SubCommand {

    private final TalismanManager talismanManager;
    private final MessageService messageService;

    public InfoSubCommand(TalismanManager talismanManager, MessageService messageService) {
        this.talismanManager = talismanManager;
        this.messageService = messageService;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) return;
        Player player = (Player) sender;

        if (!talismanManager.isRunning() || talismanManager.getSession() == null) {
            messageService.send(player, "admin.no-active-session", new HashMap<>());
            return;
        }

        TalismanSession s = talismanManager.getSession();
        Map<String, String> ph = new HashMap<>();
        ph.put("%status%", messageService.getRaw("status-active"));
        ph.put("%x%", String.valueOf(s.getLocation().getBlockX()));
        ph.put("%y%", String.valueOf(s.getLocation().getBlockY()));
        ph.put("%z%", String.valueOf(s.getLocation().getBlockZ()));
        ph.put("%bank%", String.valueOf(s.getBank()));
        ph.put("%max_bank%", String.valueOf(s.getMaxBank()));
        ph.put("%leader%", s.getLeaderName(messageService.getRaw("no-leader")));
        ph.put("%clan_count%", String.valueOf(s.getClanData().size()));
        messageService.sendList(player, "admin.info", ph);
    }

    @Override
    public String getName() {
        return "info";
    }
}