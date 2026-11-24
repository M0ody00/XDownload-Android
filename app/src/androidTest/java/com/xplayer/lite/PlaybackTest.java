package com.xplayer.lite;
import android.content.Context;
import android.net.Uri;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class PlaybackTest {
    @Test public void mp4Video() throws Exception {play("sample.mp4",true);}
    @Test public void mkvVideo() throws Exception {play("sample.mkv",true);}
    @Test public void webmVideo() throws Exception {play("sample.webm",true);}
    @Test public void mp3Audio() throws Exception {play("sample.mp3",false);}
    @Test public void flacAudio() throws Exception {play("sample.flac",false);}
    @Test public void wavAudio() throws Exception {play("sample.wav",false);}
    private void play(String name,boolean video) throws Exception {
        Context target=InstrumentationRegistry.getInstrumentation().getTargetContext();
        Context test=InstrumentationRegistry.getInstrumentation().getContext();
        File f=new File(target.getCacheDir(),name);
        try(InputStream in=test.getAssets().open(name);OutputStream out=new FileOutputStream(f)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->a.openMedia(Uri.fromFile(f),false));
            AtomicBoolean played=new AtomicBoolean(false);AtomicReference<String> error=new AtomicReference<>();
            long deadline=android.os.SystemClock.elapsedRealtime()+25000;
            while(android.os.SystemClock.elapsedRealtime()<deadline){
                scenario.onActivity(a->{
                    if(a.player.getPlayerError()!=null)error.set(a.player.getPlayerError().toString());
                    if(a.player.isPlaying()&&a.player.getCurrentPosition()>300&&(!video||a.renderedFrame))played.set(true);
                });
                if(error.get()!=null)fail(name+": "+error.get());
                if(played.get())break;
                Thread.sleep(150);
            }
            assertTrue(name+" did not play with advancing position"+(video?" and rendered frame":""),played.get());
            android.util.Log.i("XPlayerTest","PASS actual playback: "+name);
        }finally{f.delete();}
    }
}
