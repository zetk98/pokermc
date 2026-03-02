package com.pokermc.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * Helper for smooth card animations: flip (rotate reveal) and deal (slide from deck).
 */
public final class CardAnimationHelper {

    public static final int FLIP_DURATION_MS = 280;
    public static final int DEAL_DURATION_MS = 380;
    public static final int DISCARD_DURATION_MS = 350;

    /** Ease-out cubic for flip: fast start, slow end */
    public static float easeOutCubic(float t) {
        return 1f - (float) Math.pow(1 - t, 3);
    }

    /** Ease-out for deal: smooth deceleration at end */
    public static float easeOutQuad(float t) {
        return 1f - (1 - t) * (1 - t);
    }

    /**
     * Draw card with flip animation (back → face).
     * @param progress 0=back, 1=face (use easeOutCubic for smooth feel)
     * @param regionW  width of card in atlas (usually 22)
     * @param regionH  height of card in atlas (usually 32)
     */
    public static void drawCardWithFlip(DrawContext ctx, int x, int y, int w, int h,
                                         Identifier texBack, Identifier texAtlas,
                                         int atlasU, int atlasV, int atlasW, int atlasH,
                                         int regionW, int regionH, float progress) {
        float eased = easeOutCubic(progress);
        // Scale X simulates flip: 1 → 0 → 1 (at 0.5 we show "edge")
        float scaleX = 1f - 2f * Math.abs(eased - 0.5f);
        scaleX = Math.max(0.02f, scaleX);

        MatrixStack ms = ctx.getMatrices();
        ms.push();
        ms.translate(x + w / 2f, y + h / 2f, 0);
        ms.scale(scaleX, 1f, 1f);
        ms.translate(-w * scaleX / 2f, -h / 2f, 0);

        if (eased < 0.5f) {
            ctx.drawTexture(texBack, 0, 0, w, h, 0, 0, regionW, regionH, regionW, regionH);
        } else {
            ctx.drawTexture(texAtlas, 0, 0, w, h, (float) atlasU, (float) atlasV, regionW, regionH, atlasW, atlasH);
        }
        ms.pop();
    }

    /**
     * Draw card at interpolated position (deal animation).
     */
    public static void drawCardAt(DrawContext ctx, float x, float y, int w, int h,
                                   Identifier tex, int u, int v, int texW, int texH) {
        ctx.drawTexture(tex, (int) x, (int) y, u, v, w, h, texW, texH);
    }

    public static void drawCardBackAt(DrawContext ctx, float x, float y, int w, int h, Identifier texBack) {
        ctx.drawTexture(texBack, (int) x, (int) y, 0, 0, w, h, w, h);
    }

    /** Get flip progress 0..1 from start time */
    public static float getFlipProgress(long startTimeMs) {
        long elapsed = System.currentTimeMillis() - startTimeMs;
        return MathHelper.clamp((float) elapsed / FLIP_DURATION_MS, 0, 1);
    }

    /** Get deal progress 0..1 from start time */
    public static float getDealProgress(long startTimeMs) {
        long elapsed = System.currentTimeMillis() - startTimeMs;
        return MathHelper.clamp((float) elapsed / DEAL_DURATION_MS, 0, 1);
    }

    /** Get discard (fold) progress 0..1 from start time */
    public static float getDiscardProgress(long startTimeMs) {
        long elapsed = System.currentTimeMillis() - startTimeMs;
        return MathHelper.clamp((float) elapsed / DISCARD_DURATION_MS, 0, 1);
    }

    /** Lerp with easing */
    public static float lerpEased(float from, float to, float t, boolean useEaseOut) {
        float p = useEaseOut ? easeOutQuad(t) : t;
        return from + (to - from) * p;
    }
}
