package com.xplayer.lite;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.*;
import java.util.concurrent.*;

 /** A single platform media session for the visible player. The background service
  * owns its own session only after an explicit playback handoff. */
 final class NowPlaying {
 static final int ID=7401, COMPAT_ID=7402;
 static final String COMPAT_CHANNEL="xplayer.playback.compat.v1";
 static boolean compatDefault(){return Build.VERSION.SDK_INT==30&&("oppo".equalsIgnoreCase(Build.MANUFACTURER)||"oppo".equalsIgnoreCase(Build.BRAND));}
 static final String CHANNEL="xplayer.media.playback.v2";
 static NowPlaying current(){return owner!=null&&!owner.released?owner:null;}
 private static NowPlaying owner;
 interface Controls {
  void play();void pause();void previous();void next();void seek(long pos);void close();
 }
 private final Activity activity;
 private final Controls controls;
 private final NotificationManager manager;
 private final MediaSession session;
 private final PendingIntent open;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final ExecutorService io=Executors.newSingleThreadExecutor();
 private final String prefix;
 private final BroadcastReceiver receiver;
 private boolean released=false,registered=false,serviceRequested=false;
 private Uri uri;
 private String title="";
 private long duration=-1,position=0,lastCompat=0,lastServiceRequest=0;
 private boolean playing,canPrev,canNext,published=false;
 private Bitmap artwork,placeholder;
 private Future<?> artworkTask;
 NowPlaying(Activity activity, Controls controls){
  if(owner!=null)owner.release();
  this.activity=activity;this.controls=controls;manager=(NotificationManager)activity.getSystemService(Context.NOTIFICATION_SERVICE);
  prefix=activity.getPackageName()+".PLAYER."+SystemClock.elapsedRealtimeNanos()+".";
  if(Build.VERSION.SDK_INT>=26){NotificationChannel channel=new NotificationChannel(CHANNEL,"Now playing",NotificationManager.IMPORTANCE_DEFAULT);channel.setDescription("Playback controls for video and audio");channel.setSound(null,null);channel.enableVibration(false);channel.setShowBadge(false);channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);manager.createNotificationChannel(channel);NotificationChannel fallback=new NotificationChannel(COMPAT_CHANNEL,"Playback controls (compatibility)",NotificationManager.IMPORTANCE_DEFAULT);fallback.setDescription("Alternative controls for devices that hide media cards");fallback.setSound(null,null);fallback.enableVibration(false);fallback.setShowBadge(false);fallback.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);manager.createNotificationChannel(fallback);}
  open=PendingIntent.getActivity(activity,7410,new Intent(activity,activity.getClass()).setAction("xplayer.FOREGROUND").addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  session=new MediaSession(activity,"XPlayerForeground");session.setSessionActivity(open);session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS|MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);session.setPlaybackToLocal(new android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).build());
  session.setCallback(new MediaSession.Callback(){
   @Override public void onPlay(){if(!released)controls.play();}
   @Override public void onPause(){if(!released)controls.pause();}
   @Override public void onStop(){if(!released)controls.close();}
   @Override public void onSkipToPrevious(){if(!released&&canPrev)controls.previous();}
   @Override public void onSkipToNext(){if(!released&&canNext)controls.next();}
   @Override public void onSeekTo(long position){if(!released)controls.seek(Math.max(0,position));}
  },handler);
  receiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){if(released||i.getAction()==null||!i.getAction().startsWith(prefix))return;switch(i.getAction().substring(prefix.length())){case "play":controls.play();break;case "pause":controls.pause();break;case "previous":if(canPrev)controls.previous();break;case "next":if(canNext)controls.next();break;case "close":controls.close();break;}}};
  IntentFilter filter=new IntentFilter();for(String action:new String[]{"play","pause","previous","next","close"})filter.addAction(prefix+action);
  String permission=activity.getPackageName()+".MEDIA_CONTROL";if(Build.VERSION.SDK_INT>=33)activity.registerReceiver(receiver,filter,permission,handler,Context.RECEIVER_NOT_EXPORTED);else activity.registerReceiver(receiver,filter,permission,handler);registered=true;owner=this;
  placeholder=Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(placeholder);canvas.drawColor(0xff203c58);Ui.Glyph glyph=new Ui.Glyph("play",0xffdcecff);glyph.setBounds(38,34,90,94);glyph.draw(canvas);artwork=placeholder;
 }
 private PendingIntent action(String name,int code){Intent i=new Intent(prefix+name).setPackage(activity.getPackageName());return PendingIntent.getBroadcast(activity,code,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
 void update(Uri source,String name,long pos,long length,boolean isPlaying,float speed,boolean previous,boolean next){
  if(released||source==null)return;
  boolean trackChanged=!source.equals(uri),metadataChanged=trackChanged||!name.equals(title)||length!=duration;
  boolean changed=!published||metadataChanged||playing!=isPlaying||canPrev!=previous||canNext!=next;
  uri=source;title=name;duration=length;position=Math.max(0,pos);playing=isPlaying;canPrev=previous;canNext=next;
  if(trackChanged){if(artworkTask!=null)artworkTask.cancel(true);artwork=placeholder;loadArt(source);}
  if(metadataChanged)metadata();
  long actions=PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE|PlaybackState.ACTION_STOP;
  if(length>0)actions|=PlaybackState.ACTION_SEEK_TO;if(previous)actions|=PlaybackState.ACTION_SKIP_TO_PREVIOUS;if(next)actions|=PlaybackState.ACTION_SKIP_TO_NEXT;
  session.setPlaybackState(new PlaybackState.Builder().setActions(actions).setState(isPlaying?PlaybackState.STATE_PLAYING:PlaybackState.STATE_PAUSED,Math.max(0,pos),isPlaying?speed:0f,SystemClock.elapsedRealtime()).build());
  if(!session.isActive())session.setActive(true);
  if(changed)publish();long now=SystemClock.elapsedRealtime();if(isPlaying&&(!serviceRequested||!MediaNotificationService.foreground&&now-lastServiceRequest>3000)){serviceRequested=true;lastServiceRequest=now;try{activity.startService(new Intent(activity,MediaNotificationService.class));}catch(RuntimeException e){serviceRequested=false;android.util.Log.w("XPlayer","Media service start rejected",e);}}
 if(ProPrefs.flag(activity,"compat_notification",compatDefault())){if(changed||lastCompat==0||playing&&now-lastCompat>=2000)publishCompatibility();}else if(lastCompat!=0){manager.cancel(COMPAT_ID);lastCompat=0;}
 }
 private void metadata(){MediaMetadata.Builder m=new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,title).putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE,title).putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART,artwork).putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON,artwork).putString(MediaMetadata.METADATA_KEY_ARTIST,"X Player Pro");if(duration>0)m.putLong(MediaMetadata.METADATA_KEY_DURATION,duration);session.setMetadata(m.build());}
 private void loadArt(Uri source){if(!"content".equals(source.getScheme())&&!"file".equals(source.getScheme()))return;try{artworkTask=io.submit(()->{if(Thread.currentThread().isInterrupted())return;Bitmap b=null;try{if(Build.VERSION.SDK_INT>=29)b=activity.getContentResolver().loadThumbnail(source,new android.util.Size(128,128),null);}catch(Exception ignored){}if(b==null&&!Thread.currentThread().isInterrupted()){android.media.MediaMetadataRetriever r=new android.media.MediaMetadataRetriever();try{r.setDataSource(activity,source);byte[] art=r.getEmbeddedPicture();if(art!=null&&art.length<=4*1024*1024){BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(art,0,art.length,o);o.inSampleSize=1;while(Math.max(o.outWidth,o.outHeight)/o.inSampleSize>256)o.inSampleSize*=2;o.inJustDecodeBounds=false;b=BitmapFactory.decodeByteArray(art,0,art.length,o);}}catch(Exception ignored){}finally{try{r.release();}catch(Exception ignored){}}}final Bitmap result=b;if(result!=null&&!Thread.currentThread().isInterrupted())handler.post(()->{if(!released&&source.equals(uri)){artwork=result;metadata();publish();}});});}catch(RejectedExecutionException ignored){}}
 Notification buildNotification(){Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(activity,CHANNEL):new Notification.Builder(activity);
 b.setSmallIcon(android.R.drawable.ic_media_play).setLargeIcon(artwork).setContentTitle(title).setContentIntent(open).setCategory(Notification.CATEGORY_TRANSPORT).setVisibility(Notification.VISIBILITY_PUBLIC).setColor(0xff244b70).setColorized(true).setOnlyAlertOnce(true).setShowWhen(false).setOngoing(playing).setDeleteIntent(action("close",7415));
 java.util.ArrayList<Integer> compact=new java.util.ArrayList<>();int index=0;
 if(canPrev){b.addAction(new Notification.Action.Builder(android.R.drawable.ic_media_previous,"Previous",action("previous",7411)).build());compact.add(index++);}
 b.addAction(new Notification.Action.Builder(playing?android.R.drawable.ic_media_pause:android.R.drawable.ic_media_play,playing?"Pause":"Play",action(playing?"pause":"play",playing?7412:7413)).build());compact.add(index++);
 if(canNext){b.addAction(new Notification.Action.Builder(android.R.drawable.ic_media_next,"Next",action("next",7414)).build());compact.add(index++);}
 b.addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel,"Close",action("close",7415)).build());
 if(Build.VERSION.SDK_INT>=31)b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
 int[] visible=new int[compact.size()];for(int i=0;i<visible.length;i++)visible[i]=compact.get(i);b.setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(visible));return b.build();
 }
 void onForegroundReady(){if(!released){published=true;if(ProPrefs.flag(activity,"compat_notification",compatDefault()))publishCompatibility();}}
 private static String time(long ms){long s=Math.max(0,ms/1000);return String.format(java.util.Locale.US,"%02d:%02d",s/60,s%60);}
 private void publishCompatibility(){if(released)return;lastCompat=SystemClock.elapsedRealtime();Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(activity,COMPAT_CHANNEL):new Notification.Builder(activity);
 b.setSmallIcon(android.R.drawable.ic_media_play).setLargeIcon(artwork).setContentTitle(title).setContentText(time(position)+(duration>0?" / "+time(duration):"")).setContentIntent(open).setCategory(Notification.CATEGORY_TRANSPORT).setVisibility(Notification.VISIBILITY_PUBLIC).setOnlyAlertOnce(true).setShowWhen(false).setOngoing(playing).setDeleteIntent(action("close",7415));
 if(duration>0)b.setProgress(1000,(int)Math.min(1000,position*1000.0/duration),false);
 if(canPrev)b.addAction(new Notification.Action.Builder(android.R.drawable.ic_media_previous,"Previous",action("previous",7411)).build());
 b.addAction(new Notification.Action.Builder(playing?android.R.drawable.ic_media_pause:android.R.drawable.ic_media_play,playing?"Pause":"Play",action(playing?"pause":"play",playing?7412:7413)).build());
 if(canNext)b.addAction(new Notification.Action.Builder(android.R.drawable.ic_media_next,"Next",action("next",7414)).build());else b.addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel,"Close",action("close",7415)).build());
 try{manager.notify(COMPAT_ID,b.build());}catch(RuntimeException e){android.util.Log.w("XPlayer","Compatibility notification unavailable",e);}
 }
 static String notificationStatus(Context c){NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);if(!nm.areNotificationsEnabled())return "App notifications: blocked";String status="App notifications: enabled";if(Build.VERSION.SDK_INT>=26){NotificationChannel a=nm.getNotificationChannel(CHANNEL),b=nm.getNotificationChannel(COMPAT_CHANNEL);status+="\nMedia channel: "+(a==null?"created at first playback":a.getImportance()==NotificationManager.IMPORTANCE_NONE?"blocked":"enabled")+"\nCompatibility channel: "+(b==null?"created at first playback":b.getImportance()==NotificationManager.IMPORTANCE_NONE?"blocked":"enabled");}boolean primary=false,compat=false;for(android.service.notification.StatusBarNotification x:nm.getActiveNotifications()){if(x.getId()==ID)primary=true;if(x.getId()==COMPAT_ID||x.getId()==BackgroundService.BACKGROUND_COMPAT_ID)compat=true;}return status+"\nMedia notification posted: "+primary+"\nCompatibility notification posted: "+compat+"\nForeground service: "+MediaNotificationService.foreground;}
 private void publish(){if(released)return;try{manager.notify(ID,buildNotification());published=true;}catch(Exception e){android.util.Log.w("XPlayer","Media notification unavailable",e);}
 }
 boolean isPublished(){return published&&!released&&session.isActive();}
 MediaSession.Token token(){return session.getSessionToken();}
 void release(){if(released)return;released=true;handler.removeCallbacksAndMessages(null);if(artworkTask!=null)artworkTask.cancel(true);io.shutdownNow();if(registered){try{activity.unregisterReceiver(receiver);}catch(Exception ignored){}registered=false;}session.setActive(false);session.release();if(owner==this){owner=null;activity.stopService(new Intent(activity,MediaNotificationService.class));manager.cancel(ID);manager.cancel(COMPAT_ID);}}
}
