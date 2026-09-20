package ru.rooyzee.elytrixclans.function.impl.info;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
import ru.rooyzee.elytrixclans.role.ClanRoles;
import ru.rooyzee.elytrixclans.status.Status;
import ru.rooyzee.elytrixclans.utils.ConfigUtil;
import ru.rooyzee.elytrixclans.utils.HexUtil;
import ru.rooyzee.elytrixclans.utils.MenuUtil;
import ru.rooyzee.elytrixclans.utils.NBTUtil;
import ru.rooyzee.elytrixclans.utils.PlayerHeadCache;

/**
 * Карточка участника клана: то, что открывается лидеру по клику на голову в /clan menu.
 *
 * Оформление намеренно повторяет /clan menu — те же 54 слота, та же рамка из стекла,
 * голова в слоте 4, кнопка выхода в 45. Отличие одно: вместо состава клана здесь
 * два действия лидера — выдать роль и кикнуть.
 *
 * Для обычного игрока это меню не открывается вовсе: у него тут нет ни одного действия.
 */
public class MemberInventory implements InventoryHolder {

    private static final String NBT_MEMBER_ACTION = "clanMemberAction";

    /** Слоты ролей — по центру, ровно там же, где в /clan menu идёт второй ряд состава. */
    private static final int SLOT_ROOKIE = 20;
    private static final int SLOT_TRAINEE = 21;
    private static final int SLOT_VETERAN = 22;
    private static final int SLOT_MODERATOR = 23;
    private static final int SLOT_LEADER = 24;
    private static final int SLOT_KICK = 40;

    private final Inventory inventory;
    /** Имя участника: обработчик кликов достаёт по нему актуальные данные. */
    private final String memberName;

    /** Просмотр без управления (совместимость со старыми вызовами). */
    public MemberInventory(ClanMember clanMember) {
        this(clanMember, null);
    }

    public MemberInventory(ClanMember clanMember, Player viewer) {
        this.memberName = clanMember.getName();

        inventory = Bukkit.createInventory(this, 54,
                HexUtil.translateHexColorCodes("&#F8BEFB&lУчастник клана"));

        // Та же рамка, что и в меню клана: меню выглядит единообразно.
        MenuUtil.applyLayout(inventory);

        Status status = ClanMember.getStatus(clanMember);
        String role = ClanRoles.normalize(clanMember.getRole().getName());

        List<String> headLore = new ArrayList<>();
        headLore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fРоль: &#F8BEFB" + role));
        headLore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fСтатус: " + status.getName()));
        headLore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
        headLore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fУбийств: &#F8BEFB" + clanMember.getKills()));
        headLore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fСмертей: &#F8BEFB" + clanMember.getDeaths()));
        headLore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fУ/С: &#F8BEFB" + clanMember.getKDA()));
        headLore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
        headLore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fВклад в клан: &#F8BEFB"
                + round(clanMember.getLevel()) + " опыта"));
        // Автоматические роли растут сами — показываем, сколько осталось до следующей.
        if (!ClanRoles.isManual(role)) {
            String next = ClanRoles.nextAutoRole(clanMember.getLevel());
            if (next != null) {
                headLore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fДо роли &#F8BEFB" + next
                        + "&f: &#F8BEFB" + round(ClanRoles.expToNextRole(clanMember.getLevel())) + " опыта"));
            }
        }
        headLore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
        PlayerHeadCache.fillHead(inventory, 4, clanMember.getName(), clanMember.getPlayer(),
                HexUtil.translateHexColorCodes("&#F8BEFB" + clanMember.getName()), headLore);

        inventory.setItem(45, MenuUtil.createBackButton());

        // Управление — только лидеру и только в чужой карточке (не в своей и не владельца).
        Clan clan = Main.getInstance().getClanManager().getPlayerClan(clanMember.getName());
        ClanMember viewerMember = viewer != null && clan != null
                ? Main.getInstance().getClanManager().getMember(clan, viewer.getName())
                : null;
        boolean leader = viewerMember != null
                && ClanRoles.LEADER.equals(ClanRoles.normalize(viewerMember.getRole().getName()));
        boolean targetIsOwner = clan != null && clanMember.getName() != null
                && clanMember.getName().equalsIgnoreCase(clan.getOwner());
        boolean self = viewer != null && clanMember.getName() != null
                && clanMember.getName().equalsIgnoreCase(viewer.getName());

        if (!leader || targetIsOwner || self) return;

        inventory.setItem(13, sectionLabel());

        roleButton(SLOT_ROOKIE, Material.LEATHER_BOOTS, ClanRoles.ROOKIE, role,
                "Только бой за клан, магазин закрыт");
        roleButton(SLOT_TRAINEE, Material.IRON_INGOT, ClanRoles.TRAINEE, role,
                "Доступ к магазину клана");
        roleButton(SLOT_VETERAN, Material.GOLD_INGOT, ClanRoles.VETERAN, role,
                "Знак заслуг, доступ к магазину");
        roleButton(SLOT_MODERATOR, Material.DIAMOND, ClanRoles.MODERATOR, role,
                "Кик из клана и переключение PvP");
        roleButton(SLOT_LEADER, Material.NETHER_STAR, ClanRoles.LEADER, role,
                "&cВы сами станете Новичком");

        ItemStack kickBtn = button(Material.RED_DYE, "&7« &cКикнуть игрока &7»",
                "Удалить из клана: &cБезвозвратно", "Нажмите для кика", null);
        NBTUtil.addItemNBT(kickBtn, NBT_MEMBER_ACTION, "kick");
        inventory.setItem(SLOT_KICK, kickBtn);
    }

    private ItemStack sectionLabel() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(HexUtil.translateHexColorCodes("&7« &#F8BEFBВыдать роль &7»"));
            meta.setLore(Arrays.asList(
                    HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "),
                    HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fНовичок, Стажёр и Опытный"),
                    HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fучастник получает и сам — за опыт."),
                    HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fМодератора и Лидера выдаёте только вы."),
                    HexUtil.translateHexColorCodes("&#F8BEFB&l┃ ")));
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Кнопка выдачи роли; текущая роль подсвечивается зачарованием и пометкой. */
    private void roleButton(int slot, Material material, String role, String currentRole,
                            String description) {
        boolean active = role.equals(currentRole);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(HexUtil.translateHexColorCodes(
                    (active ? "&7« &a" : "&7« &#F8BEFB") + role + " &7»"));
            List<String> lore = new ArrayList<>();
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &f" + description));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
            lore.add(HexUtil.translateHexColorCodes(active
                    ? "&a● Текущая роль участника"
                    : "&7● &fНажмите, чтобы выдать"));
            meta.setLore(lore);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES,
                    org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        if (active) {
            // Блеск у текущей роли: видно с одного взгляда, без чтения лора.
            item.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.DURABILITY, 1);
        }
        NBTUtil.addItemNBT(item, NBT_MEMBER_ACTION, "role:" + role);
        inventory.setItem(slot, item);
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

    public void onInventoryClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getCurrentItem() == null || !(event.getWhoClicked() instanceof Player)) return;
        Player clicker = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        ClanMember clanMember = Main.getInstance().getClanManager().getPlayerClanMember(memberName);
        if (clanMember == null) return;
        Clan clan = Main.getInstance().getClanManager().getPlayerClan(clanMember.getName());
        if (clan == null) return;

        if (NBTUtil.hasItemNBT(clickedItem, "arrowItem")) {
            clicker.openInventory(new InfoFunction(clan, clicker).getInventory());
            return;
        }

        String action = NBTUtil.getNBTvalue(clickedItem, NBT_MEMBER_ACTION);
        if (action == null) return;

        // Права проверяем на каждый клик: меню могло провисеть открытым после смены роли.
        ClanMember clickerMember = Main.getInstance().getClanManager().getMember(clan, clicker.getName());
        if (clickerMember == null) return;
        if (!ClanRoles.LEADER.equals(ClanRoles.normalize(clickerMember.getRole().getName()))) {
            ConfigUtil.sendMessage(clicker, "messages.onlyOwnerRole", null);
            return;
        }
        if (clanMember.getName().equalsIgnoreCase(clicker.getName())) {
            ConfigUtil.sendMessage(clicker, "messages.notDoYourself", null);
            return;
        }
        if (clanMember.getName().equalsIgnoreCase(clan.getOwner())) {
            ConfigUtil.sendMessage(clicker, "messages.notAccess", null);
            return;
        }

        if (action.startsWith("role:")) {
            applyRole(clicker, clan, clanMember, action.substring("role:".length()));
            return;
        }

        if ("kick".equals(action)) {
            Main.getInstance().getClanManager().kickPlayer(clan, clanMember);
            if (clanMember.getPlayer() != null && clanMember.getPlayer().isOnline()) {
                ConfigUtil.sendMessage(clanMember.getPlayer(), "messages.youKicked",
                        ConfigUtil.setHolder(new String[]{"%player%"}, new String[]{clicker.getName()}));
            }
            for (ClanMember m : clan.getMemberList()) {
                if (m == null || m.getPlayer() == null || !m.getPlayer().isOnline()) continue;
                ConfigUtil.sendMessage(m.getPlayer(), "messages.playerKicked",
                        ConfigUtil.setHolder(new String[]{"%player%", "%target%"},
                                new String[]{clicker.getName(), clanMember.getName()}));
            }
            clicker.openInventory(new InfoFunction(clan, clicker).getInventory());
        }
    }

    /** Выдача роли лидером. Передача лидерства обрабатывается отдельно. */
    private void applyRole(Player clicker, Clan clan, ClanMember target, String rawRole) {
        String role = ClanRoles.normalize(rawRole);

        if (ClanRoles.LEADER.equals(role)) {
            ClanMember clickerMember = Main.getInstance().getClanManager()
                    .getMember(clan, clicker.getName());
            if (clickerMember == null) return;
            // Лидер в клане один: бывший владелец опускается до заслуженной роли.
            target.setRole(ClanRoles.leader());
            clickerMember.setRole(ClanRoles.create(ClanRoles.earnedRole(clickerMember.getLevel())));
            clan.setOwner(target.getName());
            broadcast(clan, "messages.leaderChanged", clicker.getName(), target.getName());
            clicker.openInventory(new InfoFunction(clan, clicker).getInventory());
            return;
        }

        if (role.equals(ClanRoles.normalize(target.getRole().getName()))) {
            ConfigUtil.sendMessage(clicker, "messages.roleAlready",
                    ConfigUtil.setHolder(new String[]{"%player%", "%role%"},
                            new String[]{target.getName(), role}));
            return;
        }

        target.setRole(ClanRoles.create(role));
        broadcast(clan, "messages.roleChanged", clicker.getName(), target.getName(), role);
        clicker.openInventory(new MemberInventory(target, clicker).getInventory());
    }

    private void broadcast(Clan clan, String key, String player, String target) {
        broadcast(clan, key, player, target, null);
    }

    private void broadcast(Clan clan, String key, String player, String target, String role) {
        for (ClanMember m : clan.getMemberList()) {
            if (m == null) continue;
            Player online = m.getPlayer();
            if (online == null || !online.isOnline()) continue;
            ConfigUtil.sendMessage(online, key, ConfigUtil.setHolder(
                    new String[]{"%player%", "%target%", "%role%"},
                    new String[]{player, target, role != null ? role : ""}));
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private static double round(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return 0.0;
        return Math.round(value * 100.0) / 100.0;
    }
}
