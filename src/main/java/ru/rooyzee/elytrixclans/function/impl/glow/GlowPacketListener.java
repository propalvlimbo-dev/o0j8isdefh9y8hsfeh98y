package ru.rooyzee.elytrixclans.function.impl.glow;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.Pair;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import ru.rooyzee.elytrixclans.Main;
import ru.rooyzee.elytrixclans.clans.Clan;

public class GlowPacketListener extends PacketAdapter {

    public GlowPacketListener() {
        super(Main.getInstance(), ListenerPriority.NORMAL, PacketType.Play.Server.ENTITY_EQUIPMENT);
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        try {
            Main main = Main.getInstance();
            if (main == null || main.getGlowManager() == null || main.getClanManager() == null) return;
            GlowManager glowManager = main.getGlowManager();

            // Пакеты экипировки летят постоянно и на каждом раньше обходились ВСЕ сущности мира.
            // Сначала — дешёвые проверки, чтобы на сервере без активной подсветки слушатель
            // не делал вообще ничего.
            if (!glowManager.hasAnyGlow()) return;

            Player viewer = event.getPlayer();
            if (viewer == null) return;

            Clan viewerClan = main.getClanManager().getPlayerClan(viewer);
            if (viewerClan == null || !viewerClan.isGlow() || viewerClan.isPvp()) return;

            Color color = glowManager.getGlowColor(viewerClan);
            if (color == null) return;

            int entityId = event.getPacket().getIntegers().read(0);
            Player wearer = findPlayerById(viewer, entityId);
            if (wearer == null || wearer.equals(viewer)) return;

            Clan wearerClan = main.getClanManager().getPlayerClan(wearer);
            if (wearerClan != viewerClan) return;

            List<Pair<EnumWrappers.ItemSlot, ItemStack>> pairs = event.getPacket().getSlotStackPairLists().read(0);
            if (pairs == null || pairs.isEmpty()) return;

            boolean hasHead = false;
            for (Pair<EnumWrappers.ItemSlot, ItemStack> pair : pairs) {
                if (pair != null && pair.getFirst() == EnumWrappers.ItemSlot.HEAD) {
                    hasHead = true;
                    break;
                }
            }
            if (!hasHead) return;

            List<Pair<EnumWrappers.ItemSlot, ItemStack>> newPairs = new ArrayList<>(pairs.size());
            for (Pair<EnumWrappers.ItemSlot, ItemStack> pair : pairs) {
                if (pair != null && pair.getFirst() == EnumWrappers.ItemSlot.HEAD) {
                    newPairs.add(new Pair<>(EnumWrappers.ItemSlot.HEAD, createColoredHelmet(color)));
                } else {
                    newPairs.add(pair);
                }
            }
            event.getPacket().getSlotStackPairLists().write(0, newPairs);
        } catch (Exception ignored) {
        }
    }

    /**
     * Ищем игрока по id сущности только среди игроков того же мира (а не среди всех сущностей):
     * экипировку имеет смысл подменять только у игроков.
     */
    private Player findPlayerById(Player near, int id) {
        World world = near.getWorld();
        if (world == null) return null;
        List<Player> players = world.getPlayers();
        for (int i = 0, size = players.size(); i < size; i++) {
            Player candidate = players.get(i);
            if (candidate != null && candidate.getEntityId() == id) return candidate;
        }
        return null;
    }

    private ItemStack createColoredHelmet(Color color) {
        ItemStack helmet = new ItemStack(Material.LEATHER_HELMET);
        LeatherArmorMeta meta = (LeatherArmorMeta) helmet.getItemMeta();
        if (meta != null) {
            meta.setColor(color);
            helmet.setItemMeta(meta);
        }
        return helmet;
    }
}