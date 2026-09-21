package ru.rooyzee.elytrixclans;

import com.comphenix.protocol.ProtocolLibrary;
import java.util.Arrays;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import ru.rooyzee.elytrixclans.export.ClanExporter;
import ru.rooyzee.elytrixclans.api.ClanManager;
import ru.rooyzee.elytrixclans.command.admin.AdminCommand;
import ru.rooyzee.elytrixclans.command.player.ClanCommand;
import ru.rooyzee.elytrixclans.database.IDataBase;
import ru.rooyzee.elytrixclans.database.impl.YAMLDataBase;
import ru.rooyzee.elytrixclans.function.impl.glow.GlowManager;
import ru.rooyzee.elytrixclans.function.impl.glow.GlowPacketListener;
import ru.rooyzee.elytrixclans.function.impl.shop.ShopTicker;
import ru.rooyzee.elytrixclans.function.impl.shop.config.ItemsConfiguration;
import ru.rooyzee.elytrixclans.function.impl.shop.config.PriceConfiguration;
import ru.rooyzee.elytrixclans.function.impl.shop.kit.KitManager;
import ru.rooyzee.elytrixclans.function.impl.shop.util.BuyManager;
import ru.rooyzee.elytrixclans.function.impl.shop.util.PurchaseCooldownStorage;
import ru.rooyzee.elytrixclans.hook.IHook;
import ru.rooyzee.elytrixclans.hook.impl.CoreHook;
import ru.rooyzee.elytrixclans.hook.impl.PAPIHook;
import ru.rooyzee.elytrixclans.hook.impl.PLibHook;
import ru.rooyzee.elytrixclans.hook.impl.VaultHook;
import ru.rooyzee.elytrixclans.level.config.LevelConfiguration;
import ru.rooyzee.elytrixclans.listener.ArmorUpdateListener;
import ru.rooyzee.elytrixclans.listener.DisbandChatListener;
import ru.rooyzee.elytrixclans.listener.InventoryClickListener;
import ru.rooyzee.elytrixclans.listener.InventoryDragListener;
import ru.rooyzee.elytrixclans.listener.InviteListener;
import ru.rooyzee.elytrixclans.listener.PlayerDeathListener;
import ru.rooyzee.elytrixclans.listener.PvpListener;
import ru.rooyzee.elytrixclans.listener.ShopEditListener;
import ru.rooyzee.elytrixclans.listener.kill.KillCooldownStorage;
import ru.rooyzee.elytrixclans.placeholder.ClanPlaceholder;
import ru.rooyzee.elytrixclans.utils.CloseInventoryUtil;
import ru.rooyzee.elytrixclans.utils.PlayerHeadCache;

public final class Main extends JavaPlugin {

    private static Main INSTANCE;
    private IDataBase dataBase;
    private ClanManager clanManager;
    private ItemsConfiguration itemsConfiguration;
    private PriceConfiguration priceConfiguration;
    private LevelConfiguration levelConfiguration;
    private GlowManager glowManager;
    private KitManager kitManager;
    private BuyManager buyManager;
    private KillCooldownStorage killCooldownStorage;
    private PurchaseCooldownStorage purchaseCooldownStorage;
    private int autoSaveTaskId = -1;

    @Override
    public void onEnable() {
        INSTANCE = this;

        Arrays.asList(new VaultHook(), new PAPIHook(), new PLibHook(), new CoreHook())
                .forEach(IHook::initialized);

        saveDefaultConfig();
        saveResource("levels.yml", false);
        saveResource("shop/shop_item.yml", false);

        // Цены читаем раньше ассортимента: ItemsConfiguration сразу применяет переопределения.
        priceConfiguration = new PriceConfiguration();
        itemsConfiguration = new ItemsConfiguration();
        levelConfiguration = new LevelConfiguration(this);
        kitManager = new KitManager();
        buyManager = new BuyManager();
        clanManager = new ClanManager();
        dataBase = new YAMLDataBase();
        dataBase.connect();
        killCooldownStorage = new KillCooldownStorage(this);
        purchaseCooldownStorage = new PurchaseCooldownStorage(this);

        if (VaultHook.getEconomy() == null) {
            getLogger().warning("Экономика через Vault не найдена: /clan create не сможет списать деньги.");
        }

        // Кэш голов: без него меню кланов дёргает Mojang синхронно в main-потоке (лаги на /clan info).
        PlayerHeadCache.register(this);
        // Живые таймеры перезарядки в открытых меню магазина.
        ShopTicker.start(this);

        try {
            glowManager = new GlowManager();
            ProtocolLibrary.getProtocolManager().addPacketListener(new GlowPacketListener());
            // Цвет подсветки хранится в clans.yml — возвращаем его в память после загрузки базы,
            // иначе после рестарта у кланов с glow=true не было цвета и шлемы не красились.
            glowManager.restoreFromStorage();
        } catch (Throwable t) {
            glowManager = null;
            getLogger().warning("ProtocolLib отсутствует. Подсветка союзников недоступна.");
        }

        try {
            new ClanPlaceholder(this).register();
        } catch (Throwable t) {
            getLogger().warning("PlaceholderAPI не готов, плейсхолдеры кланов отключены: " + t.getMessage());
        }

        autoSaveTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (dataBase != null) {
                try {
                    // saveAsync(): снимок данных в main-потоке, запись файла асинхронно.
                    dataBase.saveAsync();
                } catch (Exception e) {
                    getLogger().warning("Failed to auto-save: " + e.getMessage());
                }
            }
        }, 6000L, 6000L).getTaskId();

        ClanCommand clanCommand = new ClanCommand();
        if (getCommand("clan") != null) {
            getCommand("clan").setExecutor(clanCommand);
            getCommand("clan").setTabCompleter(clanCommand);
        }
        AdminCommand adminCommand = new AdminCommand();
        if (getCommand("elytrixclan") != null) {
            getCommand("elytrixclan").setExecutor(adminCommand);
            getCommand("elytrixclan").setTabCompleter(adminCommand);
        }

        Bukkit.getPluginManager().registerEvents(new InventoryClickListener(), this);
        Bukkit.getPluginManager().registerEvents(new InventoryDragListener(), this);
        Bukkit.getPluginManager().registerEvents(new InviteListener(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerDeathListener(), this);
        Bukkit.getPluginManager().registerEvents(new PvpListener(), this);
        Bukkit.getPluginManager().registerEvents(new DisbandChatListener(), this);
        Bukkit.getPluginManager().registerEvents(new ArmorUpdateListener(), this);
        Bukkit.getPluginManager().registerEvents(new ShopEditListener(), this);

        // Выгрузка топа кланов в JSON для сайта (plugins/ElytrixClans/export/clans.json).
        ClanExporter.start(this);

        getLogger().info("ElytrixClans loaded successfully");
        // Маркер сборки: если его нет в логе запуска, jar не обновился.
        getLogger().info("Build 2026-09-20: магазин на монетах Vault, опыт только за PvP и Талисман");
    }

    @Override
    public void onDisable() {
        if (autoSaveTaskId != -1) Bukkit.getScheduler().cancelTask(autoSaveTaskId);
        CloseInventoryUtil.closeAllMenus();
        if (glowManager != null) glowManager.removeAllGlow();
        if (killCooldownStorage != null) killCooldownStorage.shutdown();
        if (purchaseCooldownStorage != null) purchaseCooldownStorage.shutdown();
        PlayerHeadCache.shutdown();
        ShopTicker.stop();
        ClanExporter.stop();
        if (dataBase != null) {
            try {
                // При выключении пишем синхронно: асинхронные задачи уже отменены.
                dataBase.disconnect();
            } catch (Exception e) {
                getLogger().severe("Failed to save data on disable: " + e.getMessage());
            }
        }
        INSTANCE = null;
        getLogger().info("ElytrixClans unloaded successfully");
    }

    /**
     * Перезагрузка всех конфигов плагина. Раньше /elytrixclan reload перечитывал только
     * config.yml, levels.yml и shop_item.yml — правки меню магазина и наборов не применялись.
     */
    public void reloadEverything() {
        reloadConfig();
        if (levelConfiguration != null) levelConfiguration.reloadYml();
        // Цены перечитываем перед ассортиментом и наборами — они их применяют.
        if (priceConfiguration != null) priceConfiguration.reloadYml();
        if (itemsConfiguration != null) itemsConfiguration.reloadYml();
        if (kitManager != null) kitManager.load();
        if (clanManager != null) clanManager.rebuildIndexes();
    }

    public static Main getInstance() { return INSTANCE; }
    public ClanManager getClanManager() { return clanManager; }
    public ItemsConfiguration getItemsConfiguration() { return itemsConfiguration; }
    public PriceConfiguration getPriceConfiguration() { return priceConfiguration; }
    public LevelConfiguration getLevelConfiguration() { return levelConfiguration; }
    public IDataBase getDataBase() { return dataBase; }
    public GlowManager getGlowManager() { return glowManager; }
    public KitManager getKitManager() { return kitManager; }
    public BuyManager getBuyManager() { return buyManager; }
    public KillCooldownStorage getKillCooldownStorage() { return killCooldownStorage; }
    public PurchaseCooldownStorage getPurchaseCooldownStorage() { return purchaseCooldownStorage; }
    public void saveData() { if (dataBase != null) dataBase.save(); }
}