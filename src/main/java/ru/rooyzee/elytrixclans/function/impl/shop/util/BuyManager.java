package ru.rooyzee.elytrixclans.function.impl.shop.util;

import java.util.UUID;
import java.util.regex.Pattern;
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
import ru.rooyzee.elytrixclans.role.ClanRoles;
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

    /**
     * Ник для подстановки в консольную команду. Всё, что не входит в набор символов ника
     * Minecraft, отбрасывается: иначе игрок с «хитрым» ником (Floodgate, оффлайн-режим,
     * ник с пробелом или кавычкой) мог бы дописать в консольную команду свои аргументы.
     */
    private static final Pattern UNSAFE_NAME = Pattern.compile("[^A-Za-z0-9_\\.]");

    public static String safeName(String name) {
        return name == null ? "" : UNSAFE_NAME.matcher(name).replaceAll("");
    }

    /** Выполняет консольную команду выдачи. Возвращает false, если команда небезопасна. */
    public static boolean dispatchGive(String rawCommand, Player player, String amountPlaceholder) {
        if (rawCommand == null) return false;
        String command = rawCommand.trim();
        if (command.isEmpty()) return false;
        String safe = safeName(player.getName());
        if (safe.isEmpty()) return false;
        command = command.replace("%player%", safe);
        if (amountPlaceholder != null) command = command.replace("%amount%", amountPlaceholder);
        // Перевод строки в команде позволил бы «склеить» вторую команду — такие записи отклоняем.
        if (command.indexOf('\n') >= 0 || command.indexOf('\r') >= 0) {
            Main.getInstance().getLogger().warning("Команда магазина отклонена (перевод строки): " + rawCommand);
            return false;
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        return true;
    }

    public synchronized void buyItem(Player player, ShopItem shopItem) {
        if (player == null || shopItem == null) return;

        Clan clan = requireClan(player);
        if (clan == null) return;
        if (!hasShopPermission(player, clan)) return;
        if (!hasLevel(player, clan, shopItem.getRequiredLevel())) return;
        if (!checkCooldown(player, itemKey(shopItem))) return;

        if (!withdraw(player, shopItem.getPrice())) return;

        try {
            if (shopItem.isCommandItem()) {
                for (String command : shopItem.getCommands()) {
                    dispatchGive(command, player, String.valueOf(shopItem.getAmount()));
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

        markCooldown(player, itemKey(shopItem),
                PurchaseCooldownStorage.itemCooldownMillis(shopItem.getPrice(), shopItem.getCooldownSeconds()));
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
        if (!checkCooldown(player, kitKey(kit))) return;

        if (!withdraw(player, kit.getPrice())) return;

        try {
            Main.getInstance().getKitManager().giveKit(player, kit);
        } catch (Exception e) {
            deposit(player, kit.getPrice());
            Main.getInstance().getLogger().warning("Не удалось выдать набор " + kit.getId()
                    + ": " + e.getMessage());
            return;
        }

        markCooldown(player, kitKey(kit),
                PurchaseCooldownStorage.kitCooldownMillis(kit.getPrice(), kit.getCooldownSeconds()));
        ConfigUtil.sendMessage(player, "messages.buyItem", ConfigUtil.setHolder(
                new String[]{"%item%", "%price%"},
                new String[]{kit.getDisplayName(), MenuUtil.money(kit.getPrice())}));
    }

    // --- Кулдауны ---------------------------------------------------------------------------

    /** Ключ кулдауна позиции. Наборы и товары разведены префиксом, чтобы id не пересекались. */
    public static String itemKey(ShopItem shopItem) {
        return "item:" + shopItem.getId();
    }

    public static String kitKey(Kit kit) {
        return "kit:" + kit.getId();
    }

    /** Сколько осталось ждать до покупки позиции (мс). */
    public long remainingCooldown(Player player, String key) {
        PurchaseCooldownStorage storage = Main.getInstance().getPurchaseCooldownStorage();
        if (storage == null || player == null) return 0L;
        return storage.remaining(player.getUniqueId(), key);
    }

    private boolean checkCooldown(Player player, String key) {
        long left = remainingCooldown(player, key);
        if (left <= 0) return true;
        ConfigUtil.sendMessage(player, "messages.shopCooldown", ConfigUtil.setHolder(
                new String[]{"%time%"}, new String[]{PurchaseCooldownStorage.format(left)}));
        return false;
    }

    private void markCooldown(Player player, String key, long durationMillis) {
        PurchaseCooldownStorage storage = Main.getInstance().getPurchaseCooldownStorage();
        if (storage == null) return;
        UUID id = player.getUniqueId();
        storage.mark(id, key, durationMillis);
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
        // Жёсткий потолок: опечатка в конфиге (amount: 100000) иначе уронила бы сервер
        // тысячами дропов в одном тике.
        int left = Math.min(shopItem.getAmount(), 2304);
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
        if (member == null || member.getRole() == null) {
            ConfigUtil.sendMessage(player, "messages.noPermission", null);
            return false;
        }
        if (!member.getRole().getPermissions().contains(Permissions.SHOP)) {
            // Новичку магазин закрыт: объясняем, что нужно дорасти до следующей роли,
            // а не просто «нет прав».
            String next = ClanRoles.nextAutoRole(member.getLevel());
            double left = ClanRoles.expToNextRole(member.getLevel());
            ConfigUtil.sendMessage(player, "messages.shopRoleLocked", ConfigUtil.setHolder(
                    new String[]{"%role%", "%exp%"},
                    new String[]{next != null ? next : ClanRoles.TRAINEE,
                            MenuUtil.money(Math.max(0, left))}));
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
            sendNoMoney(player, price);
            return false;
        }
        EconomyResponse response = economy.withdrawPlayer(player, price);
        if (response == null || !response.transactionSuccess()) {
            sendNoMoney(player, price);
            return false;
        }
        return true;
    }

    /** %price% в сообщении раньше не подставлялся — игрок видел сырой плейсхолдер. */
    private void sendNoMoney(Player player, double price) {
        ConfigUtil.sendMessage(player, "messages.noMoney", ConfigUtil.setHolder(
                new String[]{"%price%"}, new String[]{MenuUtil.money(price)}));
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
