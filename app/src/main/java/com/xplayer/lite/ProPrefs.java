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
 static String string(Context c,String k,String def){return get(c).getString(k,def);}
 static boolean backgroundAllowed(int mode,boolean audio){return mode==3||(mode==2&&audio);}
 static final String[] TOOL_IDS={"audio","speed","background","repeat","eq","night","shuffle","mute","sleep","ab","mirror","flip","snapshot","rotate","pip","subtitle","ratio","customize"};
 static final String[] TOOL_EN={"Audio track","Speed","Background","Repeat","Equalizer","Night mode","Shuffle","Mute","Sleep timer","A–B repeat","Mirror","Vertical flip","Screenshot","Rotation","Pop-up","Subtitle","Aspect ratio","Customize"};
 static final String[] TOOL_AR={"الصوت","السرعة","الخلفية","تكرار","معادل الصوت","الوضع الليلي","عشوائي","كتم الصوت","مؤقت النوم","تكرار A–B","انعكاس","قلب رأسي","لقطة شاشة","تدوير","عائم","الترجمة","نسبة العرض","تخصيص"};
 static final String[] TOOL_ICONS={"audio","clock","music","repeat","equalizer","moon","shuffle","mute","clock","repeat","ratio","ratio","camera","refresh","view","subtitle","ratio","settings"};
 static ArrayList<String> toolOrder(Context c){ArrayList<String> a=list(c,"quick_tools");if(!get(c).contains("quick_tools"))a.addAll(Arrays.asList(TOOL_IDS));a.removeAll(Collections.singleton("customize"));a.add("customize");return a;}
 static boolean hiddenPath(String name,String path){if(name.startsWith("."))return true;for(String part:path.split("/"))if(part.startsWith(".")&&!part.equals(".")&&!part.equals(".."))return true;return false;}
 static boolean allowedExtension(Context c,String name){ArrayList<String> a=list(c,"scan_extensions");return !get(c).contains("scan_extensions")||a.contains(TreeScanner.extension(name));}
 static int decoder(Context c,String name,boolean network){String ext=TreeScanner.extension(name);return number(c,"decoder_ext_"+ext,number(c,network?"decoder_network":"decoder_local",0));}
 static String[] speeds={"0.25×","0.5×","0.75×","1×","1.25×","1.5×","2×","3×","4×"};
 static float[] rates={.25f,.5f,.75f,1f,1.25f,1.5f,2f,3f,4f};
}
