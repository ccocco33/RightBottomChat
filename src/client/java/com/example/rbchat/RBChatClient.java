package com.example.rbchat;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.text.Text;

public class RBChatClient implements ClientModInitializer {
    private static Overlay overlay;

    @Override
    public void onInitializeClient() {
        Config.load();
        overlay = new Overlay();

        // 오른쪽 하단 오버레이 렌더러 등록
        HudRenderCallback.EVENT.register(overlay::render);

        // 1) 시스템/게임 메시지 (예: 보스바/액션바류 아님, 시스템 알림류)
        ClientReceiveMessageEvents.ALLOW_SYSTEM.register(message -> {
            String s = message.getString();
            if (Config.shouldHide(s)) {
                overlay.push(message); // 오른쪽 하단으로 보냄
                return false; // 원래 채팅창에 숨김
            }
            return true;
        });

        // 2) 게임 메시지 (일부 서버 안내 등)
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, isOverlay) -> {
            String s = message.getString();
            if (Config.shouldHide(s)) {
                overlay.push(Text.literal(s));
                return false;
            }
            return true;
        });

        // 3) 일반 채팅(플레이어 채팅)
        ClientReceiveMessageEvents.ALLOW_CHAT.register((signedMessage, sender, params) -> {
            // 서명된 채팅의 실제 텍스트 추출
            String s = signedMessage.getContent().getString();
            if (Config.shouldHide(s)) {
                overlay.push(Text.literal(s));
                return false;
            }
            return true;
        });
    }
}
