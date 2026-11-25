package com.xdownload.mobile;
import java.io.IOException;
import java.util.function.*;
/** Shared direct HTTP payload pacing, not a network-layer or native media/torrent limiter. */
final class SharedRateLimiter{
 private long next=0;private int lastRate=0;
 synchronized long reserve(long now,int bytes,int kib){if(kib<=0){lastRate=0;next=now;return now;}if(lastRate!=kib){next=now;lastRate=kib;}long duration=(long)Math.ceil(bytes*1000000000.0/(kib*1024.0));next=Math.max(now,next)+duration;return next;}
 boolean acquire(int bytes,IntSupplier setting,BooleanSupplier proceed)throws IOException{
  int rate=Math.max(0,Math.min(102400,setting.getAsInt()));long due=reserve(System.nanoTime(),bytes,rate);
  try{while(proceed.getAsBoolean()){if(setting.getAsInt()!=rate)return true;long remaining=due-System.nanoTime();if(remaining<=0)return true;Thread.sleep(Math.max(1,Math.min(100,remaining/1000000)));}return false;}catch(InterruptedException e){Thread.currentThread().interrupt();return false;}
 }
}