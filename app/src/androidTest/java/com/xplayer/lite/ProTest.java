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
 @Test public void settingsNavigation(){try(ActivityScenario<SettingsActivity> s=ActivityScenario.launch(SettingsActivity.class)){s.onActivity(a->{for(int i=0;i<8;i++){a.show(i);a.onBackPressed();}});}}
}
