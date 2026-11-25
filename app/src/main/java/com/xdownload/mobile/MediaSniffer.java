package com.xdownload.mobile;
import android.net.Uri;import java.util.*;import org.json.*;
/** Ephemeral candidates only; no cookies, authentication headers, DRM keys or segment reconstruction. */
final class MediaSniffer {
 static final int LIMIT=20;
 static String type(String raw){try{String p=new java.net.URI(raw).getPath();if(p==null)return "";p=p.toLowerCase(Locale.ROOT);if(p.matches(".*\\.(m3u8|mpd)$"))return "stream";if(p.matches(".*\\.(mp4|webm|mkv|mov|m4v|avi|3gp|ogv)$"))return "video";if(p.matches(".*\\.(mp3|m4a|aac|ogg|opus|wav|flac)$"))return "audio";}catch(Exception ignored){}return "";}
 static final class Entry{final String url,type;final boolean playing;Entry(String u,String t,boolean p){url=u;type=t;playing=p;}}
 static final class Session{
  private int epoch=0;private boolean protectedPage=false,playing=false,blob=false;private final LinkedHashMap<String,Entry> network=new LinkedHashMap<>(),dom=new LinkedHashMap<>();
  synchronized void start(String u){epoch++;protectedPage=false;playing=false;blob=false;network.clear();dom.clear();}
  synchronized int epoch(){return epoch;}
  synchronized void observe(String raw,boolean http,int version){if(version!=epoch||protectedPage)return;String u=QuickLink.parse(raw,http),t=type(u);if(u.isEmpty()||t.isEmpty()||XShield.adPath(Uri.parse(u)))return;if(network.size()<LIMIT&&!network.containsKey(u))network.put(u,new Entry(u,t,false));}
  synchronized void apply(JSONArray entries,boolean active,boolean opaque,boolean drm,boolean http,int version){if(version!=epoch)return;protectedPage=protectedPage||drm;playing=active;blob=opaque;dom.clear();if(protectedPage){network.clear();return;}for(int i=0;i<Math.min(LIMIT,entries.length());i++){JSONObject e=entries.optJSONObject(i);if(e==null)continue;String u=QuickLink.parse(e.optString("url"),http),t=e.optString("type");if(u.isEmpty()||XShield.adPath(Uri.parse(u))||!(t.equals("video")||t.equals("audio")||t.equals("file")))continue;dom.put(u,new Entry(u,t,e.optBoolean("playing")));}}
  synchronized ArrayList<Entry> entries(){ArrayList<Entry> result=new ArrayList<>();if(protectedPage)return result;for(Entry e:dom.values())if(e.playing&&result.size()<LIMIT)result.add(e);for(Entry e:dom.values())if(!e.playing&&result.size()<LIMIT)result.add(e);for(Entry e:network.values())if(!dom.containsKey(e.url)&&result.size()<LIMIT)result.add(e);return result;}
  synchronized boolean offer(){return !protectedPage&&(playing||blob||!dom.isEmpty()||!network.isEmpty());}
  synchronized boolean playing(){return !protectedPage&&playing;}
  synchronized boolean protectedPage(){return protectedPage;}
 }
 static final String SCAN="(function(){let out=[],playing=false,blob=false,drm=false;for(let e of document.querySelectorAll('video,audio')){if(e.mediaKeys){drm=true;continue}let active=!e.paused&&!e.ended&&e.readyState>=2;playing=playing||active;let type=e.tagName.toLowerCase();for(let u of [e.currentSrc,e.src,...Array.from(e.querySelectorAll('source')).map(s=>s.src)]){if(u&&u.startsWith('blob:'))blob=true;if(u&&/^https?:/i.test(u)&&out.length<20)out.push({url:u,type:type,playing:active})}}for(let a of document.querySelectorAll('a[download]'))if(a.href&&/^https?:/i.test(a.href)&&out.length<20)out.push({url:a.href,type:'file',playing:false});return {items:out,playing:playing,blob:blob,drm:drm}})()";
}
