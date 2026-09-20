package ru.rooyzee.elytrixclans.function.impl.info;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import ru.rooyzee.elytrixclans.Main;
import ru.rooyzee.elytrixclans.clans.Clan;
import ru.rooyzee.elytrixclans.clans.ClanMember;
import ru.rooyzee.elytrixclans.permission.Permissions;
import ru.rooyzee.elytrixclans.status.Status;
import ru.rooyzee.elytrixclans.utils.ConfigUtil;
import ru.rooyzee.elytrixclans.utils.HexUtil;
import ru.rooyzee.elytrixclans.utils.MenuUtil;
import ru.rooyzee.elytrixclans.utils.NBTUtil;
import ru.rooyzee.elytrixclans.utils.PlayerHeadCache;
import ru.rooyzee.elytrixclans.utils.ValidatorUtil;

public class MemberInventory implements InventoryHolder {

    private final Inventory inventory;
    private final int[] dyesSlots = {21, 22, 23, 30, 31, 32};
    private final String[] permNames = {"shop", "kick", "sethome", "invite", "pvp", "glow"};

    public MemberInventory(ClanMember clanMember) {
        inventory = Bukkit.createInventory(this, 54,
                HexUtil.translateHexColorCodes("&#F8BEFB&lУчастник клана"));

        // Рамку ставим первой: раньше applyLayout вызывался в конце и затирал голову (слот 4)
        // и кнопку кика (слот 53) — меню выглядело пустым, а клики по цветам не работали.
        MenuUtil.applyLayout(inventory);

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) head.getItemMeta();
        if (skullMeta != null) {
            skullMeta.setDisplayName(HexUtil.translateHexColorCodes("&#F8BEFB" + clanMember.getName()));
            Status status = ClanMember.getStatus(clanMember);
            List<String> lore = new ArrayList<>();
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fРоль: &#F8BEFB" + roleName(clanMember)));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fСтатус: " + status.getName()));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fУбийств: &#F8BEFB" + clanMember.getKills()));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fСмертей: &#F8BEFB" + clanMember.getDeaths()));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fУ/С: &#F8BEFB" + clanMember.getKDA()));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fОпыт: &#F8BEFB" + round(clanMember.getLevel())));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fПоинты: &#F8BEFB" + round(clanMember.getPoints())));
            skullMeta.setLore(lore);
            head.setItemMeta(skullMeta);
        }
        PlayerHeadCache.fillHead(inventory, 4, head, clanMember.getName(), clanMember.getPlayer());

        for (int i = 0; i < dyesSlots.length; i++) {
            boolean hasPerm = clanMember.getRole().getPermissions().contains(
                    Permissions.valueOf(permNames[i].toUpperCase(Locale.ROOT)));
            ItemStack dye = new ItemStack(hasPerm ? Material.LIME_DYE : Material.GRAY_DYE);
            ItemMeta dyeMeta = dye.getItemMeta();
            if (dyeMeta != null) {
                dyeMeta.setDisplayName(HexUtil.translateHexColorCodes(
                        "&fДоступ к &#F8BEFB/clan " + permNames[i]));
                List<String> dyeLore = new ArrayList<>();
                dyeLore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
                dyeLore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fСтатус: " + (hasPerm ? "&aВключено" : "&cВыключено")));
                dyeLore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
                dyeLore.add(HexUtil.translateHexColorCodes("&7● &fНажмите для переключения"));
                dyeMeta.setLore(dyeLore);
                dye.setItemMeta(dyeMeta);
            }
            NBTUtil.addItemNBT(dye, "dye", permNames[i]);
            inventory.setItem(dyesSlots[i], dye);
        }

        inventory.setItem(45, MenuUtil.createBackButton());

        ItemStack kickBtn = new ItemStack(Material.RED_DYE);
        ItemMeta kickMeta = kickBtn.getItemMeta();
        if (kickMeta != null) {
            kickMeta.setDisplayName(HexUtil.translateHexColorCodes("&7« &cКикнуть игрока &7»"));
            kickMeta.setLore(Arrays.asList(
                    HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "),
                    HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fУдалить из клана: &cБезвозвратно"),
                    HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "),
                    HexUtil.translateHexColorCodes("&7● &fНажмите для кика")
            ));
            kickBtn.setItemMeta(kickMeta);
        }
        NBTUtil.addItemNBT(kickBtn, "barrierItem", "");
        inventory.setItem(53, kickBtn);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void onInventoryClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getCurrentItem() == null || !(event.getWhoClicked() instanceof Player)) return;
        if (event.getClickedInventory() == null) return;
        Player clickedPlayer = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        ItemStack headItem = event.getClickedInventory().getItem(4);
        if (headItem == null || headItem.getType() != Material.PLAYER_HEAD || headItem.getItemMeta() == null) return;
        if (headItem.getItemMeta().getDisplayName() == null) return;

        String memberName = ValidatorUtil.removeAllColors(headItem.getItemMeta().getDisplayName());
        ClanMember clanMember = Main.getInstance().getClanManager().getPlayerClanMember(memberName);
        if (clanMember == null) return;
        Clan clan = Main.getInstance().getClanManager().getPlayerClan(clanMember.getName());
        if (clan == null) return;

        if (NBTUtil.hasItemNBT(clickedItem, "arrowItem")) {
            clickedPlayer.openInventory(new InfoFunction(clan).getInventory());
            return;
        }

        ClanMember clickerMember = Main.getInstance().getClanManager().getPlayerClanMember(clickedPlayer);
        if (clickerMember == null) return;
        if (Main.getInstance().getClanManager().getPlayerClan(clickedPlayer) != clan) return;
        if (!"Лидер".equals(clickerMember.getRole().getName())) return;

        if (clanMember.getName().equalsIgnoreCase(clickedPlayer.getName())) {
            ConfigUtil.sendMessage(clickedPlayer, "messages.notDoYourself", null);
            return;
        }
        if (clanMember.getName().equalsIgnoreCase(clan.getOwner())) {
            ConfigUtil.sendMessage(clickedPlayer, "messages.notAccess", null);
            return;
        }

        if (NBTUtil.hasItemNBT(clickedItem, "barrierItem")) {
            Main.getInstance().getClanManager().kickPlayer(clan, clanMember);
            if (clanMember.getPlayer() != null && clanMember.getPlayer().isOnline()) {
                ConfigUtil.sendMessage(clanMember.getPlayer(), "messages.youKicked",
                        ConfigUtil.setHolder(new String[]{"%player%"}, new String[]{clickedPlayer.getName()}));
            }
            for (ClanMember m : clan.getMemberList()) {
                if (m.getPlayer() != null && m.getPlayer().isOnline()) {
                    ConfigUtil.sendMessage(m.getPlayer(), "messages.playerKicked",
                            ConfigUtil.setHolder(new String[]{"%player%", "%target%"},
                                    new String[]{clickedPlayer.getName(), clanMember.getName()}));
                }
            }
            clickedPlayer.closeInventory();
        } else if (NBTUtil.hasItemNBT(clickedItem, "dye")) {
            String permName = NBTUtil.getNBTvalue(clickedItem, "dye");
            if (permName == null) return;
            Permissions perm;
            try {
                perm = Permissions.valueOf(permName.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return;
            }
            List<Permissions> perms = new ArrayList<>(clanMember.getRole().getPermissions());
            if (perms.contains(perm)) {
                perms.remove(perm);
                clickedItem.setType(Material.GRAY_DYE);
            } else {
                perms.add(perm);
                clickedItem.setType(Material.LIME_DYE);
            }
            clanMember.getRole().setPermissions(perms);
            ItemMeta dyeMeta = clickedItem.getItemMeta();
            if (dyeMeta != null) {
                boolean hasPerm = perms.contains(perm);
                List<String> dyeLore = new ArrayList<>();
                dyeLore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
                dyeLore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fСтатус: " + (hasPerm ? "&aВключено" : "&cВыключено")));
                dyeLore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
                dyeLore.add(HexUtil.translateHexColorCodes("&7● &fНажмите для переключения"));
                dyeMeta.setLore(dyeLore);
                clickedItem.setItemMeta(dyeMeta);
            }
            clickedPlayer.updateInventory();
        }
    }

    private static String roleName(ClanMember member) {
        try {
            return member.getRole().getName();
        } catch (Exception e) {
            return "Участник";
        }
    }

    private static double round(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return 0.0;
        return Math.round(value * 100.0) / 100.0;
    }
}