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
 static void record(Context c,Uri u){if(!flag(c,"history_enabled",true)||!"content".equals(u.getScheme()))return;ArrayList<String> a=list(c,"history");a.remove(u.toString());a.add(0,u.toString());while(a.size()>300)a.remove(a.size()-1);save(c,"history",a);}
 static ArrayList<String> playlists(Context c){return list(c,"playlist_names");}
 static String playlistKey(String name){return "playlist:"+name;}
 static boolean createPlaylist(Context c,String name){name=name.trim();if(name.isEmpty()||name.length()>80)return false;ArrayList<String> names=playlists(c);if(names.contains(name))return false;names.add(name);save(c,"playlist_names",names);save(c,playlistKey(name),new ArrayList<String>());return true;}
 static void deletePlaylist(Context c,String name){ArrayList<String> names=playlists(c);names.remove(name);save(c,"playlist_names",names);get(c).edit().remove(playlistKey(name)).apply();}
 static boolean playlistAdd(Context c,String name,Uri uri){if(!playlists(c).contains(name)||!"content".equals(uri.getScheme()))return false;ArrayList<String> entries=list(c,playlistKey(name));if(entries.contains(uri.toString()))return false;entries.add(uri.toString());save(c,playlistKey(name),entries);return true;}
 static void playlistRemove(Context c,String name,Uri uri){ArrayList<String> a=list(c,playlistKey(name));a.remove(uri.toString());save(c,playlistKey(name),a);}
 static int accent(Context c){int[] colors={0xff44e7b2,0xff63a8ff,0xffd5a7ff,0xffffb85b};return colors[Math.max(0,Math.min(3,number(c,"accent",0)))];}
 static String label(Context c,String arabic,String english){return number(c,"language",0)==1?english:arabic;}
 static String[] speeds={"0.25×","0.5×","0.75×","1×","1.25×","1.5×","2×","3×","4×"};
 static float[] rates={.25f,.5f,.75f,1f,1.25f,1.5f,2f,3f,4f};
}
