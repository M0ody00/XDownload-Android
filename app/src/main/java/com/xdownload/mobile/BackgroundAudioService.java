package com.xdownload.mobile;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.media.*;
import android.media.session.*;
import android.net.Uri;
import android.os.*;

/** Explicit single-track audio handoff; no automatic restart after process death. */
public final class BackgroundAudioService extends Service {
 static final String CHANNEL="xdownload.audio.v1";
 static final int NOTICE=90420;
 static volatile boolean playingForTest=false;
 static volatile String currentId="";
 final Handler main=new Handler(Looper.getMainLooper());
 MediaPlayer player;final AudioEffects effects=new AudioEffects(); MediaSession session; AudioManager audio; AudioFocusRequest focus;
 Store.Job job; boolean ready,held,requested,noisyRegistered;
 int initialPosition;
 final BroadcastReceiver noisy=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){if(AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(i.getAction()))pause();}};
 static boolean isAudio(Store.Job job){return LocalPlayerActivity.playable(job)&&"Music".equals(HttpEngine.category(job.name,job.mime));}
 static void launch(Context c,String id,int offset){c.startForegroundService(new Intent(c,BackgroundAudioService.class).setAction("load").putExtra("id",id).putExtra("offset",offset));}
 @Override public void onCreate(){super.onCreate();
  NotificationManager nm=getSystemService(NotificationManager.class);
  NotificationChannel ch=new NotificationChannel(CHANNEL,"Audio playback",NotificationManager.IMPORTANCE_LOW);ch.setShowBadge(false);nm.createNotificationChannel(ch);
  audio=getSystemService(AudioManager.class);
  focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
    .setOnAudioFocusChangeListener(change->{if(change==AudioManager.AUDIOFOCUS_LOSS||change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT||change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)pause();},main).build();
  session=new MediaSession(this,"XDownloadAudio");session.setCallback(new MediaSession.Callback(){
   @Override public void onPlay(){resume();}
   @Override public void onPause(){pause();}
   @Override public void onStop(){finishPlayback();}
   @Override public void onSeekTo(long pos){if(ready&&player!=null){try{player.seekTo((int)Math.max(0,Math.min(pos,player.getDuration())));publish();}catch(RuntimeException ignored){}}}
  },main);session.setActive(true);
  IntentFilter f=new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
  if(Build.VERSION.SDK_INT>=33)registerReceiver(noisy,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(noisy,f);
  noisyRegistered=true;
 }
 @Override public IBinder onBind(Intent i){return null;}
 @Override public int onStartCommand(Intent i,int flags,int startId){
  String action=i==null?null:i.getAction();
  if("load".equals(action)){startForeground(NOTICE,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
   Store.Job selected=Store.get(this).getJob(i.getStringExtra("id"));
   if(!isAudio(selected)){finishPlayback();return START_NOT_STICKY;}load(selected,Math.max(0,i.getIntExtra("offset",0)));
  }else if(job==null){stopSelf(startId);}else if("play".equals(action))resume();else if("pause".equals(action))pause();else if("stop".equals(action))finishPlayback();
  return START_NOT_STICKY;
 }
 void load(Store.Job selected,int offset){
  remember();releasePlayer();job=selected;currentId=job.id;initialPosition=offset;requested=true;ready=false;
  session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,job.name).build());publish();
  try{MediaPlayer next=new MediaPlayer();player=next;
   next.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());next.setWakeMode(this,PowerManager.PARTIAL_WAKE_LOCK);
   next.setDataSource(this,Uri.parse(job.uri));
   next.setOnPreparedListener(mp->{if(mp!=player)return;effects.attach(this,mp.getAudioSessionId());ready=true;int d=mp.getDuration();session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,job.name).putLong(MediaMetadata.METADATA_KEY_DURATION,d).build());if(initialPosition>0&&d>0)mp.seekTo(Math.min(initialPosition,Math.max(0,d-1)));if(requested)resume();else publish();});
   next.setOnCompletionListener(mp->{if(mp!=player)return;if(job!=null){job.playbackMs=0;Store.get(this).save();}ready=false;finishPlayback();});
   next.setOnErrorListener((mp,what,extra)->{if(mp==player)finishPlayback();return true;});
   next.prepareAsync();
  }catch(Exception e){finishPlayback();}
 }
 void resume(){requested=true;if(!ready||player==null){publish();return;}try{
  if(!held)held=audio!=null&&audio.requestAudioFocus(focus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
  if(!held){requested=false;publish();return;}
  player.start();playingForTest=true;publish();
 }catch(RuntimeException ex){finishPlayback();}}
 void pause(){requested=false;if(ready&&player!=null)try{player.pause();}catch(RuntimeException ignored){}playingForTest=false;remember();releaseFocus();publish();}
 void remember(){if(job!=null&&ready&&player!=null)try{int pos=player.getCurrentPosition(),d=player.getDuration();job.playbackMs=d>0&&d-pos<1500?0:Math.max(0,pos);Store.get(this).save();}catch(RuntimeException ignored){}}
 void releaseFocus(){if(held&&audio!=null)audio.abandonAudioFocusRequest(focus);held=false;}
 void releasePlayer(){effects.release();ready=false;playingForTest=false;releaseFocus();if(player!=null){player.release();player=null;}}
 PendingIntent command(String action,int request){return PendingIntent.getService(this,request,new Intent(this,BackgroundAudioService.class).setAction(action),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);}
 Notification notification(){
  String name=job==null?"X Download Audio":job.name;boolean playing=playingForTest;
  PendingIntent open=PendingIntent.getActivity(this,90421,new Intent(this,MainActivity.class).putExtra("downloads",true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
  Notification.Builder b=new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_media_play).setContentTitle(name).setContentText(playing?"Playing audio":ready?"Paused":"Preparing audio").setContentIntent(open).setOnlyAlertOnce(true).setOngoing(playing).setVisibility(Notification.VISIBILITY_PRIVATE);
  b.addAction(new Notification.Action.Builder(playing?android.R.drawable.ic_media_pause:android.R.drawable.ic_media_play,playing?"Pause":"Play",command(playing?"pause":"play",90422)).build());
  b.addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel,"Stop",command("stop",90423)).build());
  if(session!=null)b.setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0,1));return b.build();
 }
 void publish(){if(job==null||session==null)return;long pos=0;if(ready&&player!=null)try{pos=player.getCurrentPosition();}catch(RuntimeException ignored){}
  int state=!ready?PlaybackState.STATE_BUFFERING:playingForTest?PlaybackState.STATE_PLAYING:PlaybackState.STATE_PAUSED;
  session.setPlaybackState(new PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE|PlaybackState.ACTION_STOP|PlaybackState.ACTION_SEEK_TO).setState(state,pos,playingForTest?1f:0f).build());
  getSystemService(NotificationManager.class).notify(NOTICE,notification());
 }
 void finishPlayback(){remember();releasePlayer();job=null;currentId="";stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
 @Override public void onDestroy(){remember();releasePlayer();job=null;currentId="";if(noisyRegistered)unregisterReceiver(noisy);if(session!=null){session.setActive(false);session.release();session=null;}main.removeCallbacksAndMessages(null);super.onDestroy();}
}
