package com.xdownload.mobile;
import android.content.Context;
import org.json.*;
import java.io.*;
import java.util.*;
/** Small plain-text reading cache, app-private; no executable web archives. */
final class OfflinePages{
 static final int MAX=20,LIMIT=100000,MAX_BYTES=800000;
 static final class Page{String id,title,url,text;Page(String id,JSONObject j){this.id=id;title=j.optString("title","Page");url=j.optString("url","");text=j.optString("text","");}}
 static File dir(Context c)throws IOException{File d=new File(c.getFilesDir(),"offline-pages");if(!d.isDirectory()&&!d.mkdirs())throw new IOException("Cannot create reading cache");return d;}
 static String cut(String s,int n){return s==null?"":s.substring(0,Math.min(n,s.length()));}
 static synchronized ArrayList<Page> list(Context c)throws Exception{ArrayList<Page> pages=new ArrayList<>();File[] all=dir(c).listFiles();if(all==null)return pages;Arrays.sort(all,(a,b)->Long.compare(b.lastModified(),a.lastModified()));for(File f:all){if(pages.size()>=MAX)break;if(!f.getName().matches("[a-f0-9-]{36}\\.json")||f.length()>MAX_BYTES||java.nio.file.Files.isSymbolicLink(f.toPath()))continue;try{pages.add(new Page(f.getName(),new JSONObject(new String(java.nio.file.Files.readAllBytes(f.toPath()),java.nio.charset.StandardCharsets.UTF_8))));}catch(Exception ignored){}}return pages;}
 static synchronized void save(Context c,String title,String url,String text)throws Exception{if(list(c).size()>=MAX)throw new IOException("Offline page limit: delete a saved page first");File d=dir(c),f=new File(d,UUID.randomUUID()+".json");byte[] data=new JSONObject().put("title",cut(title,200)).put("url",cut(url,2048)).put("text",cut(text,LIMIT)).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);if(data.length>MAX_BYTES)throw new IOException("Page too large");File tmp=File.createTempFile("page-",".tmp",d);try{try(FileOutputStream out=new FileOutputStream(tmp)){out.write(data);out.getFD().sync();}if(!tmp.renameTo(f))throw new IOException("Cannot save page");}finally{tmp.delete();}}
 static synchronized void delete(Context c,String id){if(id==null||!id.matches("[a-f0-9-]{36}\\.json"))return;try{new File(dir(c),id).delete();}catch(IOException ignored){}}
}