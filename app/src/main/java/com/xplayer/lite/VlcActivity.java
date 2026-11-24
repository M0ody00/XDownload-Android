package com.xplayer.lite;
import android.app.*;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;
import java.util.ArrayList;
import java.util.Locale;
public class VlcActivity extends Activity {
 LibVLC engine; MediaPlayer player; boolean started,failed;
 private TextView info;
 private SeekBar seek;
 private VLCVideoLayout video;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private long audioOffset=0,subtitleOffset=0,startAt=0;
 private boolean tracking=false,software=true;
 private Uri source;
 private final ArrayList<org.videolan.libvlc.RendererDiscoverer> discoverers=new ArrayList<>();
 private final java.util.LinkedHashMap<String,org.videolan.libvlc.RendererItem> renderers=new java.util.LinkedHashMap<>();
 private android.net.wifi.WifiManager.MulticastLock multicast;
 private AlertDialog castDialog;
 private android.widget.ArrayAdapter<String> castAdapter;
 private final Runnable tick=new Runnable(){public void run(){if(player==null)return;long len=player.getLength(),pos=player.getTime();if(len>0&&!tracking)seek.setProgress((int)Math.min(1000,Math.max(0,pos*1000/len)));if(!failed)info.setText((software?"SW":"HW")+" • VLC   "+Math.max(0,pos/1000)+" / "+Math.max(0,len/1000)+" s");handler.postDelayed(this,400);}};
 private Button button(String title,Runnable r){Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setOnClickListener(v->r.run());return b;}
 private void row(LinearLayout root,Button... buttons){LinearLayout r=new LinearLayout(this);for(Button b:buttons)r.addView(b,new LinearLayout.LayoutParams(0,-2,1));root.addView(r);}
 @Override public void onCreate(Bundle b){
   super.onCreate(b);source=getIntent().getData();startAt=getIntent().getLongExtra("position",0);software=getIntent().getBooleanExtra("software",true);
   LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xff121923);
   info=new TextView(this);info.setTextColor(Color.WHITE);info.setTextSize(15);info.setPadding(12,12,12,12);info.setText("LibVLC • Software");root.addView(info);
   video=new VLCVideoLayout(this);root.addView(video,new LinearLayout.LayoutParams(-1,0,1));seek=new SeekBar(this);seek.setMax(1000);root.addView(seek);
   seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){tracking=true;}public void onStopTrackingTouch(SeekBar b){if(player!=null&&player.getLength()>0)player.setTime(player.getLength()*b.getProgress()/1000);tracking=false;}public void onProgressChanged(SeekBar b,int p,boolean user){}});
   row(root,button("−10s",()->jump(-10000)),button("Play / Pause",()->{if(player!=null){if(player.isPlaying())player.pause();else player.play();}}),button("+10s",()->jump(10000)));
   row(root,button("HW / SW",()->{software=!software;startAt=player==null?0:player.getTime();startMedia();}),button("Speed",()->new AlertDialog.Builder(this).setItems(ProPrefs.speeds,(d,w)->{if(player!=null)player.setRate(ProPrefs.rates[w]);}).show()),button("Sync",()->sync()));
   row(root,button("Subtitle file",()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivityForResult(i,19);}),button("Cast",()->castMenu()),button("Back",this::finish));setContentView(root);
   if(source==null){info.setText("No media");return;}
   try{engine=new LibVLC(this,new ArrayList<String>());player=new MediaPlayer(engine);player.attachViews(video,null,true,false);
     player.setEventListener(event->{if(event.type==MediaPlayer.Event.Playing){started=true;runOnUiThread(()->{if(player!=null){if(startAt>0){player.setTime(startAt);startAt=0;}player.setAudioDelay(audioOffset);player.setSpuDelay(subtitleOffset);}});}else if(event.type==MediaPlayer.Event.EncounteredError){failed=true;runOnUiThread(()->info.setText("LibVLC: media could not be opened"));}});
     startMedia();handler.post(tick);
   }catch(Exception|LinkageError e){failed=true;info.setText("LibVLC unavailable: "+e.getClass().getSimpleName());}
 }
 private void castMenu(){if(engine==null||player==null)return;
  if(discoverers.isEmpty())try{android.net.wifi.WifiManager wifi=(android.net.wifi.WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);if(wifi!=null){multicast=wifi.createMulticastLock("xplayer-cast");multicast.setReferenceCounted(false);multicast.acquire();}
  org.videolan.libvlc.RendererDiscoverer.Description[] services=org.videolan.libvlc.RendererDiscoverer.list(engine);
  if(services!=null)for(org.videolan.libvlc.RendererDiscoverer.Description service:services){org.videolan.libvlc.RendererDiscoverer d=new org.videolan.libvlc.RendererDiscoverer(engine,service.name);d.setEventListener(e->{org.videolan.libvlc.RendererItem item=e.getItem();if(item==null)return;runOnUiThread(()->{if(player==null)return;if(e.type==org.videolan.libvlc.RendererDiscoverer.Event.ItemAdded)renderers.put(item.name,item);else if(e.type==org.videolan.libvlc.RendererDiscoverer.Event.ItemDeleted)renderers.remove(item.name);refreshCast();});});if(d.start())discoverers.add(d);else d.release();}}
  catch(Exception e){Toast.makeText(this,"Discovery: "+e.getMessage(),Toast.LENGTH_SHORT).show();}
  castAdapter=new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,new ArrayList<String>());refreshCast();
  castDialog=new AlertDialog.Builder(this).setTitle("Cast • devices on same Wi-Fi").setAdapter(castAdapter,(d,w)->{if(player==null)return;player.pause();if(w==0)player.setRenderer(null);else{ArrayList<org.videolan.libvlc.RendererItem> items=new ArrayList<>(renderers.values());if(w-1<items.size())player.setRenderer(items.get(w-1));}player.play();}).setNegativeButton("Close",null).show();
 }
 private void refreshCast(){if(castAdapter==null)return;castAdapter.clear();castAdapter.add("This phone");for(org.videolan.libvlc.RendererItem r:renderers.values())castAdapter.add(r.displayName);castAdapter.notifyDataSetChanged();}
 private void stopDiscovery(){for(org.videolan.libvlc.RendererDiscoverer d:discoverers){try{d.setEventListener(null);d.stop();d.release();}catch(Exception ignored){}}discoverers.clear();renderers.clear();if(multicast!=null&&multicast.isHeld())multicast.release();}
 private void startMedia(){if(player==null||source==null)return;try{player.stop();started=false;failed=false;Media media=new Media(engine,source);media.setHWDecoderEnabled(!software,false);player.setMedia(media);media.release();player.play();}catch(Exception e){failed=true;info.setText("VLC: "+e.getMessage());}}
 private void jump(long d){if(player!=null)player.setTime(Math.max(0,player.getTime()+d));}
 private void sync(){String[] labels={"Audio −250 ms","Audio +250 ms","Subtitle −250 ms","Subtitle +250 ms","Reset both"};new AlertDialog.Builder(this).setTitle("Audio / subtitle synchronization").setItems(labels,(d,w)->{if(player==null)return;if(w==0)audioOffset-=250000;if(w==1)audioOffset+=250000;if(w==2)subtitleOffset-=250000;if(w==3)subtitleOffset+=250000;if(w==4)audioOffset=subtitleOffset=0;player.setAudioDelay(audioOffset);player.setSpuDelay(subtitleOffset);Toast.makeText(this,"Audio "+audioOffset/1000+" ms • Subtitle "+subtitleOffset/1000+" ms",Toast.LENGTH_SHORT).show();}).show();}
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==19&&result==RESULT_OK&&data!=null&&data.getData()!=null&&player!=null){try{player.addSlave(org.videolan.libvlc.interfaces.IMedia.Slave.Type.Subtitle,data.getData(),true);}catch(Exception e){Toast.makeText(this,"Subtitle not available",Toast.LENGTH_LONG).show();}}}
 @Override protected void onStop(){if(player!=null)player.pause();super.onStop();}
 @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);if(player!=null){player.setEventListener(null);player.stop();player.detachViews();player.release();player=null;}stopDiscovery();if(engine!=null){engine.release();engine=null;}super.onDestroy();}
}
