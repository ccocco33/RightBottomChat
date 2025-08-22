package com.example.rbchat;

import com.mojang.authlib.GameProfile;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.time.Instant;

public class RBChatClient implements ClientModInitializer {
    private KeyBinding openConfigKey;

    @Override
    public void onInitializeClient() {
        // 설정 로드
        Config.load();

        // 오버레이 HUD 등록
        HudRenderCallback.EVENT.register(new Overlay());

        // 설정 화면 키 바인딩 (U)
        openConfigKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.rbchat.open_config",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_U,
                "key.categories.rbchat"
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openConfigKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new ConfigScreen(client.currentScreen));
                }
            }
        });

        // 시스템/게임 메시지
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlayFlag) -> {
            if (!Config.enabled) return true; // 모드 꺼짐이면 통과
            String s = message.getString();
            if (Config.shouldHide(s)) {
                Overlay.addMessage(message); // ✅ static API 사용
                return false; // 메인 채팅창엔 숨김
            }
            return true;
        });

        // 일반 채팅 메시지
        ClientReceiveMessageEvents.ALLOW_CHAT.register((
                Text message,
                SignedMessage signedMessage,
                GameProfile sender,
                MessageType.Parameters params,
                Instant receptionTimestamp
        ) -> {
            if (!Config.enabled) return true;
            String s = message.getString();
            if (Config.shouldHide(s)) {
                Overlay.addMessage(message); // ✅ static API 사용
                return false;
            }
            return true;
        });
    }
}
