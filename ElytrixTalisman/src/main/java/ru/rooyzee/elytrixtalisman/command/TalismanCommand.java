package ru.rooyzee.elytrixtalisman.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import ru.rooyzee.elytrixtalisman.command.subcommand.*;
import ru.rooyzee.elytrixtalisman.config.ConfigManager;
import ru.rooyzee.elytrixtalisman.manager.ScheduleManager;
import ru.rooyzee.elytrixtalisman.manager.TalismanManager;
import ru.rooyzee.elytrixtalisman.menu.LootMenu;
import ru.rooyzee.elytrixtalisman.service.MessageService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TalismanCommand implements CommandExecutor, TabCompleter {

    private final Map<String, SubCommand> subCommands = new HashMap<>();

    public TalismanCommand(TalismanManager talismanManager, ConfigManager configManager,
                           MessageService messageService, ScheduleManager scheduleManager, LootMenu lootMenu) {
        register(new StartSubCommand(talismanManager, messageService));
        register(new StopSubCommand(talismanManager, messageService));
        register(new ReloadSubCommand(configManager, messageService, scheduleManager));
        register(new TpSubCommand(talismanManager, messageService));
        register(new InfoSubCommand(talismanManager, messageService));
        register(new ItemsSubCommand(lootMenu, messageService));
    }

    private void register(SubCommand sub) {
        subCommands.put(sub.getName().toLowerCase(), sub);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("elytrixtalisman.admin")) return false;
        if (args.length == 0) return false;

        SubCommand sub = subCommands.get(args[0].toLowerCase());
        if (sub == null) return false;

        sub.execute(sender, args);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("elytrixtalisman.admin")) return new ArrayList<>();
        if (args.length == 1) {
            return subCommands.keySet().stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}