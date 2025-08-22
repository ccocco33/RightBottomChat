package com.example.rbchat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class ConfigScreen extends Screen {
    private final Screen parent;

    // 스크롤에 포함되는 컨트롤들 (전부 스크롤 대상!)
    private ButtonWidget toggleEnabledBtn;
    private ButtonWidget bgToggleBtn;
    private SimpleSlider  scaleSlider;
    private SimpleSlider  bgOpacitySlider;

    private ButtonWidget saveBtn;
    private ButtonWidget cancelBtn;

    private static class Row {
        final TextFieldWidget field;
        final ButtonWidget    delBtn;
        Row(TextFieldWidget f, ButtonWidget b) { this.field = f; this.delBtn = b; }
    }

    private final List<Row> systemRows = new ArrayList<>();
    private final List<Row> allowRows  = new ArrayList<>();
    private ButtonWidget addSystemBtn;
    private ButtonWidget addAllowBtn;

    private TextFieldWidget maxVisibleField;
    private TextFieldWidget displayMillisField;

    // 레이아웃 기준
    private int baseX;
    private int baseY;

    // 스크롤 영역(가로는 고정, 세로만 스크롤)
    private int viewportTop;         // 스크롤 시작 Y (== baseY)
    private int viewportHeight;      // 스크롤 가능한 높이
    private int scrollY = 0;         // 현재 스크롤 값
    private int contentHeight = 0;   // 컨텐츠 총높이
    private int contentWidthPx = 320; // 컨텐츠 최대 가로폭(스크롤바 기준) — 동적으로 계산

    // 라벨용 로컬Y 저장
    private int lastTitleLocalY = 0;
    private int lastSystemTitleLocalY = 0;
    private int lastAllowTitleLocalY  = 0;
    private int lastNumbersLocalY     = 0;

    public ConfigScreen(Screen parent) {
        super(Text.literal("RBChat 설정"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        baseX = this.width / 2 - 160;
        baseY = this.height / 6;

        // 스크롤 영역을 화면 상단 기준으로 설정
        viewportTop = baseY;
        viewportHeight = this.height - viewportTop - 24;

        // ===== 스크롤 대상 위젯 생성 (초기 위치는 임시, relayout에서 재배치) =====
        toggleEnabledBtn = ButtonWidget.builder(Text.literal(labelEnabled()), btn -> {
            Config.enabled = !Config.enabled;
            toggleEnabledBtn.setMessage(Text.literal(labelEnabled()));
        }).dimensions(baseX, viewportTop, 120, 20).build();
        addDrawableChild(toggleEnabledBtn);

        scaleSlider = new SimpleSlider(baseX + 130, viewportTop, 190, 20,
                Text.literal("크기: "), Config.fontScale, 0.60f, 1.20f,
                v -> Config.fontScale = v, this::formatScale);
        addDrawableChild(scaleSlider);

        bgToggleBtn = ButtonWidget.builder(Text.literal(labelBg()), btn -> {
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

        maxVisibleField = new TextFieldWidget(this.textRenderer, baseX, viewportTop, 140, 20, Text.literal("maxVisible"));
        maxVisibleField.setText(String.valueOf(Config.maxVisible));
        addDrawableChild(maxVisibleField);

        displayMillisField = new TextFieldWidget(this.textRenderer, baseX + 150, viewportTop, 170, 20, Text.literal("displayMillis"));
        displayMillisField.setText(String.valueOf(Config.displayMillis));
        addDrawableChild(displayMillisField);

        saveBtn = ButtonWidget.builder(Text.literal("저장"), btn -> {
            applyAndSave();
            MinecraftClient.getInstance().setScreen(parent);
        }).dimensions(baseX, viewportTop, 140, 20).build();
        addDrawableChild(saveBtn);

        cancelBtn = ButtonWidget.builder(Text.literal("취소"), btn -> {
            MinecraftClient.getInstance().setScreen(parent);
        }).dimensions(baseX + 150, viewportTop, 170, 20).build();
        addDrawableChild(cancelBtn);

        relayout();
    }

    // ===== Row 생성/삭제 =====
    private void addSystemRow(String val) {
        int rowW = 260, rowH = 20;
        final TextFieldWidget tf = new TextFieldWidget(this.textRenderer, baseX, viewportTop, rowW, rowH, Text.literal("system"));
        tf.setText(val);
        addDrawableChild(tf);

        final Row[] holder = new Row[1];
        ButtonWidget del = ButtonWidget.builder(Text.literal("-"), b -> {
            if (systemRows.size() <= 1) return; // 최소 1줄 보존
            deleteSystemRow(holder[0]);
            relayout();
        }).dimensions(baseX + rowW + 4, viewportTop, 20, rowH).build();
        addDrawableChild(del);

        Row row = new Row(tf, del);
        holder[0] = row;
        systemRows.add(row);
    }

    private void deleteSystemRow(Row row) {
        this.remove(row.field);
        this.remove(row.delBtn);
        systemRows.remove(row);
    }

    private void addAllowRow(String val) {
        int rowW = 260, rowH = 20;
        final TextFieldWidget tf = new TextFieldWidget(this.textRenderer, baseX, viewportTop, rowW, rowH, Text.literal("allow"));
        tf.setText(val);
        addDrawableChild(tf);

        final Row[] holder = new Row[1];
        ButtonWidget del = ButtonWidget.builder(Text.literal("-"), b -> {
            if (allowRows.size() <= 1) return; // 최소 1줄 보존
            deleteAllowRow(holder[0]);
            relayout();
        }).dimensions(baseX + rowW + 4, viewportTop, 20, rowH).build();
        addDrawableChild(del);

        Row row = new Row(tf, del);
        holder[0] = row;
        allowRows.add(row);
    }

    private void deleteAllowRow(Row row) {
        this.remove(row.field);
        this.remove(row.delBtn);
        allowRows.remove(row);
    }

    // ===== 스크롤 재배치 =====
    private void relayout() {
        int x = baseX;
        int localY = 0;

        final int TITLE_LARGE  = 18; // 제목 줄 높이
        final int CONTROL_GAP  = 6;  // 제목과 컨트롤 사이 여백
        final int LINE_GAP     = 26; // 컨트롤 라인 간격
        final int TITLE_SMALL  = 12; // 섹션 라벨 높이
        final int ROW_SPACING  = 24; // 리스트 한 줄 간격
        final int SECTION_GAP  = 16; // 섹션 사이 여백

        // 제목(스크롤 포함)
        int titleLocalY = localY;               // "사이드 HUD 필터링 옵션"
        localY += TITLE_LARGE + CONTROL_GAP;

        // 1줄: 모드 토글 + 크기 슬라이더
        place(toggleEnabledBtn, x, localY);
        place(scaleSlider,      x + 130, localY);
        localY += LINE_GAP;

        // 2줄: 배경 토글 + 배경 투명도 슬라이더
        place(bgToggleBtn,    x, localY);
        place(bgOpacitySlider, x + 130, localY);
        localY += LINE_GAP;

        // system 라벨
        int systemTitleLocalY = localY;
        localY += TITLE_SMALL;

        // system 리스트
        int rowX = x, rowW = 260;
        for (int i = 0; i < systemRows.size(); i++) {
            Row r = systemRows.get(i);
            int rowLocalY = localY + i * ROW_SPACING;
            place(r.field,  rowX,            rowLocalY);
            place(r.delBtn, rowX + rowW + 4, rowLocalY);
        }
        localY += systemRows.size() * ROW_SPACING;

        // + 시스템
        place(addSystemBtn, rowX + rowW + 4, localY);

        // 섹션 간 여백
        localY += SECTION_GAP;

        // allow 라벨
        int allowTitleLocalY = localY;
        localY += TITLE_SMALL;

        // allow 리스트
        for (int i = 0; i < allowRows.size(); i++) {
            Row r = allowRows.get(i);
            int rowLocalY = localY + i * ROW_SPACING;
            place(r.field,  rowX,            rowLocalY);
            place(r.delBtn, rowX + rowW + 4, rowLocalY);
        }
        localY += allowRows.size() * ROW_SPACING;

        // + 허용
        place(addAllowBtn, rowX + rowW + 4, localY);

        // 숫자 입력(스크롤 포함)
        localY += 24;
        int numbersLocalY = localY;
        place(maxVisibleField,   x,        numbersLocalY);
        place(displayMillisField, x + 150, numbersLocalY);

        // 저장/취소(스크롤 포함)
        localY += 36;
        place(saveBtn,   x,        localY);
        place(cancelBtn, x + 150,  localY);

        // 컨텐츠 높이 및 스크롤 보정
        contentHeight = localY + 28;
        clampScroll();

        // 컨텐츠 가로폭 계산(스크롤바 기준점)
        // - 행: 260 + 4 + 20 = 284
        // - 컨트롤/숫자: 오른쪽 끝이 x + 130 + 190 = x + 320 (숫자필드도 150+170=320)
        int rowsWidth   = rowW + 4 + 20; // 284
        int controlsW   = 130 + 190;     // 320
        int numbersW    = 150 + 170;     // 320
        contentWidthPx  = Math.max(rowsWidth, Math.max(controlsW, numbersW)); // = 320

        // 라벨용 로컬Y 저장
        lastTitleLocalY       = titleLocalY;
        lastSystemTitleLocalY = systemTitleLocalY;
        lastAllowTitleLocalY  = allowTitleLocalY;
        lastNumbersLocalY     = numbersLocalY;
    }

    private void place(ClickableWidget w, int x, int localY) {
        w.setPosition(x, viewportTop + localY - scrollY);
        // 폭은 생성 시 지정된 값 유지 (필요 시 setWidth 호출)
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
        viewportTop = baseY; // 상단 컨트롤도 스크롤 포함
        viewportHeight = height - viewportTop - 24;
        clampScroll();
        relayout();
    }

    private String labelEnabled() { return "모드: " + (Config.enabled ? "켜짐" : "꺼짐"); }
    private String labelBg()      { return "배경: " + (Config.bgEnabled ? "켜짐" : "꺼짐"); }
    private String formatScale(float v) { return String.format("%.2f×", v); }

    private void applyAndSave() {
        Config.systemPatterns = collectTexts(systemRows);
        Config.allowPatterns  = collectTexts(allowRows);
        Config.maxVisible     = parseIntSafe(maxVisibleField.getText(), 10, 1, 40);
        Config.displayMillis  = parseIntSafe(displayMillisField.getText(), 3000, 500, 20000);
        Config.saveCurrent();
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

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx);
        int x = baseX;

        // 제목/라벨(스크롤 적용)
        int titleY = viewportTop + (lastTitleLocalY - scrollY);
        ctx.drawText(textRenderer, "사이드 HUD 필터링 옵션", x, titleY, 0xFFFFFF, false);

        int sysLabelY = viewportTop + (lastSystemTitleLocalY - scrollY);
        ctx.drawText(textRenderer, "숨길 패턴(system):", x, sysLabelY, 0xFFFFFF, false);

        int allowLabelY = viewportTop + (lastAllowTitleLocalY - scrollY);
        ctx.drawText(textRenderer, "허용 패턴(allow):", x, allowLabelY, 0xFFFFFF, false);

        int numbersY = viewportTop + (lastNumbersLocalY - scrollY);
        ctx.drawText(textRenderer, "maxVisible",        x,        numbersY - 12, 0xAAAAAA, false);
        ctx.drawText(textRenderer, "displayMillis(ms)", x + 150,  numbersY - 12, 0xAAAAAA, false);

        drawScrollbar(ctx);
        super.render(ctx, mouseX, mouseY, delta);
    }

    private void drawScrollbar(DrawContext ctx) {
        if (contentHeight <= viewportHeight) return;
        int barW = 4;
        // 스크롤 트랙은 "컨텐츠 최대 가로폭" 오른쪽에 그린다 → 컨텐츠가 트랙을 넘지 않음
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

    /* ===== 간단 슬라이더 위젯 ===== */
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

        private void setValueFromReal(float real) {
            double norm = (real - min) / (max - min);
            this.value = Math.max(0.0, Math.min(1.0, norm));
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
            apply.accept(getReal());
        }
    }
}
