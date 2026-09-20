package ru.rooyzee.elytrixclans.command.admin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.rooyzee.elytrixclans.Main;
import ru.rooyzee.elytrixclans.clans.Clan;
import ru.rooyzee.elytrixclans.function.impl.shop.admin.EditMenuInventory;
import ru.rooyzee.elytrixclans.utils.ConfigUtil;
import ru.rooyzee.elytrixclans.utils.HexUtil;

public class AdminCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("elytrixclans.admin")) {
            ConfigUtil.sendMessage(sender, "messages.noPermission", null);
            return false;
        }

        if (args.length == 0) {
            usage(sender);
            return false;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("edit")) return handleEdit(sender);
        else if (sub.equals("reload")) return handleReload(sender);
        else if (sub.equals("set")) return handleSet(sender, args);
        else if (sub.equals("remove")) return handleRemove(sender, args);

        usage(sender);
        return false;
    }

    private boolean handleEdit(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(HexUtil.translateHexColorCodes("&f☁ &7» &cРедактор магазина доступен только из игры"));
            return false;
        }
        Player player = (Player) sender;
        player.openInventory(new EditMenuInventory(player).getInventory());
        return false;
    }

    private boolean handleReload(CommandSender sender) {
        Main.getInstance().reloadEverything();
        sender.sendMessage(HexUtil.translateHexColorCodes("&f☁ &7» &aКонфиги перечитаны"));
        return false;
    }

    private boolean handleSet(CommandSender sender, String[] args) {
        if (args.length != 4) {
            usage(sender);
            return false;
        }
        Clan clan = Main.getInstance().getClanManager().getClanByName(args[1]);
        if (clan == null) {
            sender.sendMessage(HexUtil.translateHexColorCodes("&f☁ &7» &cКлан не найден"));
            return false;
        }
        double value;
        try {
            value = Double.parseDouble(args[3]);
        } catch (NumberFormatException e) {
            usage(sender);
            return false;
        }
        String type = args[2].toLowerCase(Locale.ROOT);
        if (type.equals("points")) clan.setPoints(value);
        else if (type.equals("exp")) clan.setExp(value);
        else usage(sender);
        return false;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (args.length != 2) {
            usage(sender);
            return false;
        }
        Clan clan = Main.getInstance().getClanManager().getClanByName(args[1]);
        if (clan == null) {
            sender.sendMessage(HexUtil.translateHexColorCodes("&f☁ &7» &cКлан не найден"));
            return false;
        }
        Main.getInstance().getClanManager().deleteClan(clan);
        return false;
    }

    private void usage(CommandSender sender) {
        sender.sendMessage(HexUtil.translateHexColorCodes("&c/" + "elytrixclan set <clan> <points|exp> <n>"));
        sender.sendMessage(HexUtil.translateHexColorCodes("&c/elytrixclan remove <clan>"));
        sender.sendMessage(HexUtil.translateHexColorCodes("&c/elytrixclan reload"));
        sender.sendMessage(HexUtil.translateHexColorCodes("&c/elytrixclan edit &7— редактор предметов и наборов магазина"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("set", "remove", "reload", "edit"), args[0]);
        }
        if (args.length == 2) {
            List<String> clans = new ArrayList<>();
            if (Main.getInstance().getClanManager() != null) {
                for (Clan clan : Main.getInstance().getClanManager().getClans()) {
                    clans.add(clan.getName());
                }
            }
            return filter(clans, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            return filter(Arrays.asList("points", "exp"), args[2]);
        }
        return new ArrayList<>();
    }

    private List<String> filter(List<String> options, String token) {
        List<String> out = new ArrayList<>();
        String lower = token.toLowerCase(Locale.ROOT);
        for (String s : options) {
            if (s.toLowerCase(Locale.ROOT).startsWith(lower)) out.add(s);
        }
        return out;
    }
}