package com.pokermc.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Bang! Settings - Language (EN/VI).
 * English / Vietnamese sub below for editing.
 */
public class BangSettingsScreen extends Screen {

    private final Screen parent;

    public BangSettingsScreen(Screen parent) {
        super(Text.literal("Bang Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int cy = height / 2 - 20;
        int btnW = 80;
        int btnH = 20;
        int gap = 10;

        addDrawableChild(ButtonWidget.builder(
                Text.literal(BangLang.get() == BangLang.Lang.EN ? "English ✓" : "English"),
                b -> { BangLang.set(BangLang.Lang.EN); init(); })
                .dimensions(cx - btnW - gap/2, cy - btnH/2, btnW, btnH).build());

        addDrawableChild(ButtonWidget.builder(
                Text.literal(BangLang.get() == BangLang.Lang.VI ? "Tiếng Việt ✓" : "Tiếng Việt"),
                b -> { BangLang.set(BangLang.Lang.VI); init(); })
                .dimensions(cx + gap/2, cy - btnH/2, btnW, btnH).build());

        addDrawableChild(ButtonWidget.builder(Text.literal(BangLang.tr("Back", "Quay lại")), b -> close())
                .dimensions(cx - 40, cy + 40, 80, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx, mouseX, mouseY, delta);
        int cx = width / 2;
        int cy = height / 2 - 50;
        String title = BangLang.tr("Language", "Ngôn ngữ");
        ctx.drawCenteredTextWithShadow(textRenderer, title, cx, cy, 0xFFFFFF);
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
