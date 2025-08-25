package com.young.rbchat;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.text.Text;

import java.time.Instant;

public class RBChatClient implements ClientModInitializer {

    private static Overlay overlay;

    @Override
    public void onInitializeClient() {
        // 설정 로드
        Config.loadOrDefaults();

        // HUD 오버레이 준비 & 등록
        overlay = new Overlay();
        HudRenderCallback.EVENT.register(overlay);

        // === 시스템/게임 메시지 필터 ===
        // 시그니처: boolean allowReceiveGameMessage(Text message, boolean overlay)
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, isActionBar) -> {
            String s = message.getString();
            if (Config.shouldHide(s)) {
                overlay.push(message);   // 우리 사이드 HUD로 보냄
                return false;            // 원래 채팅창에는 표시하지 않음
            }
            return true;
        });

        // === 플레이어 채팅 필터 (선택) ===
        // 시그니처: boolean allowReceiveChatMessage(Text message, SignedMessage signedMessage,
        //                                           GameProfile sender, MessageType.Parameters params,
        //                                           Instant receptionTimestamp)
        ClientReceiveMessageEvents.ALLOW_CHAT.register((Text message,
                                                        net.minecraft.network.message.SignedMessage signedMessage,
                                                        com.mojang.authlib.GameProfile sender,
                                                        net.minecraft.network.message.MessageType.Parameters params,
                                                        Instant ts) -> {
            String s = message.getString();
            if (Config.shouldHide(s)) {
                overlay.push(message);
                return false;
            }
            return true;
        });
    }
}
