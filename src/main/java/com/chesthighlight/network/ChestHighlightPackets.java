package com.chesthighlight.network;

import com.chesthighlight.ChestHighlightMod;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;

/**
 * Handles all network communication for the ChestHighlight mod.
 *
 * <p>Protocol: Server → Client packet that tells the client to add or remove
 * a chunk position from the "highlighted" set.
 */
public final class ChestHighlightPackets {

    private ChestHighlightPackets() {}

    // -------------------------------------------------------------------------
    // Payload definition
    // -------------------------------------------------------------------------

    /**
     * S2C payload carrying (chunkX, chunkZ, highlight=true/false).
     */
    public record HighlightChunkPayload(int chunkX, int chunkZ, boolean highlight)
            implements CustomPayload {

        public static final CustomPayload.Id<HighlightChunkPayload> ID =
                new CustomPayload.Id<>(Identifier.of(ChestHighlightMod.MOD_ID, "highlight_chunk"));

        public static final PacketCodec<PacketByteBuf, HighlightChunkPayload> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.INTEGER, HighlightChunkPayload::chunkX,
                        PacketCodecs.INTEGER, HighlightChunkPayload::chunkZ,
                        PacketCodecs.BOOL,    HighlightChunkPayload::highlight,
                        HighlightChunkPayload::new
                );

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    // -------------------------------------------------------------------------
    // Registration
    // -------------------------------------------------------------------------

    /** Called once during server init. */
    public static void registerServerPackets() {
        PayloadTypeRegistry.playS2C().register(
                HighlightChunkPayload.ID,
                HighlightChunkPayload.CODEC
        );
        ChestHighlightMod.LOGGER.info("ChestHighlight packets registered");
    }

    // -------------------------------------------------------------------------
    // Sending helpers
    // -------------------------------------------------------------------------

    /** Broadcast a chunk highlight update to every player currently in the given world. */
    public static void sendHighlightUpdate(ServerWorld world, ChunkPos pos, boolean highlight) {
        HighlightChunkPayload payload = new HighlightChunkPayload(pos.x, pos.z, highlight);
        for (ServerPlayerEntity player : PlayerLookup.world(world)) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    /** Send the current highlight state for one chunk to a single player. */
    public static void sendHighlightUpdateToPlayer(ServerPlayerEntity player,
                                                   ChunkPos pos,
                                                   boolean highlight) {
        HighlightChunkPayload payload = new HighlightChunkPayload(pos.x, pos.z, highlight);
        ServerPlayNetworking.send(player, payload);
    }
}
