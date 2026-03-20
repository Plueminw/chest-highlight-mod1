package com.chesthighlight;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.Set;

import static net.minecraft.server.command.CommandManager.literal;

/**
 * Registers the {@code /chesthighlight} command with several sub-commands:
 *
 * <pre>
 *   /chesthighlight rescan         – Force-rescan every loaded chunk in current world
 *   /chesthighlight list            – List all highlighted chunks in current world
 *   /chesthighlight here            – Show chest count in the chunk you're standing in
 *   /chesthighlight threshold <n>   – (future: runtime config)
 * </pre>
 */
public final class ChestHighlightCommand {

    private ChestHighlightCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register(ChestHighlightCommand::registerCommands);
    }

    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher,
                                         CommandRegistryAccess registryAccess,
                                         CommandManager.RegistrationEnvironment env) {
        dispatcher.register(
            literal("chesthighlight")
                .requires(src -> src.hasPermissionLevel(2)) // OP only
                .then(literal("rescan")
                    .executes(ChestHighlightCommand::cmdRescan))
                .then(literal("list")
                    .executes(ChestHighlightCommand::cmdList))
                .then(literal("here")
                    .executes(ChestHighlightCommand::cmdHere))
        );
    }

    // -------------------------------------------------------------------------
    // Sub-command handlers
    // -------------------------------------------------------------------------

    /** Force a full rescan of all loaded chunks in the player's current world. */
    private static int cmdRescan(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        ServerWorld world = src.getWorld();

        src.sendFeedback(() -> Text.literal("§eScanning all loaded chunks..."), false);

        int[] count = { 0 };
        world.getChunkManager().entryIterator().forEachRemaining(holder -> {
            WorldChunk chunk = holder.getWorldChunk();
            if (chunk != null) {
                ChestHighlightMod.scanAndSync(world, chunk);
                count[0]++;
            }
        });

        String worldName = world.getRegistryKey().getValue().getPath();
        Set<ChunkPos> highlighted = ChestHighlightMod.highlightedChunks
                .getOrDefault(ChestHighlightMod.worldKey(world), Set.of());

        src.sendFeedback(() -> Text.literal(String.format(
                "§aRescan complete. §7Scanned §f%d§7 chunks in §f%s§7. " +
                "Highlighted: §f%d§7 chunks.",
                count[0], worldName, highlighted.size())), false);
        return 1;
    }

    /** List all currently highlighted chunks in this world. */
    private static int cmdList(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        ServerWorld world = src.getWorld();
        Set<ChunkPos> set = ChestHighlightMod.highlightedChunks
                .getOrDefault(ChestHighlightMod.worldKey(world), Set.of());

        if (set.isEmpty()) {
            src.sendFeedback(() -> Text.literal("§7No highlighted chunks in this world."), false);
            return 0;
        }

        src.sendFeedback(() -> Text.literal(
                "§6§lHighlighted chunks §r§7(§e" + set.size() + "§7):"), false);
        for (ChunkPos pos : set) {
            src.sendFeedback(() -> Text.literal(
                    "  §e[" + pos.x + ", " + pos.z + "]  §7→  block (" +
                    pos.getStartX() + ", " + pos.getStartZ() + ")"), false);
        }
        return set.size();
    }

    /** Show the chest count of the chunk you're currently standing in. */
    private static int cmdHere(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        ServerPlayerEntity player;
        try {
            player = src.getPlayerOrThrow();
        } catch (Exception e) {
            src.sendError(Text.literal("Must be run by a player."));
            return 0;
        }

        ServerWorld world = src.getWorld();
        ChunkPos cp = new ChunkPos(player.getBlockPos());
        WorldChunk chunk = world.getChunk(cp.x, cp.z);
        int count = ChestCounter.countChests(chunk);
        boolean highlighted = ChestHighlightMod.highlightedChunks
                .getOrDefault(ChestHighlightMod.worldKey(world), Set.of())
                .contains(cp);

        src.sendFeedback(() -> Text.literal(String.format(
                "§7Chunk §e[%d, %d]§7: §f%d§7 chest(s) %s",
                cp.x, cp.z, count,
                highlighted ? "§a✔ highlighted" : "§7(below threshold)")), false);
        return count;
    }
}
