package com.chesthighlight.client;

import com.chesthighlight.ChestHighlightMod;
import com.chesthighlight.network.ChestHighlightPackets;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.util.math.ChunkPos;

/**
 * Client-side initialiser.
 * <ul>
 *   <li>Registers the S2C packet receiver to maintain a local set of highlighted chunks.</li>
 *   <li>Registers a world render event to draw the glowing chunk outline.</li>
 * </ul>
 */
public class ChestHighlightClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ChestHighlightMod.LOGGER.info("ChestHighlight client initialising");

        // Clear client-side highlights when leaving a world/server
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client2) -> ChestHighlightRenderer.clearAll());

        // Listen for server packets
        ClientPlayNetworking.registerGlobalReceiver(
                ChestHighlightPackets.HighlightChunkPayload.ID,
                (payload, context) -> {
                    ChunkPos pos = new ChunkPos(payload.chunkX(), payload.chunkZ());
                    if (payload.highlight()) {
                        ChestHighlightRenderer.addHighlight(pos);
                    } else {
                        ChestHighlightRenderer.removeHighlight(pos);
                    }
                }
        );

        // Hook into the world render pipeline — runs after translucent rendering
        WorldRenderEvents.LAST.register(ChestHighlightRenderer::onWorldRender);

        // HUD overlay
        ChestHighlightHud.register();
    }
}
