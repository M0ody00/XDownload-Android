package com.xdownload.mobile;
import android.content.*;
import java.net.*;
/** Accept one complete HTTP(S) URL, never coerce arbitrary clipboard objects or log content. */
final class QuickLink{
 static String parse(CharSequence raw,boolean http){if(raw==null||raw.length()>4096)return "";String text=raw.toString().trim();if(text.isEmpty()||text.length()>4096)return "";try{URI u=new URI(text);String scheme=u.getScheme();if(!("https".equalsIgnoreCase(scheme)||http&&"http".equalsIgnoreCase(scheme))||u.getHost()==null||u.getHost().isEmpty()||u.getRawUserInfo()!=null||u.getPort()>65535||u.getPort()==0)return "";return u.toASCIIString();}catch(Exception e){return "";}}
 static String host(String url){try{return new URI(url).getHost();}catch(Exception e){return "";}}
 static String read(Context c,boolean http){try{ClipboardManager manager=c.getSystemService(ClipboardManager.class);if(manager==null)return "";ClipDescription desc=manager.getPrimaryClipDescription();if(desc==null||!desc.hasMimeType("text/plain")||desc.getExtras()!=null&&desc.getExtras().getBoolean("android.content.extra.IS_SENSITIVE",false))return "";ClipData clip=manager.getPrimaryClip();if(clip==null||clip.getItemCount()!=1)return "";return parse(clip.getItemAt(0).getText(),http);}catch(RuntimeException ex){return "";}}
}