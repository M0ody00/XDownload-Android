package com.xplayer.lite;
import android.app.PendingIntent;
import android.content.*;
import android.net.Uri;
import androidx.media3.common.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.*;
public class BackgroundService extends MediaSessionService {
 private MediaSession session;
 private static final SessionCommand CLOSE=new SessionCommand("xplayer.CLOSE",android.os.Bundle.EMPTY);
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
   session=new MediaSession.Builder(this,p).setSessionActivity(open).setCustomLayout(java.util.Collections.singletonList(new CommandButton.Builder().setDisplayName("Close").setIconResId(android.R.drawable.ic_menu_close_clear_cancel).setSessionCommand(CLOSE).build())).setCallback(new MediaSession.Callback(){
 @Override public MediaSession.ConnectionResult onConnect(MediaSession session,MediaSession.ControllerInfo controller){return new MediaSession.ConnectionResult.AcceptedResultBuilder(session).setAvailableSessionCommands(MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().add(CLOSE).build()).build();}
 @Override public com.google.common.util.concurrent.ListenableFuture<SessionResult> onCustomCommand(MediaSession session,MediaSession.ControllerInfo controller,SessionCommand command,android.os.Bundle args){if(CLOSE.equals(command)){session.getPlayer().pause();session.getPlayer().clearMediaItems();stopSelf();return com.google.common.util.concurrent.Futures.immediateFuture(new SessionResult(SessionResult.RESULT_SUCCESS));}return com.google.common.util.concurrent.Futures.immediateFuture(new SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED));}
 }).build();active=p;handler.post(sleepCheck);
 }
 @Override public MediaSession onGetSession(MediaSession.ControllerInfo c){return session;}
 @Override public int onStartCommand(Intent i,int flags,int id){
   if(i!=null&&"xplayer.STOP".equals(i.getAction())){stopSelf();return START_NOT_STICKY;}if(i!=null&&"xplayer.BACKGROUND".equals(i.getAction())&&i.getData()!=null&&session!=null){
     Uri uri=i.getData();
     String scheme=uri.getScheme();
     if("file".equals(scheme)||"content".equals(scheme)||"https".equals(scheme)||"http".equals(scheme)||"rtsp".equals(scheme)){
       Player p=session.getPlayer();java.util.ArrayList<Uri> q=i.getParcelableArrayListExtra("queue");java.util.ArrayList<MediaItem> list=new java.util.ArrayList<>();if(q==null||!q.contains(uri)){q=new java.util.ArrayList<>();q.add(uri);}int index=q.indexOf(uri);for(Uri u:q){String title=u.equals(uri)?i.getStringExtra("title"):null;if(title==null&&!"content".equals(u.getScheme()))title=u.getLastPathSegment();list.add(new MediaItem.Builder().setUri(u).setMediaMetadata(new MediaMetadata.Builder().setTitle(title).build()).build());}p.setMediaItems(list,index,Math.max(0,i.getLongExtra("position",0)));p.setPlaybackSpeed(i.getFloatExtra("speed",1));p.setShuffleModeEnabled(i.getBooleanExtra("shuffle",false));p.setRepeatMode(i.getIntExtra("repeat",Player.REPEAT_MODE_OFF));if(!ProPrefs.flag(this,"auto_next",true)&&q.size()>1){MediaItem current=list.get(index);p.setMediaItem(current,Math.max(0,i.getLongExtra("position",0)));}p.setVolume(i.getFloatExtra("volume",1));sleepUntil=i.getLongExtra("sleep_until",0);p.prepare();p.play();
     }
     return START_NOT_STICKY;
   }
   return super.onStartCommand(i,flags,id);
 }
 @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);if(session!=null){Player p=session.getPlayer();MediaItem m=p.getCurrentMediaItem();if(m!=null&&m.localConfiguration!=null){Uri u=m.localConfiguration.uri;if("content".equals(u.getScheme())||"file".equals(u.getScheme())){long pos=p.getCurrentPosition();if(p.getPlaybackState()==Player.STATE_ENDED)pos=0;getSharedPreferences("MainActivity",MODE_PRIVATE).edit().putLong("p:"+u,pos).apply();}}session.getPlayer().release();session.release();session=null;}active=null;super.onDestroy();}
}
