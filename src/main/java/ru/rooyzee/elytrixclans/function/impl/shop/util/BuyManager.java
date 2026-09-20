package ru.rooyzee.elytrixclans.function.impl.shop.util;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.rooyzee.elytrixclans.Main;
import ru.rooyzee.elytrixclans.clans.Clan;
import ru.rooyzee.elytrixclans.clans.ClanMember;
import ru.rooyzee.elytrixclans.function.impl.shop.config.ItemsConfiguration;
import ru.rooyzee.elytrixclans.function.impl.shop.kit.Kit;
import ru.rooyzee.elytrixclans.function.impl.shop.object.ShopItem;
import ru.rooyzee.elytrixclans.hook.impl.VaultHook;
import ru.rooyzee.elytrixclans.level.Level;
import ru.rooyzee.elytrixclans.permission.Permissions;
import ru.rooyzee.elytrixclans.utils.ConfigUtil;
import ru.rooyzee.elytrixclans.utils.LevelUtil;
import ru.rooyzee.elytrixclans.utils.MenuUtil;

/**
 * Покупки в клановом магазине.
 *
 * Поинтов больше нет: единственная валюта — монеты экономики Vault, они списываются
 * с личного счёта покупателя. Клан влияет только на доступность позиции (уровень)
 * и на право покупать (пермишен SHOP).
 */
public class BuyManager {

    public synchronized void buyItem(Player player, ShopItem shopItem) {
        if (player == null || shopItem == null) return;

        Clan clan = requireClan(player);
        if (clan == null) return;
        if (!hasShopPermission(player, clan)) return;
        if (!hasLevel(player, clan, shopItem.getRequiredLevel())) return;

        if (!withdraw(player, shopItem.getPrice())) return;

        try {
            if (shopItem.isCommandItem()) {
                for (String command : shopItem.getCommands()) {
                    if (command == null || command.trim().isEmpty()) continue;
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                            command.replace("%player%", player.getName())
                                    .replace("%amount%", String.valueOf(shopItem.getAmount())));
                }
            } else {
                giveItem(player, shopItem);
            }
        } catch (Exception e) {
            // Выдача сорвалась — возвращаем деньги, иначе игрок платит за воздух.
            deposit(player, shopItem.getPrice());
            Main.getInstance().getLogger().warning("Не удалось выдать позицию магазина "
                    + shopItem.getId() + ": " + e.getMessage());
            return;
        }

        ConfigUtil.sendMessage(player, "messages.buyItem", ConfigUtil.setHolder(
                new String[]{"%item%", "%price%"},
                new String[]{shopItem.getName(), MenuUtil.money(shopItem.getPrice())}));
    }

    public synchronized void buyKit(Player player, Kit kit) {
        if (player == null || kit == null) return;

        Clan clan = requireClan(player);
        if (clan == null) return;
        if (!hasShopPermission(player, clan)) return;
        if (!hasLevel(player, clan, kit.getRequiredLevel())) return;

        if (!withdraw(player, kit.getPrice())) return;

        try {
            Main.getInstance().getKitManager().giveKit(player, kit);
        } catch (Exception e) {
            deposit(player, kit.getPrice());
            Main.getInstance().getLogger().warning("Не удалось выдать набор " + kit.getId()
                    + ": " + e.getMessage());
            return;
        }

        ConfigUtil.sendMessage(player, "messages.buyItem", ConfigUtil.setHolder(
                new String[]{"%item%", "%price%"},
                new String[]{kit.getDisplayName(), MenuUtil.money(kit.getPrice())}));
    }

    /** Баланс игрока в монетах Vault (0, если экономики нет). */
    public double getBalance(Player player) {
        Economy economy = VaultHook.getEconomy();
        if (economy == null || player == null) return 0.0;
        try {
            return economy.getBalance(player);
        } catch (Exception e) {
            return 0.0;
        }
    }

    private void giveItem(Player player, ShopItem shopItem) {
        ItemStack stack = ItemsConfiguration.buildRawItem(shopItem);
        if (stack.getType() == Material.AIR) return;
        // Если количество не влезает в один стак, выдаём несколькими.
        int left = shopItem.getAmount();
        int max = Math.max(1, stack.getMaxStackSize());
        while (left > 0) {
            ItemStack part = stack.clone();
            part.setAmount(Math.min(max, left));
            left -= part.getAmount();
            if (player.getInventory().firstEmpty() == -1) {
                player.getWorld().dropItemNaturally(player.getLocation(), part);
            } else {
                player.getInventory().addItem(part);
            }
        }
    }

    private Clan requireClan(Player player) {
        Clan clan = Main.getInstance().getClanManager().getPlayerClan(player);
        if (clan == null) {
            ConfigUtil.sendMessage(player, "messages.withoutClan", null);
        }
        return clan;
    }

    private boolean hasShopPermission(Player player, Clan clan) {
        ClanMember member = Main.getInstance().getClanManager().getMember(clan, player.getName());
        if (member == null || member.getRole() == null
                || !member.getRole().getPermissions().contains(Permissions.SHOP)) {
            ConfigUtil.sendMessage(player, "messages.noPermission", null);
            return false;
        }
        return true;
    }

    private boolean hasLevel(Player player, Clan clan, int requiredLevel) {
        Level level = LevelUtil.getClanLevel(clan.getExp());
        int current = level != null ? level.getLevel() : 1;
        if (current < requiredLevel) {
            ConfigUtil.sendMessage(player, "messages.shopLevelLocked", ConfigUtil.setHolder(
                    new String[]{"%level%"}, new String[]{String.valueOf(requiredLevel)}));
            return false;
        }
        return true;
    }

    private boolean withdraw(Player player, double price) {
        if (price <= 0) return true;
        Economy economy = VaultHook.getEconomy();
        if (economy == null) {
            ConfigUtil.sendMessage(player, "messages.noEconomy", null);
            return false;
        }
        if (!economy.has(player, price)) {
            ConfigUtil.sendMessage(player, "messages.noMoney", null);
            return false;
        }
        EconomyResponse response = economy.withdrawPlayer(player, price);
        if (response == null || !response.transactionSuccess()) {
            ConfigUtil.sendMessage(player, "messages.noMoney", null);
            return false;
        }
        return true;
    }

    private void deposit(Player player, double price) {
        if (price <= 0) return;
        Economy economy = VaultHook.getEconomy();
        if (economy == null) return;
        try {
            economy.depositPlayer(player, price);
        } catch (Exception ignored) {
        }
    }
}
