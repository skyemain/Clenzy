package com.skyemain.clenzy;
import android.content.Context;
import android.graphics.*;
import android.view.View;
public final class StorageRing extends View {
    private final Paint paint=new Paint(3);private final float fraction;private final int track,accent;
    public StorageRing(Context context,float fraction,int track,int accent){super(context);this.fraction=Math.max(0,Math.min(1,fraction));this.track=track;this.accent=accent;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float stroke=8*getResources().getDisplayMetrics().density;float pad=stroke;RectF rect=new RectF(pad,pad,getWidth()-pad,getHeight()-pad);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(stroke);paint.setStrokeCap(Paint.Cap.ROUND);paint.setColor(track);canvas.drawArc(rect,0,360,false,paint);paint.setColor(accent);canvas.drawArc(rect,-90,360*fraction,false,paint);}
}
