package com.example.tunas.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import com.example.tunas.R;

public final class BotanicalBackground extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Bitmap leaf;
    private final boolean welcome;
    private final boolean home;
    private final int cream;
    public BotanicalBackground(Context context, String screen) {
        super(context);
        welcome = screen.equals("welcome"); home = screen.equals("home");
        leaf = BitmapFactory.decodeResource(getResources(), welcome ? R.drawable.figma_leaf_welcome
                : home ? R.drawable.figma_leaf_home : R.drawable.figma_leaf_plants);
        cream = context.getColor(R.color.tunas_cream);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    @Override protected void onDraw(Canvas canvas) {
        canvas.drawColor(cream);
        float scale = getWidth() / 390f;
        canvas.save(); canvas.scale(scale, scale);
        float height = getHeight() / scale;
        paint.setColor(0x6BA2B595); paint.setStrokeWidth(1);
        paint.setPathEffect(new DashPathEffect(new float[]{15, 16}, 0));
        for (int x : new int[]{125, 262}) canvas.drawLine(x, 66, x, height - 66, paint);
        for (int y : new int[]{224, 361, 498}) canvas.drawLine(6, y, 390, y, paint);
        paint.setPathEffect(null); paint.setColor(0xFFFFFFFF);
        canvas.save();
        if (welcome) {
            canvas.translate(225.7f, 141); canvas.rotate(-60.84f);
            canvas.drawBitmap(leaf, null, new RectF(-285.639f, -300.67f, 285.639f, 300.67f), paint);
        } else if (home) {
            canvas.translate(430.44f, 228.97f); canvas.rotate(-12.13f);
            canvas.drawBitmap(leaf, null, new RectF(-285.639f, -300.67f, 285.639f, 300.67f), paint);
        } else {
            canvas.translate(308.4f, 372.7f); canvas.rotate(12.13f); canvas.scale(1, -1);
            canvas.drawBitmap(leaf, null, new RectF(-285.639f, -305.53f, 285.639f, 305.53f), paint);
        }
        canvas.restore();
        paint.setShader(new LinearGradient(0, 66, 0, 280, cream, 0x00EFEECF, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, 390, 316, paint); paint.setShader(null);
        paint.setShader(new LinearGradient(0, height - 250, 0, height, new int[]{0x00454D39, 0x00454D39, 0xFF454D39}, new float[]{0, .24f, 1}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, height - 250, 390, height, paint); paint.setShader(null);
        canvas.restore();
    }
}
