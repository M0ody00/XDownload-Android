package com.xplayer.lite;
import android.app.*;
import android.content.*;
import android.os.*;
/** Keep the visible player's platform media session associated with a real foreground
 * media-playback service, including on Android 11 OEM system media panels. */
public final class MediaNotificationService extends Service {
 static volatile boolean foreground=false;
 @Override public IBinder onBind(Intent intent){return null;}
 @Override public int onStartCommand(Intent intent,int flags,int id){
   NowPlaying owner=NowPlaying.current();
   if(owner==null){stopSelfResult(id);return START_NOT_STICKY;}
   try{
     Notification n=owner.buildNotification();
     if(Build.VERSION.SDK_INT>=29)startForeground(NowPlaying.ID,n,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
     else startForeground(NowPlaying.ID,n);
     foreground=true;owner.onForegroundReady();
   }catch(RuntimeException e){android.util.Log.e("XPlayer","Unable to promote media notification",e);foreground=false;stopSelfResult(id);}
   return START_NOT_STICKY;
 }
 @Override public void onDestroy(){foreground=false;stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
}
