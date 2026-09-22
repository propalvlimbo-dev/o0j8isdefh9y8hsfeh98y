package ru.rooyzee.elytrixtalisman.service;

import dev.by1337.virtualentity.api.entity.EquipmentSlot;
import dev.by1337.virtualentity.api.tracker.PlayerTracker;
import dev.by1337.virtualentity.api.virtual.decoration.VirtualArmorStand;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.by1337.blib.geom.Vec3d;
import ru.rooyzee.elytrixtalisman.util.ColorUtil;

/**
 * Тотемы захватчиков.
 *
 * Над головой игрока висит тотем, а над тотемом — счётчик его личных очков захвата.
 * Очки копятся, пока игрок стоит на точке, и служат ставкой: убийца забирает их себе
 * вместе со своим счётчиком, а при смерти от окружения они сгорают.
 *
 * Тотем и подпись — ДВЕ отдельные пакетные стойки. Одной не обойтись: армор-стенд
 * рисует предмет на голове и подпись в одной и той же точке, и число налезало бы на
 * сам тотем. Вторая стойка просто висит чуть выше.
 *
 * Стойки пакетные (VirtualEntityApi): на сервере их не существует, они рисуются только
 * в клиентах зрителей. Поэтому их не подсвечивают чужие плагины, не ловит /kill и они
 * не остаются в мире после падения сервера.
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

    /** Шаблон подписи над тотемом: %points% — личные очки игрока. */
    private String labelTemplate = "&#F8BEFB✦ %points%";

    /** Один тотем: стойка с предметом, стойка-подпись и накопленные очки. */
    private static final class Totem {
        private final VirtualArmorStand stand;
        private final VirtualArmorStand label;
        /** Сдвиг фазы, чтобы соседние тотемы качались вразнобой, а не синхронно. */
        private final double phase;
        private int points;
        /** Последнее показанное число: лишний раз пакет с подписью не шлём. */
        private int shownPoints = -1;

        private Totem(VirtualArmorStand stand, VirtualArmorStand label, double phase) {
            this.stand = stand;
            this.label = label;
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
            // Радиус трекера — кому вообще шлём пакеты. С запасом от точки захвата,
            // чтобы тотемы видели подбегающие, но не весь сервер.
            tracker = new PlayerTracker(center.getWorld(), toVec(center));
            tracker.setRadius(64);
        }
    }

    /** Шаблон подписи из messages.yml. */
    public void setLabelTemplate(String template) {
        if (template != null && !template.isEmpty()) this.labelTemplate = template;
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

        VirtualArmorStand label = VirtualArmorStand.create();
        label.setInvisible(true);
        label.setSmall(true);
        label.setMarker(true);
        label.setNoBasePlate(true);
        label.setNoGravity(true);
        label.setSilent(true);
        label.setCustomNameVisible(true);
        label.setPos(toVec(above(owner.getLocation(), 0).add(0, LABEL_GAP, 0)));

        tracker.addEntity(stand);
        tracker.addEntity(label);

        // Фазу разводим золотым углом: тотемы рядом стоящих игроков не совпадут.
        Totem totem = new Totem(stand, label, totems.size() * 2.399963);
        totems.put(playerUuid, totem);
        applyLabel(totem);
    }

    public void removeTotem(UUID playerUuid) {
        Totem totem = totems.remove(playerUuid);
        if (totem == null) return;
        despawn(totem);
    }

    // --- Очки ------------------------------------------------------------------------------

    /** Начисляет игроку личные очки за удержание точки. */
    public void addPoints(UUID playerUuid, int amount) {
        Totem totem = totems.get(playerUuid);
        if (totem == null || amount <= 0) return;
        totem.points += amount;
    }

    public int getPoints(UUID playerUuid) {
        Totem totem = totems.get(playerUuid);
        return totem == null ? 0 : totem.points;
    }

    /**
     * Передаёт очки жертвы убийце.
     *
     * Если убийца сам не в захвате (например, подстрелил издалека и на точке не стоял),
     * передавать некуда — очки сгорают. Возвращаем сколько реально передали, чтобы
     * вызывающий код знал, о чём писать в чат.
     */
    public int transferPoints(UUID victimUuid, UUID killerUuid) {
        Totem victim = totems.get(victimUuid);
        if (victim == null || victim.points <= 0) return 0;

        int stolen = victim.points;
        victim.points = 0;
        applyLabel(victim);

        Totem killer = killerUuid == null ? null : totems.get(killerUuid);
        if (killer == null) return 0;

        killer.points += stolen;
        applyLabel(killer);
        return stolen;
    }

    /** Обнуляет очки игрока — смерть не от игрока. */
    public int burnPoints(UUID playerUuid) {
        Totem totem = totems.get(playerUuid);
        if (totem == null || totem.points <= 0) return 0;
        int lost = totem.points;
        totem.points = 0;
        applyLabel(totem);
        return lost;
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
     * Тотем облетает голову владельца и плавно покачивается — левитация задана синусом,
     * поэтому движение непрерывное, без рывка на замыкании круга. Подпись держится строго
     * над тотемом и не вращается: читать прыгающие цифры невозможно.
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

            Location base = above(owner.getLocation(), bob);
            Location at = base.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);

            totem.stand.setPos(toVec(at));
            // Разворачиваем тотем по касательной — он «смотрит» по ходу вращения.
            totem.stand.setYaw((float) Math.toDegrees(-angle));

            // Подпись — по центру над головой, без вращения и без наклона.
            totem.label.setPos(toVec(base.clone().add(0, LABEL_GAP, 0)));

            applyLabel(totem);
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

    /** Насколько подпись выше тотема. */
    private static final double LABEL_GAP = 0.45;

    /** Обновляет текст подписи, если число изменилось. */
    private void applyLabel(Totem totem) {
        if (totem.points == totem.shownPoints) return;
        totem.shownPoints = totem.points;
        String text = ColorUtil.colorize(labelTemplate.replace("%points%", String.valueOf(totem.points)));
        totem.label.setCustomName(toComponent(text));
    }

    /** Точка над головой владельца с учётом покачивания. */
    private Location above(Location base, double bob) {
        return base.clone().add(0, 2.2 + heightOffset + bob, 0);
    }

    /**
     * Снимает обе стойки у всех, кто их видел.
     *
     * Пустой список зрителей — это и есть команда «убрать»: API само разошлёт пакет
     * удаления тем, кому раньше слало пакет появления.
     */
    private void despawn(Totem totem) {
        removeEntity(totem.stand);
        removeEntity(totem.label);
    }

    private void removeEntity(VirtualArmorStand stand) {
        if (stand == null) return;
        try {
            if (tracker != null) tracker.removeEntity(stand);
            else stand.tick(Collections.emptySet());
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

    /** Строка с кодами цвета → Adventure-компонент, которым подписываются сущности. */
    private static Component toComponent(String legacy) {
        return LegacyComponentSerializer.legacySection().deserialize(legacy);
    }

    private static Vec3d toVec(Location loc) {
        return new Vec3d(loc);
    }
}
