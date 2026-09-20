package ru.rooyzee.elytrixclans.function.impl.shop.config;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.rooyzee.elytrixclans.Main;

/**
 * Цены магазина из отдельного файла shop/prices.yml.
 *
 * Зачем отдельный файл: shop_item.yml большой, в нём зачарования, команды и свойства
 * предметов, и искать там цены неудобно. prices.yml состоит только из строк «id: цена»,
 * поэтому правится за секунды и не рискует сломать описание предмета.
 *
 * Приоритет: если позиция есть в prices.yml с неотрицательной ценой — берётся она,
 * иначе используется цена из shop_item.yml. Так старые конфиги продолжают работать,
 * а новые позиции не обязаны дублироваться в двух файлах.
 */
public class PriceConfiguration {

    private File configFile;
    private Map<String, Double> itemPrices = Collections.emptyMap();
    private Map<String, Double> kitPrices = Collections.emptyMap();

    public PriceConfiguration() {
        setupYml();
    }

    public void reloadYml() {
        load();
    }

    private void setupYml() {
        File shopFolder = new File(Main.getInstance().getDataFolder(), "shop");
        if (!shopFolder.exists() && !shopFolder.mkdirs()) {
            Bukkit.getLogger().warning("[ElytrixClans] Не удалось создать папку shop/");
        }
        configFile = new File(shopFolder, "prices.yml");
        if (!configFile.exists()) {
            try {
                Main.getInstance().saveResource("shop/prices.yml", false);
            } catch (Exception ignored) {
            }
        }
        load();
    }

    private void load() {
        Map<String, Double> items = new HashMap<>();
        Map<String, Double> kits = new HashMap<>();
        if (configFile != null && configFile.exists()) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
            read(config.getConfigurationSection("items"), items);
            read(config.getConfigurationSection("kits"), kits);
        }
        itemPrices = Collections.unmodifiableMap(items);
        kitPrices = Collections.unmodifiableMap(kits);
    }

    private void read(ConfigurationSection section, Map<String, Double> target) {
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            Object raw = section.get(key);
            if (!(raw instanceof Number)) {
                Bukkit.getLogger().warning("[ElytrixClans] prices.yml: у позиции '" + key
                        + "' цена не является числом, строка пропущена");
                continue;
            }
            double price = ((Number) raw).doubleValue();
            // Отрицательная цена = «не переопределять», удобно временно отключить строку.
            if (price < 0 || Double.isNaN(price) || Double.isInfinite(price)) continue;
            target.put(key.toLowerCase(Locale.ROOT), price);
        }
    }

    /** Цена позиции: из prices.yml, а если её там нет — значение по умолчанию. */
    public double itemPrice(String id, double fallback) {
        return price(itemPrices, id, fallback);
    }

    /** Цена набора: из prices.yml, а если её там нет — значение по умолчанию. */
    public double kitPrice(String id, double fallback) {
        return price(kitPrices, id, fallback);
    }

    private double price(Map<String, Double> source, String id, double fallback) {
        if (id == null) return fallback;
        Double override = source.get(id.toLowerCase(Locale.ROOT));
        return override != null ? override : fallback;
    }
}
