package com.xdownload.mobile;
final class PerformancePolicy{
 static long rate(int mode,int customKiB){long custom=Math.max(0,Math.min(102400,customKiB))*1024L;long base=mode==0?512*1024L:mode==3?1024*1024L:0;return base==0?custom:custom==0?base:Math.min(base,custom);} 
 static boolean retryable(String message){String m=message==null?"":message.toLowerCase(java.util.Locale.ROOT);return !(m.contains("http 400")||m.contains("http 401")||m.contains("http 403")||m.contains("http 404")||m.contains("http 410")||m.contains("http 429")||m.contains("storage")||m.contains("web page")||m.contains("streaming manifest"));}
 static long retryDelay(int attempt){return Math.min(8000L,1000L << Math.max(0,Math.min(3,attempt)));}
 static int jobs(int requested,boolean lowRam,boolean hot,boolean battery){return lowRam||hot||battery?1:Math.max(1,Math.min(3,requested));}
 static int connections(int mode,int requested,long size,boolean lowRam,boolean hot,boolean battery,boolean metered,int active){
  if(mode==0||mode==3||lowRam||hot||battery||size<4L*1024*1024)return 1;
  int cap=mode==2?(size>=512L*1024*1024?32:size>=128L*1024*1024?16:8):3;if(metered)cap=Math.min(cap,3);if(active>1)cap=Math.min(cap,3);if(size<16L*1024*1024)cap=Math.min(cap,3);return Math.max(1,Math.min(cap,requested));
 }
}