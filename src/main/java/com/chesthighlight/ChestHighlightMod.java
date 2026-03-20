package com.chesthighlight;

import com.chesthighlight.network.ChestHighlightPackets;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ChestHighlightMod implements ModInitializer {

    public static final String MOD_ID = "chesthighlight";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Minimum number of chests in a chunk to trigger the highlight. */
    public static final int CHEST_THRESHOLD = 5;

    /**
     * Server-side cache: tracks which chunk positions are currently "highlighted"
     * per world (keyed by world registry key string).
     */
    public static final Map<String, Set<ChunkPos>> highlightedChunks = new HashMap<>();

    @Override
    public void onInitialize() {
        LOGGER.info("ChestHighlight mod initialising (server)");

        // Register network packets
        ChestHighlightPackets.registerServerPackets();

        // Register block placement/breaking listeners for immediate updates
        ChestBlockListener.register();

        // Register /chesthighlight command
        ChestHighlightCommand.register();

        // When a chunk is loaded on the server, scan it and sync to all players
        ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            scanAndSync(world, chunk);
        });

        // When a chunk is unloaded, remove it from our tracking set and notify clients
        ServerChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            String key = worldKey(world);
            Set<ChunkPos> set = highlightedChunks.get(key);
            if (set != null && set.remove(chunk.getPos())) {
                ChestHighlightPackets.sendHighlightUpdate(world, chunk.getPos(), false);
            }
        });

        // Periodically re-scan loaded chunks so placement/breaking of chests is reflected.
        // We re-scan one chunk per tick per world to spread the cost out.
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (world.getTime() % 40 == 0) { // every 2 seconds
                rescanLoadedChunks(world);
            }
        });

        // When a new player joins, send them the full list of highlighted chunks
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            server.execute(() -> {
                for (ServerWorld w : server.getWorlds()) {
                    String key = worldKey(w);
                    Set<ChunkPos> set = highlightedChunks.getOrDefault(key, Set.of());
                    for (ChunkPos pos : set) {
                        ChestHighlightPackets.sendHighlightUpdateToPlayer(handler.player, pos, true);
                    }
                }
            });
        });
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Scan a single chunk and update highlight state, notifying all clients. */
    public static void scanAndSync(ServerWorld world, WorldChunk chunk) {
        int count = ChestCounter.countChests(chunk);
        boolean shouldHighlight = count >= CHEST_THRESHOLD;

        String key = worldKey(world);
        highlightedChunks.computeIfAbsent(key, k -> new HashSet<>());
        Set<ChunkPos> set = highlightedChunks.get(key);

        boolean wasHighlighted = set.contains(chunk.getPos());

        if (shouldHighlight && !wasHighlighted) {
            set.add(chunk.getPos());
            ChestHighlightPackets.sendHighlightUpdate(world, chunk.getPos(), true);
        } else if (!shouldHighlight && wasHighlighted) {
            set.remove(chunk.getPos());
            ChestHighlightPackets.sendHighlightUpdate(world, chunk.getPos(), false);
        }
    }

    /** Re-scan all currently loaded chunks in a world. */
    private static void rescanLoadedChunks(ServerWorld world) {
        // Walk every loaded chunk — catches both new chests AND removed chests
        world.getChunkManager().entryIterator().forEachRemaining(holder -> {
            WorldChunk chunk = holder.getWorldChunk();
            if (chunk != null) {
                scanAndSync(world, chunk);
            }
        });
    }

    public static String worldKey(ServerWorld world) {
        return world.getRegistryKey().getValue().toString();
    }
}
