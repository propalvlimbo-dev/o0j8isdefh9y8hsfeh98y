package ru.rooyzee.elytrixtalisman.service;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import ru.rooyzee.elytrixtalisman.Main;
import ru.rooyzee.elytrixtalisman.model.LootItem;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class LootService {

    private final Main plugin;
    private final File file;
    private FileConfiguration config;
    private final List<LootItem> items = new ArrayList<>();

    public LootService(Main plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "loot.yml");
        if (!file.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Cannot create loot.yml");
            }
        }
        load();
    }

    public void load() {
        items.clear();
        config = YamlConfiguration.loadConfiguration(file);
        List<?> raw = config.getList("items");
        if (raw == null) return;
        for (Object obj : raw) {
            if (obj instanceof ItemStack) {
                items.add(new LootItem((ItemStack) obj));
            }
        }
    }

    public void save(List<ItemStack> newItems) {
        items.clear();
        List<ItemStack> toSave = new ArrayList<>();
        for (ItemStack is : newItems) {
            if (is != null && is.getType() != Material.AIR) {
                items.add(new LootItem(is));
                toSave.add(is);
            }
        }
        config.set("items", toSave);
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Cannot save loot.yml");
        }
    }

    public List<LootItem> getItems() {
        return items;
    }

    public void dropItems(JavaPlugin plugin, Location loc, int amount) {
        if (items.isEmpty() || loc == null) return;
        for (int i = 0; i < amount; i++) {
            final int index = i;
            final double angle = (Math.PI * 2 / Math.max(amount, 1)) * i
                    + ThreadLocalRandom.current().nextDouble(-0.3, 0.3);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                LootItem li = items.get(ThreadLocalRandom.current().nextInt(items.size()));
                ItemStack drop = li.getItemStack();
                double vx = Math.cos(angle) * 0.6;
                double vz = Math.sin(angle) * 0.6;
                double vy = 0.4 + ThreadLocalRandom.current().nextDouble(0.2);
                org.bukkit.entity.Item entity = loc.getWorld().dropItem(loc.clone(), drop);
                entity.setVelocity(new Vector(vx, vy, vz));
                loc.getWorld().playSound(loc, Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.5f);
            }, index * 3L);
        }
    }
}