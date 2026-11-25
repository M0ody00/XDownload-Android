package com.xdownload.mobile;
import android.content.*;import android.net.Uri;import java.io.*;import java.util.*;import java.util.function.BooleanSupplier;
/** Analysis never enqueues; every queued job comes from an explicit user action. */
final class QuickPanelModel {
 static final class Result {
  final String url;final HttpEngine.Info direct;final MediaEngine.Result media;
  Result(String u,HttpEngine.Info d,MediaEngine.Result m){url=u;direct=d;media=m;}
  String title(){return direct!=null?direct.name:media.title;}
  boolean file(){return direct!=null;}
 }
 static Result inspect(Context c,String u,String id,BooleanSupplier go)throws Exception{
  SharedPreferences p=Store.settings(c);boolean http=p.getBoolean("http",false);
  if(QuickLink.parse(u,http).isEmpty())throw new IOException("Invalid URL");
  HttpEngine.Info d=null;Exception probe=null;try{d=HttpEngine.probe(u,http);}catch(Exception x){probe=x;}
  if(!go.getAsBoolean())throw new InterruptedIOException("Cancelled");
  if(d!=null&&!HttpEngine.html(d.mime)&&!HttpEngine.manifest(d.name,d.mime))return new Result(u,d,null);
  if(!p.getBoolean("media_enabled",true)){if(probe!=null)throw probe;throw new IOException("Media engine disabled");}
  MediaEngine.Result r=MediaEngine.analyze(c,u,id);
  if(!go.getAsBoolean())throw new InterruptedIOException("Cancelled");
  return new Result(u,null,r);
 }
 static long budget(long free,boolean direct){return Math.max(0,free-8L*1024*1024)/(direct?2:3);}
 static boolean fits(long bytes,long free,boolean direct){return free>8L*1024*1024&&(bytes<0||bytes<=budget(free,direct));}
 static Store.Job job(Result r,MediaEngine.Choice choice,String name,String hash,int minutes,int priority,long now)throws IOException{
  if(r==null||(!r.file()&&(choice==null||!r.media.choices.contains(choice))))throw new IOException("Select an available format");
  if(hash==null||!hash.isEmpty()&&!hash.matches("[a-fA-F0-9]{64}"))throw new IOException("Invalid SHA-256");
  if(minutes<0||minutes>1440)throw new IOException("Invalid schedule");
  Store.Job j;
  if(r.file()){j=new Store.Job(r.url,r.direct,hash);j.name=HttpEngine.safeName(name);}
  else {j=new Store.Job(r.url,new HttpEngine.Info(r.media.title+"."+choice.ext,choice.mime,choice.size,"","",false),hash);j.selector=choice.selector;j.extension=choice.ext;if(choice.label.startsWith("Convert · "))j.audioConvert=choice.ext;if(choice.mp3)j.mp3Rate=MediaEngine.mp3Rate(choice);}
  j.priority=Math.max(-1,Math.min(1,priority));
  if(minutes>0){j.state="scheduled";j.notBefore=now+minutes*60000L;}
  return j;
 }
 static boolean folderReady(Context c){String tree=Store.settings(c).getString("download_tree","");try{return tree.isEmpty()||DownloadDestination.granted(c,Uri.parse(tree));}catch(RuntimeException e){return false;}}
 static boolean safeQuick(Result r,long free,boolean duplicate){return r!=null&&r.file()&&r.direct.size>0&&!duplicate&&fits(r.direct.size,free,true);}
 static ArrayList<String> batch(CharSequence raw,boolean http)throws IOException{if(raw==null||raw.length()>24576)throw new IOException("List too long");LinkedHashSet<String> urls=new LinkedHashSet<>();for(String line:raw.toString().split("\\r?\\n")){if(line.trim().isEmpty())continue;String u=QuickLink.parse(line,http);if(u.isEmpty())throw new IOException("One valid URL per line");urls.add(u);if(urls.size()>5)throw new IOException("Maximum 5 links");}if(urls.isEmpty())throw new IOException("No links");return new ArrayList<>(urls);}
}
