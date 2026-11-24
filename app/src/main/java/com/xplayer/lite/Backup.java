package com.xplayer.lite;
import android.content.*;
import org.json.*;
import java.util.*;
final class Backup {
 static final Set<String> bools=new HashSet<>(Arrays.asList("autoplay","auto_scan","thumbnails","folders","resume","auto_next","gestures","double_tap","keep_screen","noisy","subtitle_bg","history_enabled"));
 static final Map<String,Integer> limits=new HashMap<>();static{limits.put("speed",8);limits.put("seek",3);limits.put("resize",2);limits.put("subtitle_size",3);limits.put("subtitle_color",2);limits.put("accent",3);limits.put("language",1);}
 static String exportJson(Context c)throws JSONException{JSONObject root=new JSONObject(),values=new JSONObject();root.put("version",1);root.put("settings",values);Map<String,?> all=ProPrefs.get(c).getAll();for(String k:bools)if(all.get(k) instanceof Boolean)values.put(k,all.get(k));for(String k:limits.keySet())if(all.get(k) instanceof Integer)values.put(k,all.get(k));if(all.get("excluded") instanceof String)values.put("excluded",all.get("excluded"));return root.toString(2);}
 static void restore(Context c,String json)throws JSONException{JSONObject root=new JSONObject(json);if(root.getInt("version")!=1)throw new JSONException("Unsupported backup");JSONObject v=root.getJSONObject("settings");SharedPreferences.Editor e=ProPrefs.get(c).edit();for(String k:bools)if(v.has(k)&&v.get(k) instanceof Boolean)e.putBoolean(k,v.getBoolean(k));for(String k:limits.keySet())if(v.has(k)&&v.get(k) instanceof Number){int n=v.getInt(k);if(n>=0&&n<=limits.get(k))e.putInt(k,n);}if(v.has("excluded")&&v.get("excluded") instanceof String&&v.getString("excluded").length()<=20000)e.putString("excluded",v.getString("excluded"));if(!e.commit())throw new JSONException("Save failed");}
}
