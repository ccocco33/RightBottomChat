package com.example.rbchat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

public class Config {
    public static List<String> systemPatterns = new ArrayList<>();
    public static int maxVisible = 10;
    public static int displayMillis = 3000;

    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("rbchat.json");

    public static void load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                String json = Files.readString(CONFIG_PATH, StandardCharsets.UTF_8);
                Gson gson = new Gson();
                Data d = gson.fromJson(json, Data.class);
                if (d != null) {
                    systemPatterns = (d.systemPatterns != null && !d.systemPatterns.isEmpty())
                            ? d.systemPatterns : defaults().systemPatterns;
                    maxVisible = d.maxVisible > 0 ? d.maxVisible : defaults().maxVisible;
                    displayMillis = d.displayMillis > 0 ? d.displayMillis : defaults().displayMillis;
                    return;
                }
            }
            Data def = defaults();
            save(def);
            systemPatterns = def.systemPatterns;
            maxVisible = def.maxVisible;
            displayMillis = def.displayMillis;
        } catch (Exception e) {
            Data def = defaults();
            systemPatterns = def.systemPatterns;
            maxVisible = def.maxVisible;
            displayMillis = def.displayMillis;
        }
    }

    public static boolean shouldHide(String text) {
        if (text == null || text.isEmpty()) return false;
        for (String p : systemPatterns) {
            if (text.contains(p)) return true; // 부분 포함 매칭(간단/빠름)
        }
        return false;
    }

    private static void save(Data d) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Files.writeString(
                CONFIG_PATH,
                gson.toJson(d),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        );
    }

    private static Data defaults() {
        Data d = new Data();
        d.systemPatterns = Arrays.asList(
                "획득했습니다",
                "후 사용할 수 있습니다",
                "포인트 +"
        );
        d.maxVisible = 10;
        d.displayMillis = 3000;
        return d;
    }

    static class Data {
        List<String> systemPatterns;
        int maxVisible;
        int displayMillis;
    }
}
