package com.chesthighlight;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.TrappedChestBlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Utility class that counts the number of chest block entities in a given chunk.
 * Counts both regular {@link ChestBlockEntity} and trapped chest variants.
 */
public final class ChestCounter {

    private ChestCounter() {}

    /**
     * Count all chest-type block entities (regular + trapped) inside a chunk.
     *
     * @param chunk The chunk to scan.
     * @return Total number of chests found.
     */
    public static int countChests(WorldChunk chunk) {
        if (chunk == null) return 0;

        int count = 0;
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            if (be instanceof ChestBlockEntity || be instanceof TrappedChestBlockEntity) {
                count++;
            }
        }
        return count;
    }
}
