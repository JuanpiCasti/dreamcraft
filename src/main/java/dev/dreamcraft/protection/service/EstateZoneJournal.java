package dev.dreamcraft.protection.service;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Edit journal for adventure zones (END / TRIAL_CHAMBER estate areas).
 *
 * <p>Every player modification inside a zone records the ORIGINAL block state
 * as {@code world;x;y;z;blockdata} in {@code zone-edits/<estateId>.log}.
 * When the party closes the zone — retreat through the exit portal, scheduled
 * reset or admin reset — {@link #rollback(UUID)} replays the journal in
 * reverse, restoring every touched chunk block to its pre-adventure state so
 * the next group finds the structure pristine.
 *
 * <p>Why a journal instead of {@code World.regenerateChunk}: zone columns may
 * reach surface builds; regenerating whole chunks would destroy them. The
 * journal only touches what adventurers actually modified.
 *
 * <p>All methods must run on the main server thread (they write blocks).
 */
public final class EstateZoneJournal {

    /** Hard cap per zone to bound file size; structure protection keeps real volume tiny. */
    private static final int MAX_ENTRIES = 50_000;

    private final Path folder;
    private final Logger logger;
    private final Map<UUID, Deque<String>> memory = new ConcurrentHashMap<>();

    public EstateZoneJournal(File dataFolder, Logger logger) {
        this.folder = new File(dataFolder, "zone-edits").toPath();
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            logger.warning("[ZoneJournal] No se pudo crear " + folder + ": " + e.getMessage());
        }
        this.logger = logger;
    }

    /** Records the original state of a block about to be changed inside a zone. */
    public void record(UUID estateId, Block block, BlockData originalState) {
        if (estateId == null || originalState == null || block.getWorld() == null) return;
        String line = String.join(";",
                block.getWorld().getName(),
                String.valueOf(block.getX()),
                String.valueOf(block.getY()),
                String.valueOf(block.getZ()),
                originalState.getAsString());
        Deque<String> entries = memory.computeIfAbsent(estateId, k -> new ArrayDeque<>());
        synchronized (entries) {
            if (entries.size() >= MAX_ENTRIES) return;
            entries.addLast(line);
        }
        try {
            Files.writeString(entryFile(estateId), line + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            logger.warning("[ZoneJournal] Append falló para " + estateId + ": " + e.getMessage());
        }
    }

    /**
     * Restores every journaled block to its original state (reverse order),
     * then clears the journal for that zone. No-op when there are no edits.
     */
    public void rollback(UUID estateId) {
        if (estateId == null) return;
        Deque<String> entries = memory.remove(estateId);
        List<String> lines = new ArrayList<>();
        if (entries != null) {
            synchronized (entries) {
                lines.addAll(entries);
            }
        } else {
            Path file = entryFile(estateId);
            if (!Files.exists(file)) return;
            try {
                lines.addAll(Files.readAllLines(file, StandardCharsets.UTF_8));
            } catch (IOException e) {
                logger.warning("[ZoneJournal] Lectura falló para " + estateId + ": " + e.getMessage());
                return;
            }
        }
        if (lines.isEmpty()) return;

        int restored = 0;
        // Reverse order: undo applies newest-first so the final state is exact
        for (int i = lines.size() - 1; i >= 0; i--) {
            String[] parts = lines.get(i).split(";", 5);
            if (parts.length < 5) continue;
            try {
                World world = Bukkit.getWorld(parts[0]);
                if (world == null) continue;
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);
                int z = Integer.parseInt(parts[3]);
                world.getBlockAt(x, y, z).setBlockData(Bukkit.createBlockData(parts[4]));
                restored++;
            } catch (Exception ignored) {
                // Corrupt/legacy line — skip it, the rest still restores
            }
        }
        deleteFile(estateId);
        if (restored > 0) {
            logger.info("[ZoneJournal] Zona " + estateId + " regenerada: "
                    + restored + " bloque(s) restaurados a su estado original.");
        }
    }

    /** Discards any recorded edits without applying them (e.g. area re-anchored). */
    public void clear(UUID estateId) {
        if (estateId == null) return;
        memory.remove(estateId);
        deleteFile(estateId);
    }

    private Path entryFile(UUID estateId) {
        return folder.resolve(estateId + ".log");
    }

    private void deleteFile(UUID estateId) {
        try {
            Files.deleteIfExists(entryFile(estateId));
        } catch (IOException ignored) {}
    }
}
