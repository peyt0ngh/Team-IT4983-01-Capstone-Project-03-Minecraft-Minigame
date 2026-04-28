package com.ryan.dungeoncrawler.util;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.format.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.session.ClipboardHolder;
import org.bukkit.Location;

import java.io.File;
import java.io.FileInputStream;

public class SchematicUtil {

    public static void pasteSchematic(File file, Location loc, int rotation) {

        try {
            ClipboardFormat format = ClipboardFormats.findByFile(file);

            if (format == null) {
                System.out.println("Unknown schematic format: " + file.getName());
                return;
            }

            try (ClipboardReader reader =
                         format.getReader(new FileInputStream(file))) {

                Clipboard clipboard = reader.read();

                ClipboardHolder holder = new ClipboardHolder(clipboard);

                holder.setTransform(
                        new AffineTransform().rotateY(rotation)
                );

                try (EditSession editSession =
                             WorldEdit.getInstance()
                                     .newEditSession(
                                             BukkitAdapter.adapt(loc.getWorld()))) {

                    Operation operation = holder
                            .createPaste(editSession)
                            .to(BlockVector3.at(
                                    loc.getBlockX(),
                                    loc.getBlockY(),
                                    loc.getBlockZ()))
                            .ignoreAirBlocks(false)
                            .build();

                    Operations.complete(operation);
                }
            }

            System.out.println("Pasted schematic: " + file.getName());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
