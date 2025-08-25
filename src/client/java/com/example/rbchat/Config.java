package com.example.rbchat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

public class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("rbchat.json");

    /* ===== 사용자 설정 값들 ===== */
    public static boolean enabled = true;

    public static float  fontScale        = 0.90f; // 0.6 ~ 1.2
    public static boolean bgEnabled       = true;
    public static int    bgOpacityPercent = 30;    // 0~100

    public static int    maxVisible    = 10;       // 1~40
    public static int    displayMillis = 3000;     // 500~20000

    // 오버레이 위치/여백
    public static int offsetX = 0;   // px
    public static int offsetY = 0;   // px
    public static int edgePadding = 8; // px (이전 safePadding 대체) 0~32 권장

    // 패턴
    public static List<String> systemPatterns = new ArrayList<>(List.of(
            "획득했습니다",         // 예: "마나 140.0 를 획득했습니다!"
            "후 사용할 수 있습니다",   // 예: "[얼음길] 6.799 후 사용할 수 있습니다."
            "포인트 \\+"            // 예: "생활랭킹 포인트 +10.0"
    ));
    public static List<String> allowPatterns = new ArrayList<>(List.of(
            "님께서"               // “~님께서”가 들어가면 허용
    ));

    /* ===== 정규식 캐시 & 오류 인덱스 ===== */
    private static List<Pattern> compiledSystem = new ArrayList<>();
    private static List<Pattern> compiledAllow  = new ArrayList<>();
    public  static Set<Integer> invalidSystemIdx = new HashSet<>();
    public  static Set<Integer> invalidAllowIdx  = new HashSet<>();

    /* ===== 직렬화용 DTO ===== */
    private static class DTO {
        boolean enabled;
        float fontScale;
        boolean bgEnabled;
        int bgOpacityPercent;
        int maxVisible;
        int displayMillis;
        int offsetX, offsetY;
        int edgePadding;
        List<String> systemPatterns;
        List<String> allowPatterns;
    }

    /* ====== 기본값 ====== */
    public static void resetToDefaults() {
        enabled = true;
        fontScale = 0.90f;
        bgEnabled = true;
        bgOpacityPercent = 30;
        maxVisible = 10;
        displayMillis = 3000;
        offsetX = 0;
        offsetY = 0;
        edgePadding = 8;

        systemPatterns = new ArrayList<>(List.of(
                "획득했습니다",
                "후 사용할 수 있습니다",
                "포인트 \\+"
        ));
        allowPatterns = new ArrayList<>(List.of("님께서"));

        compilePatternsFromCurrent();
    }

    /* ====== 파일 저장/로드 ====== */
    public static void saveCurrent() {
        try {
            DTO dto = new DTO();
            dto.enabled = enabled;
            dto.fontScale = fontScale;
            dto.bgEnabled = bgEnabled;
            dto.bgOpacityPercent = bgOpacityPercent;
            dto.maxVisible = maxVisible;
            dto.displayMillis = displayMillis;
            dto.offsetX = offsetX;
            dto.offsetY = offsetY;
            dto.edgePadding = edgePadding;
            dto.systemPatterns = new ArrayList<>(systemPatterns);
            dto.allowPatterns = new ArrayList<>(allowPatterns);

            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(dto));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void loadOrDefaults() {
        if (!Files.exists(CONFIG_PATH)) {
            resetToDefaults();
            saveCurrent();
            return;
        }
        try {
            String json = Files.readString(CONFIG_PATH);
            DTO dto = GSON.fromJson(json, DTO.class);
            if (dto == null) { resetToDefaults(); return; }

            enabled = dto.enabled;
            fontScale = clamp(dto.fontScale, 0.60f, 1.20f);
            bgEnabled = dto.bgEnabled;
            bgOpacityPercent = clamp(dto.bgOpacityPercent, 0, 100);
            maxVisible = clamp(dto.maxVisible, 1, 40);
            displayMillis = clamp(dto.displayMillis, 500, 20000);
            offsetX = clamp(dto.offsetX, 0, 400);
            offsetY = clamp(dto.offsetY, 0, 400);
            edgePadding = clamp(dto.edgePadding, 0, 64);

            systemPatterns = dto.systemPatterns != null ? new ArrayList<>(dto.systemPatterns) : new ArrayList<>();
            allowPatterns  = dto.allowPatterns  != null ? new ArrayList<>(dto.allowPatterns)  : new ArrayList<>();

            compilePatternsFromCurrent();
        } catch (Exception e) {
            e.printStackTrace();
            resetToDefaults();
        }
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
    private static float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }

    /* ===== 정규식 컴파일 & 오류 인덱스 표기 ===== */
    public static void compilePatternsFromCurrent() {
        compiledSystem = new ArrayList<>();
        compiledAllow  = new ArrayList<>();
        invalidSystemIdx.clear();
        invalidAllowIdx.clear();

        for (int i = 0; i < systemPatterns.size(); i++) {
            String s = systemPatterns.get(i);
            try {
                compiledSystem.add(Pattern.compile(s));
            } catch (Exception ex) {
                invalidSystemIdx.add(i);
            }
        }
        for (int i = 0; i < allowPatterns.size(); i++) {
            String s = allowPatterns.get(i);
            try {
                compiledAllow.add(Pattern.compile(s));
            } catch (Exception ex) {
                invalidAllowIdx.add(i);
            }
        }
    }

    /* ===== 메시지 필터 =====
       - 허용 패턴이 우선 -> false
       - 그 다음 시스템(숨김) 패턴 -> true
    */
    public static boolean shouldHide(String plain) {
        if (!enabled) return false;

        for (Pattern p : compiledAllow) {
            try { if (p.matcher(plain).find()) return false; } catch (Exception ignored) {}
        }
        for (Pattern p : compiledSystem) {
            try { if (p.matcher(plain).find()) return true; } catch (Exception ignored) {}
        }
        return false;
    }

    /* ===== 유틸 ===== */
    public static int argb(int a, int r, int g, int b) {
        return (a & 0xff) << 24 | (r & 0xff) << 16 | (g & 0xff) << 8 | (b & 0xff);
    }
}
