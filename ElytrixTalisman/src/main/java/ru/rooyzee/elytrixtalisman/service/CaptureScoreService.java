package ru.rooyzee.elytrixtalisman.service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Личные очки захвата.
 *
 * Пока игрок стоит на точке, ему капают очки — те же, что идут его клану. Это его
 * ставка: если его убьёт игрок другого клана, всё накопленное переходит убийце и его
 * клану. Смерть от мобов, падения или лавы просто сжигает очки.
 *
 * Раньше этот счётчик жил внутри TotemService и умирал вместе с тотемом над головой.
 * Теперь он отдельно: механика от способа отрисовки не зависит, и очки не теряются
 * оттого, что игрока перестали показывать.
 *
 * Очки НЕ сбрасываются, когда игрок вышел из радиуса: он накопил их честно, и уход
 * на минуту за стеной не должен обнулять вклад. Обнуление — только смерть и конец
 * сессии.
 */
public class CaptureScoreService {

    private final Map<UUID, Integer> points = new HashMap<>();

    /** Начисляет очки за удержание точки. */
    public void add(UUID playerUuid, int amount) {
        if (playerUuid == null || amount <= 0) return;
        points.merge(playerUuid, amount, Integer::sum);
    }

    public int get(UUID playerUuid) {
        return playerUuid == null ? 0 : points.getOrDefault(playerUuid, 0);
    }

    /**
     * Передаёт очки жертвы убийце.
     *
     * @return сколько очков перешло. 0, если жертве нечего было терять или убийца
     *         не указан — в этом случае очки просто сгорают.
     */
    public int transfer(UUID victimUuid, UUID killerUuid) {
        int stolen = points.getOrDefault(victimUuid, 0);
        if (stolen <= 0) return 0;

        points.remove(victimUuid);
        if (killerUuid == null) return 0;

        points.merge(killerUuid, stolen, Integer::sum);
        return stolen;
    }

    /** Сжигает очки игрока — смерть без убийцы. */
    public int burn(UUID playerUuid) {
        Integer lost = points.remove(playerUuid);
        return lost == null ? 0 : lost;
    }

    /** Полный сброс при старте новой сессии. */
    public void reset() {
        points.clear();
    }
}
