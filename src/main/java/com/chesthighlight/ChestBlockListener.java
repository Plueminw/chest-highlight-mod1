package com.chesthighlight;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.TrappedChestBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Listens for chest block placement and breaking so the highlight state
 * can be updated immediately, without waiting for the periodic rescan.
 */
public final class ChestBlockListener {

    private ChestBlockListener() {}

    public static void register() {
        // After a player breaks any block, check if it was a chest
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, entity) -> {
            if (world.isClient()) return;
            if (!(state.getBlock() instanceof ChestBlock || state.getBlock() instanceof TrappedChestBlock)) return;

            ServerWorld sw = (ServerWorld) world;
            WorldChunk chunk = sw.getWorldChunk(pos);
            ChestHighlightMod.scanAndSync(sw, chunk);
        });

        // After a player places a block, check if it was a chest
        // We use ServerTickEvents approach via block entity appearance — UseBlockCallback
        // fires before the block is placed, so we schedule a tick-delayed check instead.
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient()) return ActionResult.PASS;
            var stack = player.getStackInHand(hand);
            if (stack.isEmpty()) return ActionResult.PASS;

            var item = stack.getItem();
            // Check if the item being used could place a chest
            boolean isChestItem = item == Blocks.CHEST.asItem()
                    || item == Blocks.TRAPPED_CHEST.asItem();

            if (isChestItem && world instanceof ServerWorld sw) {
                // Schedule a check 1 tick later (after the block is actually placed)
                sw.getServer().execute(() -> {
                    ChunkPos cp = new ChunkPos(hitResult.getBlockPos());
                    WorldChunk chunk = sw.getChunk(cp.x, cp.z);
                    ChestHighlightMod.scanAndSync(sw, chunk);
                });
            }
            return ActionResult.PASS;
        });
    }
}
