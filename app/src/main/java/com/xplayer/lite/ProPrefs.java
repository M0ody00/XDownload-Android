package com.xplayer.lite;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import org.json.JSONArray;
import java.util.*;
final class ProPrefs {
 static SharedPreferences get(Context c){return c.getSharedPreferences("pro_settings",Context.MODE_PRIVATE);}
 static boolean flag(Context c,String k,boolean fallback){return get(c).getBoolean(k,fallback);}
 static int number(Context c,String k,int fallback){return get(c).getInt(k,fallback);}
 static ArrayList<String> list(Context c,String k){ArrayList<String> a=new ArrayList<>();try{JSONArray j=new JSONArray(get(c).getString(k,"[]"));for(int i=0;i<j.length();i++)a.add(j.getString(i));}catch(Exception ignored){}return a;}
 static void save(Context c,String k,Collection<String> a){get(c).edit().putString(k,new JSONArray(a).toString()).apply();}
 static boolean favorite(Context c,Uri u){return list(c,"favorites").contains(u.toString());}
 static boolean toggle(Context c,Uri u){ArrayList<String> a=list(c,"favorites");boolean added=!a.remove(u.toString());if(added)a.add(0,u.toString());save(c,"favorites",a);return added;}
 static void record(Context c,Uri u){if(!flag(c,"history_enabled",true)||"file".equals(u.getScheme()))return;ArrayList<String> a=list(c,"history");a.remove(u.toString());a.add(0,u.toString());while(a.size()>300)a.remove(a.size()-1);save(c,"history",a);}
 static String[] speeds={"0.25×","0.5×","0.75×","1×","1.25×","1.5×","2×","3×","4×"};
 static float[] rates={.25f,.5f,.75f,1f,1.25f,1.5f,2f,3f,4f};
}
