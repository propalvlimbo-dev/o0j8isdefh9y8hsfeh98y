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
import org.bukkit.inventory.meta.SkullMeta;
import ru.rooyzee.elytrixclans.Main;
import ru.rooyzee.elytrixclans.clans.Clan;
import ru.rooyzee.elytrixclans.clans.ClanMember;
import ru.rooyzee.elytrixclans.level.Level;
import ru.rooyzee.elytrixclans.status.Status;
import ru.rooyzee.elytrixclans.utils.HexUtil;
import ru.rooyzee.elytrixclans.utils.LevelUtil;
import ru.rooyzee.elytrixclans.utils.MenuUtil;
import ru.rooyzee.elytrixclans.utils.PlayerHeadCache;
import ru.rooyzee.elytrixclans.utils.ValidatorUtil;

public class InfoFunction implements InventoryHolder {

    private final Inventory inventory;

    private static final int[] MEMBER_SLOTS = {
            11, 12, 13, 14, 15,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            38, 39, 40, 41, 42
    };

    public InfoFunction(Clan clan) {
        inventory = Bukkit.createInventory(this, 54,
                HexUtil.translateHexColorCodes("&#F8BEFB&lИнформация о клане"));

        MenuUtil.applyLayout(inventory);

        int onlineMembers = 0;
        int i = 0;

        for (ClanMember member : clan.getMemberList()) {
            if (member == null) continue;
            // Как и раньше: показываем (и считаем в «онлайн») только первые 24 участника.
            if (i >= MEMBER_SLOTS.length) break;

            // Статус считаем один раз на участника: в нём рефлексия по Essentials/CMI.
            Status status = ClanMember.getStatus(member);
            if (status != Status.OFFLINE) onlineMembers++;

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(HexUtil.translateHexColorCodes("&#F8BEFB" + member.getName()));

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
                lore.add(HexUtil.translateHexColorCodes("&7● &fНажмите для подробной информации"));
                meta.setLore(lore);
                head.setItemMeta(meta);
            }
            // Головы оффлайн-игроков берём только из кэша: ни одного сетевого запроса из main-потока.
            PlayerHeadCache.fillHead(inventory, MEMBER_SLOTS[i], head, member.getName(), member.getPlayer());
            i++;
        }

        ItemStack infoItem = new ItemStack(Material.CLOCK);
        ItemMeta infoMeta = infoItem.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName(HexUtil.translateHexColorCodes("&7« &#F8BEFBИнформация о клане &7»"));
            Level currentLevel = LevelUtil.getClanLevel(round(clan.getExp()));
            Level nextLevel = LevelUtil.getNextClanLevel(round(clan.getExp()));
            List<String> lore = new ArrayList<>();
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fНазвание: &#F8BEFB" + clan.getName()));
            String needExp = nextLevel != null ? String.valueOf(nextLevel.getExp()) : "&#F8BEFBMAXIMUM";
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fУровень: &#F8BEFB" + currentLevel.getLevel()
                    + " &8(&#F8BEFB" + round(clan.getExp()) + "&8/&#F8BEFB" + needExp + "&8)"));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fУчастники: &#F8BEFB" + clan.getMemberList().size()
                    + "&8/&#F8BEFB" + currentLevel.getMaxMembers()
                    + " &8(&#F8BEFB" + onlineMembers + " &aонлайн&8)"));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
            infoMeta.setLore(lore);
            infoItem.setItemMeta(infoMeta);
        }
        inventory.setItem(4, infoItem);
        inventory.setItem(45, MenuUtil.createCloseButton());
    }

    public void onInventoryClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() != Material.PLAYER_HEAD) return;
        ItemMeta clickedMeta = event.getCurrentItem().getItemMeta();
        if (clickedMeta == null || clickedMeta.getDisplayName() == null) return;
        String name = ValidatorUtil.removeAllColors(clickedMeta.getDisplayName());
        if (name == null || name.isEmpty()) return;
        ClanMember member = Main.getInstance().getClanManager().getPlayerClanMember(name);
        if (member != null) {
            player.openInventory(new MemberInventory(member).getInventory());
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