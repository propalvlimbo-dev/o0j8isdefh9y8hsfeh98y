package ru.rooyzee.elytrixtalisman.service;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.World;
import org.bukkit.Location;
import ru.rooyzee.elytrixtalisman.Main;

import java.io.File;
import java.io.FileInputStream;

public class SchematicService {

    private final Main plugin;
    private Clipboard backupClipboard;
    private BlockVector3 backupTo;
    private World backupWorld;

    public SchematicService(Main plugin) {
        this.plugin = plugin;
    }

    public boolean paste(Location loc, String fileName, int offsetX, int offsetY, int offsetZ) {
        File file = new File(plugin.getDataFolder() + File.separator + "schematics", fileName);
        if (!file.exists()) {
            plugin.getLogger().warning("Schematic not found: " + fileName);
            return false;
        }

        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) {
            plugin.getLogger().warning("Unknown schematic format: " + fileName);
            return false;
        }

        try (FileInputStream fis = new FileInputStream(file);
             ClipboardReader reader = format.getReader(fis)) {

            Clipboard clipboard = reader.read();
            World weWorld = BukkitAdapter.adapt(loc.getWorld());

            BlockVector3 to = BlockVector3.at(
                    loc.getBlockX() + offsetX,
                    loc.getBlockY() + offsetY,
                    loc.getBlockZ() + offsetZ);

            BlockVector3 dim = clipboard.getDimensions();
            BlockVector3 origin = clipboard.getOrigin();
            BlockVector3 minPoint = clipboard.getMinimumPoint();
            BlockVector3 offset = minPoint.subtract(origin);

            BlockVector3 worldMin = to.add(offset);
            BlockVector3 worldMax = worldMin.add(dim.subtract(BlockVector3.ONE));

            CuboidRegion region = new CuboidRegion(weWorld, worldMin, worldMax);
            BlockArrayClipboard backup = new BlockArrayClipboard(region);
            backup.setOrigin(to);

            try (EditSession readSession = WorldEdit.getInstance().getEditSessionFactory().getEditSession(weWorld, -1)) {
                ForwardExtentCopy copy = new ForwardExtentCopy(readSession, region, backup, region.getMinimumPoint());
                Operations.complete(copy);
            }

            this.backupClipboard = backup;
            this.backupTo = to;
            this.backupWorld = weWorld;

            try (EditSession editSession = WorldEdit.getInstance().getEditSessionFactory().getEditSession(weWorld, -1)) {
                ClipboardHolder holder = new ClipboardHolder(clipboard);
                Operation op = holder.createPaste(editSession).to(to).ignoreAirBlocks(false).build();
                Operations.complete(op);
                editSession.flushSession();
            }
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to paste schematic: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public void restore() {
        if (backupClipboard == null || backupTo == null || backupWorld == null) return;
        try (EditSession editSession = WorldEdit.getInstance().getEditSessionFactory().getEditSession(backupWorld, -1)) {
            ClipboardHolder holder = new ClipboardHolder(backupClipboard);
            Operation op = holder.createPaste(editSession).to(backupTo).ignoreAirBlocks(false).build();
            Operations.complete(op);
            editSession.flushSession();
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to restore schematic area: " + e.getMessage());
        }
        backupClipboard = null;
        backupTo = null;
        backupWorld = null;
    }
}