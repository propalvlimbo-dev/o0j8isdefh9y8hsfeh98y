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
import ru.rooyzee.elytrixclans.Main;
import ru.rooyzee.elytrixclans.clans.Clan;
import ru.rooyzee.elytrixclans.clans.ClanMember;
import ru.rooyzee.elytrixclans.permission.Permissions;
import ru.rooyzee.elytrixclans.role.Roles;
import ru.rooyzee.elytrixclans.status.Status;
import ru.rooyzee.elytrixclans.utils.ConfigUtil;
import ru.rooyzee.elytrixclans.utils.HexUtil;
import ru.rooyzee.elytrixclans.utils.MenuUtil;
import ru.rooyzee.elytrixclans.utils.NBTUtil;
import ru.rooyzee.elytrixclans.utils.PlayerHeadCache;
import ru.rooyzee.elytrixclans.utils.ValidatorUtil;

public class MemberInventory implements InventoryHolder {

    private static final String NBT_MEMBER_ACTION = "clanMemberAction";

    private final Inventory inventory;
    private final int[] dyesSlots = {21, 22, 23, 30, 31, 32};
    private final String[] permNames = {"shop", "kick", "sethome", "invite", "pvp", "glow"};

    /** Просмотр без управления (совместимость со старыми вызовами). */
    public MemberInventory(ClanMember clanMember) {
        this(clanMember, null);
    }

    public MemberInventory(ClanMember clanMember, Player viewer) {
        inventory = Bukkit.createInventory(this, 54,
                HexUtil.translateHexColorCodes("&#F8BEFB&lУчастник клана"));

        // Рамку ставим первой: раньше applyLayout вызывался в конце и затирал голову (слот 4)
        // и кнопку кика (слот 53) — меню выглядело пустым, а клики по цветам не работали.
        MenuUtil.applyLayout(inventory);

        Status status = ClanMember.getStatus(clanMember);
        List<String> headLore = new ArrayList<>();
        headLore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fРоль: &#F8BEFB" + roleName(clanMember)));
        headLore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fСтатус: " + status.getName()));
        headLore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
        headLore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fУбийств: &#F8BEFB" + clanMember.getKills()));
        headLore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fСмертей: &#F8BEFB" + clanMember.getDeaths()));
        headLore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fУ/С: &#F8BEFB" + clanMember.getKDA()));
        headLore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
        headLore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fОпыт: &#F8BEFB" + round(clanMember.getLevel())));
        PlayerHeadCache.fillHead(inventory, 4, clanMember.getName(), clanMember.getPlayer(),
                HexUtil.translateHexColorCodes("&#F8BEFB" + clanMember.getName()), headLore);

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

        // Панель управления показываем только лидеру и только для чужой карточки:
        // сам себя и владельца клана кикать/понижать нельзя.
        Clan clan = Main.getInstance().getClanManager().getPlayerClan(clanMember.getName());
        ClanMember viewerMember = viewer != null && clan != null
                ? Main.getInstance().getClanManager().getMember(clan, viewer.getName())
                : null;
        boolean leader = viewerMember != null && "Лидер".equals(viewerMember.getRole().getName());
        boolean targetIsOwner = clan != null && clanMember.getName() != null
                && clanMember.getName().equalsIgnoreCase(clan.getOwner());
        boolean self = viewer != null && clanMember.getName() != null
                && clanMember.getName().equalsIgnoreCase(viewer.getName());

        if (!leader || targetIsOwner || self) return;

        boolean moderator = "Модератор".equals(clanMember.getRole().getName());
        if (moderator) {
            inventory.setItem(47, button(Material.IRON_INGOT, "&7« &eПонизить до участника &7»",
                    "Сейчас: &#F8BEFBМодератор", "Нажмите, чтобы понизить", "demote"));
        } else {
            inventory.setItem(47, button(Material.GOLD_INGOT, "&7« &aПовысить до модератора &7»",
                    "Сейчас: &#F8BEFB" + roleName(clanMember), "Нажмите, чтобы повысить", "promote"));
        }

        inventory.setItem(51, button(Material.NETHER_STAR, "&7« &6Передать лидерство &7»",
                "&cВы станете обычным участником", "Нажмите, чтобы передать", "setleader"));

        ItemStack kickBtn = button(Material.RED_DYE, "&7« &cКикнуть игрока &7»",
                "Удалить из клана: &cБезвозвратно", "Нажмите для кика", null);
        NBTUtil.addItemNBT(kickBtn, "barrierItem", "");
        inventory.setItem(53, kickBtn);
    }

    private static ItemStack button(Material material, String title, String description,
                                    String hint, String action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(HexUtil.translateHexColorCodes(title));
            meta.setLore(Arrays.asList(
                    HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "),
                    HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &f" + description),
                    HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "),
                    HexUtil.translateHexColorCodes("&7● &f" + hint)));
            item.setItemMeta(meta);
        }
        if (action != null) NBTUtil.addItemNBT(item, NBT_MEMBER_ACTION, action);
        return item;
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
            clickedPlayer.openInventory(new InfoFunction(clan, clickedPlayer).getInventory());
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

        String action = NBTUtil.getNBTvalue(clickedItem, NBT_MEMBER_ACTION);
        if (action != null) {
            handleRoleAction(clickedPlayer, clan, clanMember, action);
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

    /**
     * Смена роли участника прямо из меню — это /clan promote, /clan demote и передача
     * лидерства без необходимости печатать команды и помнить точный ник.
     */
    private void handleRoleAction(Player clicker, Clan clan, ClanMember target, String action) {
        if ("promote".equals(action)) {
            if ("Модератор".equals(target.getRole().getName())) {
                ConfigUtil.sendMessage(clicker, "messages.alreadyHasPromote", null);
                return;
            }
            target.setRole(new Roles("Модератор", Permissions.SETHOME, Permissions.INVITE,
                    Permissions.KICK, Permissions.PVP, Permissions.GLOW));
            ConfigUtil.sendMessage(clicker, "messages.promoted",
                    ConfigUtil.setHolder(new String[]{"%player%"}, new String[]{target.getName()}));
            clicker.openInventory(new MemberInventory(target, clicker).getInventory());
            return;
        }

        if ("demote".equals(action)) {
            if (!"Модератор".equals(target.getRole().getName())) {
                ConfigUtil.sendMessage(clicker, "messages.notModerator", null);
                return;
            }
            target.setRole(new Roles("Участник"));
            ConfigUtil.sendMessage(clicker, "messages.demoted",
                    ConfigUtil.setHolder(new String[]{"%player%"}, new String[]{target.getName()}));
            clicker.openInventory(new MemberInventory(target, clicker).getInventory());
            return;
        }

        if ("setleader".equals(action)) {
            ClanMember clickerMember = Main.getInstance().getClanManager()
                    .getMember(clan, clicker.getName());
            if (clickerMember == null) return;
            // Лидерство ровно одно: новый лидер получает все права, старый становится участником.
            target.setRole(new Roles("Лидер", Permissions.values()));
            clickerMember.setRole(new Roles("Участник"));
            clan.setOwner(target.getName());
            for (ClanMember m : clan.getMemberList()) {
                if (m == null) continue;
                Player online = m.getPlayer();
                if (online != null && online.isOnline()) {
                    ConfigUtil.sendMessage(online, "messages.leaderChanged",
                            ConfigUtil.setHolder(new String[]{"%player%", "%target%"},
                                    new String[]{clicker.getName(), target.getName()}));
                }
            }
            clicker.openInventory(new InfoFunction(clan, clicker).getInventory());
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