package com.chesthighlight.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

/**
 * Draws a small HUD label at the top-centre of the screen when the player
 * is standing inside (or looking at) a highlighted chunk.
 *
 * Shows: "⚠ Chest-heavy chunk  [X: -3, Z: 2]"
 */
public final class ChestHighlightHud {

    private ChestHighlightHud() {}

    public static void register() {
        HudRenderCallback.EVENT.register(ChestHighlightHud::onHudRender);
    }

    private static void onHudRender(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        if (client.options.debugEnabled) return; // don't clutter F3 screen

        PlayerEntity player = client.player;
        ChunkPos cp = new ChunkPos(new BlockPos(
                (int) player.getX(),
                (int) player.getY(),
                (int) player.getZ()
        ));

        if (!ChestHighlightRenderer.isHighlighted(cp)) return;

        String msg = String.format("§6§l⚑ Chest-heavy chunk  §r§e[X: %d, Z: %d]",
                cp.x, cp.z);
        Text text = Text.literal(msg);

        int screenWidth  = ctx.getScaledWindowWidth();
        int textWidth    = client.textRenderer.getWidth(text);
        int x = (screenWidth - textWidth) / 2;
        int y = 8;

        // Dark background pill
        ctx.fill(x - 4, y - 2, x + textWidth + 4, y + 10, 0x88000000);
        ctx.drawText(client.textRenderer, text, x, y, 0xFFFFFFFF, false);
    }
}
