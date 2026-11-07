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
    public static Drawable glass(Context context,boolean ai){return new Drawable(){
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        @Override public void draw(Canvas canvas){
            RectF r=new RectF(getBounds());r.inset(1,1);float radius=dp(context,32);
            p.setStyle(Paint.Style.FILL);p.setShader(new LinearGradient(r.left,r.top,r.right,r.bottom,
                ai?new int[]{0x664f5996,0x38352e68,0x88202b4a}:new int[]{0x665a9eac,0x382e5074,0x88182640},null,Shader.TileMode.CLAMP));
            canvas.drawRoundRect(r,radius,radius,p);
            p.setShader(new LinearGradient(r.left,r.top,r.left,r.bottom,new int[]{0x22ffffff,0x00ffffff},new float[]{0,0.55f},Shader.TileMode.CLAMP));canvas.drawRoundRect(r,radius,radius,p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(context,1));p.setShader(new LinearGradient(r.left,r.top,r.right,r.bottom,new int[]{0x99d1faff,0x225c7ba8,0x556c92c4},null,Shader.TileMode.CLAMP));canvas.drawRoundRect(r,radius,radius,p);p.setShader(null);
        }
        @Override public void getOutline(Outline outline){outline.setRoundRect(getBounds(),dp(context,32));}
        @Override public void setAlpha(int a){} @Override public void setColorFilter(ColorFilter f){} @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    };}
    public static void button(MaterialButton b){
        b.setAllCaps(false);b.setMinHeight(dp(b.getContext(),48));b.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{MUTED,INK}));
        b.setBackgroundTintList(null);b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x4477e8ff),glass(b.getContext(),false),null));
        b.setPadding(dp(b.getContext(),12),dp(b.getContext(),8),dp(b.getContext(),12),dp(b.getContext(),8));
    }
    public static android.widget.ImageButton iconButton(Context c,String icon,String description,Runnable action){
        android.widget.ImageButton b=new android.widget.ImageButton(c);b.setImageDrawable(new LineIcon(icon,INK));b.setContentDescription(description);b.setPadding(dp(c,12),dp(c,12),dp(c,12),dp(c,12));b.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x3377e8ff),null,null));b.setOnClickListener(v->action.run());return b;
    }
    public static Drawable symbol(boolean ai){return new Drawable(){
        final Paint p=new Paint(3);
        @Override public void draw(Canvas c){c.save();Rect b=getBounds();c.translate(b.left,b.top);c.scale(b.width()/100f,b.height()/100f);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);p.setStrokeCap(Paint.Cap.ROUND);p.setColor(ai?0xffc5b5ff:ACCENT);
            if(ai){c.save();c.rotate(-30,50,50);c.drawOval(9,28,91,72,p);c.rotate(60,50,50);c.drawOval(9,28,91,72,p);c.restore();p.setStyle(Paint.Style.FILL);Path star=new Path();star.moveTo(50,26);star.quadTo(53,47,73,50);star.quadTo(53,53,50,74);star.quadTo(47,53,27,50);star.quadTo(47,47,50,26);c.drawPath(star,p);}
            else{for(int i=0;i<4;i++){c.save();c.rotate(i*90,50,50);Path q=new Path();q.moveTo(17,38);q.lineTo(17,27);q.quadTo(17,17,27,17);q.lineTo(38,17);c.drawPath(q,p);c.restore();}p.setShader(new LinearGradient(10,50,90,50,new int[]{0x0077e8ff,ACCENT,0x0077e8ff},null,Shader.TileMode.CLAMP));c.drawLine(10,50,90,50,p);p.setShader(null);}c.restore();}
        @Override public void setAlpha(int a){} @Override public void setColorFilter(ColorFilter f){} @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    };}
}
