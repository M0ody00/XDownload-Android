package com.xplayer.lite;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.*;
import android.widget.*;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.interfaces.IMedia;
import org.videolan.libvlc.util.MediaBrowser;
import java.util.*;
public class NetworkBrowserActivity extends Activity {
 private int scanGeneration=0;private boolean closed=false;
 private LibVLC engine;private MediaBrowser browser;private WifiManager.MulticastLock lock;private ArrayAdapter<String> adapter;private TextView state;private final ArrayList<Entry> entries=new ArrayList<>();private final ArrayList<Uri> parents=new ArrayList<>();
 private static class Entry{Uri uri;String name;boolean folder;Entry(Uri u,String n,boolean f){uri=u;name=n;folder=f;}}
 @Override public void onCreate(Bundle b){super.onCreate(b);LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(12,12,12,12);state=new TextView(this);state.setText("Network • discovering nearby shares");l.addView(state);Button refresh=new Button(this);refresh.setText("Refresh / Browse network");l.addView(refresh);ListView list=new ListView(this);adapter=new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,new ArrayList<String>());list.setAdapter(adapter);l.addView(list,new LinearLayout.LayoutParams(-1,0,1));setContentView(l);
 list.setOnItemClickListener((p,v,i,id)->{if(i>=entries.size())return;Entry e=entries.get(i);if(e.folder){parents.add(e.uri);scan(e.uri);}else startActivity(new Intent(this,VlcActivity.class).setData(e.uri));});
 refresh.setOnClickListener(v->{parents.clear();scan(null);});
 try{engine=new LibVLC(this,new ArrayList<String>());WifiManager w=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);if(w!=null){lock=w.createMulticastLock("xplayer-network");lock.setReferenceCounted(false);lock.acquire();}scan(null);}catch(Exception|LinkageError e){state.setText("Network engine unavailable: "+e.getMessage());}}
 private void scan(Uri uri){if(engine==null)return;final int generation=++scanGeneration;if(browser!=null)browser.release();entries.clear();adapter.clear();state.setText(uri==null?"Discovering SMB / UPnP / DLNA on local Wi-Fi…":"Browsing "+uri.getScheme());final boolean root=uri==null;browser=new MediaBrowser(engine,new MediaBrowser.EventListener(){public void onMediaAdded(int i,IMedia m){Uri u=m.getUri();if(u==null)return;String name=m.getMeta(IMedia.Meta.Title);if(name==null||name.isEmpty())name=u.getLastPathSegment();if(name==null)name=u.toString();boolean folder=root||m.getType()==IMedia.Type.Directory;final Entry e=new Entry(u,name,folder);runOnUiThread(()->{if(closed||generation!=scanGeneration||isFinishing())return;for(Entry old:entries)if(old.uri.equals(e.uri))return;entries.add(e);adapter.add((e.folder?"▰ ":"▶ ")+e.name);});}public void onMediaRemoved(int i,IMedia m){}public void onBrowseEnd(){runOnUiThread(()->{if(!closed&&generation==scanGeneration)state.setText(entries.size()+" items • refresh if needed");});}});try{if(root)browser.discoverNetworkShares();else browser.browse(uri,0);}catch(Exception e){state.setText("Browse failed: "+e.getMessage());}}
 @Override public void onBackPressed(){if(!parents.isEmpty()){parents.remove(parents.size()-1);scan(parents.isEmpty()?null:parents.get(parents.size()-1));}else super.onBackPressed();}
 @Override protected void onDestroy(){closed=true;scanGeneration++;if(browser!=null)browser.release();if(engine!=null)engine.release();if(lock!=null&&lock.isHeld())lock.release();super.onDestroy();}
}
