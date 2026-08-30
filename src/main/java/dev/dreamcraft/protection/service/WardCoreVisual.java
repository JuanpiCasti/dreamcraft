package dev.dreamcraft.protection.service;

import dev.dreamcraft.protection.domain.model.Ward;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.NoteBlock;

import java.util.function.Predicate;

/**
 * Keeps the physical Núcleo block's visual state in sync with protection.
 *
 * <p>The resource pack maps two magic states of the note block (identity
 * {@code instrument=flute}) to two cube textures:
 * {@code note=14} → protected (active) cube,
 * {@code note=15} → unprotected (inactive) cube.
 *
 * <p>The state channel is the NOTE property, never {@code powered}: vanilla
 * recomputes {@code powered} on every neighbor update (placing ANY block next
 * to the core forces it back to {@code powered=false}), which made the old
 * powered-based channel flip to the "active" look with an empty balance. The
 * note property is only changed by explicit right-click tuning — which this
 * plugin cancels on core blocks — so it is stable under world edits.
 * {@code powered} is kept permanently {@code false}, which vanilla itself
 * preserves whenever no redstone signal reaches the block.
 *
 * <p>Pure observation — never loads chunks; skips silently when the core's
 * chunk is unloaded or the physical block is missing.
 */
public final class WardCoreVisual {

    /** Magic note value rendered as the protected (active) cube. */
    public static final int NOTE_PROTECTED = 14;
    /** Magic note value rendered as the unprotected (inactive) cube. */
    public static final int NOTE_UNPROTECTED = 15;

    private final Predicate<Ward> unprotected;
    private final Material coreMaterial;

    public WardCoreVisual(Predicate<Ward> unprotected, Material coreMaterial) {
        this.unprotected = unprotected;
        this.coreMaterial = coreMaterial;
    }

    /** Refreshes the core block's note state when its chunk is loaded. */
    public void refresh(Ward ward) {
        apply(ward);
    }

    /**
     * Re-applies the full magic state (instrument/note/powered). Run 1 tick
     * after placement: vanilla finalizes the note block's context-dependent
     * state after the place event, which would otherwise overwrite what the
     * listener set inline.
     */
    public void applyMagicState(Ward ward) {
        apply(ward);
    }

    private void apply(Ward ward) {
        if (coreMaterial == null) return;
        World world = Bukkit.getWorld(ward.worldName());
        if (world == null || !world.isChunkLoaded(ward.centerX() >> 4, ward.centerZ() >> 4)) return;
        Block block = world.getBlockAt(ward.centerX(), ward.centerY(), ward.centerZ());
        if (block.getType() != coreMaterial) return;
        if (!(block.getBlockData() instanceof NoteBlock note)) return;

        int wantNote = unprotected.test(ward) ? NOTE_UNPROTECTED : NOTE_PROTECTED;
        boolean alreadyCorrect = note.getInstrument() == org.bukkit.Instrument.FLUTE
                && !note.isPowered()
                && note.getNote().getId() == wantNote;
        if (alreadyCorrect) return;

        note.setInstrument(org.bukkit.Instrument.FLUTE);
        note.setNote(new org.bukkit.Note(wantNote));
        note.setPowered(false);
        block.setBlockData(note, false);
    }
}
