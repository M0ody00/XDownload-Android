package com.xplayer.lite;
import android.content.*;
import android.net.Uri;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;
@RunWith(AndroidJUnit4.class)
public class AdvancedTest{
 private Context app(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
 private File sample(String name)throws Exception{File f=new File(app().getCacheDir(),"advanced-"+name);try(InputStream in=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open(name);OutputStream out=new FileOutputStream(f)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}return f;}
 @Test public void playlistAndBackup()throws Exception{Context c=app();String n="test-playlist";ProPrefs.deletePlaylist(c,n);Uri u=Uri.parse("content://media/external/video/media/99999");assertTrue(ProPrefs.createPlaylist(c,n));assertTrue(ProPrefs.playlistAdd(c,n,u));assertTrue(ProPrefs.list(c,ProPrefs.playlistKey(n)).contains(u.toString()));ProPrefs.playlistRemove(c,n,u);assertTrue(ProPrefs.list(c,ProPrefs.playlistKey(n)).isEmpty());ProPrefs.deletePlaylist(c,n);ProPrefs.get(c).edit().putInt("accent",2).commit();String backup=Backup.exportJson(c);ProPrefs.get(c).edit().putInt("accent",0).commit();Backup.restore(c,backup);assertEquals(2,ProPrefs.number(c,"accent",0));ProPrefs.get(c).edit().remove("accent").commit();}
 @Test public void vlcSoftwarePlayback()throws Exception{File f=sample("sample.mp4");try(ActivityScenario<VlcActivity> s=ActivityScenario.launch(new Intent(app(),VlcActivity.class).setData(Uri.fromFile(f)).putExtra("software",true))){AtomicBoolean playing=new AtomicBoolean();long deadline=android.os.SystemClock.elapsedRealtime()+25000;while(android.os.SystemClock.elapsedRealtime()<deadline){s.onActivity(a->playing.set(a.player!=null&&!a.failed&&a.player.isPlaying()&&a.player.getTime()>300));if(playing.get())break;Thread.sleep(150);}assertTrue("LibVLC SW playback must advance",playing.get());android.util.Log.i("XPlayerTest","PASS LibVLC software playback");}finally{f.delete();}}
 @Test public void mp3Export()throws Exception{File input=sample("sample.mp4"),output=new File(app().getCacheDir(),"advanced-result.mp3");try{ExportActivity.encodeMp3(input,output);assertTrue(output.length()>1000);android.media.MediaExtractor e=new android.media.MediaExtractor();try{e.setDataSource(output.getAbsolutePath());assertTrue(e.getTrackCount()>0);assertEquals("audio/mpeg",e.getTrackFormat(0).getString(android.media.MediaFormat.KEY_MIME));}finally{e.release();}android.util.Log.i("XPlayerTest","PASS MP3 export with MPEG audio track");}finally{input.delete();output.delete();}}
}
