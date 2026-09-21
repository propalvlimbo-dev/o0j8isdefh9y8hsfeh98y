package ru.rooyzee.elytrixclans.export;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import ru.rooyzee.elytrixclans.Main;
import ru.rooyzee.elytrixclans.clans.Clan;
import ru.rooyzee.elytrixclans.clans.ClanMember;
import ru.rooyzee.elytrixclans.level.Level;
import ru.rooyzee.elytrixclans.role.ClanRoles;
import ru.rooyzee.elytrixclans.utils.LevelUtil;

/**
 * Выгрузка кланов в JSON для сайта.
 *
 * Плагин намеренно НЕ ходит в интернет и не поднимает свой HTTP-порт: и то, и другое
 * пришлось бы охранять (ключи, таймауты, блокировки главного потока). Вместо этого он
 * с заданным интервалом кладёт готовый снимок в файл
 *
 *     plugins/ElytrixClans/export/clans.json
 *
 * Дальше его читает плагин-мост ElytrixSite (у него уже есть HTTP API с HMAC-подписью,
 * которым пользуется backend сайта) либо любой внешний процесс. Файл пишется атомарно
 * через временный файл, поэтому читатель никогда не поймает половину записи.
 *
 * Сбор данных идёт в главном потоке (коллекции кланов живут там), а запись на диск —
 * в асинхронном: дисковый ввод-вывод не должен задерживать тики.
 */
public final class ClanExporter {

    private static BukkitTask task;
    /** Последний записанный JSON: не переписываем файл, если ничего не изменилось. */
    private static volatile String lastJson;

    private ClanExporter() {
    }

    public static void start(Main main) {
        stop();
        if (main == null) return;
        if (!main.getConfig().getBoolean("export.enabled", true)) return;

        int seconds = Math.max(10, main.getConfig().getInt("export.interval-seconds", 60));
        long ticks = seconds * 20L;
        task = Bukkit.getScheduler().runTaskTimer(main, ClanExporter::export, 200L, ticks);
    }

    public static void stop() {
        if (task != null) {
            try {
                task.cancel();
            } catch (Throwable ignored) {
            }
            task = null;
        }
    }

    /** Файл выгрузки. Публичный: путь удобно показать администратору в консоли. */
    public static File file() {
        Main main = Main.getInstance();
        File base = main == null ? new File(".") : main.getDataFolder();
        return new File(new File(base, "export"), "clans.json");
    }

    /**
     * Снимок всех кланов. Вызывается из главного потока, запись уходит в фон.
     * Можно дёрнуть вручную: /elytrixclan export.
     */
    public static void export() {
        Main main = Main.getInstance();
        if (main == null || main.getClanManager() == null) return;

        List<Clan> clans = new ArrayList<>();
        for (Clan clan : main.getClanManager().getClans()) {
            if (clan != null) clans.add(clan);
        }
        // Порядок — готовый топ: сайту останется только вывести список.
        clans.sort(Comparator.comparingDouble(Clan::getExp).reversed()
                .thenComparing(Clan::getName, String.CASE_INSENSITIVE_ORDER));

        final String json = build(clans);
        if (json.equals(lastJson)) return;

        if (!main.isEnabled()) {
            writeQuietly(json);
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(main, () -> writeQuietly(json));
    }

    private static String build(List<Clan> clans) {
        StringBuilder out = new StringBuilder(1024);
        out.append("{\n");
        out.append("  \"generated_at\": ").append(System.currentTimeMillis()).append(",\n");
        out.append("  \"count\": ").append(clans.size()).append(",\n");
        out.append("  \"clans\": [\n");

        int place = 0;
        for (int i = 0; i < clans.size(); i++) {
            Clan clan = clans.get(i);
            place++;

            Level level = LevelUtil.getClanLevel(clan.getExp());
            int levelNumber = level != null ? level.getLevel() : 1;

            out.append("    {\n");
            out.append("      \"place\": ").append(place).append(",\n");
            field(out, "name", clan.getName(), true);
            field(out, "owner", clan.getOwner(), true);
            out.append("      \"level\": ").append(levelNumber).append(",\n");
            out.append("      \"exp\": ").append(trim(clan.getExp())).append(",\n");
            out.append("      \"members_count\": ").append(clan.getMemberList().size()).append(",\n");
            out.append("      \"pvp\": ").append(clan.isPvp()).append(",\n");
            out.append("      \"members\": [\n");

            List<ClanMember> members = new ArrayList<>(clan.getMemberList());
            // Внутри клана — по личному вкладу: то же, что показывает меню клана.
            members.sort(Comparator.comparingDouble(ClanMember::getLevel).reversed()
                    .thenComparing(member -> member.getName() == null ? "" : member.getName(),
                            String.CASE_INSENSITIVE_ORDER));

            for (int m = 0; m < members.size(); m++) {
                ClanMember member = members.get(m);
                if (member == null) continue;
                out.append("        {");
                out.append("\"name\": \"").append(escape(member.getName())).append("\", ");
                out.append("\"role\": \"")
                        .append(escape(ClanRoles.normalize(member.getRole().getName()))).append("\", ");
                out.append("\"exp\": ").append(trim(member.getLevel())).append(", ");
                out.append("\"kills\": ").append(member.getKills()).append(", ");
                out.append("\"deaths\": ").append(member.getDeaths());
                out.append("}");
                if (m < members.size() - 1) out.append(',');
                out.append('\n');
            }

            out.append("      ]\n");
            out.append("    }");
            if (i < clans.size() - 1) out.append(',');
            out.append('\n');
        }

        out.append("  ]\n");
        out.append("}\n");
        return out.toString();
    }

    private static void field(StringBuilder out, String key, String value, boolean comma) {
        out.append("      \"").append(key).append("\": \"").append(escape(value)).append('"');
        if (comma) out.append(',');
        out.append('\n');
    }

    /** Опыт целым числом, если дробной части нет: в JSON не нужен «250.0». */
    private static String trim(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return "0";
        if (value == Math.rint(value)) return String.valueOf((long) value);
        return String.valueOf(Math.round(value * 10.0) / 10.0);
    }

    /** Экранирование строки для JSON: имена кланов приходят от игроков. */
    private static String escape(String raw) {
        if (raw == null) return "";
        StringBuilder out = new StringBuilder(raw.length() + 8);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
            }
        }
        return out.toString();
    }

    private static void writeQuietly(String json) {
        try {
            write(json);
            lastJson = json;
        } catch (Exception e) {
            Bukkit.getLogger().warning("[ElytrixClans] Не удалось выгрузить кланы: " + e.getMessage());
        }
    }

    /** Атомарная запись: читатель не должен увидеть файл на середине записи. */
    private static void write(String json) throws IOException {
        File target = file();
        File folder = target.getParentFile();
        if (folder != null && !folder.exists() && !folder.mkdirs()) {
            throw new IOException("не удалось создать папку " + folder);
        }
        File temp = new File(folder, target.getName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temp.toPath(), StandardCharsets.UTF_8)) {
            writer.write(json);
        }
        try {
            Files.move(temp.toPath(), target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException | UnsupportedOperationException e) {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
