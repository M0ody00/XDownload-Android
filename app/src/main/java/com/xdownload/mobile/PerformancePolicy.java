package com.xdownload.mobile;
final class PerformancePolicy{
 static long rate(int mode,int customKiB){long custom=Math.max(0,Math.min(102400,customKiB))*1024L;long base=mode==0?512*1024L:mode==3?1024*1024L:0;return base==0?custom:custom==0?base:Math.min(base,custom);}
 static int jobs(int requested,boolean lowRam,boolean hot,boolean battery){return lowRam||hot||battery?1:Math.max(1,Math.min(3,requested));}
 static int connections(int mode,int requested,long size,boolean lowRam,boolean hot,boolean battery,boolean metered,int active){
  if(mode==0||mode==3||lowRam||hot||battery||size<4L*1024*1024)return 1;
  int cap=mode==2?8:3;if(metered)cap=Math.min(cap,3);if(active>1)cap=Math.min(cap,3);if(size<16L*1024*1024)cap=Math.min(cap,3);return Math.max(1,Math.min(cap,requested));
 }
}