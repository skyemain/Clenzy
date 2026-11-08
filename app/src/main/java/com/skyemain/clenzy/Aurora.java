package com.skyemain.clenzy;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.View;
import com.google.android.material.button.MaterialButton;

public final class Aurora {
    public static final int INK=0xfff7f5fb, MUTED=0xffa4a1af, BASE=0xff101014, SURFACE=0xff1b1a22, ACCENT=0xffc1b6ff;
    private Aurora(){}
    public static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    public static void window(Activity a){a.getWindow().setStatusBarColor(BASE);a.getWindow().setNavigationBarColor(BASE);a.getWindow().getDecorView().setSystemUiVisibility(0);}
    public static Drawable background(){return new ColorDrawable(BASE);}
    public static GradientDrawable surface(Context context,int color,int radius) {
        GradientDrawable shape=new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(context,radius));
        return shape;
    }
    public static Drawable glass(Context context,boolean ai){
        return surface(context,ai?0xff242033:SURFACE,20);
    }
    public static Drawable panel(Context context,boolean ai){return glass(context,ai);}
    public static void button(MaterialButton b){button(b,false);}
    public static void button(MaterialButton b,boolean primary){
        Context context=b.getContext();
        b.setAllCaps(false);
        b.setMinHeight(dp(context,48));
        b.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{MUTED,primary?0xff141026:INK}));
        b.setBackgroundTintList(null);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x447e67ba),surface(context,primary?ACCENT:0xff25232d,13),null));
        b.setPadding(dp(context,12),dp(context,8),dp(context,12),dp(context,8));
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
