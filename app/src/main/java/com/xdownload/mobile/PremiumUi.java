package com.xdownload.mobile;
import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
final class PremiumUi{
 static final int BLACK=0xff000000,PANEL=0xff17191c,WHITE=0xfff4f4f5,MUTED=0xffa7a9af,BLUE=0xff438ee9;
 static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
 static GradientDrawable shape(Context c,int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));return d;}
 static TextView text(Context c,String s,int sp,int color){TextView t=new TextView(c);t.setText(s);t.setTextSize(sp);t.setTextColor(color);return t;}
 static void ripple(View v){android.util.TypedValue a=new android.util.TypedValue();if(v.getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground,a,true))v.setBackgroundResource(a.resourceId);}
 static ImageView action(Context c,String key,String desc,int color,Runnable r){ImageView i=new ImageView(c);i.setImageDrawable(new Glyph(key,color));i.setScaleType(ImageView.ScaleType.FIT_CENTER);int p=dp(c,12);i.setPadding(p,p,p,p);i.setContentDescription(desc);ripple(i);i.setOnClickListener(v->r.run());return i;}
 static LinearLayout shortcut(Context c,String key,String title,int color,Runnable run){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.VERTICAL);l.setGravity(Gravity.CENTER);l.setPadding(dp(c,3),dp(c,8),dp(c,3),dp(c,8));ImageView i=new ImageView(c);i.setImageDrawable(new Glyph(key,color));l.addView(i,new LinearLayout.LayoutParams(dp(c,26),dp(c,26)));TextView t=text(c,title,12,color);t.setGravity(Gravity.CENTER);t.setMaxLines(2);t.setPadding(0,dp(c,4),0,0);l.addView(t);l.setContentDescription(title);ripple(l);l.setOnClickListener(v->run.run());return l;}
 static class Glyph extends Drawable{
 final String kind;final int color;final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);Glyph(String k,int c){kind=k;color=c;}
 void line(Canvas c,float x,float y,float X,float Y){c.drawLine(x,y,X,Y,p);}
 void path(Canvas c,float... xy){Path a=new Path();a.moveTo(xy[0],xy[1]);for(int i=2;i<xy.length;i+=2)a.lineTo(xy[i],xy[i+1]);c.drawPath(a,p);}
 public void draw(Canvas c){c.save();Rect b=getBounds();c.translate(b.left,b.top);c.scale(b.width()/24f,b.height()/24f);p.setColor(color);p.setStrokeWidth(1.8f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setStyle(Paint.Style.STROKE);
 switch(kind){
 case "home":path(c,2,11,12,2,22,11);path(c,5,10,5,22,10,22,10,15,14,15,14,22,19,22,19,10);break;
 case "globe":c.drawCircle(12,12,10,p);c.drawOval(7,2,17,22,p);line(c,2,12,22,12);break;
 case "download":path(c,12,2,12,16,6,10);path(c,12,16,18,10);path(c,3,16,3,22,21,22,21,16);break;
 case "link":path(c,9,15,15,9);c.drawRoundRect(2,8,13,16,3,3,p);c.drawRoundRect(11,8,22,16,3,3,p);break;
 case "folder":p.setStyle(Paint.Style.FILL);Path f=new Path();f.moveTo(2,5);f.quadTo(2,3,4,3);f.lineTo(9,3);f.lineTo(12,6);f.lineTo(21,6);f.quadTo(23,6,23,8);f.lineTo(23,19);f.quadTo(23,21,21,21);f.lineTo(4,21);f.quadTo(2,21,2,19);f.close();c.drawPath(f,p);break;
 case "search":c.drawCircle(10,10,6,p);line(c,14.5f,14.5f,21,21);break;
 case "view":c.drawRoundRect(2,3,10,21,1,1,p);c.drawRoundRect(13,3,22,10,1,1,p);c.drawRoundRect(13,13,22,21,1,1,p);break;
 case "grid":for(int x=2;x<20;x+=11)for(int y=2;y<20;y+=11)c.drawRoundRect(x,y,x+8,y+8,1,1,p);break;
 case "list":for(int y=5;y<=19;y+=7){line(c,7,y,22,y);c.drawCircle(3,y,.7f,p);}break;
 case "back":path(c,15,4,7,12,15,20);line(c,7,12,22,12);break;
 case "more":p.setStyle(Paint.Style.FILL);for(int y=5;y<=19;y+=7)c.drawCircle(12,y,1.5f,p);break;
 case "music":path(c,9,18,9,5,21,2,21,15);line(c,9,9,21,6);p.setStyle(Paint.Style.FILL);c.drawOval(2,15,10,22,p);c.drawOval(14,12,22,19,p);break;
 case "play":p.setStyle(Paint.Style.FILL);Path t=new Path();t.moveTo(6,2);t.lineTo(22,12);t.lineTo(6,22);t.close();c.drawPath(t,p);break;
 case "pause":p.setStyle(Paint.Style.FILL);c.drawRoundRect(5,3,9,21,1,1,p);c.drawRoundRect(15,3,19,21,1,1,p);break;
 case "prev":case "next":if(kind.equals("prev")){c.translate(24,0);c.scale(-1,1);}p.setStyle(Paint.Style.FILL);Path n=new Path();n.moveTo(3,3);n.lineTo(17,12);n.lineTo(3,21);n.close();c.drawPath(n,p);c.drawRect(19,3,22,21,p);break;
 case "lock":c.drawRoundRect(4,10,20,22,2,2,p);c.drawArc(7,1,17,15,180,180,false,p);line(c,12,15,12,18);break;
 case "ratio":c.drawRoundRect(2,5,22,19,1,1,p);path(c,8,9,5,12,8,15);path(c,16,9,19,12,16,15);line(c,5,12,19,12);break;
 case "subtitle":c.drawRoundRect(2,4,22,20,2,2,p);line(c,5,13,11,13);line(c,14,13,19,13);line(c,5,16,19,16);break;
 case "audio":for(int x=3;x<=21;x+=4)line(c,x,12-(x%5+2),x,12+(x%5+2));break;
 case "settings":c.drawCircle(12,12,7,p);c.drawCircle(12,12,2.7f,p);for(int i=0;i<8;i++){double a=i*Math.PI/4;line(c,(float)(12+7*Math.cos(a)),(float)(12+7*Math.sin(a)),(float)(12+10*Math.cos(a)),(float)(12+10*Math.sin(a)));}break;
 case "person":c.drawCircle(12,7,4,p);c.drawArc(3,13,21,27,180,180,false,p);break;
 case "network":c.drawRoundRect(2,3,22,16,1,1,p);line(c,12,16,12,21);line(c,6,21,18,21);break;
 case "share":path(c,2,12,20,12,15,7);path(c,20,12,15,17);break;
 case "star":Path st=new Path();for(int i=0;i<10;i++){double a=-Math.PI/2+i*Math.PI/5;float r=i%2==0?10:4.5f;float x=(float)(12+r*Math.cos(a)),y=(float)(12+r*Math.sin(a));if(i==0)st.moveTo(x,y);else st.lineTo(x,y);}st.close();c.drawPath(st,p);break;
 case "clock":c.drawCircle(12,12,9,p);path(c,12,6,12,12,16,15);break;
 case "refresh":c.drawArc(3,3,21,21,35,295,false,p);path(c,21,4,21,10,15,10);break;
 case "file":path(c,5,2,15,2,20,7,20,22,5,22,5,2);path(c,15,2,15,7,20,7);break;
 case "decoder":c.drawRoundRect(5,5,19,19,2,2,p);c.drawRect(9,9,15,15,p);for(int i=7;i<=17;i+=5){line(c,i,2,i,5);line(c,i,19,i,22);line(c,2,i,5,i);line(c,19,i,22,i);}break;
 case "info":c.drawCircle(12,12,9,p);line(c,12,11,12,17);c.drawCircle(12,7,.7f,p);break;
 case "moon":c.drawArc(3,3,21,21,65,260,false,p);c.drawArc(10,1,25,16,85,175,false,p);break;
 case "mute":path(c,3,9,7,9,12,4,12,20,7,15,3,15,3,9);line(c,16,9,22,15);line(c,22,9,16,15);break;
 case "shuffle":path(c,3,5,8,5,16,19,21,19,18,16);path(c,3,19,8,19,16,5,21,5,18,2);break;
 case "camera":c.drawRoundRect(2,6,22,21,2,2,p);c.drawCircle(12,13,4,p);path(c,8,6,9,3,15,3,16,6);break;
 case "repeat":path(c,4,8,20,8,16,4);path(c,20,16,4,16,8,20);line(c,4,8,4,13);line(c,20,11,20,16);break;
 case "equalizer":for(int x=5;x<=19;x+=7)line(c,x,3,x,21);c.drawCircle(5,9,2,p);c.drawCircle(12,15,2,p);c.drawCircle(19,7,2,p);break;
 default:c.drawCircle(12,12,9,p);break;
 }c.restore();}
 public void setAlpha(int a){p.setAlpha(a);}public void setColorFilter(ColorFilter f){p.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
 }
}
