package com.example.rbchat;

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

    private final Deque<Entry> entries = new ArrayDeque<>();

    public void push(Text text) {
        entries.addFirst(new Entry(text));
        while (entries.size() > 64) { // 내부 버퍼(보이는 건 maxVisible로 제한)
            entries.removeLast();
        }
    }

    @Override
    public void onHudRender(DrawContext context, float tickDelta) {
        long now = System.currentTimeMillis();
        int duration = Config.displayMillis;
        int maxVisible = Config.maxVisible;

        // 만료 제거
        entries.removeIf(e -> now - e.createdAtMs > duration);
        if (entries.isEmpty()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;

        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();

        int shown = 0;
        int y = screenH - 28; // 핫바 바로 위 정도

        for (Entry e : entries) {
            if (shown >= maxVisible) break;

            int age = (int) (now - e.createdAtMs);
            float alphaF = 1f - (age / (float) duration);
            int alpha = Math.max(0, Math.min(255, (int)(alphaF * 255)));

            int textWidth = tr.getWidth(e.text);
            int x = screenW - 8 - textWidth;

            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 500); // 최상단에 그리기
            int argb = (alpha << 24) | 0xFFFFFF;
            context.drawTextWithShadow(tr, e.text, x, y, argb);
            context.getMatrices().pop();

            y -= tr.fontHeight + 2;
            shown++;
        }
    }
}
