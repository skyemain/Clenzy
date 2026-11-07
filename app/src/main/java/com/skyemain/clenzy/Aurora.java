package com.skyemain.clenzy;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.View;
import com.google.android.material.button.MaterialButton;

public final class Aurora {
    public static final int INK=0xfff3f7ff, MUTED=0xffb6c8e2, BASE=0xff080f25, SURFACE=0xff172344, ACCENT=0xff77e8ff;
    private Aurora(){}
    public static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    public static void window(Activity a){a.getWindow().setStatusBarColor(BASE);a.getWindow().setNavigationBarColor(BASE);a.getWindow().getDecorView().setSystemUiVisibility(0);}
    public static Drawable background(){return new Drawable(){
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        @Override public void draw(Canvas c){Rect b=getBounds();float w=b.width(),h=b.height();c.drawColor(BASE);
            paint.setShader(new RadialGradient(w*.12f,h*.18f,w*.85f,new int[]{0xff104f6c,0x00080f25},null,Shader.TileMode.CLAMP));c.drawRect(b,paint);
            paint.setShader(new RadialGradient(w*.95f,h*.55f,w*.9f,new int[]{0xff392561,0x00080f25},null,Shader.TileMode.CLAMP));c.drawRect(b,paint);paint.setShader(null);
        }
        @Override public void setAlpha(int a){} @Override public void setColorFilter(ColorFilter f){} @Override public int getOpacity(){return PixelFormat.OPAQUE;}
    };}
    public static GradientDrawable glass(Context c,boolean ai){GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,ai?new int[]{0xe62a426f,0xe633255c,0xee111d39}:new int[]{0xe61a5268,0xe620355b,0xee15213c});d.setCornerRadius(dp(c,28));d.setStroke(dp(c,1),ai?0x887f8eff:0x8866dce9);return d;}
    public static void button(MaterialButton b){b.setAllCaps(false);b.setCornerRadius(dp(b.getContext(),18));b.setMinHeight(dp(b.getContext(),48));b.setTextColor(INK);b.setBackgroundTintList(ColorStateList.valueOf(SURFACE));b.setStrokeWidth(dp(b.getContext(),1));b.setStrokeColor(ColorStateList.valueOf(0xff41567c));b.setRippleColor(ColorStateList.valueOf(0x4477e8ff));}
    public static Drawable symbol(boolean ai){return new Drawable(){
        final Paint p=new Paint(3);
        @Override public void draw(Canvas c){c.save();Rect b=getBounds();c.translate(b.left,b.top);c.scale(b.width()/100f,b.height()/100f);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);p.setStrokeCap(Paint.Cap.ROUND);p.setColor(ai?0xffc5b5ff:ACCENT);
            if(ai){c.save();c.rotate(-30,50,50);c.drawOval(9,28,91,72,p);c.rotate(60,50,50);c.drawOval(9,28,91,72,p);c.restore();p.setStyle(Paint.Style.FILL);Path star=new Path();star.moveTo(50,26);star.quadTo(53,47,73,50);star.quadTo(53,53,50,74);star.quadTo(47,53,27,50);star.quadTo(47,47,50,26);c.drawPath(star,p);}
            else{for(int i=0;i<4;i++){c.save();c.rotate(i*90,50,50);Path q=new Path();q.moveTo(17,38);q.lineTo(17,27);q.quadTo(17,17,27,17);q.lineTo(38,17);c.drawPath(q,p);c.restore();}p.setShader(new LinearGradient(10,50,90,50,new int[]{0x0077e8ff,ACCENT,0x0077e8ff},null,Shader.TileMode.CLAMP));c.drawLine(10,50,90,50,p);p.setShader(null);}c.restore();}
        @Override public void setAlpha(int a){} @Override public void setColorFilter(ColorFilter f){} @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    };}
}
