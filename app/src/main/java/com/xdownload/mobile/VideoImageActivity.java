package com.xdownload.mobile;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.widget.*;
import java.io.*;
import java.util.concurrent.*;
/** Explicit local file input, bounded GIF/frame exports, no network output. */
public final class VideoImageActivity extends SecureActivity {
 final ExecutorService io=Executors.newSingleThreadExecutor();
 volatile boolean dead=false;boolean busy=false,en;
 Uri source;File result;String suffix="png";
 Button select,frame,gif,save;EditText at,duration;TextView status;
 String tr(String ar,String eng){return en?eng:ar;}
 Button button(LinearLayout l,String text,Runnable action){Button b=new Button(this);b.setAllCaps(false);b.setText(text);b.setOnClickListener(v->action.run());l.addView(b);return b;}
 EditText field(LinearLayout l,String hint,String value){EditText e=new EditText(this);e.setSingleLine(true);e.setInputType(8194);e.setHint(hint);e.setText(value);l.addView(e);return e;}
 @Override public void onCreate(Bundle b){super.onCreate(b);en=Store.settings(this).getBoolean("english",false);
  ScrollView scroll=new ScrollView(this);LinearLayout l=new LinearLayout(this);l.setOrientation(1);l.setPadding(24,24,24,24);scroll.addView(l);
  TextView h=new TextView(this);h.setText(tr("لقطات الفيديو و GIF","Video frames & GIF"));h.setTextSize(22);l.addView(h);
  select=button(l,tr("اختر فيديو محليًا","Select local video"),()->startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("video/*"),1));
  at=field(l,tr("البداية بالثواني","Start seconds"),"0");duration=field(l,tr("مدة GIF (1–15 ثانية)","GIF duration (1–15 seconds)"),"5");
  frame=button(l,tr("استخراج صورة PNG","Export PNG frame"),()->process(false));gif=button(l,tr("إنشاء GIF","Create GIF"),()->process(true));
  save=button(l,tr("حفظ الناتج","Save output"),()->startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/"+suffix).putExtra(Intent.EXTRA_TITLE,"XDownload-"+System.currentTimeMillis()+"."+suffix),2));
  status=new TextView(this);status.setText(tr("يحفظ الأصل. الناتج بعرض أقصاه 480 بكسل لـ GIF و1920 للصورة. أبقِ الشاشة مفتوحة.","Original preserved. Output width capped at 480px GIF / 1920px frame. Keep this screen open."));l.addView(status);setContentView(scroll);state(false);}
 void state(boolean b){busy=b;select.setEnabled(!b);at.setEnabled(!b);duration.setEnabled(!b);frame.setEnabled(!b&&source!=null);gif.setEnabled(!b&&source!=null);save.setEnabled(!b&&result!=null);}
 void discard(){if(result!=null){result.delete();result=null;}}
 void report(String msg){runOnUiThread(()->{if(!dead){status.setText(msg);state(false);}});}
 @Override protected void onActivityResult(int r,int code,Intent data){super.onActivityResult(r,code,data);if(code!=RESULT_OK||data==null||data.getData()==null)return;Uri u=data.getData();if(r==1){discard();source=u;status.setText(tr("تم اختيار الفيديو","Video selected"));state(false);}else if(r==2&&result!=null){File f=result;state(true);io.execute(()->{try(InputStream in=new FileInputStream(f);OutputStream out=getContentResolver().openOutputStream(u,"w")){AudioStudioActivity.copy(in,out);report(tr("تم الحفظ","Saved"));}catch(Exception e){report(tr("فشل الحفظ: ","Save failed: ")+e.getMessage());}});}}
 static boolean valid(double start,double length,boolean gif){return Double.isFinite(start)&&start>=0&&start<=86400&&(!gif||Double.isFinite(length)&&length>=1&&length<=15);}
 static void encode(Context c,File input,File output,double start,double length,boolean animated)throws IOException{
  if(!valid(start,length,animated))throw new IOException("Invalid clip timing");
  if(animated)NativeMedia.run(c,"-y","-protocol_whitelist","file,pipe","-ss",Double.toString(start),"-i",input.getAbsolutePath(),"-t",Double.toString(length),"-an","-vf","fps=10,scale='min(480,iw)':-1:flags=lanczos","-loop","0",output.getAbsolutePath());
  else NativeMedia.run(c,"-y","-protocol_whitelist","file,pipe","-ss",Double.toString(start),"-i",input.getAbsolutePath(),"-an","-vf","scale='min(1920,iw)':-1","-frames:v","1",output.getAbsolutePath());
  if(output.length()<32||output.length()>64L*1024*1024)throw new IOException("Invalid output or size limit");
  try(InputStream in=new FileInputStream(output)){byte[] header=FileTools.readExact(in,8);if(animated){if(header[0]!='G'||header[1]!='I'||header[2]!='F')throw new IOException("Invalid GIF");}else if((header[0]&255)!=137||header[1]!='P'||header[2]!='N'||header[3]!='G')throw new IOException("Invalid PNG");}
 }
 void process(boolean animated){if(busy||source==null)return;double a,b;try{a=Double.parseDouble(at.getText().toString());b=animated?Double.parseDouble(duration.getText().toString()):0;if(!valid(a,b,animated))throw new IllegalArgumentException();}catch(Exception e){status.setText(tr("راجع وقت البداية والمدة","Check start and duration"));return;}
  final Uri u=source;final double start=a,length=b;final String ext=animated?"gif":"png";discard();state(true);status.setText(tr("جارٍ المعالجة…","Processing…"));io.execute(()->{File input=null,out=null;try{input=File.createTempFile("studio-in-",".video",getCacheDir());out=File.createTempFile("studio-out-","."+ext,getCacheDir());try(InputStream in=getContentResolver().openInputStream(u)){AudioStudioActivity.stage(in,input);}encode(this,input,out,start,length,animated);File done=out;out=null;runOnUiThread(()->{if(dead){done.delete();return;}result=done;suffix=ext;status.setText(tr("جاهز للحفظ: ","Ready: ")+MainActivity.size(done.length()));state(false);});}catch(Exception|LinkageError e){report(tr("فشلت المعالجة: ","Processing failed: ")+e.getMessage());}finally{if(input!=null)input.delete();if(out!=null)out.delete();}});}
 @Override protected void onDestroy(){dead=true;io.shutdownNow();discard();super.onDestroy();}
}
