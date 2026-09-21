package ru.rooyzee.elytrixtalisman.service;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class TotemService {

    private final Map<UUID, ArmorStand> totems = new LinkedHashMap<>();
    private Location center;
    private double radius;
    private double heightOffset;
    private double rotationSpeed;
    private double currentAngle = 0.0;

    public void configure(Location center, double radius, double heightOffset, double rotationSpeed) {
        this.center = center;
        this.radius = radius;
        this.heightOffset = heightOffset;
        this.rotationSpeed = rotationSpeed;
    }

    public void addTotem(UUID playerUuid) {
        if (center == null) return;
        if (totems.containsKey(playerUuid)) return;
        Location loc = center.clone().add(0, heightOffset, 0);
        ArmorStand as = (ArmorStand) center.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        as.setVisible(false);
        as.setGravity(false);
        as.setMarker(true);
        as.setSmall(true);
        as.setInvulnerable(true);
        as.getEquipment().setItem(EquipmentSlot.HEAD, new ItemStack(Material.TOTEM_OF_UNDYING));
        totems.put(playerUuid, as);
    }

    public void removeTotem(UUID playerUuid) {
        ArmorStand as = totems.remove(playerUuid);
        if (as != null && !as.isDead()) as.remove();
    }

    public void explodeTotem(UUID playerUuid, double power, double damage) {
        ArmorStand as = totems.remove(playerUuid);
        if (as == null) return;
        Location loc = as.getLocation();
        if (!as.isDead()) as.remove();

        loc.getWorld().spawnParticle(Particle.EXPLOSION_HUGE, loc, 1);
        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 1.0f);

        for (org.bukkit.entity.Entity e : loc.getWorld().getNearbyEntities(loc, power, power, power)) {
            if (e instanceof Player) {
                Player p = (Player) e;
                if (!p.isDead() && p.getLocation().distance(loc) <= power) {
                    p.damage(damage);
                }
            }
        }
    }

    public boolean hasTotem(UUID uuid) {
        return totems.containsKey(uuid);
    }

    public void tick() {
        if (center == null || totems.isEmpty()) return;
        currentAngle += rotationSpeed;
        int count = totems.size();
        int i = 0;
        for (Map.Entry<UUID, ArmorStand> entry : new ArrayList<>(totems.entrySet())) {
            ArmorStand as = entry.getValue();
            if (as == null || as.isDead()) {
                totems.remove(entry.getKey());
                continue;
            }
            double angle = currentAngle + (Math.PI * 2 / count) * i;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            Location newLoc = center.clone().add(x, heightOffset, z);
            newLoc.setYaw((float) Math.toDegrees(-angle));
            as.teleport(newLoc);
            i++;
        }
    }

    public void destroy() {
        totems.values().forEach(as -> {
            if (as != null && !as.isDead()) as.remove();
        });
        totems.clear();
        center = null;
    }
}