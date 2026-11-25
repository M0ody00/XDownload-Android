package com.xdownload.mobile;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
/** Optional Android credential gate, not encryption of external files. */
public abstract class SecureActivity extends Activity{
 static final long AWAY_MS=30000L;
 private static final int AUTH_GATE=9521,AUTH_ENABLE=9522,AUTH_DISABLE=9523;
 private static boolean unlocked=false;private static long awayAt=-1;private static int started=0;
 private boolean requesting=false;
 static boolean needsAuth(boolean hasUnlocked,long away,long now){return !hasUnlocked||away>=0&&(now<away||now-away>=AWAY_MS);}
 File lockMarker(){return new File(getNoBackupFilesDir(),"screen-lock.enabled");}
 boolean screenLockEnabled(){return lockMarker().isFile();}
 boolean screenAccessAllowed(){return !screenLockEnabled()||unlocked;}
 String lockText(String ar,String en){return Store.settings(this).getBoolean("english",false)?en:ar;}
 void hideContent(boolean hide){getWindow().getDecorView();View content=findViewById(android.R.id.content);if(content!=null)content.setVisibility(hide?View.INVISIBLE:View.VISIBLE);}
 @Override protected void onCreate(Bundle b){super.onCreate(b);if(screenLockEnabled()){getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);hideContent(true);}}
 @Override protected void onStart(){super.onStart();long now=SystemClock.elapsedRealtime();if(screenLockEnabled()&&needsAuth(unlocked,awayAt,now))unlocked=false;started++;awayAt=-1;if(screenLockEnabled()){getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);hideContent(!unlocked);}}
 @Override protected void onResume(){super.onResume();if(screenLockEnabled()&&!unlocked){hideContent(true);if(!requesting&&!isFinishing())request(AUTH_GATE);}else hideContent(false);}
 @Override protected void onStop(){if(started>0)started--;if(started==0)awayAt=SystemClock.elapsedRealtime();super.onStop();}
 void request(int code){KeyguardManager keyguard=getSystemService(KeyguardManager.class);if(keyguard==null||!keyguard.isDeviceSecure()){Toast.makeText(this,lockText("فعّل قفل شاشة أندرويد أولًا","Set a secure Android screen lock first"),Toast.LENGTH_LONG).show();if(code==AUTH_GATE)finish();return;}Intent i=keyguard.createConfirmDeviceCredentialIntent("X Download Pro",lockText("تأكيد قفل الجهاز","Confirm device credential"));if(i==null){if(code==AUTH_GATE)finish();return;}try{requesting=true;startActivityForResult(i,code);}catch(RuntimeException e){requesting=false;if(code==AUTH_GATE)finish();else Toast.makeText(this,lockText("تعذر فتح المصادقة","Authentication unavailable"),Toast.LENGTH_SHORT).show();}}
 void changeScreenLock(boolean enable){if(!requesting)request(enable?AUTH_ENABLE:AUTH_DISABLE);}
 @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(req!=AUTH_GATE&&req!=AUTH_ENABLE&&req!=AUTH_DISABLE)return;requesting=false;if(result!=RESULT_OK){if(req==AUTH_GATE)finish();return;}
  try{if(req==AUTH_ENABLE&&!lockMarker().isFile()&&!lockMarker().createNewFile())throw new IOException("Enable failed");if(req==AUTH_DISABLE&&lockMarker().exists()&&!lockMarker().delete())throw new IOException("Disable failed");}catch(IOException e){Toast.makeText(this,lockText("تعذر تغيير القفل","Cannot change lock"),Toast.LENGTH_SHORT).show();return;}
  unlocked=true;awayAt=-1;if(req==AUTH_ENABLE)getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);if(req==AUTH_DISABLE)getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
  hideContent(false);onScreenUnlocked();if(req!=AUTH_GATE)Toast.makeText(this,req==AUTH_ENABLE?lockText("تم تفعيل القفل","Screen lock enabled"):lockText("تم إلغاء القفل","Screen lock disabled"),Toast.LENGTH_SHORT).show();
 }
 protected void onScreenUnlocked(){}
}
