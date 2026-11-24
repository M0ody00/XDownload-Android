package com.xdownload.mobile;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import android.text.InputType;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import com.yausername.ffmpeg.FFmpeg;
import kotlin.Unit;
import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class MainActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private static final int BG=0xff101d2d, PANEL=0xff203348, INK=0xfff4f7fb, MINT=0xff48e5ae;
    private static final Pattern LINK=Pattern.compile("https?://[^\\s<>\"\\u200e\\u200f]+",Pattern.CASE_INSENSITIVE);
    private EditText url;
    private TextView status;
    private ProgressBar progress;
    private Spinner format;
    private Button start, update, cancel;
    private volatile boolean busy, cancelled;
    private volatile String processId;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable bg(int c){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(14));return d;}
    private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);return t;}
    private void add(LinearLayout l,View v,int h){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,h<0?h:dp(h));p.bottomMargin=dp(12);l.addView(v,p);}
    private Button button(String name,boolean primary){Button b=new Button(this);b.setText(name);b.setAllCaps(false);b.setTextColor(primary?BG:INK);b.setBackground(bg(primary?MINT:PANEL));return b;}
    @Override public void onCreate(Bundle b){
        super.onCreate(b);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        ScrollView sc=new ScrollView(this);sc.setFillViewport(true);sc.setBackgroundColor(BG);
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(20),dp(28),dp(20),dp(24));sc.addView(l);setContentView(sc);
        TextView title=text("X DOWNLOAD",32,MINT);title.setTypeface(null,Typeface.BOLD);add(l,title,-2);
        add(l,text("تحميل من الرابط • بدون متصفح",18,INK),-2);
        add(l,text("شارك رابطًا من تطبيق آخر أو الصقه هنا. اختر نوع التحميل.",14,0xffb8c8db),-2);
        url=new EditText(this);url.setTextColor(INK);url.setHintTextColor(0xffaabbd0);url.setHint("https://...");url.setSingleLine(true);url.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);url.setPadding(dp(14),0,dp(14),0);url.setBackground(bg(PANEL));url.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);add(l,url,58);
        Button paste=button("لصق الرابط | Paste",false);add(l,paste,48);paste.setOnClickListener(v->{ClipboardManager c=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);if(c.hasPrimaryClip()){CharSequence s=c.getPrimaryClip().getItemAt(0).coerceToText(this);putLink(s==null?"":s.toString());}});
        format=new Spinner(this);format.setBackground(bg(PANEL));format.setPadding(dp(10),0,dp(10),0);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,new String[]{"فيديو • أفضل جودة MP4","فيديو • حتى 1080p","فيديو • حتى 720p","صوت • MP3","ملف مباشر • PDF / APK / ZIP / Any"});adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);format.setAdapter(adapter);add(l,format,54);
        start=button("ابدأ التحميل | Download",true);add(l,start,56);start.setOnClickListener(v->begin());
        cancel=button("إيقاف التحميل الحالي",false);cancel.setEnabled(false);add(l,cancel,48);cancel.setOnClickListener(v->{cancelled=true;String id=processId;if(id!=null){new Thread(()->{try{YoutubeDL.getInstance().destroyProcessById(id);}catch(Exception ignored){}}).start();}status.setText("جارٍ الإيقاف…");});
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);progress.setProgressTintList(android.content.res.ColorStateList.valueOf(MINT));add(l,progress,10);
        status=text("جاهز • Ready",15,INK);status.setTextIsSelectable(true);add(l,status,-2);
        Button files=button("الملفات المحمّلة | Downloads",false);add(l,files,48);files.setOnClickListener(v->{try{startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS));}catch(ActivityNotFoundException e){status.setText("افتح تطبيق الملفات > Downloads > XDownload");}});
        update=button("تحديث محرك المواقع | Update",false);add(l,update,48);update.setOnClickListener(v->updateEngine());
        add(l,text("الوسائط تُحفظ في Downloads/XDownload. أبقِ التطبيق مفتوحًا أثناء تحميل الوسائط. استخدم الروابط التي يسمح لك أصحابها بتنزيلها.",13,0xffb8c8db),-2);
        receive(getIntent());
    }
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);receive(i);}
    private void receive(Intent i){if(i!=null && Intent.ACTION_SEND.equals(i.getAction())){CharSequence s=i.getCharSequenceExtra(Intent.EXTRA_TEXT);if(s!=null)putLink(s.toString());}}
    private static String extract(String s){Matcher m=LINK.matcher(s==null?"":s);if(!m.find())return null;String candidate=m.group().replaceAll("[),.؛،]+$","");Uri u=Uri.parse(candidate);return u.getHost()!=null&&!u.getHost().isEmpty()&&u.getUserInfo()==null?candidate:null;}
    private void putLink(String s){String a=extract(s);if(a!=null)url.setText(a);else status.setText("لم أجد رابط HTTP أو HTTPS صالحًا.");}
    private void setBusy(boolean b){busy=b;start.setEnabled(!b);update.setEnabled(!b);cancel.setEnabled(b && processId!=null);format.setEnabled(!b);if(b)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}
    private void begin(){if(busy)return;String link=extract(url.getText().toString());if(link==null){url.setError("رابط غير صالح");return;}int f=format.getSelectedItemPosition();if(f==4){direct(link);return;}cancelled=false;processId="media-"+System.nanoTime();String id=processId;setBusy(true);progress.setProgress(0);status.setText("جارٍ تجهيز محرك التحميل…");worker.execute(()->media(link,f,id));}
    private void direct(String link){try{
        DownloadManager dm=(DownloadManager)getSystemService(DOWNLOAD_SERVICE);
        String name=URLUtil.guessFileName(link,null,null).replaceAll("[^\\p{L}\\p{N}._ -]","_");
        if(name.isEmpty()||name.equals(".")||name.equals(".."))name="file";
        DownloadManager.Request r=new DownloadManager.Request(Uri.parse(link));r.setTitle(name);r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,"XDownload/"+System.currentTimeMillis()+"-"+name);long id=dm.enqueue(r);status.setText("تمت إضافة الملف إلى مدير تنزيلات أندرويد. تابع الإشعار. #"+id);
    }catch(Exception e){status.setText("تعذّر بدء الملف: "+e.getMessage());}}
    private void init() throws Exception {YoutubeDL.getInstance().init(getApplicationContext());FFmpeg.getInstance().init(getApplicationContext());}
    private void updateEngine(){if(busy)return;processId=null;setBusy(true);status.setText("جارٍ تحديث محرك المواقع…");worker.execute(()->{try{YoutubeDL.getInstance().init(getApplicationContext());YoutubeDL.getInstance().updateYoutubeDL(getApplicationContext(),YoutubeDL.UpdateChannel.STABLE);finish("انتهى فحص تحديث المحرك.",true);}catch(Exception e){finish("تعذّر التحديث: "+e.getMessage(),false);}});}
    private void media(String link,int mode,String id){try{
        init();if(cancelled)throw new InterruptedException();
        byte[] key=MessageDigest.getInstance("SHA-256").digest((link+"|"+mode).getBytes(java.nio.charset.StandardCharsets.UTF_8));StringBuilder hex=new StringBuilder();for(byte b:key)hex.append(String.format(Locale.ROOT,"%02x",b&255));
        File dir=new File(getFilesDir(),"jobs/"+hex);if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Cannot create download directory");
        YoutubeDLRequest req=new YoutubeDLRequest(link);req.addOption("--no-playlist");req.addOption("--continue");req.addOption("--no-mtime");req.addOption("--retries","10");req.addOption("--fragment-retries","10");req.addOption("--concurrent-fragments","4");req.addOption("--trim-filenames","160");req.addOption("-o",new File(dir,"%(title)s [%(id)s].%(ext)s").getAbsolutePath());
        if(mode==3){req.addOption("-x");req.addOption("--audio-format","mp3");req.addOption("--audio-quality","0");}
        else {String limit=mode==1?"[height<=1080]":mode==2?"[height<=720]":"";req.addOption("-f","bv[ext=mp4]"+limit+"+ba[ext=m4a]/b[ext=mp4]"+limit+"/b"+limit);req.addOption("--merge-output-format","mp4");req.addOption("--remux-video","mp4");}
        if(cancelled)throw new InterruptedException();
        YoutubeDL.getInstance().execute(req,id,(p,eta,line)->{runOnUiThread(()->{if(!cancelled){if(p>=0)progress.setProgress(Math.min(99,p.intValue()));status.setText((p>=0?String.format(Locale.ROOT,"%.1f%% • ",p):"")+(eta>=0?"ETA "+eta+"s\n":"\n")+line);}});return Unit.INSTANCE;});
        if(cancelled)throw new InterruptedException();
        File[] list=dir.listFiles((d,n)->n.toLowerCase(Locale.ROOT).endsWith(mode==3?".mp3":".mp4"));if(list==null||list.length==0)throw new IOException("No completed media file found");
        runOnUiThread(()->status.setText("جارٍ حفظ الملف في Downloads…"));int count=0;
        for(File f:list){publish(f);count++;f.delete();}finish("تم حفظ "+count+" ملف في Downloads/XDownload",true);
    }catch(Exception e){finish(cancelled?"تم الإيقاف. أعد الرابط بنفس الإعداد لاستكمال الأجزاء إن أمكن.":"فشل التحميل: "+e.getMessage()+"\nجرّب تحديث المحرك أو رابطًا آخر.",false);}}
    private void publish(File f)throws IOException{
        String ext=MimeTypeMap.getFileExtensionFromUrl(f.getName());String mime=MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);if(mime==null)mime=f.getName().endsWith(".mp3")?"audio/mpeg":"video/mp4";
        ContentValues v=new ContentValues();v.put(MediaStore.MediaColumns.DISPLAY_NAME,f.getName());v.put(MediaStore.MediaColumns.MIME_TYPE,mime);v.put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/XDownload");v.put(MediaStore.MediaColumns.IS_PENDING,1);
        ContentResolver r=getContentResolver();Uri dest=r.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);if(dest==null)throw new IOException("Cannot create public file");
        try{try(OutputStream out=r.openOutputStream(dest)){if(out==null)throw new IOException("Cannot open public file");Files.copy(f.toPath(),out);}ContentValues done=new ContentValues();done.put(MediaStore.MediaColumns.IS_PENDING,0);r.update(dest,done,null,null);}catch(Exception e){r.delete(dest,null,null);throw new IOException(e);}
    }
    private void finish(String message,boolean ok){runOnUiThread(()->{processId=null;setBusy(false);if(ok)progress.setProgress(100);status.setText(message);});}
}
