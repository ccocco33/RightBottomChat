package com.example.rbchat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.text.Text;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

public class ConfigScreen extends Screen {
    private final Screen parent;

    // 전부 스크롤 대상 (라이브 프리뷰 반영)
    private ButtonWidget toggleEnabledBtn;
    private ButtonWidget bgToggleBtn;
    private SimpleSlider  scaleSlider;
    private SimpleSlider  bgOpacitySlider;

    private SimpleSlider  offsetXSlider;
    private SimpleSlider  offsetYSlider;
    private SimpleSlider  edgePaddingSlider; // ← 가장자리 여백

    private ButtonWidget applyBtn;
    private ButtonWidget saveCloseBtn;
    private ButtonWidget defaultsBtn;

    private static class Row {
        final TextFieldWidget field;
        final ButtonWidget    delBtn;
        int lastLocalY;
        Row(TextFieldWidget f, ButtonWidget b) { this.field = f; this.delBtn = b; }
    }

    private final List<Row> systemRows = new ArrayList<>();
    private final List<Row> allowRows  = new ArrayList<>();
    private ButtonWidget addSystemBtn;
    private ButtonWidget addAllowBtn;

    private TextFieldWidget maxVisibleField;
    private TextFieldWidget displayMillisField;

    private int baseX;
    private int baseY;

    // 스크롤
    private int viewportTop;
    private int viewportHeight;
    private int scrollY = 0;
    private int contentHeight = 0;

    // 컨텐츠 폭(스크롤 기준)
    private static final int CONTENT_W  = 320; // 고정 컨텐츠 폭
    private int contentWidthPx = CONTENT_W;

    // 반반 슬라이더 너비/간격 (320 = 155 + 10 + 155)
    private static final int HALF_W   = 155;
    private static final int HALF_GAP = 10;

    // 라벨(로컬Y)
    private int titleYLocal=0, sysLabelLocal=0, allowLabelLocal=0, numbersLocal=0;
    private int posTitleLocal=0, padTitleLocal=0;

    // 정규식 오류 UI
    private Set<Integer> invalidSystemIdx = new HashSet<>();
    private Set<Integer> invalidAllowIdx  = new HashSet<>();

    public ConfigScreen(Screen parent) {
        super(Text.literal("RBChat 설정"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        baseX = this.width / 2 - 160;
        baseY = this.height / 6;

        viewportTop = baseY;
        viewportHeight = this.height - viewportTop - 24;

        /* ===== 상단 컨트롤 (라이브 프리뷰) ===== */
        toggleEnabledBtn = ButtonWidget.builder(Text.literal(labelEnabled()), b -> {
            Config.enabled = !Config.enabled;
            toggleEnabledBtn.setMessage(Text.literal(labelEnabled()));
        }).dimensions(baseX, viewportTop, 120, 20).build();
        addDrawableChild(toggleEnabledBtn);

        scaleSlider = new SimpleSlider(baseX + 130, viewportTop, 190, 20,
                Text.literal("크기: "), Config.fontScale, 0.60f, 1.20f,
                v -> Config.fontScale = v, v -> String.format("%.2f×", v));
        addDrawableChild(scaleSlider);

        bgToggleBtn = ButtonWidget.builder(Text.literal(labelBg()), b -> {
            Config.bgEnabled = !Config.bgEnabled;
            bgToggleBtn.setMessage(Text.literal(labelBg()));
        }).dimensions(baseX, viewportTop, 120, 20).build();
        addDrawableChild(bgToggleBtn);

        bgOpacitySlider = new SimpleSlider(baseX + 130, viewportTop, 190, 20,
                Text.literal("배경 투명도: "),
                Config.bgOpacityPercent / 100f, 0f, 1f,
                v -> Config.bgOpacityPercent = Math.round(v * 100),
                v -> (int)Math.round(v * 100) + "%");
        addDrawableChild(bgOpacitySlider);

        // 위치/여백(라이브 적용) — X/Y를 반반 크기(HALF_W)로, 중앙 간격 HALF_GAP 유지
        offsetXSlider = new SimpleSlider(baseX + 0, viewportTop, HALF_W, 20,
                Text.literal("X 오프셋: "),
                Config.offsetX, 0f, 200f,
                v -> Config.offsetX = Math.round(v),
                v -> String.valueOf(Math.round(v)));
        addDrawableChild(offsetXSlider);

        offsetYSlider = new SimpleSlider(baseX + HALF_W + HALF_GAP, viewportTop, HALF_W, 20,
                Text.literal("Y 오프셋: "),
                Config.offsetY, 0f, 200f,
                v -> Config.offsetY = Math.round(v),
                v -> String.valueOf(Math.round(v)));
        addDrawableChild(offsetYSlider);

        edgePaddingSlider = new SimpleSlider(baseX + 0, viewportTop, CONTENT_W, 20,
                Text.literal("가장자리 여백(px): "),
                Config.edgePadding, 0f, 32f,
                v -> Config.edgePadding = Math.round(v),
                v -> String.valueOf(Math.round(v)));
        addDrawableChild(edgePaddingSlider);

        /* ===== 리스트 초기화 ===== */
        systemRows.clear();
        allowRows.clear();
        if (Config.systemPatterns.isEmpty()) Config.systemPatterns.add("");
        if (Config.allowPatterns.isEmpty())  Config.allowPatterns.add("");

        for (String s : Config.systemPatterns) addSystemRow(s);
        for (String s : Config.allowPatterns) addAllowRow(s);

        addSystemBtn = ButtonWidget.builder(Text.literal("+"), b -> {
            addSystemRow("");
            relayout();
        }).dimensions(baseX + 260 + 4, viewportTop, 20, 20).build();
        addDrawableChild(addSystemBtn);

        addAllowBtn = ButtonWidget.builder(Text.literal("+"), b -> {
            addAllowRow("");
            relayout();
        }).dimensions(baseX + 260 + 4, viewportTop, 20, 20).build();
        addDrawableChild(addAllowBtn);

        /* ===== 숫자 입력 ===== */
        maxVisibleField = new TextFieldWidget(this.textRenderer, baseX, viewportTop, 140, 20, Text.literal("maxVisible"));
        maxVisibleField.setText(String.valueOf(Config.maxVisible));
        addDrawableChild(maxVisibleField);

        displayMillisField = new TextFieldWidget(this.textRenderer, baseX + 150, viewportTop, 170, 20, Text.literal("displayMillis"));
        displayMillisField.setText(String.valueOf(Config.displayMillis));
        addDrawableChild(displayMillisField);

        /* ===== 하단 버튼 ===== */
        applyBtn = ButtonWidget.builder(Text.literal("적용"), b -> {
            applyFromWidgets(false);       // 디스크 저장 X
            compileAndMarkErrors();        // 정규식 미리 컴파일 + 오류 표시
        }).dimensions(baseX, viewportTop, 100, 20).build();
        addDrawableChild(applyBtn);

        saveCloseBtn = ButtonWidget.builder(Text.literal("저장 후 닫기"), b -> {
            applyFromWidgets(true);        // 디스크 저장 O
            compileAndMarkErrors();
            MinecraftClient.getInstance().setScreen(parent);
        }).dimensions(baseX + 110, viewportTop, 120, 20).build();
        addDrawableChild(saveCloseBtn);

        defaultsBtn = ButtonWidget.builder(Text.literal("기본값"), b -> {
            Config.resetToDefaults();
            resetUIFromConfig();
            compileAndMarkErrors();
        }).dimensions(baseX + 240, viewportTop, 80, 20).build();
        addDrawableChild(defaultsBtn);

        compileAndMarkErrors();
        relayout();
    }

    private void resetUIFromConfig() {
        for (Row r : new ArrayList<>(systemRows)) { remove(r.field); remove(r.delBtn); }
        for (Row r : new ArrayList<>(allowRows))  { remove(r.field); remove(r.delBtn); }
        systemRows.clear();
        allowRows.clear();

        if (Config.systemPatterns.isEmpty()) Config.systemPatterns.add("");
        if (Config.allowPatterns.isEmpty())  Config.allowPatterns.add("");
        for (String s : Config.systemPatterns) addSystemRow(s);
        for (String s : Config.allowPatterns) addAllowRow(s);

        toggleEnabledBtn.setMessage(Text.literal(labelEnabled()));
        bgToggleBtn.setMessage(Text.literal(labelBg()));

        scaleSlider.setValueFromReal(Config.fontScale);
        bgOpacitySlider.setValueFromReal(Config.bgOpacityPercent / 100f);
        offsetXSlider.setValueFromReal(Config.offsetX);
        offsetYSlider.setValueFromReal(Config.offsetY);
        edgePaddingSlider.setValueFromReal(Config.edgePadding);

        maxVisibleField.setText(String.valueOf(Config.maxVisible));
        displayMillisField.setText(String.valueOf(Config.displayMillis));

        relayout();
    }

    /* ===== Row 생성/삭제 ===== */
    private void addSystemRow(String val) {
        int rowW = 260, rowH = 20;
        final TextFieldWidget tf = new TextFieldWidget(this.textRenderer, baseX, viewportTop, rowW, rowH, Text.literal("system"));
        tf.setText(val);
        addDrawableChild(tf);

        final Row[] holder = new Row[1];
        ButtonWidget del = ButtonWidget.builder(Text.literal("-"), b -> {
            if (systemRows.size() <= 1) return;
            deleteSystemRow(holder[0]);
            relayout();
        }).dimensions(baseX + rowW + 4, viewportTop, 20, rowH).build();
        addDrawableChild(del);

        Row row = new Row(tf, del);
        holder[0] = row;
        systemRows.add(row);
    }
    private void deleteSystemRow(Row row) {
        remove(row.field); remove(row.delBtn);
        systemRows.remove(row);
    }

    private void addAllowRow(String val) {
        int rowW = 260, rowH = 20;
        final TextFieldWidget tf = new TextFieldWidget(this.textRenderer, baseX, viewportTop, rowW, rowH, Text.literal("allow"));
        tf.setText(val);
        addDrawableChild(tf);

        final Row[] holder = new Row[1];
        ButtonWidget del = ButtonWidget.builder(Text.literal("-"), b -> {
            if (allowRows.size() <= 1) return;
            deleteAllowRow(holder[0]);
            relayout();
        }).dimensions(baseX + rowW + 4, viewportTop, 20, rowH).build();
        addDrawableChild(del);

        Row row = new Row(tf, del);
        holder[0] = row;
        allowRows.add(row);
    }
    private void deleteAllowRow(Row row) {
        remove(row.field); remove(row.delBtn);
        allowRows.remove(row);
    }

    /* ===== 레이아웃 ===== */
    private void relayout() {
        int x = baseX;
        int localY = 0;

        final int TITLE_LARGE = 18;
        final int CONTROL_GAP = 6;
        final int LINE_GAP    = 26;
        final int TITLE_SMALL = 12;
        final int ROW_SPACE   = 24;
        final int SECTION_GAP = 16;

        // 제목
        titleYLocal = localY;
        localY += TITLE_LARGE + CONTROL_GAP;

        // 1행: 모드, 크기
        place(toggleEnabledBtn, x, localY);
        place(scaleSlider,      x + 130, localY);
        localY += LINE_GAP;

        // 2행: 배경, 투명도
        place(bgToggleBtn,     x,        localY);
        place(bgOpacitySlider, x + 130,  localY);
        localY += LINE_GAP;

        // ===== “채팅창 위치” 라벨 → 그 아래 X/Y 슬라이더(반반 크기) =====
        posTitleLocal = localY;                    // 라벨 줄
        localY += TITLE_SMALL + 4;                 // 라벨 높이 + 여백
        place(offsetXSlider, x,                          localY);
        place(offsetYSlider, x + HALF_W + HALF_GAP,      localY);
        localY += LINE_GAP;

        // ===== “가장자리 여백(px)” 라벨 → 그 아래 전체폭 슬라이더 =====
        padTitleLocal = localY;
        localY += TITLE_SMALL + 4;
        place(edgePaddingSlider, x, localY);
        localY += LINE_GAP;

        // system 섹션
        sysLabelLocal = localY;
        localY += TITLE_SMALL;
        int rowX = x, rowW = 260;
        for (int i = 0; i < systemRows.size(); i++) {
            Row r = systemRows.get(i);
            int y = localY + i * ROW_SPACE;
            r.lastLocalY = y;
            place(r.field,  rowX,            y);
            place(r.delBtn, rowX + rowW + 4, y);
        }
        localY += systemRows.size() * ROW_SPACE;
        place(addSystemBtn, rowX + rowW + 4, localY);
        localY += SECTION_GAP;

        // allow 섹션
        allowLabelLocal = localY;
        localY += TITLE_SMALL;
        for (int i = 0; i < allowRows.size(); i++) {
            Row r = allowRows.get(i);
            int y = localY + i * ROW_SPACE;
            r.lastLocalY = y;
            place(r.field,  rowX,            y);
            place(r.delBtn, rowX + rowW + 4, y);
        }
        localY += allowRows.size() * ROW_SPACE;
        place(addAllowBtn, rowX + rowW + 4, localY);

        // 숫자 입력
        localY += 24;
        numbersLocal = localY;
        place(maxVisibleField,    x,        localY);
        place(displayMillisField, x + 150,  localY);

        // 하단 버튼
        localY += 36;
        place(applyBtn,     x,         localY);
        place(saveCloseBtn, x + 110,   localY);
        place(defaultsBtn,  x + 240,   localY);

        // 컨텐츠 크기 & 스크롤보정
        contentHeight = localY + 28;
        clampScroll();

        // 컨텐츠 폭(스크롤바 기준) — 고정 320으로 유지
        int rowsWidth   = rowW + 4 + 20; // 284
        int controlsW   = CONTENT_W;     // 320로 고정
        int numbersW    = 150 + 170;     // 320
        contentWidthPx  = Math.max(rowsWidth, Math.max(controlsW, numbersW));
    }

    private void place(ClickableWidget w, int x, int localY) {
        w.setPosition(x, viewportTop + localY - scrollY);
    }

    private void clampScroll() {
        int maxScroll = Math.max(0, contentHeight - viewportHeight);
        if (scrollY < 0) scrollY = 0;
        if (scrollY > maxScroll) scrollY = maxScroll;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (mouseY >= viewportTop && mouseY < viewportTop + viewportHeight) {
            int step = 20;
            scrollY -= (int)(amount * step);
            clampScroll();
            relayout();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public void resize(MinecraftClient client, int width, int height) {
        super.resize(client, width, height);
        baseX = width / 2 - 160;
        baseY = height / 6;
        viewportTop = baseY;
        viewportHeight = height - viewportTop - 24;
        clampScroll();
        relayout();
    }

    /* ===== 적용/저장 & 컴파일 ===== */
    private void applyFromWidgets(boolean saveToDisk) {
        Config.systemPatterns = collectTexts(systemRows);
        Config.allowPatterns  = collectTexts(allowRows);

        Config.maxVisible    = parseIntSafe(maxVisibleField.getText(), 10, 1, 40);
        Config.displayMillis = parseIntSafe(displayMillisField.getText(), 3000, 500, 20000);

        if (saveToDisk) Config.saveCurrent();
    }

    private void compileAndMarkErrors() {
        Config.compilePatternsFromCurrent();
        invalidSystemIdx = new HashSet<>(Config.invalidSystemIdx);
        invalidAllowIdx  = new HashSet<>(Config.invalidAllowIdx);
    }

    private static List<String> collectTexts(List<Row> rows) {
        List<String> out = new ArrayList<>();
        for (Row r : rows) {
            String s = r.field.getText();
            if (s != null) {
                s = s.trim();
                if (!s.isEmpty()) out.add(s);
            }
        }
        return out;
    }

    private static int parseIntSafe(String s, int def, int min, int max) {
        try {
            int v = Integer.parseInt(s.trim());
            if (v < min) v = min;
            if (v > max) v = max;
            return v;
        } catch (Exception e) {
            return def;
        }
    }

    private String labelEnabled() { return "모드: " + (Config.enabled ? "켜짐" : "꺼짐"); }
    private String labelBg()      { return "배경: " + (Config.bgEnabled ? "켜짐" : "꺼짐"); }

    /* ===== 렌더 ===== */
    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx);
        int x = baseX;

        ctx.drawText(textRenderer, "사이드 HUD 필터링 옵션", x, viewportTop + (titleYLocal - scrollY), 0xFFFFFF, false);

        // 라벨들은 슬라이더 바로 위에 그려서 겹침 제거
        ctx.drawText(textRenderer, "채팅창 위치",      x, viewportTop + (posTitleLocal - scrollY), 0xAAAAAA, false);
        ctx.drawText(textRenderer, "가장자리 여백(px)", x, viewportTop + (padTitleLocal - scrollY), 0xAAAAAA, false);

        ctx.drawText(textRenderer, "숨길 패턴(system):", x, viewportTop + (sysLabelLocal - scrollY), 0xFFFFFF, false);
        ctx.drawText(textRenderer, "허용 패턴(allow):",  x, viewportTop + (allowLabelLocal - scrollY), 0xFFFFFF, false);

        int numbersY = viewportTop + (numbersLocal - scrollY);
        ctx.drawText(textRenderer, "maxVisible",        x,        numbersY - 12, 0xAAAAAA, false);
        ctx.drawText(textRenderer, "displayMillis(ms)", x + 150,  numbersY - 12, 0xAAAAAA, false);

        // 정규식 오류 경고
        int warnColor = 0xFFDD5555;
        for (int i = 0; i<systemRows.size(); i++) {
            if (invalidSystemIdx.contains(i)) {
                Row r = systemRows.get(i);
                int yy = viewportTop + (r.lastLocalY - scrollY);
                ctx.drawText(textRenderer, "정규식 오류", x + 260 + 28, yy + 6, warnColor, false);
            }
        }
        for (int i = 0; i<allowRows.size(); i++) {
            if (invalidAllowIdx.contains(i)) {
                Row r = allowRows.get(i);
                int yy = viewportTop + (r.lastLocalY - scrollY);
                ctx.drawText(textRenderer, "정규식 오류", x + 260 + 28, yy + 6, warnColor, false);
            }
        }

        drawScrollbar(ctx);
        super.render(ctx, mouseX, mouseY, delta);
    }

    private void drawScrollbar(DrawContext ctx) {
        if (contentHeight <= viewportHeight) return;
        int barW = 4;
        int trackX = baseX + contentWidthPx + 8;
        int trackY0 = viewportTop;
        int trackY1 = viewportTop + viewportHeight;

        int trackColor = 0x44000000;
        ctx.fill(trackX, trackY0, trackX + barW, trackY1, trackColor);

        float ratio = viewportHeight / (float) contentHeight;
        int handleH = Math.max(24, Math.round(viewportHeight * ratio));
        int maxScroll = contentHeight - viewportHeight;
        int handleY = (maxScroll == 0) ? trackY0
                : trackY0 + Math.round((viewportHeight - handleH) * (scrollY / (float) maxScroll));
        int handleColor = 0x88FFFFFF;
        ctx.fill(trackX, handleY, trackX + barW, handleY + handleH, handleColor);
    }

    @Override
    public boolean shouldPause() { return false; }

    /* ===== 간단 슬라이더 ===== */
    private static class SimpleSlider extends SliderWidget {
        private final float min, max;
        private final Consumer<Float> apply;
        private final Function<Float, String> labelFmt;
        private final String prefix;

        public SimpleSlider(int x, int y, int width, int height,
                            Text prefix, float currentValue, float min, float max,
                            Consumer<Float> apply, Function<Float, String> labelFmt) {
            super(x, y, width, height, prefix, 0.0);
            this.min = min; this.max = max; this.apply = apply; this.labelFmt = labelFmt;
            this.prefix = prefix.getString();
            setValueFromReal(currentValue);
            updateMessage();
        }

        void setValueFromReal(float real) {
            double norm = (real - min) / (max - min);
            this.value = Math.max(0.0, Math.min(1.0, norm));
            updateMessage();
        }

        private float getReal() {
            return (float)(min + value * (max - min));
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal(prefix + labelFmt.apply(getReal())));
        }

        @Override
        protected void applyValue() {
            apply.accept(getReal()); // 라이브 프리뷰: 즉시 Config 반영
        }
    }
}
