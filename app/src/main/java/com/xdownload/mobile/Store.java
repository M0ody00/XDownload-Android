package com.xdownload.mobile;
import android.content.*;
import org.json.*;
import java.util.*;
final class Store {
 static final class Job{
 String id,url,name,mime,expected="",selector="",extension="",audioConvert="";int mp3Rate=0,priority=0,playbackMs=0;boolean torrent=false,favorite=false;volatile int mediaPercent=0;volatile long eta=-1;volatile String state="queued",etag="",modified="",uri="",sha="",error="";
 volatile long done=0,total=-1,speed=0,notBefore=0;
 Job(String url,HttpEngine.Info info,String expected){id=UUID.randomUUID().toString();this.url=url;name=info.name;mime=info.mime;total=info.size;this.expected=expected;}
 Job(JSONObject j)throws JSONException{id=j.getString("id");url=j.getString("url");name=j.getString("name");mime=j.optString("mime","application/octet-stream");state=j.optString("state","paused");if(state.equals("running")||state.equals("saving"))state="paused";etag=j.optString("etag");modified=j.optString("modified");uri=j.optString("uri");sha=j.optString("sha");error=j.optString("error");expected=j.optString("expected");selector=j.optString("selector");extension=j.optString("extension");audioConvert=j.optString("audioConvert");torrent=j.optBoolean("torrent");favorite=j.optBoolean("favorite",false);mp3Rate=j.optInt("mp3Rate");playbackMs=Math.max(0,j.optInt("playbackMs",0));priority=Math.max(-1,Math.min(1,j.optInt("priority")));notBefore=j.optLong("notBefore");done=j.optLong("done");total=j.optLong("total",-1);}
 JSONObject json()throws JSONException{return new JSONObject().put("id",id).put("url",url).put("name",name).put("mime",mime).put("state",state).put("etag",etag).put("modified",modified).put("uri",uri).put("sha",sha).put("error",error).put("expected",expected).put("selector",selector).put("extension",extension).put("audioConvert",audioConvert).put("mp3Rate",mp3Rate).put("playbackMs",playbackMs).put("torrent",torrent).put("favorite",favorite).put("priority",priority).put("notBefore",notBefore).put("done",done).put("total",total);}
 }
 private static Store instance;private final SharedPreferences prefs;private final ArrayList<Job> jobs=new ArrayList<>();
 private Store(Context c){prefs=c.getSharedPreferences("downloads",0);try{JSONArray a=new JSONArray(prefs.getString("jobs","[]"));for(int i=0;i<a.length();i++){try{Job j=new Job(a.getJSONObject(i));if(j.id.matches("[a-f0-9-]{36}"))jobs.add(j);}catch(JSONException ignored){}}}catch(JSONException ignored){}}
 static synchronized Store get(Context c){if(instance==null)instance=new Store(c.getApplicationContext());return instance;}
 synchronized ArrayList<Job> list(){return new ArrayList<>(jobs);}
 synchronized ArrayList<Job> ordered(){ArrayList<Job> out=new ArrayList<>(jobs);out.sort((a,b)->Integer.compare(b.priority,a.priority));return out;}
 synchronized Job getJob(String id){for(Job j:jobs)if(j.id.equals(id))return j;return null;}
 synchronized boolean duplicate(String url){for(Job j:jobs)if(j.url.equals(url)&&!j.state.equals("cancelled"))return true;return false;}
 synchronized void add(Job j){jobs.add(j);save();}
 synchronized void remove(Job j){jobs.remove(j);save();}
 synchronized void save(){try{JSONArray a=new JSONArray();for(Job j:jobs)a.put(j.json());prefs.edit().putString("jobs",a.toString()).apply();}catch(JSONException ignored){}}
 synchronized Job next(){for(Job j:jobs)if(j.state.equals("queued")){j.state="running";j.error="";save();return j;}return null;}
 static SharedPreferences settings(Context c){return c.getSharedPreferences("settings",0);}
}