package com.xplayer.lite;
import android.content.Context;
import android.net.Uri;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class ProTest {
 @Test public void favoritesAndSettingsPersist(){Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();Uri u=Uri.parse("content://media/external/video/media/987654");if(ProPrefs.favorite(c,u))ProPrefs.toggle(c,u);assertTrue(ProPrefs.toggle(c,u));assertTrue(ProPrefs.favorite(c,u));assertFalse(ProPrefs.toggle(c,u));assertFalse(ProPrefs.favorite(c,u));ProPrefs.get(c).edit().putInt("seek",2).commit();assertEquals(2,ProPrefs.number(c,"seek",1));ProPrefs.get(c).edit().remove("seek").commit();}
 @Test public void settingsNavigation(){try(ActivityScenario<SettingsActivity> s=ActivityScenario.launch(SettingsActivity.class)){s.onActivity(a->{for(int i=0;i<10;i++){a.show(i);a.onBackPressed();}});}}
 @Test public void playerOverlayUsesFullScreen(){try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){s.onActivity(a->{android.view.View root=a.findViewById(android.R.id.content);assertTrue(root instanceof android.widget.FrameLayout);assertTrue(root.getHeight()>0);assertTrue(hasDescription(root,"Player tools"));assertTrue(hasDescription(root,"Play or pause"));});}}
 @Test public void libraryNavigationVisible(){try(ActivityScenario<LibraryActivity> s=ActivityScenario.launch(LibraryActivity.class)){s.onActivity(a->{android.view.View root=a.findViewById(android.R.id.content);assertTrue(hasDescription(root,"Search"));assertTrue(hasDescription(root,"View and sort"));assertTrue(hasDescription(root,"Resume playback")||hasDescription(root,"استكمال المشاهدة"));});}}
 private boolean hasDescription(android.view.View v,String s){if(s.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return true;if(v instanceof android.view.ViewGroup){android.view.ViewGroup g=(android.view.ViewGroup)v;for(int i=0;i<g.getChildCount();i++)if(hasDescription(g.getChildAt(i),s))return true;}return false;}
 @Test public void captureNewScreens()throws Exception{try(ActivityScenario<LibraryActivity> s=ActivityScenario.launch(LibraryActivity.class)){capture("library.png");s.onActivity(a->{android.view.View v=find(a.findViewById(android.R.id.content),"View and sort");assertNotNull(v);v.performClick();});capture("view-sort.png");}try(ActivityScenario<SettingsActivity> s=ActivityScenario.launch(SettingsActivity.class)){capture("settings.png");s.onActivity(a->a.show(7));capture("about.png");}try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){capture("player.png");s.onActivity(a->{android.view.View v=find(a.findViewById(android.R.id.content),"Player tools");assertNotNull(v);v.performClick();});capture("player-tools.png");}}
 private android.view.View find(android.view.View v,String s){if(v.getContentDescription()!=null&&s.contentEquals(v.getContentDescription()))return v;if(v instanceof android.view.ViewGroup){android.view.ViewGroup g=(android.view.ViewGroup)v;for(int i=0;i<g.getChildCount();i++){android.view.View hit=find(g.getChildAt(i),s);if(hit!=null)return hit;}}return null;}
 private void capture(String name)throws Exception{InstrumentationRegistry.getInstrumentation().waitForIdleSync();android.graphics.Bitmap b=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(b);Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();java.io.File dir=new java.io.File(c.getFilesDir(),"ui-evidence");dir.mkdirs();try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(dir,name))){assertTrue(b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));}finally{b.recycle();}}
}
