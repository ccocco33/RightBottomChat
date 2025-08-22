package com.example.rbchat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Overlay implements HudRenderCallback {
    public static class Entry {
        public final Text text;
        public final long createdAtTick;

        public Entry(Text text, long tick) {
            this.text = text;
            this.createdAtTick = tick;
        }
    }

    private static final List<Entry> entries = new ArrayList<>();
    private static long fallbackTick = 0;

    public static void addMessage(Text text) {
        MinecraftClient mc = MinecraftClient.getInstance();
        long tick = (mc.world != null) ? mc.world.getTime() : ++fallbackTick;
        entries.add(0, new Entry(text, tick));
    }

    @Override
    public void onHudRender(DrawContext ctx, float tickDelta) {
        if (!Config.enabled) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;

        long nowTick = (mc.world != null) ? mc.world.getTime() : ++fallbackTick;
        final int durationTicks = Math.max(1, Math.round(Config.displayMillis / 50f));

        // 오래된 메시지 제거
        entries.removeIf(e -> (nowTick - e.createdAtTick) >= durationTicks);
        if (entries.isEmpty()) return;

        TextRenderer tr = mc.textRenderer;
        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();

        // ✅ Config.fontScale 사용
        float scale = Math.max(0.60f, Math.min(1.20f, Config.fontScale));
        int lineHeight = Math.round(tr.fontHeight * scale) + 2;

        int shown = 0;
        int y = Math.round(screenH - 28 - (lineHeight - tr.fontHeight));

        for (Entry e : entries) {
            if (shown >= Config.maxVisible) break;

            int ageTicks = (int) (nowTick - e.createdAtTick);
            if (ageTicks < 0 || ageTicks >= durationTicks) continue;

            float t = ageTicks / (float) durationTicks;
            float smooth = t * t * (3f - 2f * t);
            float alphaF = 1f - smooth;
            if (alphaF <= 0.08f) continue;

            int alpha = ((int) (alphaF * 255)) & 0xFF;
            int textWidth = (int) Math.ceil(tr.getWidth(e.text) * scale);
            int x = Math.round(screenW - 8 - textWidth);

            if (Config.bgEnabled) {
                int padX = 4, padY = 2;
                int left = x - padX, top = y - padY;
                int right = x + textWidth + padX, bottom = y + lineHeight - padY;
                ctx.fill(left, top, right, bottom, Config.getBgArgb());
            }

            ctx.getMatrices().push();
            ctx.getMatrices().translate(0, 0, 500);
            ctx.getMatrices().translate(x, y, 0);
            ctx.getMatrices().scale(scale, scale, 1f);

            int argb = (alpha << 24) | 0xFFFFFF;
            if (alphaF < 0.2f) ctx.drawText(tr, e.text, 0, 0, argb, false);
            else               ctx.drawTextWithShadow(tr, e.text, 0, 0, argb);

            ctx.getMatrices().pop();

            y -= lineHeight;
            shown++;
        }
    }
}
