package ru.rooyzee.elytrixtalisman.service;

import dev.by1337.virtualentity.api.entity.EquipmentSlot;
import dev.by1337.virtualentity.api.tracker.PlayerTracker;
import dev.by1337.virtualentity.api.virtual.decoration.VirtualArmorStand;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.by1337.blib.geom.Vec3d;

/**
 * Тотемы захватчиков.
 *
 * Раньше это были НАСТОЯЩИЕ армор-стенды, и отсюда шли обе жалобы.
 *
 * «Каша»: все тотемы летали по одной окружности вокруг талисмана. Пока захватчиков
 * двое-трое — красиво, но на десяти они смешивались в одну кучу, и было не понять, чей
 * тотем чей. Теперь тотем принадлежит игроку и висит над ЕГО головой: сколько бы народу
 * ни набежало, каждый видит свой и сразу понимает, кто в захвате, а кто просто рядом.
 *
 * «Видно стойки»: настоящий армор-стенд — это сущность мира. Его подсвечивают чужие
 * плагины, он мигает при лагах, остаётся после падения сервера и попадает под /kill.
 * Теперь стойки пакетные (VirtualEntityApi): их не существует на сервере, они живут
 * только в клиентах зрителей и исчезают сами, без следов в мире.
 *
 * Пакетные сущности к тому же не зависят от прогрузки чанков и игнорируют физику, так
 * что тотем не проваливается и не улетает.
 */
public class TotemService {

    /** Владелец → его тотем. LinkedHash — стабильный порядок обхода. */
    private final Map<UUID, Totem> totems = new LinkedHashMap<>();

    private PlayerTracker tracker;
    private Location center;
    private double radius;
    private double heightOffset;
    private double rotationSpeed;
    private double currentAngle;

    /** Один тотем: пакетная стойка плюс её собственная фаза покачивания. */
    private static final class Totem {
        private final VirtualArmorStand stand;
        /** Сдвиг фазы, чтобы соседние тотемы качались вразнобой, а не синхронно. */
        private final double phase;

        private Totem(VirtualArmorStand stand, double phase) {
            this.stand = stand;
            this.phase = phase;
        }
    }

    public void configure(Location center, double radius, double heightOffset, double rotationSpeed) {
        this.center = center;
        this.radius = radius;
        this.heightOffset = heightOffset;
        this.rotationSpeed = rotationSpeed;
        this.currentAngle = 0.0;

        destroyTracker();
        if (center != null && center.getWorld() != null) {
            // Радиус трекера — кому вообще шлём пакеты. Берём с запасом от точки захвата,
            // чтобы тотемы были видны и тем, кто подбегает, но не всему серверу.
            tracker = new PlayerTracker(center.getWorld(), toVec(center));
            tracker.setRadius(64);
        }
    }

    public void addTotem(UUID playerUuid) {
        if (center == null || tracker == null) return;
        if (totems.containsKey(playerUuid)) return;

        Player owner = Bukkit.getPlayer(playerUuid);
        if (owner == null || !owner.isOnline()) return;

        VirtualArmorStand stand = VirtualArmorStand.create();
        stand.setInvisible(true);
        stand.setSmall(true);
        stand.setMarker(true);
        stand.setNoBasePlate(true);
        stand.setNoGravity(true);
        stand.setSilent(true);
        stand.setEquipment(EquipmentSlot.HEAD, new ItemStack(Material.TOTEM_OF_UNDYING));
        stand.setPos(toVec(above(owner.getLocation(), 0)));

        tracker.addEntity(stand);
        // Фазу разводим по золотому углу: тотемы рядом стоящих игроков не совпадут.
        totems.put(playerUuid, new Totem(stand, totems.size() * 2.399963));
    }

    public void removeTotem(UUID playerUuid) {
        Totem totem = totems.remove(playerUuid);
        if (totem == null) return;
        despawn(totem);
    }

    public void explodeTotem(UUID playerUuid, double power, double damage) {
        Totem totem = totems.remove(playerUuid);
        if (totem == null) return;

        Vec3d pos = totem.stand.getPos();
        despawn(totem);

        if (center == null || center.getWorld() == null || pos == null) return;
        Location loc = new Location(center.getWorld(), pos.x, pos.y, pos.z);

        loc.getWorld().spawnParticle(Particle.EXPLOSION_HUGE, loc, 1);
        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 1.0f);

        for (org.bukkit.entity.Entity e : loc.getWorld().getNearbyEntities(loc, power, power, power)) {
            if (!(e instanceof Player)) continue;
            Player p = (Player) e;
            if (!p.isDead() && p.getLocation().distance(loc) <= power) {
                p.damage(damage);
            }
        }
    }

    public boolean hasTotem(UUID uuid) {
        return totems.containsKey(uuid);
    }

    /**
     * Кадр анимации. Зовётся каждый тик из визуальной задачи.
     *
     * Тотем вращается вокруг своего владельца и плавно покачивается вверх-вниз —
     * левитация задаётся синусом, поэтому движение непрерывное, без рывков на стыке.
     */
    public void tick() {
        if (totems.isEmpty()) return;
        currentAngle += rotationSpeed;

        for (Map.Entry<UUID, Totem> entry : new ArrayList<>(totems.entrySet())) {
            UUID uuid = entry.getKey();
            Totem totem = entry.getValue();

            Player owner = Bukkit.getPlayer(uuid);
            if (owner == null || !owner.isOnline() || owner.isDead()) {
                // Владелец вышел или умер — тотем не должен висеть в пустоте.
                totems.remove(uuid);
                despawn(totem);
                continue;
            }

            double angle = currentAngle + totem.phase;
            double bob = Math.sin(angle * 2.0) * 0.12;

            Location at = above(owner.getLocation(), bob);
            at.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);

            totem.stand.setPos(toVec(at));
            // Разворачиваем тотем по касательной — он «смотрит» по ходу вращения.
            totem.stand.setYaw((float) Math.toDegrees(-angle));
        }

        if (tracker != null) tracker.tick();
    }

    public void destroy() {
        for (Totem totem : new ArrayList<>(totems.values())) {
            despawn(totem);
        }
        totems.clear();
        destroyTracker();
        center = null;
    }

    // --- Вспомогательное -----------------------------------------------------------------

    /** Точка над головой владельца с учётом покачивания. */
    private Location above(Location base, double bob) {
        return base.clone().add(0, 2.2 + heightOffset + bob, 0);
    }

    /**
     * Снимает стойку у всех, кто её видел.
     *
     * Пустой список зрителей — это и есть команда «убрать»: API само разошлёт пакет
     * удаления тем, кому раньше слало пакет появления.
     */
    private void despawn(Totem totem) {
        try {
            if (tracker != null) tracker.removeEntity(totem.stand);
            else totem.stand.tick(java.util.Collections.emptySet());
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[ElytrixTalisman] Не удалось убрать тотем: " + t.getMessage());
        }
    }

    private void destroyTracker() {
        if (tracker == null) return;
        try {
            tracker.removeAll();
        } catch (Throwable ignored) {
        }
        tracker = null;
    }

    private static Vec3d toVec(Location loc) {
        return new Vec3d(loc);
    }
}
