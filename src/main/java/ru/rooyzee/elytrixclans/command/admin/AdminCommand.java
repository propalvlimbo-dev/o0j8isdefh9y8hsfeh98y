package ru.rooyzee.elytrixclans.command.admin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.rooyzee.elytrixclans.Main;
import ru.rooyzee.elytrixclans.clans.Clan;
import ru.rooyzee.elytrixclans.api.TalismanRewards;
import ru.rooyzee.elytrixclans.export.ClanExporter;
import ru.rooyzee.elytrixclans.function.impl.shop.admin.ShopEditInventory;
import ru.rooyzee.elytrixclans.utils.ConfigUtil;
import ru.rooyzee.elytrixclans.utils.HexUtil;

/**
 * Админ-команды.
 *
 * /elytrixclan edititem — меню, куда складывают предметы прямо из инвентаря: что положил,
 * то и продаётся. Цены при этом правятся в shop/prices.yml, а не в меню.
 * Остальной ассортимент по-прежнему настраивается через shop/shop_item.yml + reload.
 *
 * /elytrixclan addexp — публичная точка начисления опыта для внешних плагинов (ивент
 * «Талисман» дергает её командой от консоли, пока нет прямого хука).
 */
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

        if (sub.equals("edititem")) return handleEditItem(sender);
        else if (sub.equals("reload")) return handleReload(sender);
        else if (sub.equals("set")) return handleSet(sender, args);
        else if (sub.equals("remove")) return handleRemove(sender, args);
        else if (sub.equals("addexp")) return handleAddExp(sender, args);
        else if (sub.equals("talisman")) return handleTalisman(sender, args);
        else if (sub.equals("export")) return handleExport(sender);

        usage(sender);
        return false;
    }

    private boolean handleEditItem(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(HexUtil.translateHexColorCodes(
                    "&f☁ &7» &cРедактор магазина доступен только из игры"));
            return false;
        }
        Player player = (Player) sender;
        player.openInventory(new ShopEditInventory(player).getInventory());
        return false;
    }

    /**
     * /elytrixclan talisman <место> <клан> [ник1 ник2 ...] — награды за талисман.
     * Команда нужна плагинам, которые не хотят компилироваться против ElytrixClans;
     * логика та же, что и у TalismanRewards.rewardPlace().
     */
    private boolean handleTalisman(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(HexUtil.translateHexColorCodes(
                    "&c/elytrixclan talisman <место> <клан> [ник1 ник2 ...]"));
            return false;
        }
        int place;
        try {
            place = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(HexUtil.translateHexColorCodes("&c Место должно быть числом"));
            return false;
        }
        List<String> holders = new ArrayList<>();
        if (args.length > 3) holders.addAll(Arrays.asList(args).subList(3, args.length));

        TalismanRewards.Result result = TalismanRewards.rewardPlace(args[2], holders, place);
        if (!result.isSuccess()) {
            sender.sendMessage(HexUtil.translateHexColorCodes(
                    "&f☁ &7» &cТалисман: " + result.getError()));
            return false;
        }
        sender.sendMessage(HexUtil.translateHexColorCodes("&f☁ &7» &aТалисман: место &f#"
                + result.getPlace() + "&a, клан &f" + result.getClanName() + " &a— &f"
                + (long) result.getClanExp() + " &aопыта, по &f" + (long) result.getMoneyEach()
                + " &aмонет на " + result.getRewarded().size() + " игрок(ов)"));
        return false;
    }

    /** /elytrixclan export — принудительно перезаписать clans.json для сайта. */
    private boolean handleExport(CommandSender sender) {
        ClanExporter.export();
        sender.sendMessage(HexUtil.translateHexColorCodes(
                "&f☁ &7» &aТоп выгружен: &f" + ClanExporter.file().getPath()));
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
        if (args[2].equalsIgnoreCase("exp")) {
            clan.setExp(value);
            sender.sendMessage(HexUtil.translateHexColorCodes("&f☁ &7» &aОпыт клана "
                    + clan.getName() + " установлен: &f" + value));
        } else {
            usage(sender);
        }
        return false;
    }

    /**
     * /elytrixclan addexp &lt;клан|игрок&gt; &lt;число&gt; [player]
     *
     * Без третьего аргумента первый параметр — название клана. С «player» — ник игрока,
     * клан ищется по нему, а опыт дополнительно записывается в личный вклад участника.
     */
    private boolean handleAddExp(CommandSender sender, String[] args) {
        if (args.length < 3) {
            usage(sender);
            return false;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            ConfigUtil.sendMessage(sender, "messages.uncorrectNumber", null);
            return false;
        }

        boolean byPlayer = args.length >= 4 && args[3].equalsIgnoreCase("player");
        boolean ok;
        if (byPlayer) {
            ok = Main.getInstance().getClanManager().addExpByPlayer(args[1], amount);
        } else {
            Clan clan = Main.getInstance().getClanManager().getClanByName(args[1]);
            ok = clan != null && Main.getInstance().getClanManager().addClanExp(clan, amount);
        }

        if (!ok) {
            sender.sendMessage(HexUtil.translateHexColorCodes("&f☁ &7» &cКлан не найден"));
            return false;
        }
        sender.sendMessage(HexUtil.translateHexColorCodes("&f☁ &7» &aОпыт начислен: &f" + amount));
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
        sender.sendMessage(HexUtil.translateHexColorCodes("&c/elytrixclan set <clan> exp <n>"));
        sender.sendMessage(HexUtil.translateHexColorCodes("&c/elytrixclan addexp <clan> <n>"));
        sender.sendMessage(HexUtil.translateHexColorCodes("&c/elytrixclan addexp <player> <n> player"));
        sender.sendMessage(HexUtil.translateHexColorCodes("&c/elytrixclan remove <clan>"));
        sender.sendMessage(HexUtil.translateHexColorCodes("&c/elytrixclan reload"));
        sender.sendMessage(HexUtil.translateHexColorCodes("&c/elytrixclan edititem &7— витрина магазина и наборы"));
        sender.sendMessage(HexUtil.translateHexColorCodes("&c/elytrixclan talisman <место> <клан> [ники] &7— награды за талисман"));
        sender.sendMessage(HexUtil.translateHexColorCodes("&c/elytrixclan export &7— выгрузить топ для сайта"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("set", "remove", "reload", "addexp", "edititem",
                    "talisman", "export"), args[0]);
        }
        if (args.length == 2) {
            List<String> options = new ArrayList<>();
            if (Main.getInstance().getClanManager() != null) {
                for (Clan clan : Main.getInstance().getClanManager().getClans()) {
                    options.add(clan.getName());
                }
            }
            if (args[0].equalsIgnoreCase("addexp") || args[0].equalsIgnoreCase("talisman")) {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    options.add(online.getName());
                }
            }
            return filter(options, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            return filter(Arrays.asList("exp"), args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("addexp")) {
            return filter(Arrays.asList("player"), args[3]);
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
