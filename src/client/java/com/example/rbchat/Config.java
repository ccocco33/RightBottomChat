package com.example.rbchat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 설정: 모드 ON/OFF, 폰트 스케일(슬라이더), 배경 ON/OFF, 배경 투명도(슬라이더),
 * 표시 개수/지속시간, 패턴(system/allow)
 */
public class Config {
    public static boolean enabled;           // 모드 ON/OFF
    public static float   fontScale;         // 0.60 ~ 1.20
    public static boolean bgEnabled;         // HUD 배경
    public static int     bgOpacityPercent;  // 0 ~ 100

    public static int     maxVisible;        // 최대 표시 줄
    public static int     displayMillis;     // 표시 시간(ms)

    public static List<String> systemPatterns; // 숨길(필터) 패턴
    public static List<String> allowPatterns;  // 허용(화이트리스트) 패턴

    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("rbchat.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                String json = Files.readString(CONFIG_PATH, StandardCharsets.UTF_8);
                Data d = GSON.fromJson(json, Data.class);
                if (d != null) {
                    Data def = defaults();

                    enabled           = (d.enabled);
                    fontScale         = clampf(nullTo(d.fontScale, def.fontScale), 0.60f, 1.20f);
                    bgEnabled         = (d.bgEnabled);
                    bgOpacityPercent  = clamp(nullTo(d.bgOpacityPercent, def.bgOpacityPercent), 0, 100);

                    maxVisible        = Math.max(1, nullTo(d.maxVisible, def.maxVisible));
                    displayMillis     = Math.max(100, nullTo(d.displayMillis, def.displayMillis));

                    systemPatterns    = pick(d.systemPatterns, def.systemPatterns);
                    allowPatterns     = pick(d.allowPatterns,  def.allowPatterns);
                    return;
                }
            }
            // 파일 없거나 파싱 실패 → 기본값 저장
            apply(defaults());
            saveCurrent();
        } catch (Exception e) {
            apply(defaults());
        }
    }

    public static void saveCurrent() {
        try {
            Data d = new Data();
            d.enabled          = enabled;
            d.fontScale        = fontScale;
            d.bgEnabled        = bgEnabled;
            d.bgOpacityPercent = bgOpacityPercent;

            d.maxVisible       = maxVisible;
            d.displayMillis    = displayMillis;

            d.systemPatterns   = systemPatterns;
            d.allowPatterns    = allowPatterns;

            Files.writeString(
                    CONFIG_PATH,
                    GSON.toJson(d),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (IOException ignored) {}
    }

    /** 메시지를 숨길지 판단: allow(화이트) 우선, 그 다음 system(블랙) */
    public static boolean shouldHide(String text) {
        if (!enabled) return false;
        if (text == null || text.isEmpty()) return false;

        String norm = text.replaceAll("\\p{Cntrl}", "").trim();

        // 화이트리스트에 하나라도 포함되면 숨기지 않음
        for (String p : allowPatterns) {
            if (!p.isEmpty() && norm.contains(p)) return false;
        }
        // 블랙리스트에 하나라도 포함되면 숨김
        for (String p : systemPatterns) {
            if (!p.isEmpty() && norm.contains(p)) return true;
        }
        return false;
    }

    public static int getBgArgb() {
        int a = (int) Math.round(255 * (bgOpacityPercent / 100.0));
        return ((a & 0xFF) << 24) | 0x000000; // 검정 배경
    }

    // -------- 내부 유틸/기본값 --------

    private static void apply(Data d) {
        enabled          = d.enabled;
        fontScale        = d.fontScale;
        bgEnabled        = d.bgEnabled;
        bgOpacityPercent = d.bgOpacityPercent;

        maxVisible       = d.maxVisible;
        displayMillis    = d.displayMillis;

        systemPatterns   = d.systemPatterns;
        allowPatterns    = d.allowPatterns;
    }

    private static Data defaults() {
        Data d = new Data();
        d.enabled          = true;
        d.fontScale        = 0.90f;
        d.bgEnabled        = false;
        d.bgOpacityPercent = 30;

        d.maxVisible       = 10;
        d.displayMillis    = 3000;

        d.systemPatterns = new ArrayList<>(Arrays.asList(
                "획득했습니다",
                "후 사용할 수 있습니다",
                "포인트 +"
        ));
        d.allowPatterns = new ArrayList<>(Arrays.asList(
                "님께서"
        ));
        return d;
    }

    private static <T> T nullTo(T v, T def) { return v != null ? v : def; }
    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
    private static float clampf(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }
    private static <T> List<T> pick(List<T> v, List<T> def) { return (v != null && !v.isEmpty()) ? v : def; }

    // 저장용 DTO
    static class Data {
        Boolean enabled;
        Float   fontScale;
        Boolean bgEnabled;
        Integer bgOpacityPercent;

        Integer maxVisible;
        Integer displayMillis;

        List<String> systemPatterns;
        List<String> allowPatterns;
    }
}
