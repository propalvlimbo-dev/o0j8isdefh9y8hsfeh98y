package ru.rooyzee.elytrixclans.function.impl.shop.util;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ru.rooyzee.elytrixclans.Main;
import ru.rooyzee.elytrixclans.clans.Clan;
import ru.rooyzee.elytrixclans.clans.ClanMember;
import ru.rooyzee.elytrixclans.function.impl.shop.ShopFunction;
import ru.rooyzee.elytrixclans.function.impl.shop.holder.ThingsInventory;
import ru.rooyzee.elytrixclans.function.impl.shop.holder.kits.KitPreviewInventory;
import ru.rooyzee.elytrixclans.function.impl.shop.holder.kits.KitsInventory;
import ru.rooyzee.elytrixclans.function.impl.shop.holder.things.ShulkerHolder;
import ru.rooyzee.elytrixclans.function.impl.shop.holder.things.SpawnerHolder;
import ru.rooyzee.elytrixclans.function.impl.shop.holder.things.ThingsHolder;
import ru.rooyzee.elytrixclans.function.impl.shop.kit.Kit;
import ru.rooyzee.elytrixclans.permission.Permissions;
import ru.rooyzee.elytrixclans.utils.ConfigUtil;
import ru.rooyzee.elytrixclans.utils.MenuUtil;
import ru.rooyzee.elytrixclans.utils.NBTUtil;
import ru.rooyzee.elytrixclans.utils.ShopItemMeta;

public class BuyManager {

    public synchronized void buyItem(Player player, ItemStack itemStack) {
        int price = getPrice(itemStack);
        if (price <= 0) return;

        Clan clan = Main.getInstance().getClanManager().getPlayerClan(player);
        if (clan == null) return;

        ClanMember member = Main.getInstance().getClanManager().getPlayerClanMember(player);
        if (member == null) return;

        if (!member.getRole().getPermissions().contains(Permissions.SHOP)) {
            ConfigUtil.sendMessage(player, "messages.noPermission", null);
            return;
        }

        if (clan.getPoints() < price) {
            ConfigUtil.sendMessage(player, "messages.notPoints", null);
            return;
        }

        Material type = itemStack.getType();
        // AIR/воздушный предмет из кривого конфига ронял addItem() исключением уже после списания поинтов.
        if (type == null || type == Material.AIR || type.isAir()) return;

        ItemStack buyedItem = new ItemStack(type);
        if (buyedItem.getType() == Material.TIPPED_ARROW && getEffect(itemStack) != null
                && PotionEffectType.getByName(getEffect(itemStack)) != null) {
            String effect = getEffect(itemStack);
            PotionMeta sourceMeta = (PotionMeta) itemStack.getItemMeta();
            PotionMeta meta = (PotionMeta) buyedItem.getItemMeta();
            if (sourceMeta != null && meta != null) {
                meta.setColor(PotionEffectType.getByName(effect).getColor());
                for (PotionEffect pe : sourceMeta.getCustomEffects()) {
                    meta.addCustomEffect(pe, true);
                }
                meta.setDisplayName(sourceMeta.getDisplayName());
                buyedItem.setItemMeta(meta);
            }
        }
        // «Какая положил — такая и продаётся»: снима с витринного стака его реальные свойства
        // (зачарования, эффекты, прочность, флаги) и надеваем на выдаваемый. Имя и лор магазина
        // покупателю не отдаём — они и раньше не отдавались.
        try {
            Map<String, Object> look = new LinkedHashMap<>();
            ShopItemMeta.write(look, itemStack);
            ShopItemMeta.apply(buyedItem, ShopItemMeta.of(look));
        } catch (Exception e) {
            Main.getInstance().getLogger().warning("Не удалось перенести свойства предмета магазина: " + e.getMessage());
        }
        // Количество берём из конфига и ограничиваем стаком материала: стек «на 1000» —
        // это классический дюп через split/drop и битые предметы в инвентаре.
        int amount = Math.max(1, itemStack.getAmount());
        int maxStack = buyedItem.getMaxStackSize();
        if (maxStack > 0 && amount > maxStack) amount = maxStack;
        buyedItem.setAmount(amount);

        clan.setPoints(clan.getPoints() - price);
        try {
            if (player.getInventory().firstEmpty() == -1) {
                player.getWorld().dropItemNaturally(player.getLocation(), buyedItem);
            } else {
                player.getInventory().addItem(buyedItem);
            }
        } catch (Exception e) {
            // Выдача не удалась — возвращаем поинты, чтобы игрок не заплатил за воздух.
            clan.setPoints(clan.getPoints() + price);
            Main.getInstance().getLogger().warning("Не удалось выдать предмет из магазина: " + e.getMessage());
            return;
        }

        updateShopInfo(player);
        ConfigUtil.sendMessage(player, "messages.buyItem", null);
    }

    public synchronized void buyKit(Player player, Kit kit) {
        Clan clan = Main.getInstance().getClanManager().getPlayerClan(player);
        if (clan == null) return;

        ClanMember member = Main.getInstance().getClanManager().getPlayerClanMember(player);
        if (member == null) return;

        if (!member.getRole().getPermissions().contains(Permissions.SHOP)) {
            ConfigUtil.sendMessage(player, "messages.noPermission", null);
            return;
        }

        if (clan.getPoints() < kit.getPrice()) {
            ConfigUtil.sendMessage(player, "messages.notPoints", null);
            return;
        }

        clan.setPoints(clan.getPoints() - kit.getPrice());
        try {
            Main.getInstance().getKitManager().giveKit(player, kit);
        } catch (Exception e) {
            clan.setPoints(clan.getPoints() + kit.getPrice());
            Main.getInstance().getLogger().warning("Не удалось выдать набор " + kit.getId() + ": " + e.getMessage());
            return;
        }
        updateShopInfo(player);
        ConfigUtil.sendMessage(player, "messages.buyItem", null);
    }

    private int getPrice(ItemStack itemStack) {
        if (!NBTUtil.hasItemNBT(itemStack, "shopItem")) return -1;
        String nbt = NBTUtil.getNBTvalue(itemStack, "shopItem");
        if (nbt == null || !nbt.startsWith("shopItem_wtf_")) return -1;
        try {
            String[] parts = nbt.split("_wtf_");
            return Integer.parseInt(parts[3]);
        } catch (Exception e) {
            return -1;
        }
    }

    private String getEffect(ItemStack itemStack) {
        if (!NBTUtil.hasItemNBT(itemStack, "shopItem")) return null;
        String nbt = NBTUtil.getNBTvalue(itemStack, "shopItem");
        if (nbt == null || !nbt.startsWith("shopItem_wtf_")) return null;
        try {
            String[] parts = nbt.split("_wtf_");
            if (parts.length >= 5) return parts[4];
        } catch (Exception ignored) {
        }
        return null;
    }

    private void updateShopInfo(Player player) {
        Inventory inv = player.getOpenInventory().getTopInventory();
        InventoryHolder holder = inv.getHolder();
        if (!isShopHolder(holder)) return;
        Clan clan = Main.getInstance().getClanManager().getPlayerClan(player);
        if (clan == null) return;
        inv.setItem(4, MenuUtil.createInfoItem(String.valueOf(clan.getPoints())));
    }

    private boolean isShopHolder(InventoryHolder holder) {
        return holder instanceof ShopFunction
                || holder instanceof ThingsInventory
                || holder instanceof ThingsHolder
                || holder instanceof SpawnerHolder
                || holder instanceof ShulkerHolder
                || holder instanceof KitsInventory
                || holder instanceof KitPreviewInventory;
    }
}