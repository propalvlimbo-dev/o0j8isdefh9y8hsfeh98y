package ru.rooyzee.elytrixtalisman.manager;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
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
    private final TotemService totemService;
    private final ParticleService particleService;
    private final PlayerParticipationTracker participationTracker;

    private TalismanSession session;
    private BukkitTask mainTask;
    private BukkitTask visualTask;
    private BukkitTask endTask;
    private int lastDropBank = 0;
    private final Set<UUID> activePlayers = new HashSet<>();
    private final Set<UUID> deathCooldown = new HashSet<>();

    public TalismanManager(Main plugin, ConfigManager configManager, MessageService messageService,
                           ClanService clanService, LocationService locationService, RegionService regionService,
                           SchematicService schematicService, BossBarService bossBarService,
                           RewardService rewardService, EssentialsService essentialsService,
                           LootService lootService, HologramService hologramService,
                           TotemService totemService, ParticleService particleService,
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
        this.totemService = totemService;
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
        // Полный сброс: списки захватчиков прошлого ивента не должны попасть в награды.
        participationTracker.resetSession();

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

        double totemRadius = configManager.getConfig().getDouble("talisman.totem-radius", 2.0);
        double totemHeight = configManager.getConfig().getDouble("talisman.totem-height-offset", 1.0);
        double rotSpeed = configManager.getConfig().getDouble("talisman.totem-rotation-speed", 0.08);
        totemService.configure(talismanLoc.clone().add(0.5, 0, 0.5), totemRadius, totemHeight, rotSpeed);
        totemService.setLabelTemplate(configManager.getMessages().getString("totem-label", "&#F8BEFB✦ %points%"));

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
            if (session == null || session.getState() != TalismanState.RUNNING) return;

            if (session.isBankFull()) {
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
                    // Личный счётчик над тотемом. Это же и ставка: убийца заберёт её себе.
                    totemService.addPoints(player.getUniqueId(), pointsPerStand);
                    capturing.add(player);
                } else {
                    withoutClan.add(player);
                }
            });

            // Банк за этот тик посчитан полностью — теперь у всех на экране одно число.
            Map<String, String> abPh = new HashMap<>();
            abPh.put("%bank%", String.valueOf(session.getBank()));
            abPh.put("%max_bank%", String.valueOf(session.getMaxBank()));
            String actionBar = ColorUtil.colorize(PlaceholderUtil.replace(actionBarTemplate, abPh));
            for (Player player : capturing) {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText(actionBar));
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
                if (!activePlayers.contains(uuid)) {
                    activePlayers.add(uuid);
                    totemService.addTotem(uuid);
                }
            }
            for (UUID uuid : new HashSet<>(activePlayers)) {
                if (!currentTick.contains(uuid)) {
                    activePlayers.remove(uuid);
                    totemService.removeTotem(uuid);
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
            hologramService.update(holoPh);

            Map<String, String> bbPh = new HashMap<>();
            bbPh.put("%x%", String.valueOf(talisman.getBlockX()));
            bbPh.put("%z%", String.valueOf(talisman.getBlockZ()));
            bbPh.put("%leader%", session.getLeaderName(noLeader));
            bbPh.put("%bank%", String.valueOf(session.getBank()));
            bbPh.put("%max_bank%", String.valueOf(session.getMaxBank()));
            bossBarService.update(bbPh, session.getProgress());
        }, 0L, tickInterval);

        double particleRadius = configManager.getConfig().getDouble("talisman.particle-radius", 1.5);
        int particleCount = configManager.getConfig().getInt("talisman.particle-count", 40);

        visualTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (session == null || session.getTalismanBlockLocation() == null) return;
            totemService.tick();
            particleService.ring(session.getTalismanBlockLocation().clone().add(0.5, 0, 0.5),
                    particleRadius, particleCount);
        }, 0L, 1L);
    }

    /**
     * Убийство на точке.
     *
     * Кроме обычных очков за фраг убийца забирает личные очки жертвы: они уже были
     * начислены её клану, поэтому здесь их нужно и списать с проигравшего клана, и
     * выдать победившему. Иначе награбленное задвоилось бы — у жертвы в клановом
     * счёте, у убийцы вторым начислением.
     *
     * @return сколько очков перешло убийце (0, если жертве нечего было терять)
     */
    public int addKill(String killerClan, String victimClan, UUID killerUuid, UUID victimUuid) {
        if (session == null || session.getState() != TalismanState.RUNNING) return 0;
        int pointsPerKill = configManager.getConfig().getInt("capture.points-per-kill", 5);
        session.getData(killerClan).addKill();
        session.getData(killerClan).addCapturePoints(pointsPerKill);
        session.addBank(pointsPerKill);
        session.getData(victimClan).addDeath();

        int stolen = totemService.transferPoints(victimUuid, killerUuid);
        if (stolen > 0) {
            session.getData(victimClan).addCapturePoints(-stolen);
            session.getData(killerClan).addCapturePoints(stolen);
        }
        return stolen;
    }

    /**
     * Смерть на точке: взрыв тотема.
     *
     * @param burnPoints сжечь личные очки. true — когда убийцы-игрока нет (упал, лава,
     *                   моб): забирать очки некому, они пропадают. При убийстве игроком
     *                   их уже забрал addKill, и сжигать нечего.
     */
    public void handleDeathOnEvent(UUID victimUuid, boolean burnPoints) {
        if (session == null || session.getState() != TalismanState.RUNNING) return;
        if (!activePlayers.contains(victimUuid)) return;
        if (burnPoints) totemService.burnPoints(victimUuid);
        double power = configManager.getConfig().getDouble("talisman.totem-explode-power", 3.0);
        double damage = configManager.getConfig().getDouble("talisman.totem-explode-damage", 6.0);
        activePlayers.remove(victimUuid);
        deathCooldown.add(victimUuid);
        totemService.explodeTotem(victimUuid, power, damage);
        Bukkit.getScheduler().runTaskLater(plugin, () -> deathCooldown.remove(victimUuid), 60L);
    }

    private void beginEnding() {
        session.setState(TalismanState.ENDING);
        rewardService.giveRewards(session);

        int countdown = configManager.getConfig().getInt("stop-countdown", 10);
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
        totemService.destroy();
        activePlayers.clear();
        deathCooldown.clear();

        if (session != null) {
            // Регион убираем ВСЕГДА и до сноса башни: даже если снос упадёт,
            // защищённой зоны в мире не останется.
            regionService.remove(session.getLocation(), session.getRegionId());

            if (configManager.getConfig().getBoolean("remove-schematic-on-end", false)) {
                if (animated && schematicService.hasBackup()) {
                    int layers = configManager.getConfig().getInt("demolish.layers-per-tick", 1);
                    int interval = configManager.getConfig().getInt("demolish.tick-interval", 2);
                    schematicService.restoreAnimated(layers, interval, null);
                } else {
                    schematicService.restore();
                }
            }
        }

        session = null;
        lastDropBank = 0;
        participationTracker.resetSession();
    }

    /** Полная уборка без анимации — для выключения плагина. */
    public void shutdownCleanup() {
        cleanup(false);
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