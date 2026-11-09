package com.skyemain.clenzylite;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Small visual marks inside full 48dp accessible touch targets. */
final class CompactControl extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final boolean undo;
    private boolean marked;
    private int count;

    CompactControl(Context context, boolean undo) {
        super(context);
        this.undo = undo;
        setFocusable(true);
        setClickable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    void setMarked(boolean value) { marked = value; invalidate(); }
    void setCount(int value) { count = value; invalidate(); }
    @Override public CharSequence getAccessibilityClassName() { return android.widget.Button.class.getName(); }
    @Override protected void drawableStateChanged() { super.drawableStateChanged(); invalidate(); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        canvas.scale(getWidth() / 48f, getHeight() / 48f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(isPressed() ? 0xff443a5b : 0xff24212d);
        if (undo || isPressed() || isFocused()) canvas.drawCircle(24, 24, 17, paint);
        paint.setStrokeWidth(1.8f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(isEnabled() ? marked ? 0xffdfd2ff : Color.WHITE : 0xffaaa5b5);
        paint.setStyle(Paint.Style.STROKE);
        if (undo) {
            canvas.drawArc(16, 17, 32, 33, -140, 290, false, paint);
            Path arrow = new Path();
            arrow.moveTo(16, 14); arrow.lineTo(16, 21); arrow.lineTo(23, 21);
            canvas.drawPath(arrow, paint);
        } else {
            Path star = new Path();
            for (int i = 0; i < 10; i++) {
                double angle = Math.PI * i / 5 - Math.PI / 2;
                float radius = i % 2 == 0 ? 11 : 5;
                float x = 24 + (float) Math.cos(angle) * radius;
                float y = 24 + (float) Math.sin(angle) * radius;
                if (i == 0) star.moveTo(x, y); else star.lineTo(x, y);
            }
            star.close();
            paint.setShadowLayer(2, 0, 1, 0xff20202a);
            if (marked) paint.setStyle(Paint.Style.FILL);
            canvas.drawPath(star, paint);
            paint.clearShadowLayer();
        }
        if (undo && count > 0) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xff24212d); canvas.drawCircle(38, 10, 9, paint);
            paint.setColor(0xffd6caff); canvas.drawCircle(38, 10, 7.5f, paint);
            paint.setColor(0xff21162f); paint.setTextSize(10); paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(Integer.toString(count), 38, 13.5f, paint);
        }
        canvas.restore();
    }
}
