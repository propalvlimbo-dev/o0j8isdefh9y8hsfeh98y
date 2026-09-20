package ru.rooyzee.elytrixclans.function.impl.info;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.rooyzee.elytrixclans.Main;
import ru.rooyzee.elytrixclans.clans.Clan;
import ru.rooyzee.elytrixclans.clans.ClanMember;
import ru.rooyzee.elytrixclans.function.impl.confirm.DisbandConfirmManager;
import ru.rooyzee.elytrixclans.function.impl.glow.GlowFunction;
import ru.rooyzee.elytrixclans.function.impl.shop.ShopFunction;
import ru.rooyzee.elytrixclans.level.Level;
import ru.rooyzee.elytrixclans.permission.Permissions;
import ru.rooyzee.elytrixclans.status.Status;
import ru.rooyzee.elytrixclans.utils.ConfigUtil;
import ru.rooyzee.elytrixclans.utils.HexUtil;
import ru.rooyzee.elytrixclans.utils.LevelUtil;
import ru.rooyzee.elytrixclans.utils.MenuUtil;
import ru.rooyzee.elytrixclans.utils.NBTUtil;
import ru.rooyzee.elytrixclans.utils.PlayerHeadCache;
import ru.rooyzee.elytrixclans.utils.ValidatorUtil;

/**
 * Меню клана: состав, прогресс уровня и панель быстрых действий.
 *
 * Меню «живое»: кнопки внизу повторяют команды /clan (магазин, подсветка, PvP, дом),
 * но показывают только то, на что у открывшего реально есть права, а клик по участнику
 * ведёт в карточку с управлением ролями и киком. Так лидеру не нужно помнить команды.
 */
public class InfoFunction implements InventoryHolder {

    private final Inventory inventory;
    /** Кто открыл меню: от него зависит набор доступных кнопок. */
    private final Player viewer;
    private final Clan clan;

    private static final int[] MEMBER_SLOTS = {
            11, 12, 13, 14, 15,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            38, 39, 40, 41, 42
    };

    // Панель действий в нижнем ряду.
    private static final int SLOT_SHOP = 47;
    private static final int SLOT_GLOW = 48;
    private static final int SLOT_PVP = 49;
    private static final int SLOT_HOME = 50;
    private static final int SLOT_SETHOME = 51;
    private static final int SLOT_LEAVE = 53;

    private static final String NBT_ACTION = "clanInfoAction";

    /** Просмотр чужого клана: без панели действий. */
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
        boolean ownClan = viewerMember != null;
        boolean leader = ownClan && "Лидер".equals(viewerMember.getRole().getName());

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
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
            // Подсказка честно отражает, что именно даст клик этому игроку.
            if (leader && !isOwner && !self) {
                lore.add(HexUtil.translateHexColorCodes("&7● &fНажмите, чтобы управлять участником"));
            } else {
                lore.add(HexUtil.translateHexColorCodes("&7● &fНажмите для подробной информации"));
            }

            // Голова берётся из кэша готовых болванок: ни сетевых запросов, ни пересоздания
            // профиля в главном потоке. Неизвестные владельцы доклеиваются позже, по тикам.
            String prefix = isOwner ? "&#F8BEFB&l★ &#F8BEFB" : "&#F8BEFB";
            PlayerHeadCache.fillHead(inventory, MEMBER_SLOTS[i], member.getName(), member.getPlayer(),
                    HexUtil.translateHexColorCodes(prefix + member.getName()), lore);
            i++;
        }

        inventory.setItem(4, clanInfoItem(onlineMembers));
        inventory.setItem(45, MenuUtil.createCloseButton());

        if (ownClan) {
            buildActionBar(viewerMember, leader);
        }
    }

    /** Шапка: название, уровень, полоса прогресса опыта и состав. */
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
        // Полоса прогресса делает рост клана наглядным: видно, сколько осталось до уровня.
        lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ " + progressBar(currentLevel, nextLevel)));
        lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
        lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fУчастники: &#F8BEFB" + clan.getMemberList().size()
                + " &8/ &#F8BEFB" + currentLevel.getMaxMembers()
                + " &8(&a" + onlineMembers + " онлайн&8)"));
        lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fPvP в клане: "
                + (clan.isPvp() ? "&aвключён" : "&cвыключен")));
        lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fДом клана: "
                + (clan.getHome() != null ? "&aустановлен" : "&cне установлен")));
        lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
        infoMeta.setLore(lore);
        infoItem.setItemMeta(infoMeta);
        return infoItem;
    }

    /** Полоса прогресса до следующего уровня: 20 делений. */
    private String progressBar(Level current, Level next) {
        if (next == null) return "&a██████████████████████ &7100%";
        int from = current != null ? current.getExp() : 0;
        int to = next.getExp();
        double span = to - from;
        double done = span > 0 ? (clan.getExp() - from) / span : 1.0;
        if (done < 0) done = 0;
        if (done > 1) done = 1;
        int filled = (int) Math.round(done * 20);
        StringBuilder bar = new StringBuilder("&a");
        for (int i = 0; i < 20; i++) {
            if (i == filled) bar.append("&8");
            bar.append("█");
        }
        bar.append(" &7").append((int) Math.round(done * 100)).append("%");
        return bar.toString();
    }

    /** Нижняя панель: только те действия, на которые у игрока есть права. */
    private void buildActionBar(ClanMember viewerMember, boolean leader) {
        List<Permissions> perms = viewerMember.getRole().getPermissions();

        if (perms.contains(Permissions.SHOP)) {
            inventory.setItem(SLOT_SHOP, action(Material.EMERALD, "&7« &aМагазин клана &7»",
                    "shop", "Открыть витрину", "Нажмите, чтобы открыть"));
        }
        if (perms.contains(Permissions.GLOW)) {
            inventory.setItem(SLOT_GLOW, action(Material.LEATHER_HELMET, "&7« &#F8BEFBПодсветка клана &7»",
                    "glow", "Цвет свечения союзников", "Нажмите, чтобы выбрать цвет"));
        }
        if (perms.contains(Permissions.PVP)) {
            inventory.setItem(SLOT_PVP, action(clan.isPvp() ? Material.IRON_SWORD : Material.SHIELD,
                    clan.isPvp() ? "&7« &cPvP: включён &7»" : "&7« &aPvP: выключен &7»",
                    "pvp",
                    clan.isPvp() ? "Соклановцы могут бить друг друга" : "Урон между соклановцами отключён",
                    "Нажмите, чтобы переключить"));
        }
        inventory.setItem(SLOT_HOME, action(Material.COMPASS, "&7« &#F8BEFBДом клана &7»", "home",
                clan.getHome() != null ? "Телепорт к дому клана" : "Дом ещё не установлен",
                clan.getHome() != null ? "Нажмите для телепортации" : "Сначала установите дом"));
        if (perms.contains(Permissions.SETHOME)) {
            inventory.setItem(SLOT_SETHOME, action(Material.RED_BED, "&7« &#F8BEFBУстановить дом &7»",
                    "sethome", "Дом встанет на вашу позицию", "Нажмите, чтобы установить"));
        }

        // Кому можно распустить клан — тому кнопка роспуска, остальным выход из состава.
        if (leader && perms.contains(Permissions.DELETE)) {
            inventory.setItem(SLOT_LEAVE, action(Material.TNT, "&7« &cРаспустить клан &7»", "disband",
                    "Клан будет удалён навсегда", "Нажмите, потребуется подтверждение"));
        } else {
            inventory.setItem(SLOT_LEAVE, action(Material.OAK_DOOR, "&7« &cПокинуть клан &7»", "leave",
                    "Вы выйдете из состава клана", "Нажмите, чтобы выйти"));
        }
    }

    private ItemStack action(Material material, String title, String action, String description, String hint) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(HexUtil.translateHexColorCodes(title));
            meta.setLore(Arrays.asList(
                    HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "),
                    HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &f" + description),
                    HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "),
                    HexUtil.translateHexColorCodes("&7● &f" + hint)));
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        NBTUtil.addItemNBT(item, NBT_ACTION, action);
        return item;
    }

    public void onInventoryClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        ItemStack item = event.getCurrentItem();
        if (item == null) return;

        String action = NBTUtil.getNBTvalue(item, NBT_ACTION);
        if (action != null) {
            handleAction(player, action);
            return;
        }

        if (item.getType() != Material.PLAYER_HEAD) return;
        ItemMeta clickedMeta = item.getItemMeta();
        if (clickedMeta == null || clickedMeta.getDisplayName() == null) return;
        // В имени лидера есть звезда — убираем её вместе с цветами, иначе ник не найдётся.
        String name = ValidatorUtil.removeAllColors(clickedMeta.getDisplayName()).replace("★", "").trim();
        if (name.isEmpty()) return;
        ClanMember member = Main.getInstance().getClanManager().getPlayerClanMember(name);
        if (member != null) {
            player.openInventory(new MemberInventory(member, player).getInventory());
        }
    }

    /**
     * Кнопки панели. Права проверяются заново на каждый клик: меню могло провисеть открытым
     * после понижения роли или выхода игрока из клана.
     */
    private void handleAction(Player player, String action) {
        Clan actual = Main.getInstance().getClanManager().getPlayerClan(player);
        if (actual == null || actual != clan) {
            ConfigUtil.sendMessage(player, "messages.withoutClan", null);
            player.closeInventory();
            return;
        }
        ClanMember member = Main.getInstance().getClanManager().getMember(actual, player.getName());
        if (member == null) return;
        List<Permissions> perms = member.getRole().getPermissions();
        boolean leader = "Лидер".equals(member.getRole().getName());

        if ("shop".equals(action)) {
            if (!perms.contains(Permissions.SHOP)) {
                ConfigUtil.sendMessage(player, "messages.noPermission", null);
                return;
            }
            player.openInventory(new ShopFunction(player).getInventory());
            return;
        }

        if ("glow".equals(action)) {
            if (!perms.contains(Permissions.GLOW)) {
                ConfigUtil.sendMessage(player, "messages.noPermission", null);
                return;
            }
            player.openInventory(new GlowFunction().getInventory());
            return;
        }

        if ("pvp".equals(action)) {
            if (!perms.contains(Permissions.PVP)) {
                ConfigUtil.sendMessage(player, "messages.noPermission", null);
                return;
            }
            // Поведение один в один с /clan pvp, включая работу с подсветкой.
            boolean wasPvpEnabled = actual.isPvp();
            actual.setPvp(!wasPvpEnabled);
            String key = wasPvpEnabled ? "messages.clan_pvp_disable" : "messages.clan_pvp_enable";
            for (ClanMember m : actual.getMemberList()) {
                if (m == null) continue;
                Player online = m.getPlayer();
                if (online != null && online.isOnline()) {
                    ConfigUtil.sendMessage(online, key, null);
                }
            }
            if (Main.getInstance().getGlowManager() != null) {
                if (!wasPvpEnabled) {
                    Main.getInstance().getGlowManager().disableGlow(actual);
                } else if (Main.getInstance().getGlowManager().hasGlow(actual)) {
                    Main.getInstance().getGlowManager().enableGlow(actual);
                }
            }
            player.openInventory(new InfoFunction(actual, player).getInventory());
            return;
        }

        if ("home".equals(action)) {
            if (actual.getHome() == null) {
                ConfigUtil.sendMessage(player, "messages.withoutHome", null);
                return;
            }
            World world = actual.getHome().getWorld() != null
                    ? Bukkit.getWorld(actual.getHome().getWorld()) : null;
            if (world == null) {
                ConfigUtil.sendMessage(player, "messages.withoutHome", null);
                return;
            }
            player.closeInventory();
            player.teleport(new Location(world,
                    actual.getHome().getX(), actual.getHome().getY(), actual.getHome().getZ(),
                    (float) actual.getHome().getYaw(), (float) actual.getHome().getPitch()));
            return;
        }

        if ("sethome".equals(action)) {
            if (!perms.contains(Permissions.SETHOME)) {
                ConfigUtil.sendMessage(player, "messages.noPermission", null);
                return;
            }
            ru.rooyzee.elytrixclans.function.impl.sethome.SetHomeFunction.setClanHome(player);
            ConfigUtil.sendMessage(player, "messages.homeSetted",
                    ConfigUtil.setHolder(new String[]{"%player%"}, new String[]{player.getName()}));
            player.openInventory(new InfoFunction(actual, player).getInventory());
            return;
        }

        if ("disband".equals(action)) {
            if (!perms.contains(Permissions.DELETE)) {
                ConfigUtil.sendMessage(player, "messages.noPermission", null);
                return;
            }
            // Роспуск подтверждается в чате — тем же способом, что и /clan disband.
            player.closeInventory();
            DisbandConfirmManager.request(player.getName(), actual.getName());
            ConfigUtil.sendMessage(player, "messages.disbandConfirm",
                    ConfigUtil.setHolder(new String[]{"%clan%"}, new String[]{actual.getName()}));
            return;
        }

        if ("leave".equals(action)) {
            if (leader) {
                ConfigUtil.sendMessage(player, "messages.leaderCantLeave", null);
                return;
            }
            player.closeInventory();
            Main.getInstance().getClanManager().kickPlayer(actual, player);
            ConfigUtil.sendMessage(player, "messages.leaveClan",
                    ConfigUtil.setHolder(new String[]{"%player%"}, new String[]{player.getName()}));
            for (ClanMember m : actual.getMemberList()) {
                if (m == null) continue;
                Player online = m.getPlayer();
                if (online != null && online.isOnline()) {
                    ConfigUtil.sendMessage(online, "messages.playerLeft",
                            ConfigUtil.setHolder(new String[]{"%player%"}, new String[]{player.getName()}));
                }
            }
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
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
