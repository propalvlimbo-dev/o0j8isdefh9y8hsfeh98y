package ru.rooyzee.elytrixtalisman;

import org.bukkit.plugin.java.JavaPlugin;
import ru.rooyzee.elytrixtalisman.command.TalismanCommand;
import ru.rooyzee.elytrixtalisman.config.ConfigManager;
import ru.rooyzee.elytrixtalisman.hook.DependencyValidator;
import ru.rooyzee.elytrixtalisman.listener.PlayerDeathListener;
import ru.rooyzee.elytrixtalisman.listener.PlayerJoinListener;
import ru.rooyzee.elytrixtalisman.listener.PlayerMoveListener;
import ru.rooyzee.elytrixtalisman.manager.ScheduleManager;
import ru.rooyzee.elytrixtalisman.manager.TalismanManager;
import ru.rooyzee.elytrixtalisman.menu.LootMenu;
import ru.rooyzee.elytrixtalisman.service.*;

public final class Main extends JavaPlugin {

    private static Main instance;
    private ConfigManager configManager;
    private TalismanManager talismanManager;
    private ScheduleManager scheduleManager;
    private MessageService messageService;
    private ClanService clanService;
    private LocationService locationService;
    private RegionService regionService;
    private SchematicService schematicService;
    private BossBarService bossBarService;
    private RewardService rewardService;
    private PlayerParticipationTracker participationTracker;
    private EssentialsService essentialsService;
    private LootService lootService;
    private HologramService hologramService;
    private ParticleService particleService;
    private LootMenu lootMenu;

    @Override
    public void onEnable() {
        instance = this;

        if (!DependencyValidator.validate()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        configManager = new ConfigManager(this);
        messageService = new MessageService(configManager);
        clanService = new ClanService();
        locationService = new LocationService(configManager);
        regionService = new RegionService(configManager);
        schematicService = new SchematicService(this);
        bossBarService = new BossBarService(configManager);
        participationTracker = new PlayerParticipationTracker();
        rewardService = new RewardService(configManager, participationTracker, messageService);
        essentialsService = new EssentialsService(configManager);
        lootService = new LootService(this);
        hologramService = new HologramService();
        particleService = new ParticleService();

        talismanManager = new TalismanManager(this, configManager, messageService, clanService,
                locationService, regionService, schematicService, bossBarService, rewardService,
                essentialsService, lootService, hologramService, particleService,
                participationTracker);

        scheduleManager = new ScheduleManager(this, configManager, talismanManager, messageService);
        scheduleManager.scheduleAll();

        lootMenu = new LootMenu(this, lootService, messageService);

        TalismanCommand cmd = new TalismanCommand(talismanManager, configManager, messageService, scheduleManager, lootMenu);
        getCommand("elytrixtalisman").setExecutor(cmd);
        getCommand("elytrixtalisman").setTabCompleter(cmd);

        getServer().getPluginManager().registerEvents(new PlayerMoveListener(talismanManager, essentialsService), this);
        getServer().getPluginManager().registerEvents(new PlayerDeathListener(talismanManager, clanService), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(talismanManager, bossBarService), this);

        // Уборка за прошлым запуском. Если сервер упал во время ивента, в мире остаются
        // регион WorldGuard и сама башня — ни то, ни другое само не исчезает.
        // Делаем на следующем тике: миры и регионы WorldGuard к этому моменту прогружены.
        getServer().getScheduler().runTask(this, () -> {
            try {
                int removed = talismanManager.removeStaleRegions();
                if (removed > 0) {
                    getLogger().info("Удалено регионов от прошлых запусков: " + removed);
                }
                schematicService.restorePendingOnStartup();
            } catch (Throwable t) {
                getLogger().warning("Не удалось прибраться после прошлого запуска: " + t.getMessage());
            }
        });

        getLogger().info("ElytrixTalisman loaded successfully");
    }

    @Override
    public void onDisable() {
        if (talismanManager != null && talismanManager.isRunning()) {
            // Без анимации: планировщик при выключении уже не работает, и плавный
            // снос просто не доиграл бы — башня осталась бы стоять.
            talismanManager.shutdownCleanup();
        }
        // Отдельная ветка: ивент уже закончился, но башня ещё оседает. Задача сноса
        // сейчас будет убита вместе с планировщиком, поэтому дорушиваем разом.
        if (schematicService != null && schematicService.isDemolishing()) {
            schematicService.restore();
        }
        if (scheduleManager != null) {
            scheduleManager.cancelAll();
        }
        instance = null;
    }

    public static Main getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public TalismanManager getTalismanManager() {
        return talismanManager;
    }

    public ScheduleManager getScheduleManager() {
        return scheduleManager;
    }
}