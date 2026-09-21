package ru.rooyzee.elytrixtalisman.command.subcommand;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.rooyzee.elytrixtalisman.menu.LootMenu;
import ru.rooyzee.elytrixtalisman.service.MessageService;

import java.util.HashMap;

public class ItemsSubCommand implements SubCommand {

    private final LootMenu lootMenu;
    private final MessageService messageService;

    public ItemsSubCommand(LootMenu lootMenu, MessageService messageService) {
        this.lootMenu = lootMenu;
        this.messageService = messageService;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) return;
        Player player = (Player) sender;
        lootMenu.open(player, 0);
        messageService.send(player, "admin.items-opened", new HashMap<>());
    }

    @Override
    public String getName() {
        return "items";
    }
}