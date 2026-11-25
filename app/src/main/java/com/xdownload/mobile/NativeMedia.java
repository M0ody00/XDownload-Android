package com.xdownload.mobile;
import android.content.Context;
import java.io.*;import java.util.*;import java.util.concurrent.*;
/** Reuse the extractor's bundled native FFmpeg executable and library tree. No shell. */
final class NativeMedia{
 private static final Semaphore SLOT=new Semaphore(1,true);
 static void run(Context c,String... args)throws IOException{
  boolean acquired=false;Process p=null;File log=null;
  try{SLOT.acquire();acquired=true;MediaEngine.init(c);File root=new File(c.getNoBackupFilesDir(),"youtubedl-android/packages"),bin=new File(c.getApplicationInfo().nativeLibraryDir,"libffmpeg.so");if(!bin.isFile())throw new IOException("Native media executable unavailable");
   ArrayList<String> command=new ArrayList<>();command.add(bin.getAbsolutePath());command.addAll(Arrays.asList("-nostdin","-hide_banner","-loglevel","error"));command.addAll(Arrays.asList(args));
   log=File.createTempFile("native-media-",".log",c.getCacheDir());
   ProcessBuilder builder=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log);builder.environment().put("LD_LIBRARY_PATH",new File(root,"python/usr/lib").getAbsolutePath()+":"+new File(root,"ffmpeg/usr/lib").getAbsolutePath()+":"+new File(root,"aria2c/usr/lib").getAbsolutePath());builder.environment().put("TMPDIR",c.getCacheDir().getAbsolutePath());builder.environment().put("HOME",c.getFilesDir().getAbsolutePath());p=builder.start();long deadline=System.nanoTime()+TimeUnit.HOURS.toNanos(2);
   while(!p.waitFor(500,TimeUnit.MILLISECONDS)){if(Thread.currentThread().isInterrupted())throw new InterruptedException();if(System.nanoTime()>deadline||log.length()>1024*1024||c.getCacheDir().getUsableSpace()<8*1024*1024L)throw new IOException("Media operation exceeded time, log or storage limit");}
   if(p.exitValue()!=0){String details="";try(RandomAccessFile in=new RandomAccessFile(log,"r")){int n=(int)Math.min(2048,in.length());byte[] buf=new byte[n];in.seek(in.length()-n);in.readFully(buf);details=new String(buf,java.nio.charset.StandardCharsets.UTF_8);}throw new IOException("Media conversion failed ("+p.exitValue()+"): "+details);}
  }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("Media operation cancelled",e);}catch(IOException e){throw e;}catch(Exception|LinkageError e){throw new IOException("Native media unavailable",e);}finally{if(p!=null&&p.isAlive()){p.destroyForcibly();try{p.waitFor(3,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}if(log!=null)log.delete();if(acquired)SLOT.release();}
 }
}