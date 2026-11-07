package com.skyemain.clenzy;
import android.graphics.*;
import android.graphics.drawable.Drawable;
public final class LineIcon extends Drawable {
 private final String kind;private final Paint paint=new Paint(3);
 public LineIcon(String kind,int color){this.kind=kind;paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.7f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);}
 private void line(Canvas c,float... points){Path p=new Path();p.moveTo(points[0],points[1]);for(int n=2;n<points.length;n+=2)p.lineTo(points[n],points[n+1]);c.drawPath(p,paint);}
 @Override public void draw(Canvas c){c.save();c.translate(getBounds().left,getBounds().top);c.scale(getBounds().width()/24f,getBounds().height()/24f);
 switch(kind){
 case "back":line(c,14,5,7,12,14,19);break;
 case "settings":c.drawCircle(12,12,6,paint);c.drawCircle(12,12,2,paint);for(int n=0;n<8;n++){double a=n*Math.PI/4;c.drawLine(12+(float)Math.cos(a)*8,12+(float)Math.sin(a)*8,12+(float)Math.cos(a)*10,12+(float)Math.sin(a)*10,paint);}break;
 case "Home":line(c,3,11,12,3,21,11,21,21,14,21,14,14,10,14,10,21,3,21,3,11);break;
 case "Apps":for(int x:new int[]{3,14})for(int y:new int[]{3,14})c.drawRoundRect(x,y,x+7,y+7,2,2,paint);break;
 case "Duplicate copies":c.drawRoundRect(8,8,21,21,3,3,paint);line(c,8,16,3,16,3,3,16,3,16,8);break;
 case "Downloads":line(c,12,3,12,16);line(c,7,11,12,16,17,11);line(c,3,17,3,21,21,21,21,17);break;
 case "Videos":c.drawRoundRect(3,4,21,20,4,4,paint);line(c,10,8,16,12,10,16,10,8);break;
 case "Screenshots":c.drawRoundRect(3,3,21,21,3,3,paint);c.drawCircle(8,8,1,paint);line(c,3,17,9,11,13,15,17,12,21,16);break;
 default:line(c,5,3,15,3,20,8,20,21,5,21,5,3);line(c,14,3,14,9,20,9);}
 c.restore();}
 @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}@Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
