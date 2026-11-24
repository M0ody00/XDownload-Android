package com.xdownload.mobile;
final class PerformancePolicy{
 static int jobs(int requested,boolean lowRam,boolean hot,boolean battery){return lowRam||hot||battery?1:Math.max(1,Math.min(3,requested));}
 static int connections(int mode,int requested,long size,boolean lowRam,boolean hot,boolean battery,boolean metered,int active){
  if(mode==0||mode==3||lowRam||hot||battery||size<4L*1024*1024)return 1;
  int cap=mode==2?8:3;if(metered)cap=Math.min(cap,3);if(active>1)cap=Math.min(cap,3);if(size<16L*1024*1024)cap=Math.min(cap,3);return Math.max(1,Math.min(cap,requested));
 }
}