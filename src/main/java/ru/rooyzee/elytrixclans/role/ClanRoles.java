package ru.rooyzee.elytrixclans.role;

import java.util.Locale;
import ru.rooyzee.elytrixclans.permission.Permissions;

/**
 * Лестница ролей клана и правила её прохождения.
 *
 * Роли делятся на две группы:
 *
 *  1. Выдаются автоматически за личный вклад в опыт клана:
 *     Новичок  — стартовая роль, прав нет вообще. Участник может драться и приносить клану
 *                опыт, но даже магазин ему закрыт.
 *     Стажёр   — 50 личного опыта. Открывается магазин клана.
 *     Опытный  — 200 личного опыта. Прав столько же, это знак заслуг перед кланом.
 *
 *  2. Выдаются вручную владельцем клана:
 *     Модератор — может кикать из клана, переключать PvP и менять подсветку клана.
 *     Лидер     — все права.
 *
 * Весь плагин обращается к ролям только через этот класс, чтобы набор прав у роли
 * задавался в одном месте, а не собирался заново на каждом вызове setRole.
 */
public final class ClanRoles {

    public static final String ROOKIE = "Новичок";
    public static final String TRAINEE = "Стажёр";
    public static final String VETERAN = "Опытный";
    public static final String MODERATOR = "Модератор";
    public static final String LEADER = "Лидер";

    /** Личный вклад в опыт клана, необходимый для автоматических повышений. */
    public static final double TRAINEE_EXP = 50.0;
    public static final double VETERAN_EXP = 200.0;

    private ClanRoles() {
    }

    /** Новая роль по названию. Неизвестные и устаревшие названия становятся Новичком. */
    public static Roles create(String name) {
        String normalized = normalize(name);
        if (LEADER.equals(normalized)) {
            return new Roles(LEADER, Permissions.values());
        }
        if (MODERATOR.equals(normalized)) {
            return new Roles(MODERATOR, Permissions.SHOP, Permissions.KICK,
                    Permissions.PVP, Permissions.GLOW);
        }
        if (VETERAN.equals(normalized)) {
            return new Roles(VETERAN, Permissions.SHOP);
        }
        if (TRAINEE.equals(normalized)) {
            return new Roles(TRAINEE, Permissions.SHOP);
        }
        return new Roles(ROOKIE);
    }

    public static Roles rookie() {
        return create(ROOKIE);
    }

    public static Roles leader() {
        return create(LEADER);
    }

    /**
     * Приводит название роли к актуальному виду.
     * Старое «Участник» из сохранённых кланов становится Новичком.
     */
    public static String normalize(String name) {
        if (name == null) return ROOKIE;
        String trimmed = name.trim();
        if (trimmed.isEmpty()) return ROOKIE;
        if (equalsIgnoreCase(trimmed, LEADER)) return LEADER;
        if (equalsIgnoreCase(trimmed, MODERATOR)) return MODERATOR;
        if (equalsIgnoreCase(trimmed, VETERAN)) return VETERAN;
        if (equalsIgnoreCase(trimmed, TRAINEE)) return TRAINEE;
        return ROOKIE;
    }

    private static boolean equalsIgnoreCase(String a, String b) {
        return a.toLowerCase(Locale.ROOT).equals(b.toLowerCase(Locale.ROOT));
    }

    /** Порядок роли в иерархии: чем больше, тем выше. */
    public static int weight(String name) {
        String normalized = normalize(name);
        if (LEADER.equals(normalized)) return 4;
        if (MODERATOR.equals(normalized)) return 3;
        if (VETERAN.equals(normalized)) return 2;
        if (TRAINEE.equals(normalized)) return 1;
        return 0;
    }

    /** Роль назначается только владельцем клана и не выдаётся за опыт. */
    public static boolean isManual(String name) {
        String normalized = normalize(name);
        return LEADER.equals(normalized) || MODERATOR.equals(normalized);
    }

    /**
     * Какая роль положена за такой личный вклад.
     * Для ролей, выданных вручную, не применяется.
     */
    public static String earnedRole(double personalExp) {
        if (personalExp >= VETERAN_EXP) return VETERAN;
        if (personalExp >= TRAINEE_EXP) return TRAINEE;
        return ROOKIE;
    }

    /** Сколько опыта осталось до следующей автоматической роли; 0 — дальше только вручную. */
    public static double expToNextRole(double personalExp) {
        if (personalExp < TRAINEE_EXP) return TRAINEE_EXP - personalExp;
        if (personalExp < VETERAN_EXP) return VETERAN_EXP - personalExp;
        return 0.0;
    }

    /** Название следующей автоматической роли, либо null, если достигнут потолок. */
    public static String nextAutoRole(double personalExp) {
        if (personalExp < TRAINEE_EXP) return TRAINEE;
        if (personalExp < VETERAN_EXP) return VETERAN;
        return null;
    }
}
