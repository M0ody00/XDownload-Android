package com.xdownload.mobile;
import android.content.Context;import android.widget.VideoView;import android.view.View;
final class AspectVideoView extends VideoView {
 int aspect=0,vw=0,vh=0;
 AspectVideoView(Context c){super(c);}
 void setDimensions(int w,int h){vw=w;vh=h;requestLayout();}
 static int[] dimensions(int w,int h,int vw,int vh,int mode){if(w<=0||h<=0||vw<=0||vh<=0||mode==3)return new int[]{Math.max(0,w),Math.max(0,h)};double f=mode==1||mode==2?Math.max(w/(double)vw,h/(double)vh):Math.min(w/(double)vw,h/(double)vh);if(mode==2)f*=1.15;return new int[]{Math.max(1,(int)Math.round(vw*f)),Math.max(1,(int)Math.round(vh*f))};}
 @Override protected void onMeasure(int ws,int hs){int w=View.MeasureSpec.getSize(ws),h=View.MeasureSpec.getSize(hs);if(vw<=0||vh<=0||w<=0||h<=0){super.onMeasure(ws,hs);return;}int[] d=dimensions(w,h,vw,vh,aspect);setMeasuredDimension(d[0],d[1]);}
}
