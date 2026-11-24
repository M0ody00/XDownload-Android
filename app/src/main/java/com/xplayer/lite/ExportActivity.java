package com.xplayer.lite;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.widget.*;
import androidx.media3.common.MediaItem;
import androidx.media3.transformer.*;
import java.io.*;
import java.util.concurrent.*;
public class ExportActivity extends Activity {
 private Transformer transformer;private Uri source;private File temp;private TextView status;private Button trim,extract,save;private boolean busy=false,ready=false;private boolean copying=false;private volatile boolean dead=false;private boolean audio=false,mp3=false;private Button mp3Button;
 private final ExecutorService io=Executors.newSingleThreadExecutor();
 private EditText start,end;
 @Override public void onCreate(Bundle b){super.onCreate(b);purgeOldFiles();source=getIntent().getData();LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(24,24,24,24);l.setBackgroundColor(0xff121923);
 TextView title=new TextView(this);title.setText("Export media • MP4 / M4A");title.setTextSize(22);l.addView(title);
 start=new EditText(this);start.setInputType(8194);start.setHint("Start time (seconds)");start.setText("0");l.addView(start);
 end=new EditText(this);end.setInputType(8194);end.setHint("End time (seconds)");l.addView(end);
 trim=new Button(this);trim.setText("Trim video to MP4");trim.setOnClickListener(v->begin(false));l.addView(trim);
 extract=new Button(this);extract.setText("Extract AAC audio to M4A");extract.setOnClickListener(v->begin(true));l.addView(extract);
 mp3Button=new Button(this);mp3Button.setText("Extract MP3 audio");mp3Button.setOnClickListener(v->beginMp3());l.addView(mp3Button);
 status=new TextView(this);status.setText("Keep this screen open during export. The original file is not changed.");l.addView(status);
 save=new Button(this);save.setText("Save exported file");save.setEnabled(false);save.setOnClickListener(v->pick());l.addView(save);setContentView(l);}
 private void purgeOldFiles(){File[] files=getCacheDir().listFiles();if(files!=null)for(File file:files){String n=file.getName();if((n.startsWith("xplayer-export-")||n.startsWith("xplayer-input-")||n.startsWith("xplayer-mp3-"))&&System.currentTimeMillis()-file.lastModified()>86400000L)file.delete();}}
 private void begin(boolean sound){if(source==null||busy)return;mp3=false;long a=0,b=0;try{if(!sound){a=(long)(Double.parseDouble(start.getText().toString())*1000);b=(long)(Double.parseDouble(end.getText().toString())*1000);if(a<0||b<=a)throw new IllegalArgumentException();}}catch(Exception e){status.setText("Enter valid start and end times.");return;}
 try{if(temp!=null)temp.delete();temp=File.createTempFile("xplayer-export-",".mp4",getCacheDir());if(!temp.delete())throw new IOException("Cannot prepare output");audio=sound;ready=false;busy=true;trim.setEnabled(false);extract.setEnabled(false);mp3Button.setEnabled(false);save.setEnabled(false);status.setText("Exporting…");
 MediaItem.Builder m=new MediaItem.Builder().setUri(source);if(!sound)m.setClippingConfiguration(new MediaItem.ClippingConfiguration.Builder().setStartPositionMs(a).setEndPositionMs(b).build());
 EditedMediaItem item=new EditedMediaItem.Builder(m.build()).setRemoveVideo(sound).build();
 transformer=new Transformer.Builder(this).addListener(new Transformer.Listener(){@Override public void onCompleted(Composition c,ExportResult r){busy=false;ready=true;trim.setEnabled(true);extract.setEnabled(true);mp3Button.setEnabled(true);save.setEnabled(true);status.setText("Export ready. Choose where to save.");}@Override public void onError(Composition c,ExportResult r,ExportException e){error(e.getMessage());}}).build();
 transformer.start(item,temp.getAbsolutePath());
 }catch(Exception e){error(e.getMessage());}}
  private void beginMp3(){if(source==null||busy)return;mp3=true;audio=true;ready=false;busy=true;trim.setEnabled(false);extract.setEnabled(false);mp3Button.setEnabled(false);save.setEnabled(false);status.setText("Encoding MP3…");io.execute(()->{File input=null,output=null;try{input=File.createTempFile("xplayer-input-",".media",getCacheDir());output=File.createTempFile("xplayer-mp3-",".mp3",getCacheDir());try(InputStream in=getContentResolver().openInputStream(source);OutputStream out=new FileOutputStream(input)){if(in==null)throw new IOException("Cannot read media");byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){if(dead)throw new IOException("Cancelled");out.write(b,0,n);}}encodeMp3(input,output);if(dead){output.delete();return;}final File result=output;runOnUiThread(()->{if(dead){result.delete();return;}if(temp!=null)temp.delete();temp=result;busy=false;ready=true;trim.setEnabled(true);extract.setEnabled(true);mp3Button.setEnabled(true);save.setEnabled(true);status.setText("MP3 ready — choose where to save");});}catch(Exception|LinkageError e){if(output!=null)output.delete();runOnUiThread(()->{if(!dead)error("MP3: "+e.getMessage());});}finally{if(input!=null)input.delete();}});}
 static void encodeMp3(File input,File output)throws IOException{com.arthenica.ffmpegkit.FFmpegSession s=com.arthenica.ffmpegkit.FFmpegKit.executeWithArguments(new String[]{"-y","-i",input.getAbsolutePath(),"-vn","-map","0:a:0","-c:a","libmp3lame","-q:a","3",output.getAbsolutePath()});if(!com.arthenica.ffmpegkit.ReturnCode.isSuccess(s.getReturnCode())||output.length()<100)throw new IOException("MP3 encoder did not produce a valid output");}
 private void error(String s){busy=false;ready=false;trim.setEnabled(true);extract.setEnabled(true);mp3Button.setEnabled(true);save.setEnabled(false);status.setText("Export failed: "+s);}
 private void pick(){if(!ready)return;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mp3?"audio/mpeg":audio?"audio/mp4":"video/mp4").putExtra(Intent.EXTRA_TITLE,mp3?"XPlayer-audio.mp3":audio?"XPlayer-audio.m4a":"XPlayer-trim.mp4");startActivityForResult(i,27);}
 @Override protected void onActivityResult(int req,int result,Intent i){super.onActivityResult(req,result,i);if(req==27&&result==RESULT_OK&&i!=null&&i.getData()!=null&&ready){Uri dest=i.getData();File input=temp;copying=true;save.setEnabled(false);trim.setEnabled(false);extract.setEnabled(false);busy=true;io.execute(()->{try(InputStream in=new FileInputStream(input);OutputStream out=getContentResolver().openOutputStream(dest,"w")){if(out==null)throw new IOException("Cannot open output");byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);runOnUiThread(()->{if(!dead){status.setText("Saved");copying=false;busy=false;save.setEnabled(true);trim.setEnabled(true);extract.setEnabled(true);mp3Button.setEnabled(true);}});}catch(Exception e){runOnUiThread(()->{if(!dead){copying=false;error(e.getMessage());}});}finally{if(dead)input.delete();}});}}
 @Override protected void onDestroy(){dead=true;if(transformer!=null&&busy)transformer.cancel();io.shutdown();if(temp!=null&&!copying)temp.delete();super.onDestroy();}
}
