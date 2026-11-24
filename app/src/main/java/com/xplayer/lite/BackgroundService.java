package com.xplayer.lite;
import android.app.PendingIntent;
import android.content.*;
import android.net.Uri;
import androidx.media3.common.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.*;
public class BackgroundService extends MediaSessionService {
 private MediaSession session;
 private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
 private long sleepUntil=0;
 private final Runnable sleepCheck=new Runnable(){public void run(){if(session==null)return;if(sleepUntil>0&&android.os.SystemClock.elapsedRealtime()>=sleepUntil){sleepUntil=0;session.getPlayer().pause();}handler.postDelayed(this,1000);}};
 static volatile ExoPlayer active;
 @Override public void onCreate(){
   super.onCreate();
   androidx.media3.datasource.DefaultHttpDataSource.Factory http=new androidx.media3.datasource.DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(false);String ua=ProPrefs.string(this,"user_agent","").trim();if(!ua.isEmpty())http.setUserAgent(ua);ExoPlayer p=new ExoPlayer.Builder(this).setMediaSourceFactory(new androidx.media3.exoplayer.source.DefaultMediaSourceFactory(new androidx.media3.datasource.DefaultDataSource.Factory(this,http))).build();
   p.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),true);
   p.setHandleAudioBecomingNoisy(ProPrefs.flag(this,"noisy",true));
   p.setWakeMode(C.WAKE_MODE_LOCAL);
   PendingIntent open=PendingIntent.getActivity(this,7,new Intent(this,MainActivity.class).putExtra("resume_background",true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
   session=new MediaSession.Builder(this,p).setSessionActivity(open).build();active=p;handler.post(sleepCheck);
 }
 @Override public MediaSession onGetSession(MediaSession.ControllerInfo c){return session;}
 @Override public int onStartCommand(Intent i,int flags,int id){
   if(i!=null&&"xplayer.STOP".equals(i.getAction())){stopSelf();return START_NOT_STICKY;}if(i!=null&&"xplayer.BACKGROUND".equals(i.getAction())&&i.getData()!=null&&session!=null){
     Uri uri=i.getData();
     String scheme=uri.getScheme();
     if("file".equals(scheme)||"content".equals(scheme)||"https".equals(scheme)||"http".equals(scheme)||"rtsp".equals(scheme)){
       Player p=session.getPlayer();java.util.ArrayList<Uri> q=i.getParcelableArrayListExtra("queue");java.util.ArrayList<MediaItem> list=new java.util.ArrayList<>();if(q==null||!q.contains(uri)){q=new java.util.ArrayList<>();q.add(uri);}int index=q.indexOf(uri);for(Uri u:q){String title=u.equals(uri)?i.getStringExtra("title"):null;if(title==null){try(android.database.Cursor c=getContentResolver().query(u,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())title=c.getString(0);}catch(Exception ignored){}if(title==null)title=u.getLastPathSegment();}list.add(new MediaItem.Builder().setUri(u).setMediaMetadata(new MediaMetadata.Builder().setTitle(title).build()).build());}p.setMediaItems(list,index,Math.max(0,i.getLongExtra("position",0)));p.setPlaybackSpeed(i.getFloatExtra("speed",1));p.setShuffleModeEnabled(i.getBooleanExtra("shuffle",false));p.setRepeatMode(i.getIntExtra("repeat",Player.REPEAT_MODE_OFF));p.setVolume(i.getFloatExtra("volume",1));sleepUntil=i.getLongExtra("sleep_until",0);p.prepare();p.play();
     }
     return START_NOT_STICKY;
   }
   return super.onStartCommand(i,flags,id);
 }
 @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);if(session!=null){session.getPlayer().release();session.release();session=null;}active=null;super.onDestroy();}
}
