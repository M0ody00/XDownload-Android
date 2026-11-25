package com.xdownload.mobile;
import android.content.Context;
import java.io.*;
import java.nio.file.Files;
/** Read-only measurements and narrowly scoped stale cache cleanup. Never touches filesDir or noBackupFilesDir. */
final class StorageAudit{
 static final long STALE=48L*60*60*1000;
 static final String[] PREFIXES={"studio-in-","studio-out-","xdownload-input-","xdownload-mp3-","native-media-"};
 static final class Report{long apk,runtime,privateFiles,cache,stale,free;}
 static long bytes(File f){if(f==null||Files.isSymbolicLink(f.toPath()))return 0;if(f.isFile())return f.length();if(!f.isDirectory())return 0;long n=0;File[] list=f.listFiles();if(list!=null)for(File child:list)n+=bytes(child);return n;}
 static boolean eligible(File f,long now){if(Files.isSymbolicLink(f.toPath())||!f.isFile()||f.lastModified()<=0||now-f.lastModified()<STALE)return false;for(String prefix:PREFIXES)if(f.getName().startsWith(prefix))return true;return false;}
 static long stale(File cache,boolean clean,long now){long n=0;File[] list=cache.listFiles();if(list!=null)for(File f:list){if(!eligible(f,now))continue;long size=f.length();if(!clean||f.delete())n+=size;}return n;}
 static Report measure(Context c){Report r=new Report();r.apk=new File(c.getApplicationInfo().sourceDir).length();String[] splits=c.getApplicationInfo().splitSourceDirs;if(splits!=null)for(String s:splits)r.apk+=new File(s).length();r.runtime=bytes(new File(c.getNoBackupFilesDir(),"youtubedl-android"));r.privateFiles=bytes(c.getFilesDir());r.cache=bytes(c.getCacheDir());r.stale=stale(c.getCacheDir(),false,System.currentTimeMillis());r.free=c.getFilesDir().getUsableSpace();return r;}
}