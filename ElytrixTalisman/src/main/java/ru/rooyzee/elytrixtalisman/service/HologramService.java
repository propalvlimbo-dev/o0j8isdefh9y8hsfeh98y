package ru.rooyzee.elytrixtalisman.service;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import ru.rooyzee.elytrixtalisman.util.ColorUtil;
import ru.rooyzee.elytrixtalisman.util.PlaceholderUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HologramService {

    private final List<ArmorStand> lines = new ArrayList<>();
    private List<String> template;

    public void create(Location baseLocation, List<String> template) {
        destroy();
        this.template = template;
        double y = baseLocation.getY();
        for (int i = template.size() - 1; i >= 0; i--) {
            Location loc = new Location(baseLocation.getWorld(),
                    baseLocation.getX() + 0.5, y, baseLocation.getZ() + 0.5);
            ArmorStand as = (ArmorStand) baseLocation.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
            as.setVisible(false);
            as.setGravity(false);
            as.setMarker(true);
            as.setSmall(true);
            as.setCustomNameVisible(true);
            as.setCustomName(ColorUtil.colorize(template.get(i)));
            as.setInvulnerable(true);
            lines.add(0, as);
            y += 0.3;
        }
    }

    public void update(Map<String, String> placeholders) {
        if (template == null || lines.isEmpty()) return;
        for (int i = 0; i < lines.size() && i < template.size(); i++) {
            ArmorStand as = lines.get(i);
            if (as == null || as.isDead()) continue;
            as.setCustomName(ColorUtil.colorize(
                    PlaceholderUtil.replace(template.get(i), placeholders)));
        }
    }

    public void destroy() {
        lines.forEach(as -> {
            if (as != null && !as.isDead()) as.remove();
        });
        lines.clear();
    }
}