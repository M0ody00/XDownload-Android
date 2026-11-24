package com.xplayer.lite;
import android.content.*;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;
@RunWith(AndroidJUnit4.class)
public class LibraryTest {
 @Test public void sizesKeepNumbersAndUnitsTogether(){assertEquals("340 MB",LibraryActivity.sizeText(340000000));assertEquals("3.5 GB",LibraryActivity.sizeText(3500000000L));assertEquals("1.8 MB",LibraryActivity.sizeText(1800000));assertEquals("13 GB",LibraryActivity.sizeText(13000000000L));assertEquals("0 B",LibraryActivity.sizeText(0));}
 @Test public void discoversFoldersAndMediaAutomatically() throws Exception {
  if(Build.VERSION.SDK_INT<29)return;
  Context app=InstrumentationRegistry.getInstrumentation().getTargetContext();
  Context test=InstrumentationRegistry.getInstrumentation().getContext();
  Uri video=null,audio=null;
  try{
    video=seed(app,test,MediaStore.Video.Media.EXTERNAL_CONTENT_URI,"sample.mp4","xplayer-library-test.mp4","video/mp4","Movies/XPlayerTestFolder");
    audio=seed(app,test,MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,"sample.mp3","xplayer-library-test.mp3","audio/mpeg","Music/XPlayerTestAudio");
    try(ActivityScenario<LibraryActivity> s=ActivityScenario.launch(LibraryActivity.class)){
      AtomicBoolean ok=new AtomicBoolean();long until=android.os.SystemClock.elapsedRealtime()+15000;
      while(android.os.SystemClock.elapsedRealtime()<until){s.onActivity(a->ok.set(a.hasMediaForTest("xplayer-library-test.mp4")&&a.hasMediaForTest("xplayer-library-test.mp3")&&a.hasFolderForTest("XPlayerTestFolder")));if(ok.get())break;Thread.sleep(200);}
      assertTrue("Video and audio should be discovered automatically with video folder",ok.get());
      s.onActivity(a->{assertTrue("Folder details must be left-aligned directly below title",a.folderMetadataAlignedForTest("XPlayerTestFolder"));a.audioForTest();assertTrue("Audio folder should be listed",a.hasFolderForTest("XPlayerTestAudio"));assertTrue(a.rowsForTest()>0);});
      InstrumentationRegistry.getInstrumentation().waitForIdleSync();android.graphics.Bitmap screenshot=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(screenshot);File evidence=new File(app.getFilesDir(),"ui-evidence");evidence.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(evidence,"folder-metadata.png"))){assertTrue(screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));}finally{screenshot.recycle();}
      android.util.Log.i("XPlayerTest","PASS automatic MediaStore discovery and folder filtering");
    }
  } finally {if(video!=null)app.getContentResolver().delete(video,null,null);if(audio!=null)app.getContentResolver().delete(audio,null,null);}
 }
 private Uri seed(Context app,Context test,Uri base,String asset,String name,String mime,String path)throws Exception{
  ContentValues v=new ContentValues();v.put(MediaStore.MediaColumns.DISPLAY_NAME,name);v.put(MediaStore.MediaColumns.MIME_TYPE,mime);v.put(MediaStore.MediaColumns.RELATIVE_PATH,path);v.put(MediaStore.MediaColumns.IS_PENDING,1);
  Uri u=app.getContentResolver().insert(base,v);assertNotNull(u);
  try(InputStream in=test.getAssets().open(asset);OutputStream out=app.getContentResolver().openOutputStream(u)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
  ContentValues done=new ContentValues();done.put(MediaStore.MediaColumns.IS_PENDING,0);app.getContentResolver().update(u,done,null,null);return u;
 }
}
