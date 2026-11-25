package com.xdownload.mobile;
import android.content.Context;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import com.yausername.ffmpeg.FFmpeg;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import kotlin.Unit;
final class MediaEngine{
 static final class Choice{
  final String selector,ext,mime,label;final long size;final boolean estimated,audio,mp3;
  Choice(String selector,String ext,String mime,String label,long size,boolean estimated,boolean audio,boolean mp3){this.selector=selector;this.ext=ext;this.mime=mime;this.label=label;this.size=size;this.estimated=estimated;this.audio=audio;this.mp3=mp3;}
 }
 static final class Result{final String title;final double duration;final ArrayList<Choice> choices;final String source;Result(String title,double duration,ArrayList<Choice> choices){this(title,duration,choices,"Media extractor");}Result(String title,double duration,ArrayList<Choice> choices,String source){this.title=title;this.duration=duration;this.choices=choices;this.source=source;}}
 private static boolean ready=false;static final java.util.concurrent.locks.ReentrantReadWriteLock ENGINE=new java.util.concurrent.locks.ReentrantReadWriteLock(true);
 static synchronized void init(Context context)throws Exception{if(ready)return;try{YoutubeDL.getInstance().init(context.getApplicationContext());FFmpeg.getInstance().init(context.getApplicationContext());ready=true;}catch(LinkageError e){throw new IOException("Media engine incompatible with this device",e);}}
 static YoutubeDLRequest request(String link){YoutubeDLRequest req=new YoutubeDLRequest(link);req.addOption("--no-playlist");req.addOption("--ignore-config");req.addOption("--socket-timeout","15");req.addOption("--retries","2");req.addOption("--no-warnings");return req;}
 static Result analyze(Context c,String url)throws Exception{return analyze(c,url,null);}
 static void cancel(String id){if(id!=null&&!id.isEmpty())try{YoutubeDL.getInstance().destroyProcessById(id);}catch(Exception ignored){}}
 static Result analyze(Context c,String url,String id)throws Exception{ENGINE.readLock().lockInterruptibly();try{init(c);YoutubeDLRequest req=request(url);req.addOption("--dump-single-json");req.addOption("--skip-download");JSONObject info=new JSONObject(YoutubeDL.getInstance().execute(req,id,null).getOut().trim());return parse(info);}finally{ENGINE.readLock().unlock();}}
 static String explain(Throwable e,boolean en){String msg=String.valueOf(e.getMessage()).toLowerCase(Locale.ROOT);if(msg.contains("drm")||msg.contains("protected"))return en?"Protected media is not supported.":"المحتوى محمي ولا يدعم التطبيق فك الحماية.";if(msg.contains("sign in")||msg.contains("login")||msg.contains("403")||msg.contains("401"))return en?"Source requires permission or login, or rejected this request.":"المصدر يتطلب إذنًا أو تسجيل الدخول أو رفض الطلب.";if(msg.contains("timeout")||msg.contains("network")||msg.contains("unable to download"))return en?"Network or source error. Retry on another permitted connection.":"خطأ في الشبكة أو المصدر. حاول مجددًا.";if(msg.contains("javascript")||msg.contains("challenge"))return en?"The site needs a compatible JavaScript extractor/runtime.":"الموقع يحتاج محرك JavaScript متوافقًا.";return en?"No downloadable formats found for this source.":"لم نجد صيغًا قابلة للتنزيل من هذا المصدر.";}
 static Result direct(HttpEngine.Info i)throws IOException{String mime=i.mime.toLowerCase(Locale.ROOT),name=i.name;int dot=name.lastIndexOf('.');String extension=dot>=0?name.substring(dot+1).toLowerCase(Locale.ROOT):"";boolean sound=mime.startsWith("audio/"),video=mime.startsWith("video/");if(!sound&&!video||HttpEngine.manifest(name,mime))throw new IOException("Not a direct media file");if(!extension.matches("[a-z0-9]{1,8}")||!audioExt(extension)&&!extension.matches("mp4|mkv|webm|mov|avi|m4v|flv|ts")){if(mime.equals("audio/mpeg"))extension="mp3";else if(mime.equals("audio/mp4"))extension="m4a";else if(mime.equals("video/x-matroska"))extension="mkv";else{extension=mime.substring(mime.indexOf('/')+1);if(!extension.matches("[a-z0-9]{1,8}"))throw new IOException("Unknown media container");}}String title=dot>0?name.substring(0,dot):name;ArrayList<Choice> choices=new ArrayList<>();choices.add(new Choice("",extension,mime,"Original · "+extension.toUpperCase(Locale.ROOT),i.size,false,sound,false));return new Result(title,0,choices,"Direct HTTP");}
 static boolean codec(String c){return c!=null&&!c.isEmpty()&&!c.equals("none")&&!c.equals("null");}
 static String id(JSONObject f){String id=f.optString("format_id","");return id.matches("[A-Za-z0-9_.-]{1,100}")?id:"";}
 static boolean playable(JSONObject f){return !id(f).isEmpty()&&!f.optBoolean("has_drm",false)&&!f.optString("has_drm","false").equalsIgnoreCase("true")&&!f.optString("protocol","").startsWith("mhtml")&&!f.optString("ext","").equals("mhtml");}
 static long length(JSONObject f,double duration){long exact=f.optLong("filesize",-1);if(exact>0)return exact;long est=f.optLong("filesize_approx",-1);if(est>0)return est;double rate=f.optDouble("tbr",0);if(rate<=0)rate=f.optDouble("abr",0);return duration>0&&rate>0?(long)(duration*rate*125):-1;}
 static boolean estimated(JSONObject f){return f.optLong("filesize",-1)<=0;}
 static boolean audioExt(String ext){return ext.matches("mp3|m4a|aac|flac|ogg|opus|wav|wave|weba|aiff|aif|wma");}
 static boolean video(JSONObject f){if(audioExt(ext(f)))return false;String v=f.optString("vcodec","");if(v.equals("none"))return false;return codec(v)||ext(f).matches("mp4|mkv|webm|mov|avi|m4v|flv|ts")&&!f.has("vcodec");}
 static boolean audio(JSONObject f){String a=f.optString("acodec","");return codec(a)||audioExt(ext(f))||!f.has("acodec")&&video(f);}
 static String ext(JSONObject f){String x=f.optString("ext","");return x.matches("[a-zA-Z0-9]{1,8}")?x.toLowerCase(Locale.ROOT):"bin";}
 static String mime(String ext,boolean audio){if(audio){if(ext.equals("mp3"))return "audio/mpeg";if(ext.equals("m4a"))return "audio/mp4";if(ext.equals("webm"))return "audio/webm";if(ext.equals("opus")||ext.equals("ogg"))return "audio/ogg";if(ext.equals("wav"))return "audio/wav";return "audio/"+ext;}if(ext.equals("mkv"))return "video/x-matroska";return "video/"+ext;}
 static Result parse(JSONObject info)throws Exception{
  if(info.optBoolean("has_drm",false)||info.optBoolean("is_live",false)||"is_live".equals(info.optString("live_status"))||info.optJSONArray("entries")!=null)throw new IOException("Protected media, live streams or playlists are not supported");
  double duration=info.optDouble("duration",0);JSONArray formats=info.optJSONArray("formats");if(formats==null){formats=new JSONArray();formats.put(info);}
  ArrayList<JSONObject> all=new ArrayList<>(),audios=new ArrayList<>();for(int i=0;i<formats.length();i++){JSONObject x=formats.optJSONObject(i);if(x==null)continue;if(x.optString("ext").equals("wav")&&x.optString("format_id").isEmpty())x.put("format_id","best");if(!playable(x))continue;all.add(x);if(!video(x)&&audio(x))audios.add(x);}
  audios.sort((a,b)->Double.compare(b.optDouble("abr",b.optDouble("tbr",0)),a.optDouble("abr",a.optDouble("tbr",0))));
  LinkedHashMap<String,Choice> videos=new LinkedHashMap<>(),audioChoices=new LinkedHashMap<>();
  all.sort((a,b)->{int h=Integer.compare(b.optInt("height"),a.optInt("height"));return h!=0?h:Double.compare(b.optDouble("tbr",0),a.optDouble("tbr",0));});
  for(JSONObject x:all){boolean v=video(x),a=audio(x);if(!v&&!a)continue;String extension=ext(x),selector=id(x);long bytes=length(x,duration);boolean approx=estimated(x);if(v){
    if(!a){JSONObject best=null;for(JSONObject au:audios){if((extension.equals("mp4")&&ext(au).equals("m4a"))||(extension.equals("webm")&&ext(au).equals("webm"))){best=au;break;}}if(best==null&&!audios.isEmpty())best=audios.get(0);if(best==null)continue;selector+="+"+id(best);long audioSize=length(best,duration);bytes=bytes>0&&audioSize>0?bytes+audioSize:-1;approx=true;if(!((extension.equals("mp4")&&ext(best).equals("m4a"))||(extension.equals("webm")&&ext(best).equals("webm"))))extension="mkv";}
    int height=x.optInt("height",0),fps=(int)Math.round(x.optDouble("fps",0));String label=(height>0?height+"p":"Original")+(fps>=50?" "+fps+"fps":"")+" · "+extension.toUpperCase(Locale.ROOT);String key=label;if(!videos.containsKey(key))videos.put(key,new Choice(selector,extension,mime(extension,false),label,bytes,approx,false,false));
   }else{int kbps=(int)Math.round(x.optDouble("abr",x.optDouble("tbr",0)));String label=(kbps>0?kbps+" kbps":"Original")+" · "+extension.toUpperCase(Locale.ROOT);if(!audioChoices.containsKey(label))audioChoices.put(label,new Choice(selector,extension,mime(extension,true),label,bytes,approx,true,false));}
  }
  JSONObject bestAudio=!audios.isEmpty()?audios.get(0):null;if(bestAudio==null)for(JSONObject x:all)if(audio(x)){bestAudio=x;break;}
  if(bestAudio!=null)for(int rate:new int[]{128,192,320})audioChoices.put("mp3-"+rate,new Choice(id(bestAudio),"mp3","audio/mpeg","MP3 · "+rate+" kbps",duration>0?(long)(duration*rate*125):-1,true,true,true));
  if(bestAudio!=null){for(String output:new String[]{"m4a","aac","flac","wav","opus"}){long estimatedSize=-1;audioChoices.put("convert-"+output,new Choice(id(bestAudio),output,mime(output,true),"Convert · "+output.toUpperCase(Locale.ROOT),estimatedSize,true,true,false));}}
  ArrayList<Choice> result=new ArrayList<>(videos.values());result.addAll(audioChoices.values());if(result.isEmpty())throw new IOException("No supported downloadable audio/video formats found");return new Result(HttpEngine.safeName(info.optString("title","Media")),duration,result);
 }
 static Choice recommend(Result r,boolean sound,long budget){Choice best=null;double score=-Double.MAX_VALUE;for(Choice c:r.choices){if(c.audio!=sound||c.size>budget)continue;double val=0;if(sound){val=c.mp3?-10:10;if(c.label.contains("128 kbps")||c.label.contains("192 kbps"))val+=40;}else{int h=0;try{h=Integer.parseInt(c.label.split("p")[0]);}catch(Exception ignored){}val=100-Math.abs(h-720)/12.0;if(c.ext.equals("mp4"))val+=10;}if(c.size<0)val-=30;if(c.size>0&&budget>0)val-=20.0*c.size/budget;if(val>score){score=val;best=c;}}return best;}
 static int mp3Rate(Choice choice){if(!choice.mp3)return 0;if(choice.label.contains("320 kbps"))return 320;if(choice.label.contains("192 kbps"))return 192;return 128;}
 static File transfer(Context c,Store.Job job,HttpEngine.Observer observer)throws Exception{
  ENGINE.readLock().lockInterruptibly();try{init(c);if(!job.selector.matches("[A-Za-z0-9_.-]{1,100}(\\+[A-Za-z0-9_.-]{1,100})?")||!job.extension.matches("[a-z0-9]{1,8}"))throw new IOException("Invalid media format");
  File dir=new File(c.getFilesDir(),"media-"+job.id);if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Cannot create media directory");
  YoutubeDLRequest req=request(job.url);req.addOption("-f",job.selector);req.addOption("--continue");req.addOption("--no-mtime");req.addOption("--no-overwrites");req.addOption("--no-post-overwrites");req.addOption("--fragment-retries","3");req.addOption("--abort-on-unavailable-fragments");long rate=PerformancePolicy.rate(Store.settings(c).getInt("mode",1),Store.settings(c).getInt("rate_kib",0));req.addOption("--concurrent-fragments",rate>0?"1":"2");if(rate>0)req.addOption("--limit-rate",Long.toString(rate));req.addOption("--newline");req.addOption("-o",new File(dir,"media.%(ext)s").getAbsolutePath());
  if(!job.audioConvert.isEmpty()){if(!job.audioConvert.matches("m4a|aac|flac|wav|opus"))throw new IOException("Invalid conversion");req.addOption("-x");req.addOption("--audio-format",job.audioConvert);}
  else if(job.mp3Rate>0){req.addOption("-x");req.addOption("--audio-format","mp3");req.addOption("--audio-quality",job.mp3Rate+"K");}
  else if(job.selector.contains("+"))req.addOption("--merge-output-format",job.extension);
  AtomicBoolean finished=new AtomicBoolean(false);Thread monitor=new Thread(()->{while(!finished.get()){if(!observer.proceed()){YoutubeDL.getInstance().destroyProcessById(job.id);}try{Thread.sleep(300);}catch(InterruptedException e){return;}}},"media-monitor");monitor.start();
  try{YoutubeDL.getInstance().execute(req,job.id,(percent,eta,line)->{if(percent>=0&&observer.proceed()){job.mediaPercent=Math.min(99,Math.max(job.mediaPercent,(int)(float)percent));job.eta=eta;try{observer.progress(0,-1,"","");}catch(IOException e){YoutubeDL.getInstance().destroyProcessById(job.id);}}return Unit.INSTANCE;});if(!observer.proceed())return null;
   File out=new File(dir,"media."+job.extension);if(!out.isFile()||out.length()==0)throw new IOException("Expected output format not produced");return out;
  }finally{finished.set(true);monitor.interrupt();}
 }finally{ENGINE.readLock().unlock();}}
 static void cleanup(Context c,String id){FileTools.deleteTree(new File(c.getFilesDir(),"media-"+id));}
}