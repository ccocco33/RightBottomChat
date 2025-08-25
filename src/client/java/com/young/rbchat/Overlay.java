package com.young.rbchat;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayDeque;
import java.util.Deque;

public class Overlay implements HudRenderCallback {

    private static class Entry {
        final Text text;
        final long createdAtMs;
        Entry(Text t) {
            this.text = t;
            this.createdAtMs = System.currentTimeMillis();
        }
    }

    private final Deque<Entry> queue = new ArrayDeque<>();

    public void push(Text t) {
        queue.addFirst(new Entry(t));
        while (queue.size() > Config.maxVisible) {
            queue.removeLast();
        }
    }

    @Override
    public void onHudRender(DrawContext ctx, float tickDelta) {
        if (!Config.enabled) return;
        if (queue.isEmpty()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        float scale = Math.max(0.60f, Math.min(1.20f, Config.fontScale));
        int lineBase = Math.round(tr.fontHeight * scale);
        int pad = 3;
        int gap = 2;

        int margin = Math.max(0, Config.edgePadding); // ← 숫자 여백 적용

        int now = (int) System.currentTimeMillis();
        int shown = 0;

        for (Entry e : queue) {
            int age = now - (int) e.createdAtMs;
            if (age >= Config.displayMillis) continue;

            float r = Math.max(0f, Math.min(1f, age / (float) Config.displayMillis));
            // ease-out (outCubic): alpha = (1 - r)^3
            float alphaF = (float) Math.pow(1.0 - r, 3.0);
            int alpha = Math.round(alphaF * 255f);

            String s = e.text.getString();
            int textW = Math.round(tr.getWidth(s) * scale);
            int xRight = sw - margin - Config.offsetX;
            int x = xRight - textW;
            int y = sh - (margin + 20) - Config.offsetY - shown * (lineBase + gap);

            if (Config.bgEnabled && alpha > 0) {
                int bg = Config.argb(Math.round(alpha * (Config.bgOpacityPercent / 100f)), 0, 0, 0);
                ctx.fill(x - pad, y - pad, x + textW + pad, y + lineBase + pad, bg);
            }

            ctx.getMatrices().push();
            ctx.getMatrices().scale(scale, scale, 1.0f);
            int sx = Math.round(x / scale);
            int sy = Math.round(y / scale);
            int col = (alpha << 24) | 0xFFFFFF;
            ctx.drawText(tr, s, sx, sy, col, true);
            ctx.getMatrices().pop();

            shown++;
            if (shown >= Config.maxVisible) break;
        }

        queue.removeIf(e -> (now - e.createdAtMs) >= Config.displayMillis);
    }
}
