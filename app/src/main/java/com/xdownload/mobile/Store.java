package com.xdownload.mobile;
import android.content.*;
import org.json.*;
import java.util.*;
final class Store {
 static final class Job{
 String id,url,name,mime,expected="";volatile String state="queued",etag="",modified="",uri="",sha="",error="";
 volatile long done=0,total=-1,speed=0;
 Job(String url,HttpEngine.Info info,String expected){id=UUID.randomUUID().toString();this.url=url;name=info.name;mime=info.mime;total=info.size;this.expected=expected;}
 Job(JSONObject j)throws JSONException{id=j.getString("id");url=j.getString("url");name=j.getString("name");mime=j.optString("mime","application/octet-stream");state=j.optString("state","paused");if(state.equals("running")||state.equals("saving"))state="paused";etag=j.optString("etag");modified=j.optString("modified");uri=j.optString("uri");sha=j.optString("sha");error=j.optString("error");expected=j.optString("expected");done=j.optLong("done");total=j.optLong("total",-1);}
 JSONObject json()throws JSONException{return new JSONObject().put("id",id).put("url",url).put("name",name).put("mime",mime).put("state",state).put("etag",etag).put("modified",modified).put("uri",uri).put("sha",sha).put("error",error).put("expected",expected).put("done",done).put("total",total);}
 }
 private static Store instance;private final SharedPreferences prefs;private final ArrayList<Job> jobs=new ArrayList<>();
 private Store(Context c){prefs=c.getSharedPreferences("downloads",0);try{JSONArray a=new JSONArray(prefs.getString("jobs","[]"));for(int i=0;i<a.length();i++)jobs.add(new Job(a.getJSONObject(i)));}catch(JSONException ignored){}}
 static synchronized Store get(Context c){if(instance==null)instance=new Store(c.getApplicationContext());return instance;}
 synchronized ArrayList<Job> list(){return new ArrayList<>(jobs);}
 synchronized Job getJob(String id){for(Job j:jobs)if(j.id.equals(id))return j;return null;}
 synchronized boolean duplicate(String url){for(Job j:jobs)if(j.url.equals(url)&&!j.state.equals("cancelled"))return true;return false;}
 synchronized void add(Job j){jobs.add(j);save();}
 synchronized void save(){try{JSONArray a=new JSONArray();for(Job j:jobs)a.put(j.json());prefs.edit().putString("jobs",a.toString()).apply();}catch(JSONException ignored){}}
 synchronized Job next(){for(Job j:jobs)if(j.state.equals("queued")){j.state="running";j.error="";save();return j;}return null;}
 static SharedPreferences settings(Context c){return c.getSharedPreferences("settings",0);}
}