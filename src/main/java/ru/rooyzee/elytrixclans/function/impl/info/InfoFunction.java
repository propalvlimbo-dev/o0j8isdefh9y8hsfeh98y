package ru.rooyzee.elytrixclans.function.impl.info;

import java.util.ArrayList;
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
import ru.rooyzee.elytrixclans.level.Level;
import ru.rooyzee.elytrixclans.role.ClanRoles;
import ru.rooyzee.elytrixclans.status.Status;
import ru.rooyzee.elytrixclans.utils.HexUtil;
import ru.rooyzee.elytrixclans.utils.LevelUtil;
import ru.rooyzee.elytrixclans.utils.MenuUtil;
import ru.rooyzee.elytrixclans.utils.NBTUtil;
import ru.rooyzee.elytrixclans.utils.PlayerHeadCache;

/**
 * Меню клана: шапка с информацией и состав.
 *
 * Управление кланом живёт в командах /clan — здесь его намеренно нет.
 * Единственное действие в меню доступно лидеру: клик по голове участника открывает
 * его карточку, где можно выдать роль или кикнуть. Остальным клик ничего не делает,
 * поэтому им и подсказка в лоре не показывается.
 */
public class InfoFunction implements InventoryHolder {

    private final Inventory inventory;
    /** Кто открыл меню: от него зависит, показывать ли подсказку об управлении. */
    private final Player viewer;
    private final Clan clan;

    /** Ник участника пишем прямо в предмет: разбирать его обратно из имени ненадёжно. */
    public static final String NBT_MEMBER = "clanMemberName";

    private static final int[] MEMBER_SLOTS = {
            11, 12, 13, 14, 15,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            38, 39, 40, 41, 42
    };

    /** Просмотр чужого клана: клик по участнику там ничего не открывает. */
    public InfoFunction(Clan clan) {
        this(clan, null);
    }

    public InfoFunction(Clan clan, Player viewer) {
        this.clan = clan;
        this.viewer = viewer;

        inventory = Bukkit.createInventory(this, 54,
                HexUtil.translateHexColorCodes("&#F8BEFB&lИнформация о клане"));

        MenuUtil.applyLayout(inventory);

        // Управлять можно только своим кланом: чужое меню остаётся просмотром.
        ClanMember viewerMember = viewer != null
                ? Main.getInstance().getClanManager().getMember(clan, viewer.getName())
                : null;
        boolean leader = viewerMember != null && ClanRoles.LEADER.equals(
                ClanRoles.normalize(viewerMember.getRole().getName()));

        int onlineMembers = 0;
        int i = 0;

        for (ClanMember member : clan.getMemberList()) {
            if (member == null) continue;
            // Как и раньше: показываем (и считаем в «онлайн») только первые 24 участника.
            if (i >= MEMBER_SLOTS.length) break;

            // Статус считаем один раз на участника: в нём рефлексия по Essentials/CMI.
            Status status = ClanMember.getStatus(member);
            if (status != Status.OFFLINE) onlineMembers++;

            boolean isOwner = member.getName() != null && member.getName().equalsIgnoreCase(clan.getOwner());
            boolean self = viewer != null && member.getName() != null
                    && member.getName().equalsIgnoreCase(viewer.getName());

            List<String> lore = new ArrayList<>();
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fРоль: &#F8BEFB" + roleName(member)));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fСтатус: " + status.getName()));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fУбийств: &#F8BEFB" + member.getKills()));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fСмертей: &#F8BEFB" + member.getDeaths()));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fУ/С: &#F8BEFB" + member.getKDA()));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fОпыт: &#F8BEFB" + round(member.getLevel())));
            // Подсказка только лидеру: обычному участнику клик по голове ничего не даёт,
            // поэтому и строки «Нажмите...» у него быть не должно.
            if (leader && !isOwner && !self) {
                lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
                lore.add(HexUtil.translateHexColorCodes("&7● &fНажмите, чтобы управлять участником"));
            }

            // Голова берётся из кэша готовых болванок: ни сетевых запросов, ни пересоздания
            // профиля в главном потоке. Неизвестные владельцы доклеиваются позже, по тикам.
            String prefix = isOwner ? "&#F8BEFB&l★ &#F8BEFB" : "&#F8BEFB";
            PlayerHeadCache.fillHead(inventory, MEMBER_SLOTS[i], member.getName(), member.getPlayer(),
                    HexUtil.translateHexColorCodes(prefix + member.getName()), lore);
            // Ник в NBT: по нему клик находит участника даже со звездой и цветами в имени.
            ItemStack head = inventory.getItem(MEMBER_SLOTS[i]);
            if (head != null) {
                NBTUtil.addItemNBT(head, NBT_MEMBER, member.getName());
                inventory.setItem(MEMBER_SLOTS[i], head);
            }
            i++;
        }

        inventory.setItem(4, clanInfoItem(onlineMembers));
        inventory.setItem(45, MenuUtil.createCloseButton());
    }

    /** Шапка: название, лидер, уровень, опыт и состав. */
    private ItemStack clanInfoItem(int onlineMembers) {
        ItemStack infoItem = new ItemStack(Material.CLOCK);
        ItemMeta infoMeta = infoItem.getItemMeta();
        if (infoMeta == null) return infoItem;

        infoMeta.setDisplayName(HexUtil.translateHexColorCodes("&7« &#F8BEFBИнформация о клане &7»"));
        Level currentLevel = LevelUtil.getClanLevel(round(clan.getExp()));
        Level nextLevel = LevelUtil.getNextClanLevel(round(clan.getExp()));

        List<String> lore = new ArrayList<>();
        lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
        lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fНазвание: &#F8BEFB" + clan.getName()));
        lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fЛидер: &#F8BEFB" + clan.getOwner()));
        lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fУровень: &#F8BEFB" + currentLevel.getLevel()));

        String needExp = nextLevel != null ? String.valueOf(nextLevel.getExp()) : "MAXIMUM";
        lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fОпыт: &#F8BEFB"
                + round(clan.getExp()) + " &8/ &#F8BEFB" + needExp));
        lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
        lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fУчастники: &#F8BEFB" + clan.getMemberList().size()
                + " &8/ &#F8BEFB" + currentLevel.getMaxMembers()
                + " &8(&a" + onlineMembers + " онлайн&8)"));
        lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fPvP в клане: "
                + (clan.isPvp() ? "&aвключён" : "&cвыключен")));
        lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
        infoMeta.setLore(lore);
        infoItem.setItemMeta(infoMeta);
        return infoItem;
    }

    public void onInventoryClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() != Material.PLAYER_HEAD) return;
        String name = NBTUtil.getNBTvalue(item, NBT_MEMBER);
        if (name == null || name.isEmpty()) return;
        // Ищем участника в ЭТОМ клане: getPlayerClanMember искал бы по всем кланам сразу.
        ClanMember member = Main.getInstance().getClanManager().getMember(clan, name);
        if (member == null) return;

        // Карточка участника — инструмент лидера. Обычному игроку клик по голове
        // ничего не открывает: у него там нет ни одного доступного действия.
        ClanMember clicker = Main.getInstance().getClanManager().getMember(clan, player.getName());
        boolean leader = clicker != null
                && ClanRoles.LEADER.equals(ClanRoles.normalize(clicker.getRole().getName()));
        if (!leader) return;
        if (member.getName() == null) return;
        if (member.getName().equalsIgnoreCase(player.getName())) return;
        if (member.getName().equalsIgnoreCase(clan.getOwner())) return;

        player.openInventory(new MemberInventory(member, player).getInventory());
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private static String roleName(ClanMember member) {
        try {
            return ClanRoles.normalize(member.getRole().getName());
        } catch (Exception e) {
            return ClanRoles.ROOKIE;
        }
    }

    private static double round(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return 0.0;
        return Math.round(value * 100.0) / 100.0;
    }
}
