package ru.rooyzee.elytrixclans.listener;

import java.util.EnumSet;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import ru.rooyzee.elytrixclans.Main;
import ru.rooyzee.elytrixclans.clans.Clan;

public class BlockBreakListener implements Listener {

    private static final Set<Material> ORES = EnumSet.of(
            Material.EMERALD_ORE, Material.DIAMOND_ORE, Material.GOLD_ORE,
            Material.IRON_ORE, Material.LAPIS_ORE, Material.REDSTONE_ORE, Material.COAL_ORE
    );

    // MONITOR + ignoreCancelled: начисляем только за реально сломанные блоки.
    // Иначе зажатая ЛКМ на защищённой/неломаемой руде (событие отменено другим плагином)
    // приносила бы бесконечный опыт и поинты клана.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Main main = Main.getInstance();
        if (main == null || main.getClanManager() == null) return;
        // Сначала проверяем блок: это самый частый событие-хук из всех, и подавляющее
        // большинство поломок — не руда.
        if (!ORES.contains(e.getBlock().getType())) return;
        Player player = e.getPlayer();
        Clan clan = main.getClanManager().getPlayerClan(player);
        if (clan == null) return;
        double expForBlock = main.getConfig().getDouble("exp-for-block", 0);
        double pointsForBlock = main.getConfig().getDouble("points-for-block", 0);
        if (expForBlock == 0 && pointsForBlock == 0) return;
        clan.setExp(clan.getExp() + expForBlock);
        clan.setPoints(clan.getPoints() + pointsForBlock);
    }
}