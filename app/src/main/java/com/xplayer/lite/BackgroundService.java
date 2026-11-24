package com.xplayer.lite;
import android.app.PendingIntent;
import android.content.*;
import android.net.Uri;
import androidx.media3.common.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.*;
public class BackgroundService extends MediaSessionService {
 private MediaSession session;
 static volatile ExoPlayer active;
 @Override public void onCreate(){
   super.onCreate();
   ExoPlayer p=new ExoPlayer.Builder(this).build();
   p.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),true);
   p.setHandleAudioBecomingNoisy(true);
   p.setWakeMode(C.WAKE_MODE_LOCAL);
   PendingIntent open=PendingIntent.getActivity(this,7,new Intent(this,LibraryActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
   session=new MediaSession.Builder(this,p).setSessionActivity(open).build();active=p;
 }
 @Override public MediaSession onGetSession(MediaSession.ControllerInfo c){return session;}
 @Override public int onStartCommand(Intent i,int flags,int id){
   if(i!=null&&"xplayer.BACKGROUND".equals(i.getAction())&&i.getData()!=null&&session!=null){
     Uri uri=i.getData();
     String scheme=uri.getScheme();
     if("file".equals(scheme)||"content".equals(scheme)||"https".equals(scheme)||"http".equals(scheme)||"rtsp".equals(scheme)){
       Player p=session.getPlayer();java.util.ArrayList<Uri> q=i.getParcelableArrayListExtra("queue");java.util.ArrayList<MediaItem> list=new java.util.ArrayList<>();list.add(new MediaItem.Builder().setUri(uri).setMediaMetadata(new MediaMetadata.Builder().setTitle(i.getStringExtra("title")).build()).build());if(q!=null)for(Uri u:q){if(!uri.equals(u))list.add(new MediaItem.Builder().setUri(u).build());}p.setMediaItems(list,0,Math.max(0,i.getLongExtra("position",0)));p.setPlaybackSpeed(i.getFloatExtra("speed",1));p.setShuffleModeEnabled(i.getBooleanExtra("shuffle",false));p.prepare();p.play();
     }
     return START_NOT_STICKY;
   }
   return super.onStartCommand(i,flags,id);
 }
 @Override public void onDestroy(){if(session!=null){session.getPlayer().release();session.release();session=null;}active=null;super.onDestroy();}
}
