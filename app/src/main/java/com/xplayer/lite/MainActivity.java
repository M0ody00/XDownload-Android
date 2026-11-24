package com.xplayer.lite;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PictureInPictureParams;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Rational;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import android.os.Handler;
import android.os.Looper;
import android.media.audiofx.Equalizer;
import android.media.audiofx.BassBoost;
import android.media.audiofx.LoudnessEnhancer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.CaptionStyleCompat;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.ui.PlayerView;
import java.util.Collections;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int BG=0xff121923, PANEL=0xff243342, INK=0xfff5f7fa, ACCENT=0xff44e7b2;
    private static final int OPEN=101, SUBTITLE=102;
    ExoPlayer player;
    boolean renderedFrame=false;
    private PlayerView screen;
    private LinearLayout header, tools;
    private TextView title,status;
    private Uri current, subtitle;
    private static final java.util.ArrayList<Uri> libraryQueue=new java.util.ArrayList<>();
    private boolean fromLibrary=false;
    static void setLibraryQueue(java.util.List<Uri> list,Uri selected){libraryQueue.clear();libraryQueue.addAll(list);if(!libraryQueue.contains(selected))libraryQueue.add(selected);}
    private void moveTrack(int step){if(!fromLibrary||current==null)return;int i=libraryQueue.indexOf(current)+step;if(i>=0&&i<libraryQueue.size())openMedia(libraryQueue.get(i),true);else Toast.makeText(this,"لا يوجد ملف آخر",Toast.LENGTH_SHORT).show();}

    private String subtitleMime;
    private boolean full=false,locked=false;
    private long aPoint=-1,bPoint=-1,downPosition=0,lastTap=0,sleepUntil=0;
    private boolean horizontal=false,vertical=false;
    private float initialSpan=0;
    private TextView unlock;
    private Equalizer eq;
    private BassBoost bass;
    private LoudnessEnhancer boost;
    private int effectSession=-1;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable clock=new Runnable(){public void run(){if(player==null)return;if(sleepUntil>0&&android.os.SystemClock.elapsedRealtime()>=sleepUntil){sleepUntil=0;player.pause();status.setText("انتهى مؤقت النوم");}if(aPoint>=0&&bPoint>aPoint&&player.getCurrentPosition()>=bPoint)player.seekTo(aPoint);handler.postDelayed(this,200);}};
    private long stepMs(){int[] steps={5,10,15,30};return steps[Math.max(0,Math.min(3,ProPrefs.number(this,"seek",1)))]*1000L;}
    private void seekBy(long d){long end=player.getDuration(),n=Math.max(0,player.getCurrentPosition()+d);player.seekTo(end>0?Math.min(end,n):n);}

    private float downX,downY,oldBrightness;
    private int oldVolume;
    private AudioManager audio;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable bg(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(12));return d;}
    private TextView label(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setPadding(dp(12),dp(8),dp(12),dp(8));return t;}
    private Button button(String s,boolean primary){Button b=new Button(this);b.setText(s);b.setTextSize(13);b.setAllCaps(false);b.setTextColor(primary?BG:INK);b.setBackground(bg(primary?ACCENT:PANEL));b.setMinHeight(dp(44));return b;}
    private void addButton(LinearLayout row,Button b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(46),1);p.setMargins(dp(3),dp(4),dp(3),dp(4));row.addView(b,p);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);
        header=new LinearLayout(this);header.setOrientation(LinearLayout.VERTICAL);
        TextView logo=label("X PLAYER PRO",24,ACCENT);logo.setTypeface(null,Typeface.BOLD);header.addView(logo);
        title=label("اختر فيديو أو ملف صوتي",15,INK);title.setMaxLines(2);header.addView(title);
        root.addView(header);
        screen=new PlayerView(this);screen.setBackgroundColor(Color.BLACK);screen.setUseController(true);screen.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        android.widget.FrameLayout videoFrame=new android.widget.FrameLayout(this);videoFrame.addView(screen,new android.widget.FrameLayout.LayoutParams(-1,-1));unlock=label("🔒 إلغاء القفل",16,INK);unlock.setBackground(bg(PANEL));unlock.setVisibility(View.GONE);android.widget.FrameLayout.LayoutParams lockPos=new android.widget.FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.RIGHT);lockPos.setMargins(0,dp(12),dp(12),0);videoFrame.addView(unlock,lockPos);unlock.setOnClickListener(v->setLocked(false));
        root.addView(videoFrame,new LinearLayout.LayoutParams(-1,0,1));
        tools=new LinearLayout(this);tools.setOrientation(LinearLayout.VERTICAL);tools.setPadding(dp(6),dp(3),dp(6),dp(4));
        status=label("جاهز • يعمل دون إنترنت",13,INK);tools.addView(status);
        LinearLayout first=new LinearLayout(this);
        Button open=button("فتح ملف",true), speed=button("السرعة",false), subs=button("الترجمة",false);
        addButton(first,open);addButton(first,speed);addButton(first,subs);tools.addView(first);
        LinearLayout second=new LinearLayout(this);
        Button back=button("−10 ثوانٍ",false), next=button("+10 ثوانٍ",false), fullscreen=button("ملء الشاشة",false), pip=button("عائم",false);
        addButton(second,back);addButton(second,next);addButton(second,fullscreen);addButton(second,pip);tools.addView(second);
        LinearLayout tracks=new LinearLayout(this);
        Button previousFile=button("السابق",false),nextFile=button("التالي",false),trackSettings=button("مسارات الصوت",false);
        addButton(tracks,previousFile);addButton(tracks,nextFile);addButton(tracks,trackSettings);tools.addView(tracks);
        previousFile.setOnClickListener(v->moveTrack(-1));nextFile.setOnClickListener(v->moveTrack(1));
        trackSettings.setOnClickListener(v->new androidx.media3.ui.TrackSelectionDialogBuilder(this,"مسارات الصوت",player,C.TRACK_TYPE_AUDIO).build().show());
        LinearLayout moreRow=new LinearLayout(this);Button more=button("⋮ أدوات المشغل",true),settings=button("الإعدادات",false);addButton(moreRow,more);addButton(moreRow,settings);tools.addView(moreRow);
        more.setOnClickListener(v->showMore());settings.setOnClickListener(v->startActivity(new Intent(this,SettingsActivity.class)));
        root.addView(tools);setContentView(root);
        DefaultRenderersFactory renderers=new DefaultRenderersFactory(this).setEnableDecoderFallback(true);
        player=new ExoPlayer.Builder(this,renderers).build();screen.setPlayer(player);
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true);
        player.setHandleAudioBecomingNoisy(true);
        player.addListener(new Player.Listener(){
            @Override public void onPlaybackStateChanged(int state){
                if(state==Player.STATE_BUFFERING)status.setText("جارٍ تجهيز الملف…");
                else if(state==Player.STATE_READY)status.setText("الملف جاهز • Ready");
                else if(state==Player.STATE_ENDED){status.setText("انتهى التشغيل");savePosition();if(ProPrefs.flag(MainActivity.this,"auto_next",true)&&fromLibrary&&libraryQueue.indexOf(current)>=0&&libraryQueue.indexOf(current)<libraryQueue.size()-1)moveTrack(1);}
            }
            @Override public void onIsPlayingChanged(boolean playing){
                if(playing){if(ProPrefs.flag(MainActivity.this,"keep_screen",true))getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);status.setText("جارٍ التشغيل");}
                else {getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);if(player.getPlaybackState()==Player.STATE_READY)status.setText("متوقف مؤقتًا");}
            }
            @Override public void onRenderedFirstFrame(){renderedFrame=true;}
            @Override public void onPlayerError(PlaybackException e){status.setText("تعذّر التشغيل: "+e.getErrorCodeName());new AlertDialog.Builder(MainActivity.this).setTitle("تعذّر فتح الملف").setMessage("قد تكون صيغة الترميز غير مدعومة على هذا الجهاز أو الملف تالفًا.\n"+e.getErrorCodeName()).setPositiveButton("حسنًا",null).setNeutralButton("LibVLC / SW",(d,w)->openVlc(true)).show();}
        });
        open.setOnClickListener(v->pick(false));
        subs.setOnClickListener(v->{if(current==null){Toast.makeText(this,"افتح ملفًا أولًا",Toast.LENGTH_SHORT).show();return;}pick(true);});
        speed.setOnClickListener(v->chooseSpeed());
        back.setOnClickListener(v->seekBy(-stepMs()));
        next.setOnClickListener(v->seekBy(stepMs()));
        fullscreen.setOnClickListener(v->setFullscreen(!full));
        pip.setOnClickListener(v->enterPip());
        if(state!=null && state.getString("uri")!=null){fromLibrary=getIntent().getBooleanExtra("library",false);current=Uri.parse(state.getString("uri"));openMedia(current,false);player.seekTo(state.getLong("position"));player.setPlayWhenReady(state.getBoolean("play"));}
        else handle(getIntent());
        applyPrefs();handler.post(clock);
    }
    private void pick(boolean text){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");
        if(!text)i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"video/*","audio/*","application/octet-stream","application/ogg"});
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try{startActivityForResult(i,text?SUBTITLE:OPEN);}catch(Exception e){Toast.makeText(this,"لا يوجد منتقي ملفات",Toast.LENGTH_LONG).show();}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri u=data.getData();
        try{getContentResolver().takePersistableUriPermission(u,data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
        if(request==OPEN){openMedia(u,true);}
        else if(request==SUBTITLE&&current!=null){
            String n=displayName(u).toLowerCase(Locale.ROOT);
            if(n.endsWith(".srt"))subtitleMime=MimeTypes.APPLICATION_SUBRIP;
            else if(n.endsWith(".ass")||n.endsWith(".ssa"))subtitleMime=MimeTypes.TEXT_SSA;
            else if(n.endsWith(".vtt"))subtitleMime=MimeTypes.TEXT_VTT;
            else{Toast.makeText(this,"اختر SRT أو ASS أو SSA أو VTT",Toast.LENGTH_LONG).show();return;}
            long pos=player.getCurrentPosition();boolean play=player.getPlayWhenReady();subtitle=u;prepare(current,pos,play);
        }
    }
    private String displayName(Uri u){
        if("content".equals(u.getScheme()))try(Cursor c=getContentResolver().query(u,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst()&&!c.isNull(0))return c.getString(0);}catch(Exception ignored){}
        String n=u.getLastPathSegment();return n==null?"Media":n;
    }
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);handle(i);}
    private void handle(Intent i){
        if(i==null)return;fromLibrary=i.getBooleanExtra("library",false);Uri u=null;
        if(Intent.ACTION_VIEW.equals(i.getAction()))u=i.getData();
        else if(Intent.ACTION_SEND.equals(i.getAction()))u=i.getParcelableExtra(Intent.EXTRA_STREAM);
        if(u!=null){String s=u.getScheme();boolean local="content".equals(s)||"file".equals(s);boolean remote=i.getBooleanExtra("network",false)&&u.getHost()!=null&&("https".equalsIgnoreCase(s)||"rtsp".equalsIgnoreCase(s)||("http".equalsIgnoreCase(s)&&ProPrefs.flag(this,"http",false)));if(local||remote)openMedia(u,true);}
    }
    void openMedia(Uri u,boolean resume){
        stopService(new Intent(this,BackgroundService.class));savePosition();current=u;subtitle=null;subtitleMime=null;aPoint=bPoint=-1;title.setText(displayName(u));ProPrefs.record(this,u);
        long pos=resume&&ProPrefs.flag(this,"resume",true)?getPreferences(MODE_PRIVATE).getLong("p:"+u,0):0;
        prepare(u,pos,ProPrefs.flag(this,"autoplay",true));
    }
    private void prepare(Uri u,long pos,boolean play){
        renderedFrame=false;
        MediaItem.Builder item=new MediaItem.Builder().setUri(u);
        if(subtitle!=null)item.setSubtitleConfigurations(Collections.singletonList(new MediaItem.SubtitleConfiguration.Builder(subtitle).setMimeType(subtitleMime).setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()));
        player.setMediaItem(item.build(),pos);player.prepare();player.setPlayWhenReady(play);
    }
    private void savePosition(){if(player==null||current==null)return;long p=player.getCurrentPosition(),d=player.getDuration();if(player.getPlaybackState()==Player.STATE_ENDED||(d>0&&d-p<1000))p=0;getPreferences(MODE_PRIVATE).edit().putLong("p:"+current,p).apply();}
    private void chooseSpeed(){new AlertDialog.Builder(this).setTitle("سرعة التشغيل").setItems(ProPrefs.speeds,(d,w)->player.setPlaybackSpeed(ProPrefs.rates[w])).show();}
    private void applyPrefs(){if(player==null)return;player.setHandleAudioBecomingNoisy(ProPrefs.flag(this,"noisy",true));int speed=Math.max(0,Math.min(ProPrefs.rates.length-1,ProPrefs.number(this,"speed",3)));player.setPlaybackSpeed(ProPrefs.rates[speed]);applyResize(ProPrefs.number(this,"resize",0));if(screen.getSubtitleView()!=null){int size=Math.max(0,Math.min(3,ProPrefs.number(this,"subtitle_size",1)));float[] sizes={.035f,.0533f,.07f,.09f};screen.getSubtitleView().setFractionalTextSize(sizes[size]);int[] colors={Color.WHITE,Color.YELLOW,Color.CYAN};int c=colors[Math.max(0,Math.min(2,ProPrefs.number(this,"subtitle_color",0)))];screen.getSubtitleView().setStyle(new CaptionStyleCompat(c,ProPrefs.flag(this,"subtitle_bg",true)?0xcc000000:Color.TRANSPARENT,Color.TRANSPARENT,CaptionStyleCompat.EDGE_TYPE_OUTLINE,Color.BLACK,null));}}
    private void applyResize(int n){screen.setResizeMode(n==1?AspectRatioFrameLayout.RESIZE_MODE_ZOOM:n==2?AspectRatioFrameLayout.RESIZE_MODE_FILL:AspectRatioFrameLayout.RESIZE_MODE_FIT);}
    void setSleepForTest(long millis){sleepUntil=millis>0?android.os.SystemClock.elapsedRealtime()+millis:0;}
    private void sleepMenu(){String[] a={"إلغاء المؤقت","5 دقائق","15 دقيقة","30 دقيقة","60 دقيقة","90 دقيقة"};int[] m={0,5,15,30,60,90};new AlertDialog.Builder(this).setTitle("مؤقت النوم").setItems(a,(d,w)->{setSleepForTest(m[w]*60000L);status.setText(w==0?"المؤقت متوقف":"المؤقت: "+m[w]+" دقيقة");}).show();}
    private void abMenu(){new AlertDialog.Builder(this).setTitle("تكرار A–B").setMessage("A: "+(aPoint<0?"—":aPoint/1000+"s")+"   B: "+(bPoint<0?"—":bPoint/1000+"s")).setItems(new String[]{"تعيين البداية A","تعيين النهاية B","إلغاء التكرار"},(d,w)->{if(w==0){aPoint=player.getCurrentPosition();bPoint=-1;status.setText("تم تحديد A");}else if(w==1){long n=player.getCurrentPosition();if(aPoint>=0&&n>aPoint+300){bPoint=n;player.seekTo(aPoint);status.setText("تكرار A–B");}else Toast.makeText(this,"حدد A أولًا ثم B بعده",Toast.LENGTH_SHORT).show();}else{aPoint=bPoint=-1;status.setText("تم إلغاء التكرار");}}).show();}
    private void setLocked(boolean value){locked=value;screen.setUseController(!value);if(value){header.setVisibility(View.GONE);tools.setVisibility(View.GONE);screen.hideController();unlock.setVisibility(View.VISIBLE);}else{unlock.setVisibility(View.GONE);header.setVisibility(full?View.GONE:View.VISIBLE);tools.setVisibility(full?View.GONE:View.VISIBLE);screen.showController();}}
    private void showMore(){String[] opts={"نسبة العرض","قفل الشاشة","مؤقت النوم","تكرار A–B","تكرار الملف","تشغيل عشوائي من القائمة","معادل الصوت والتعزيز","مسارات الترجمة المدمجة","إضافة / إزالة من المفضلة","الإعدادات","LibVLC / SW","تشغيل في الخلفية","قص الفيديو / استخراج الصوت","LibVLC / HW"};new AlertDialog.Builder(this).setTitle("أدوات المشغل").setItems(opts,(d,w)->{switch(w){case 0:new AlertDialog.Builder(this).setTitle("نسبة العرض").setItems(new String[]{"ملاءمة","تكبير","تمديد"},(a,b)->applyResize(b)).show();break;case 1:setLocked(true);break;case 2:sleepMenu();break;case 3:abMenu();break;case 4:player.setRepeatMode(player.getRepeatMode()==Player.REPEAT_MODE_ONE?Player.REPEAT_MODE_OFF:Player.REPEAT_MODE_ONE);status.setText(player.getRepeatMode()==Player.REPEAT_MODE_ONE?"تكرار الملف مفعّل":"التكرار متوقف");break;case 5:if(fromLibrary&&libraryQueue.size()>1){int i=new java.util.Random().nextInt(libraryQueue.size());if(libraryQueue.get(i).equals(current))i=(i+1)%libraryQueue.size();openMedia(libraryQueue.get(i),true);}else Toast.makeText(this,"افتح ملفًا من المكتبة أولًا",Toast.LENGTH_SHORT).show();break;case 6:audioEffects();break;case 7:new androidx.media3.ui.TrackSelectionDialogBuilder(this,"الترجمة",player,C.TRACK_TYPE_TEXT).build().show();break;case 8:if(current!=null)status.setText(ProPrefs.toggle(this,current)?"أضيف للمفضلة":"أزيل من المفضلة");break;case 9:startActivity(new Intent(this,SettingsActivity.class));break;case 10:openVlc(true);break;case 11:background();break;case 12:exportMedia();break;case 13:openVlc(false);break;}}).show();}
    private void openVlc(boolean sw){if(current==null)return;player.pause();startActivity(new Intent(this,VlcActivity.class).setData(current).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).putExtra("software",sw).putExtra("position",player.getCurrentPosition()));}
    private void background(){if(current==null)return;try{Intent i=new Intent(this,BackgroundService.class).setAction("xplayer.BACKGROUND").setData(current).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).putExtra("position",player.getCurrentPosition()).putExtra("title",displayName(current));player.pause();startService(i);finish();}catch(Exception e){Toast.makeText(this,"Background unavailable: "+e.getMessage(),Toast.LENGTH_LONG).show();}}
    private void exportMedia(){if(current==null)return;if(!"content".equals(current.getScheme())&&!"file".equals(current.getScheme())){Toast.makeText(this,"Local files only",Toast.LENGTH_SHORT).show();return;}player.pause();startActivity(new Intent(this,ExportActivity.class).setData(current).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}
    private void releaseEffects(){if(eq!=null){eq.release();eq=null;}if(bass!=null){bass.release();bass=null;}if(boost!=null){boost.release();boost=null;}effectSession=-1;}
    private void audioEffects(){if(player==null||player.getAudioSessionId()<=0){Toast.makeText(this,"شغّل ملفًا صوتيًا أولًا",Toast.LENGTH_SHORT).show();return;}int session=player.getAudioSessionId();if(effectSession!=session){releaseEffects();effectSession=session;try{eq=new Equalizer(0,session);eq.setEnabled(true);}catch(Exception ignored){eq=null;}try{bass=new BassBoost(0,session);bass.setEnabled(true);}catch(Exception ignored){bass=null;}try{boost=new LoudnessEnhancer(session);boost.setEnabled(true);}catch(Exception ignored){boost=null;}}
       LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(16),dp(8),dp(16),dp(8));
       if(eq!=null)try{short[] range=eq.getBandLevelRange();for(short i=0;i<eq.getNumberOfBands();i++){final short band=i;content.addView(label(eq.getCenterFreq(i)/1000+" Hz",13,INK));SeekBar bar=new SeekBar(this);bar.setMax(range[1]-range[0]);bar.setProgress(eq.getBandLevel(i)-range[0]);content.addView(bar);bar.setOnSeekBarChangeListener(listener(v->{try{if(eq!=null)eq.setBandLevel(band,(short)(range[0]+v));}catch(Exception ignored){}}));}}catch(Exception ignored){}
       if(bass!=null)try{if(bass.getStrengthSupported()){content.addView(label("Bass boost",13,INK));SeekBar b=new SeekBar(this);b.setMax(1000);b.setProgress(bass.getRoundedStrength());content.addView(b);b.setOnSeekBarChangeListener(listener(v->{try{if(bass!=null)bass.setStrength((short)v);}catch(Exception ignored){}}));}}catch(Exception ignored){}
       if(boost!=null){content.addView(label("تعزيز الصوت (حتى +6 dB؛ احذر الصوت المرتفع)",13,INK));SeekBar b=new SeekBar(this);b.setMax(600);b.setProgress(0);content.addView(b);b.setOnSeekBarChangeListener(listener(v->{try{if(boost!=null)boost.setTargetGain(v);}catch(Exception ignored){}}));}
       if(content.getChildCount()==0)content.addView(label("مؤثرات الصوت غير مدعومة على هذا الجهاز",14,INK));ScrollView sc=new ScrollView(this);sc.addView(content);new AlertDialog.Builder(this).setTitle("مؤثرات الصوت").setView(sc).setNeutralButton("إيقاف المؤثرات",(d,w)->releaseEffects()).setPositiveButton("تم",null).show();}
    private interface IntAction{void accept(int v);}
    private SeekBar.OnSeekBarChangeListener listener(IntAction c){return new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}public void onProgressChanged(SeekBar b,int p,boolean user){if(user)c.accept(p);}};}

    private void setFullscreen(boolean f){full=f;header.setVisibility(f?View.GONE:View.VISIBLE);tools.setVisibility(f?View.GONE:View.VISIBLE);getWindow().getDecorView().setSystemUiVisibility(f?5894:0);screen.showController();}
    private void enterPip(){if(Build.VERSION.SDK_INT>=26&&getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)&&current!=null){try{enterPictureInPictureMode(new PictureInPictureParams.Builder().setAspectRatio(new Rational(16,9)).build());}catch(Exception e){Toast.makeText(this,"الوضع العائم غير متاح",Toast.LENGTH_SHORT).show();}}else Toast.makeText(this,"الوضع العائم غير مدعوم أو لا يوجد ملف",Toast.LENGTH_SHORT).show();}
    @Override public void onPictureInPictureModeChanged(boolean inPip,Configuration c){super.onPictureInPictureModeChanged(inPip,c);screen.setUseController(!inPip);header.setVisibility(inPip||full?View.GONE:View.VISIBLE);tools.setVisibility(inPip||full?View.GONE:View.VISIBLE);}
    @Override public void onBackPressed(){if(locked){setLocked(false);return;}if(full){setFullscreen(false);return;}super.onBackPressed();}
    @Override public boolean dispatchTouchEvent(MotionEvent e){
        if(locked)return super.dispatchTouchEvent(e);
        if(full&&current!=null&&audio!=null&&ProPrefs.flag(this,"gestures",true)&&e.getPointerCount()==2){float span=(float)Math.hypot(e.getX(0)-e.getX(1),e.getY(0)-e.getY(1));if(e.getActionMasked()==MotionEvent.ACTION_POINTER_DOWN)initialSpan=span;else if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&initialSpan>0&&Math.abs(span-initialSpan)>dp(40)){applyResize(span>initialSpan?1:0);initialSpan=span;}return true;}
        if(full&&current!=null&&audio!=null&&ProPrefs.flag(this,"gestures",true)&&e.getPointerCount()==1){
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();downPosition=player.getCurrentPosition();horizontal=vertical=false;oldVolume=audio.getStreamVolume(AudioManager.STREAM_MUSIC);oldBrightness=getWindow().getAttributes().screenBrightness;if(oldBrightness<0)oldBrightness=.5f;}
            if(e.getActionMasked()==MotionEvent.ACTION_MOVE){
                float dy=downY-e.getY();float dx=e.getX()-downX;float height=Math.max(1,screen.getHeight());
                if(!vertical&&Math.abs(dx)>dp(35)&&Math.abs(dx)>Math.abs(dy)*1.5f){horizontal=true;long dur=player.getDuration();if(dur>0)player.seekTo(Math.max(0,Math.min(dur,downPosition+(long)(dx/Math.max(1,screen.getWidth())*60000))));return true;}
                if(!horizontal&&Math.abs(dy)>dp(30)&&Math.abs(dy)>Math.abs(dx)*1.5f){vertical=true;
                    if(downX>screen.getWidth()/2f){int max=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);audio.setStreamVolume(AudioManager.STREAM_MUSIC,Math.max(0,Math.min(max,oldVolume+Math.round(dy/height*max))),0);}
                    else{WindowManager.LayoutParams p=getWindow().getAttributes();p.screenBrightness=Math.max(.05f,Math.min(1f,oldBrightness+dy/height));getWindow().setAttributes(p);}
                }
            }
            if(e.getActionMasked()==MotionEvent.ACTION_UP){if(horizontal||vertical)return true;if(Math.abs(e.getX()-downX)<dp(20)&&Math.abs(e.getY()-downY)<dp(20)&&ProPrefs.flag(this,"double_tap",true)){long now=android.os.SystemClock.elapsedRealtime();if(now-lastTap<300){seekBy(e.getX()>screen.getWidth()/2f?stepMs():-stepMs());lastTap=0;return true;}lastTap=now;}}
        }
        return super.dispatchTouchEvent(e);
    }
    @Override protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);if(current!=null){b.putString("uri",current.toString());b.putLong("position",player.getCurrentPosition());b.putBoolean("play",player.getPlayWhenReady());}}
    @Override protected void onResume(){super.onResume();if(player!=null)applyPrefs();}
    @Override protected void onPause(){savePosition();super.onPause();}
    @Override protected void onStop(){if(Build.VERSION.SDK_INT<26||!isInPictureInPictureMode())if(player!=null)player.pause();super.onStop();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);releaseEffects();savePosition();if(player!=null){screen.setPlayer(null);player.release();player=null;}super.onDestroy();}
}
