package ru.rooyzee.elytrixtalisman.manager;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import ru.rooyzee.elytrixclans.clans.Clan;
import ru.rooyzee.elytrixtalisman.Main;
import ru.rooyzee.elytrixtalisman.config.ConfigManager;
import ru.rooyzee.elytrixtalisman.model.ClanCaptureData;
import ru.rooyzee.elytrixtalisman.model.TalismanSession;
import ru.rooyzee.elytrixtalisman.model.TalismanState;
import ru.rooyzee.elytrixtalisman.service.*;
import ru.rooyzee.elytrixtalisman.util.ColorUtil;
import ru.rooyzee.elytrixtalisman.util.PlaceholderUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TalismanManager {

    private final Main plugin;
    private final ConfigManager configManager;
    private final MessageService messageService;
    private final ClanService clanService;
    private final LocationService locationService;
    private final RegionService regionService;
    private final SchematicService schematicService;
    private final BossBarService bossBarService;
    private final RewardService rewardService;
    private final EssentialsService essentialsService;
    private final LootService lootService;
    private final HologramService hologramService;
    private final ParticleService particleService;
    private final PlayerParticipationTracker participationTracker;

    private TalismanSession session;
    private BukkitTask mainTask;
    private BukkitTask visualTask;
    private BukkitTask endTask;
    private int lastDropBank = 0;
    /** Сколько мест показывает табло над талисманом. */
    private static final int TOP_LINES = 3;

    /** Сколько ошибок подряд терпит тик, прежде чем свернуть ивент. */
    private static final int MAX_TICK_ERRORS = 20;

    /** Счётчик подряд идущих ошибок тика. Обнуляется любым успешным тиком. */
    private int tickErrors = 0;

    /** Момент старта ивента: по нему считается предельная длительность. */
    private long startedAt = 0L;

    /** Личные очки захвата: ставка, которую забирает убийца. */
    private final CaptureScoreService captureScore = new CaptureScoreService();

    private final Set<UUID> activePlayers = new HashSet<>();
    private final Set<UUID> deathCooldown = new HashSet<>();

    public TalismanManager(Main plugin, ConfigManager configManager, MessageService messageService,
                           ClanService clanService, LocationService locationService, RegionService regionService,
                           SchematicService schematicService, BossBarService bossBarService,
                           RewardService rewardService, EssentialsService essentialsService,
                           LootService lootService, HologramService hologramService,
                           ParticleService particleService,
                           PlayerParticipationTracker participationTracker) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageService = messageService;
        this.clanService = clanService;
        this.locationService = locationService;
        this.regionService = regionService;
        this.schematicService = schematicService;
        this.bossBarService = bossBarService;
        this.rewardService = rewardService;
        this.essentialsService = essentialsService;
        this.lootService = lootService;
        this.hologramService = hologramService;
        this.particleService = particleService;
        this.participationTracker = participationTracker;
    }

    public boolean isRunning() {
        return session != null && session.getState() != TalismanState.IDLE;
    }

    public TalismanSession getSession() {
        return session;
    }

    public void start() {
        int maxBank = configManager.getConfig().getInt("capture.max-bank", 1000);
        session = new TalismanSession(maxBank);
        lastDropBank = 0;
        activePlayers.clear();
        deathCooldown.clear();
        tickErrors = 0;
        startedAt = System.currentTimeMillis();
        // Полный сброс: списки захватчиков прошлого ивента не должны попасть в награды.
        participationTracker.resetSession();
        captureScore.reset();

        Location loc = locationService.findSafeLocation();
        if (loc == null) {
            plugin.getLogger().warning("Could not find safe location for talisman!");
            session = null;
            return;
        }
        session.setLocation(loc);
        session.setState(TalismanState.RUNNING);
        session.setCaptureRadius(configManager.getConfig().getInt("talisman.capture-radius", 50));

        String schematicName = configManager.getConfig().getString("schematic-name", "mascot.schem");
        int ox = configManager.getConfig().getInt("schematic-offset.x", 0);
        int oy = configManager.getConfig().getInt("schematic-offset.y", 0);
        int oz = configManager.getConfig().getInt("schematic-offset.z", 0);
        schematicService.paste(loc, schematicName, ox, oy, oz);

        int blockOffsetX = configManager.getConfig().getInt("talisman.block-offset.x", 0);
        int blockOffsetY = configManager.getConfig().getInt("talisman.block-offset.y", -1);
        int blockOffsetZ = configManager.getConfig().getInt("talisman.block-offset.z", 0);
        String blockName = configManager.getConfig().getString("talisman.block", "BEACON");
        Material mat;
        try {
            mat = Material.valueOf(blockName.toUpperCase());
        } catch (IllegalArgumentException e) {
            mat = Material.BEACON;
        }
        Location talismanLoc = loc.clone().add(blockOffsetX, blockOffsetY, blockOffsetZ);
        talismanLoc.getBlock().setType(mat);
        session.setTalismanBlockLocation(talismanLoc);

        double hologramHeight = configManager.getConfig().getDouble("talisman.hologram-height", 3.0);
        Location hologramLoc = talismanLoc.clone().add(0, hologramHeight, 0);
        hologramService.create(hologramLoc, configManager.getMessages().getStringList("hologram"));


        regionService.create(loc, session.getRegionId());
        bossBarService.create();

        Map<String, String> ph = new HashMap<>();
        ph.put("%x%", String.valueOf(loc.getBlockX()));
        ph.put("%y%", String.valueOf(loc.getBlockY()));
        ph.put("%z%", String.valueOf(loc.getBlockZ()));
        messageService.broadcast("start", ph);

        int pointsPerStand = configManager.getConfig().getInt("capture.points-per-standing", 1);
        int tickInterval = configManager.getConfig().getInt("capture.tick-interval", 20);
        String noLeader = messageService.getRaw("no-leader");
        String actionBarTemplate = configManager.getMessages().getString("actionbar", "");
        String noClanActionBar = configManager.getMessages().getString("actionbar-no-clan", "&cВы не состоите в клане");
        int pointsPerDrop = configManager.getConfig().getInt("loot.points-per-drop", 100);
        int itemsPerPlayer = configManager.getConfig().getInt("loot.items-per-player", 2);

        mainTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            try {
                tickCapture(pointsPerStand, actionBarTemplate, noClanActionBar,
                        noLeader, pointsPerDrop, itemsPerPlayer);
                tickErrors = 0;
            } catch (Throwable t) {
                // Исключение внутри runTaskTimer молча убивает задачу: ивент повис бы
                // навсегда, а табло осталось висеть. Поэтому ловим здесь.
                //
                // Но сворачивать ивент из-за одного сбоя нельзя: случайная ошибка в
                // одном тике убивала весь захват и снимала табло прямо посреди игры.
                // Терпим несколько подряд и только потом сдаёмся.
                tickErrors++;
                plugin.getLogger().severe("Ошибка в тике талисмана (" + tickErrors + "): " + t);
                t.printStackTrace();
                if (tickErrors >= MAX_TICK_ERRORS) {
                    plugin.getLogger().severe("Слишком много ошибок подряд, ивент остановлен");
                    try {
                        forceStop();
                    } catch (Throwable ignored) {
                    }
                }
            }
        }, 0L, tickInterval);

        double particleRadius = configManager.getConfig().getDouble("talisman.particle-radius", 1.5);
        int particleCount = configManager.getConfig().getInt("talisman.particle-count", 40);

        visualTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            try {
                if (session == null || session.getTalismanBlockLocation() == null) return;
                particleService.ring(session.getTalismanBlockLocation().clone().add(0.5, 0, 0.5),
                        particleRadius, particleCount);
            } catch (Throwable ignored) {
                // Частицы — украшение: их падение не должно ронять ивент.
            }
        }, 0L, 1L);
    }

    /** Один тик захвата. Вынесен из лямбды, чтобы обернуть его в защиту от исключений. */
    private void tickCapture(int pointsPerStand, String actionBarTemplate, String noClanActionBar,
                             String noLeader, int pointsPerDrop, int itemsPerPlayer) {
            if (session == null || session.getState() != TalismanState.RUNNING) return;

            if (session.isBankFull()) {
                cancelMainTask();
                beginEnding();
                return;
            }

            // Страховка от бесконечного ивента.
            //
            // Банк теперь умеет убывать: смерть без убийцы откатывает захваченное.
            // Если игроки активно гибнут сами, прогресс может топтаться на месте и
            // талисман не закончится никогда. Поэтому жёсткий предел по времени —
            // после него ивент завершается с тем, что накоплено.
            int maxMinutes = configManager.getConfig().getInt("max-duration-minutes", 30);
            if (maxMinutes > 0 && startedAt > 0
                    && System.currentTimeMillis() - startedAt > maxMinutes * 60_000L) {
                plugin.getLogger().info("Талисман завершён по истечении времени");
                cancelMainTask();
                beginEnding();
                return;
            }

            Location talisman = session.getTalismanBlockLocation();
            if (talisman == null) return;
            int radius = session.getCaptureRadius();
            Set<UUID> currentTick = new HashSet<>();

            // Сначала ТОЛЬКО считаем: очки клана и банк. Ничего не показываем.
            //
            // Раньше actionbar отправлялся прямо здесь, внутри обхода игроков. Банк при
            // этом рос на каждой итерации, поэтому первый игрок в списке видел банк без
            // учёта остальных, а последний — со всеми. Отсюда и брались расхождения на
            // единицу: у стоящих рядом людей числа отличались, и при смене порядка обхода
            // одно и то же значение прыгало туда-сюда. Показываем всем один итог ниже.
            List<Player> capturing = new ArrayList<>();
            List<Player> withoutClan = new ArrayList<>();

            talisman.getWorld().getNearbyEntities(talisman, radius, 255, radius).forEach(entity -> {
                if (!(entity instanceof Player)) return;
                Player player = (Player) entity;
                if (player.isDead()) return;
                if (deathCooldown.contains(player.getUniqueId())) return;
                if (player.getLocation().distance(talisman) > radius) return;

                currentTick.add(player.getUniqueId());

                Clan clan = clanService.getPlayerClan(player);
                if (clan != null) {
                    ClanCaptureData data = session.getData(clan.getName());
                    data.addCapturePoints(pointsPerStand);
                    session.addBank(pointsPerStand);
                    participationTracker.addPlayer(player.getUniqueId());
                    // Поимённо: по этому списку кланы в конце раздадут опыт и монеты.
                    participationTracker.addHolder(clan.getName(), player.getName());
                    // Личная копилка игрока. Её заберёт тот, кто его убьёт.
                    captureScore.add(player.getUniqueId(), pointsPerStand);
                    capturing.add(player);
                } else {
                    withoutClan.add(player);
                }
            });

            // Банк за этот тик посчитан полностью — теперь у всех на экране одно число.
            // Личные очки у каждого свои, поэтому строка собирается на игрока.
            for (Player player : capturing) {
                Map<String, String> abPh = new HashMap<>();
                abPh.put("%bank%", String.valueOf(session.getBank()));
                abPh.put("%max_bank%", String.valueOf(session.getMaxBank()));
                abPh.put("%score%", String.valueOf(captureScore.get(player.getUniqueId())));
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText(
                                ColorUtil.colorize(PlaceholderUtil.replace(actionBarTemplate, abPh))));
            }
            String noClanBar = ColorUtil.colorize(noClanActionBar);
            for (Player player : withoutClan) {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText(noClanBar));
            }

            for (UUID uuid : new HashSet<>(currentTick)) {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null) continue;
                if (clanService.getPlayerClan(p) == null) continue;
                activePlayers.add(uuid);
            }
            for (UUID uuid : new HashSet<>(activePlayers)) {
                if (!currentTick.contains(uuid)) {
                    activePlayers.remove(uuid);
                }
            }

            // Банк упирается в maxBank (addBank делает Math.min), поэтому на самом верху
            // разница bank - lastDropBank перестаёт расти и последняя выдача лута может
            // не случиться. Считаем по реально накопленному значению.
            while (session.getBank() - lastDropBank >= pointsPerDrop) {
                lastDropBank += pointsPerDrop;
                int uniquePlayers = Math.max(1, participationTracker.getUniqueCount());
                int amount = uniquePlayers * itemsPerPlayer;
                lootService.dropItems(plugin, session.getTalismanBlockLocation().clone().add(0.5, 1.5, 0.5), amount);
                participationTracker.reset();

                Map<String, String> lootPh = new HashMap<>();
                lootPh.put("%amount%", String.valueOf(amount));
                messageService.broadcast("loot.drop-broadcast", lootPh);
            }

            int untilDrop = Math.max(0, pointsPerDrop - (session.getBank() - lastDropBank));
            Map<String, String> holoPh = new HashMap<>();
            holoPh.put("%points%", String.valueOf(untilDrop));
            fillTopPlaceholders(holoPh);
            hologramService.update(holoPh);

            Map<String, String> bbPh = new HashMap<>();
            bbPh.put("%x%", String.valueOf(talisman.getBlockX()));
            bbPh.put("%z%", String.valueOf(talisman.getBlockZ()));
            bbPh.put("%leader%", session.getLeaderName(noLeader));
            bbPh.put("%bank%", String.valueOf(session.getBank()));
            bbPh.put("%max_bank%", String.valueOf(session.getMaxBank()));
            bossBarService.update(bbPh, session.getProgress());
    }

    /**
     * Убийство на точке.
     *
     * Кроме очков за сам фраг убийца забирает всё, что жертва накопила на точке.
     * Эти очки уже были начислены клану жертвы, поэтому их нужно и списать у неё, и
     * выдать клану убийцы — иначе награбленное задвоилось бы: осталось бы в счёте
     * проигравшего и добавилось победителю.
     *
     * @return сколько личных очков перешло убийце (без учёта очков за фраг)
     */
    public int addKill(String killerClan, String victimClan, UUID killerUuid, UUID victimUuid) {
        if (session == null || session.getState() != TalismanState.RUNNING) return 0;
        int pointsPerKill = configManager.getConfig().getInt("capture.points-per-kill", 5);
        session.getData(killerClan).addKill();
        session.getData(killerClan).addCapturePoints(pointsPerKill);
        session.addBank(pointsPerKill);
        session.getData(victimClan).addDeath();

        int stolen = captureScore.transfer(victimUuid, killerUuid);
        if (stolen > 0) {
            session.getData(victimClan).addCapturePoints(-stolen);
            session.getData(killerClan).addCapturePoints(stolen);
        }
        return stolen;
    }

    /**
     * Смерть захватчика на точке: взрыв и короткий запрет снова копить очки.
     *
     * @param victimClan клан жертвы; нужен, чтобы списать сгоревшие очки и у клана
     * @param burnScore  сжечь личные очки. true — когда их никто не забрал: смерть от
     *                   мобов, падения, лавы, /kill либо от игрока, который сам в
     *                   захвате не участвовал. При обычном убийстве их перенёс addKill.
     * @return сколько очков сгорело
     */
    public int handleDeathOnEvent(UUID victimUuid, String victimClan, boolean burnScore) {
        if (session == null || session.getState() != TalismanState.RUNNING) return 0;

        // Смерть без убийцы обнуляет всё, что игрок наработал на точке: и личную
        // ставку, и вклад в клан, и общий банк ивента. Забирать некому — очки просто
        // исчезают, как будто он там и не стоял.
        int burned = burnScore ? captureScore.burn(victimUuid) : 0;
        if (burned > 0) {
            if (victimClan != null) session.getData(victimClan).addCapturePoints(-burned);
            session.removeBank(burned);

            // Банк поехал вниз — планку следующей выдачи лута тоже опускаем. Иначе
            // она осталась бы выше текущего банка, и лут не выпал бы до тех пор, пока
            // игроки заново не добьют до старой отметки.
            int pointsPerDrop = configManager.getConfig().getInt("loot.points-per-drop", 100);
            if (pointsPerDrop > 0) {
                lastDropBank = Math.min(lastDropBank,
                        (session.getBank() / pointsPerDrop) * pointsPerDrop);
            }
        }

        if (!activePlayers.contains(victimUuid)) return burned;
        double power = configManager.getConfig().getDouble("talisman.death-explode-power", 3.0);
        double damage = configManager.getConfig().getDouble("talisman.death-explode-damage", 6.0);
        activePlayers.remove(victimUuid);
        deathCooldown.add(victimUuid);

        // Взрыв откладываем на следующий тик. Сейчас мы внутри PlayerDeathEvent, и
        // damage() отсюда может поднять вложенное событие смерти: исключение улетит
        // в тик талисмана, аварийная остановка свернёт ивент и снимет табло — со
        // стороны это выглядит как «после моей смерти голограмма пропала».
        Location deathLoc = victimLocation(victimUuid);
        Bukkit.getScheduler().runTask(plugin, () -> explodeAt(deathLoc, victimUuid, power, damage));
        Bukkit.getScheduler().runTaskLater(plugin, () -> deathCooldown.remove(victimUuid), 60L);
        return burned;
    }

    /**
     * Заполняет %top1%..%top3% для табло над талисманом.
     *
     * Места, которые ещё никто не занял, показываются как top-empty, а не пропускаются:
     * иначе строки табло скакали бы вверх-вниз по мере появления кланов, и читать его
     * было бы невозможно. Высота табло постоянная с самого начала ивента.
     */
    private void fillTopPlaceholders(Map<String, String> holder) {
        String empty = configManager.getMessages().getString("top-empty", "&8· · ·");
        Map<String, ClanCaptureData> top = session.getTopClans(TOP_LINES);

        int place = 1;
        for (Map.Entry<String, ClanCaptureData> entry : top.entrySet()) {
            // Клан с нулём очков в топе не показываем: он попал в список, потому что
            // словил смерть на точке, но захватом это не является.
            if (entry.getValue().getCapturePoints() <= 0) continue;

            String template = configManager.getMessages()
                    .getString("top-line-" + place, "&f%place%. %clan% &8— &f%points%");
            Map<String, String> line = new HashMap<>();
            line.put("%place%", String.valueOf(place));
            line.put("%clan%", entry.getKey());
            line.put("%points%", String.valueOf(entry.getValue().getCapturePoints()));
            holder.put("%top" + place + "%", PlaceholderUtil.replace(template, line));
            place++;
        }
        for (int i = place; i <= TOP_LINES; i++) {
            holder.put("%top" + i + "%", empty);
        }
    }

    /** Личные очки игрока — показываются ему в actionbar. */
    public int getCaptureScore(UUID uuid) {
        return captureScore.get(uuid);
    }

    /** Точка гибели: снимаем сразу, к следующему тику игрок уже возродится на споне. */
    private Location victimLocation(UUID victimUuid) {
        Player victim = Bukkit.getPlayer(victimUuid);
        return victim == null ? null : victim.getLocation().clone();
    }

    /** Взрыв на месте гибели захватчика. */
    private void explodeAt(Location loc, UUID victimUuid, double power, double damage) {
        if (loc == null || loc.getWorld() == null) return;

        loc.getWorld().spawnParticle(Particle.EXPLOSION_HUGE, loc, 1);
        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 1.0f);

        for (org.bukkit.entity.Entity e : loc.getWorld().getNearbyEntities(loc, power, power, power)) {
            if (!(e instanceof Player)) continue;
            Player p = (Player) e;
            if (p.getUniqueId().equals(victimUuid)) continue;
            // Мир проверяем до distance(): для локаций из разных миров он бросает
            // исключение, а оно здесь оборвало бы остаток взрыва.
            if (p.isDead() || !p.getWorld().equals(loc.getWorld())) continue;
            if (p.getLocation().distance(loc) <= power) {
                p.damage(damage);
            }
        }
    }

    private void beginEnding() {
        session.setState(TalismanState.ENDING);
        rewardService.giveRewards(session);

        int countdown = configManager.getConfig().getInt("stop-countdown", 2);
        final int[] timer = {countdown};

        endTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (timer[0] <= 0) {
                cleanup(true);
                return;
            }
            bossBarService.flashColor();
            timer[0]--;
        }, 0L, 20L);
    }

    /** Остановка командой: башня оседает плавно, как и при обычном финале. */
    public void forceStop() {
        cancelMainTask();
        cancelVisualTask();
        cancelEndTask();
        cleanup(true);
    }

    /**
     * Уборка после ивента.
     *
     * @param animated сносить башню плавно. При выключении сервера анимации быть не должно:
     *                 планировщик там уже не работает, и незаконченный снос оставил бы башню.
     */
    private void cleanup(boolean animated) {
        cancelEndTask();
        cancelVisualTask();
        cancelMainTask();
        bossBarService.destroy();
        hologramService.destroy();
        activePlayers.clear();
        deathCooldown.clear();

        if (session != null) {
            // Регион убираем ВСЕГДА и до сноса башни: даже если снос упадёт,
            // защищённой зоны в мире не останется.
            regionService.remove(session.getLocation(), session.getRegionId());

            if (configManager.getConfig().getBoolean("remove-schematic-on-end", false)) {
                if (animated && schematicService.hasBackup()) {
                    int layers = configManager.getConfig().getInt("demolish.layers-per-tick", 1);
                    int interval = configManager.getConfig().getInt("demolish.tick-interval", 12);
                    schematicService.setJitter(configManager.getConfig().getInt("demolish.jitter", 6));
                    schematicService.restoreAnimated(layers, interval, null);
                } else {
                    schematicService.restore();
                }
            }
        }

        session = null;
        lastDropBank = 0;
        participationTracker.resetSession();
        captureScore.reset();
    }

    /** Полная уборка без анимации — для выключения плагина. */
    public void shutdownCleanup() {
        cleanup(false);
    }

    /**
     * Сносит таблички, оставшиеся от прошлых запусков.
     *
     * Ищем вокруг последней известной точки талисмана: она сохраняется вместе со
     * слепком для отката, поэтому переживает перезапуск. Если её нет, искать негде —
     * мир большой, и сканировать его целиком нельзя.
     */
    public void sweepStaleHolograms() {
        Location last = schematicService.getPendingCenter();
        if (last != null) hologramService.sweep(last, 16);
    }

    /** Снимает регионы, оставшиеся от прошлых запусков сервера. */
    public int removeStaleRegions() {
        return regionService.removeStale(session == null ? null : session.getRegionId());
    }

    private void cancelMainTask() {
        if (mainTask != null) {
            mainTask.cancel();
            mainTask = null;
        }
    }

    private void cancelVisualTask() {
        if (visualTask != null) {
            visualTask.cancel();
            visualTask = null;
        }
    }

    private void cancelEndTask() {
        if (endTask != null) {
            endTask.cancel();
            endTask = null;
        }
    }

    public RegionService getRegionService() {
        return regionService;
    }

    public EssentialsService getEssentialsService() {
        return essentialsService;
    }
}