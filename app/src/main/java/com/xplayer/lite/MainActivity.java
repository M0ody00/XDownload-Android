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
    private static final int BG=Ui.BLACK, PANEL=Ui.PANEL, INK=Ui.WHITE, ACCENT=Ui.BLUE;
    private static final int OPEN=101, SUBTITLE=102;
    ExoPlayer player;
    boolean renderedFrame=false;
    private PlayerView screen;
    private LinearLayout header, tools;
    private TextView title,status;
    private Uri current, subtitle;
    private static final java.util.ArrayList<Uri> libraryQueue=new java.util.ArrayList<>();
    private boolean fromLibrary=false, fallbackAttempted=false,shuffle=false;
    private int appliedSpeed=-1,appliedResize=-1;
    private android.graphics.Bitmap screenshot;
    private VirtualizerHolder surround;
    static void setLibraryQueue(java.util.List<Uri> list,Uri selected){libraryQueue.clear();libraryQueue.addAll(list);if(!libraryQueue.contains(selected))libraryQueue.add(selected);}
    private void moveTrack(int step){if(!fromLibrary||current==null)return;int index=libraryQueue.indexOf(current);int i=index+step;if(shuffle&&step>0&&libraryQueue.size()>1){i=new java.util.Random().nextInt(libraryQueue.size()-1);if(i>=index)i++;}if(i>=0&&i<libraryQueue.size())openMedia(libraryQueue.get(i),true);else Toast.makeText(this,"لا يوجد ملف آخر",Toast.LENGTH_SHORT).show();}

    private String subtitleMime;
    private boolean full=false,locked=false;
    private long aPoint=-1,bPoint=-1,downPosition=0,lastTap=0,sleepUntil=0;
    private boolean horizontal=false,vertical=false;
    private float initialSpan=0;
    private TextView unlock;
    private FrameLayout videoFrame;
    private SeekBar timeline;
    private TextView elapsed,total;
    private ImageView playIcon;
    private boolean dragging=false,controlsVisible=true;
    private final Runnable autoHide=()->{if(player!=null&&player.isPlaying()&&!locked&&!dragging)setControls(false);};
    private Equalizer eq;
    private BassBoost bass;
    private LoudnessEnhancer boost;
    private int effectSession=-1;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable clock=new Runnable(){public void run(){if(player==null)return;updateProgress();if(sleepUntil>0&&android.os.SystemClock.elapsedRealtime()>=sleepUntil){sleepUntil=0;player.pause();status.setText("انتهى مؤقت النوم");}if(aPoint>=0&&bPoint>aPoint&&player.getCurrentPosition()>=bPoint)player.seekTo(aPoint);handler.postDelayed(this,ProPrefs.flag(MainActivity.this,"lite_mode",false)&&bPoint<0?1000:200);}};
    private long stepMs(){int[] steps={5,10,15,30};return steps[Math.max(0,Math.min(3,ProPrefs.number(this,"seek",1)))]*1000L;}
    private void seekBy(long d){long end=player.getDuration(),n=Math.max(0,player.getCurrentPosition()+d);player.seekTo(end>0?Math.min(end,n):n);}

    private float downX,downY,oldBrightness;
    private int oldVolume;
    private AudioManager audio;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable bg(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(12));return d;}
    private TextView label(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setPadding(dp(12),dp(8),dp(12),dp(8));return t;}
    private Button button(String s,boolean primary){Button b=new Button(this);b.setText(s);b.setTextSize(13);b.setAllCaps(false);b.setTextColor(primary?BG:INK);b.setBackground(bg(primary?ProPrefs.accent(this):PANEL));b.setMinHeight(dp(44));return b;}
    private void addButton(LinearLayout row,Button b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(46),1);p.setMargins(dp(3),dp(4),dp(3),dp(4));row.addView(b,p);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        videoFrame=new FrameLayout(this);videoFrame.setBackgroundColor(Color.BLACK);videoFrame.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        screen=new PlayerView(this);screen.setBackgroundColor(Color.BLACK);screen.setUseController(false);screen.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);videoFrame.addView(screen,new FrameLayout.LayoutParams(-1,-1));screen.setOnClickListener(v->toggleControls());
        header=new LinearLayout(this);header.setOrientation(LinearLayout.VERTICAL);header.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xdc000000,0x00000000}));header.setPadding(dp(6),dp(4),dp(6),dp(12));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(Ui.action(this,"back","Back",INK,this::finish),new LinearLayout.LayoutParams(dp(44),dp(48)));
        title=label("X Player Pro",17,INK);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);top.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        TextView decoder=label("HW",15,INK);decoder.setGravity(Gravity.CENTER);decoder.setContentDescription("Decoder");decoder.setOnClickListener(v->decoderMenu());top.addView(decoder,new LinearLayout.LayoutParams(dp(50),dp(48)));
        top.addView(Ui.action(this,"more","Player tools",INK,this::showMore),new LinearLayout.LayoutParams(dp(44),dp(48)));header.addView(top);
        LinearLayout quick=new LinearLayout(this);quick.setGravity(Gravity.CENTER_VERTICAL);
        quick.addView(Ui.shortcut(this,"audio",tr("الصوت","Audio"),INK,()->audioTrackMenu()),new LinearLayout.LayoutParams(0,dp(56),1));
        TextView speed=label("1×",17,INK);speed.setGravity(Gravity.CENTER);speed.setOnClickListener(v->chooseSpeed());speed.setContentDescription("Playback speed");quick.addView(speed,new LinearLayout.LayoutParams(0,dp(56),1));
        quick.addView(Ui.shortcut(this,"music",tr("خلفية","Background"),INK,this::background),new LinearLayout.LayoutParams(0,dp(56),1));
        quick.addView(Ui.shortcut(this,"repeat",tr("تكرار","Repeat"),INK,()->{if(player!=null){player.setRepeatMode(player.getRepeatMode()==Player.REPEAT_MODE_ONE?Player.REPEAT_MODE_OFF:Player.REPEAT_MODE_ONE);Toast.makeText(this,player.getRepeatMode()==Player.REPEAT_MODE_ONE?"Repeat on":"Repeat off",Toast.LENGTH_SHORT).show();}}),new LinearLayout.LayoutParams(0,dp(56),1));
        quick.addView(Ui.shortcut(this,"equalizer","EQ",INK,this::audioEffects),new LinearLayout.LayoutParams(0,dp(56),1));header.addView(quick);
        videoFrame.addView(header,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
        tools=new LinearLayout(this);tools.setOrientation(LinearLayout.VERTICAL);tools.setPadding(dp(12),dp(16),dp(12),dp(12));tools.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,new int[]{0xe6000000,0x00000000}));
        status=label("",12,INK);status.setVisibility(View.GONE);tools.addView(status);
        LinearLayout progress=new LinearLayout(this);progress.setGravity(Gravity.CENTER_VERTICAL);elapsed=label("00:00",12,INK);elapsed.setPadding(0,0,0,0);progress.addView(elapsed,new LinearLayout.LayoutParams(dp(52),-2));timeline=new SeekBar(this);timeline.setMax(1000);timeline.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.BLUE));timeline.setThumbTintList(android.content.res.ColorStateList.valueOf(0xff65b6fb));progress.addView(timeline,new LinearLayout.LayoutParams(0,dp(40),1));total=label("00:00",12,INK);total.setPadding(0,0,0,0);total.setGravity(Gravity.RIGHT);progress.addView(total,new LinearLayout.LayoutParams(dp(52),-2));tools.addView(progress);
        timeline.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){dragging=true;handler.removeCallbacks(autoHide);}public void onStopTrackingTouch(SeekBar b){if(player!=null&&player.getDuration()>0)player.seekTo(player.getDuration()*b.getProgress()/1000);dragging=false;armHide();}public void onProgressChanged(SeekBar b,int n,boolean user){if(user&&player!=null&&player.getDuration()>0)elapsed.setText(timeText(player.getDuration()*n/1000));}});
        LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER);
        controls.addView(Ui.action(this,"lock","Lock controls",INK,()->setLocked(true)),new LinearLayout.LayoutParams(dp(48),dp(54)));
        controls.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        controls.addView(Ui.action(this,"prev","Previous file",INK,()->{moveTrack(-1);armHide();}),new LinearLayout.LayoutParams(dp(48),dp(54)));
        playIcon=Ui.action(this,"play","Play or pause",INK,()->{if(player!=null){if(player.isPlaying())player.pause();else player.play();armHide();}});controls.addView(playIcon,new LinearLayout.LayoutParams(dp(64),dp(64)));
        controls.addView(Ui.action(this,"next","Next file",INK,()->{moveTrack(1);armHide();}),new LinearLayout.LayoutParams(dp(48),dp(54)));
        controls.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        controls.addView(Ui.action(this,"ratio","Aspect ratio",INK,()->ratioMenu()),new LinearLayout.LayoutParams(dp(48),dp(54)));tools.addView(controls);
        videoFrame.addView(tools,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        unlock=label("Unlock",14,INK);unlock.setBackground(Ui.shape(this,0xaa202020,12));unlock.setVisibility(View.GONE);FrameLayout.LayoutParams lockPos=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.RIGHT);lockPos.setMargins(0,dp(12),dp(12),0);videoFrame.addView(unlock,lockPos);unlock.setOnClickListener(v->setLocked(false));
        setContentView(videoFrame);full=true;getWindow().getDecorView().setSystemUiVisibility(5894);
        DefaultRenderersFactory renderers=new DefaultRenderersFactory(this).setEnableDecoderFallback(true);
        player=new ExoPlayer.Builder(this,renderers).build();screen.setPlayer(player);
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true);
        player.setHandleAudioBecomingNoisy(true);
        player.addListener(new Player.Listener(){
            @Override public void onPlaybackStateChanged(int state){
                if(state==Player.STATE_BUFFERING)status.setText("جارٍ تجهيز الملف…");
                else if(state==Player.STATE_READY)status.setText("الملف جاهز • Ready");
                else if(state==Player.STATE_ENDED){status.setText("انتهى التشغيل");savePosition();if(ProPrefs.flag(MainActivity.this,"auto_next",true)&&fromLibrary&&libraryQueue.indexOf(current)>=0&&(shuffle&&libraryQueue.size()>1||libraryQueue.indexOf(current)<libraryQueue.size()-1))moveTrack(1);}
            }
            @Override public void onIsPlayingChanged(boolean playing){if(playIcon!=null){playIcon.setImageDrawable(new Ui.Glyph(playing?"pause":"play",INK));if(playing)armHide();else{handler.removeCallbacks(autoHide);if(!locked)setControls(true);}}
                if(playing){if(ProPrefs.flag(MainActivity.this,"keep_screen",true)&&!ProPrefs.flag(MainActivity.this,"lite_mode",false))getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);status.setText("جارٍ التشغيل");}
                else {getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);if(player.getPlaybackState()==Player.STATE_READY)status.setText("متوقف مؤقتًا");}
            }
            @Override public void onRenderedFirstFrame(){renderedFrame=true;}
            @Override public void onPlayerError(PlaybackException e){status.setText("تعذّر التشغيل: "+e.getErrorCodeName());if(current!=null&&!fallbackAttempted&&ProPrefs.flag(MainActivity.this,"auto_decoder",true)&&("content".equals(current.getScheme())||"file".equals(current.getScheme()))&&(e.errorCode==PlaybackException.ERROR_CODE_DECODER_INIT_FAILED||e.errorCode==PlaybackException.ERROR_CODE_DECODING_FAILED||e.errorCode==PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED||e.errorCode==PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES||e.errorCode==PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED)){fallbackAttempted=true;handler.post(()->{if(!isFinishing()&&player!=null)openVlc(true);});return;}new AlertDialog.Builder(MainActivity.this).setTitle("تعذّر فتح الملف").setMessage("قد تكون صيغة الترميز غير مدعومة على هذا الجهاز أو الملف تالفًا.\n"+e.getErrorCodeName()).setPositiveButton("حسنًا",null).setNeutralButton("LibVLC / SW",(d,w)->openVlc(true)).show();}
        });
        if(state!=null && state.getString("uri")!=null){fromLibrary=getIntent().getBooleanExtra("library",false);current=Uri.parse(state.getString("uri"));openMedia(current,false);player.seekTo(state.getLong("position"));player.setPlayWhenReady(state.getBoolean("play"));}
        else handle(getIntent());
        applyPrefs();handler.post(clock);armHide();
    }
    private String tr(String ar,String en){return ProPrefs.label(this,ar,en);}
    private void updateProgress(){if(timeline==null||player==null||dragging)return;long d=player.getDuration(),pos=Math.max(0,player.getCurrentPosition());elapsed.setText(timeText(pos));total.setText(timeText(d));timeline.setProgress(d>0?(int)Math.min(1000,pos*1000/d):0);}
    private String timeText(long ms){long s=Math.max(0,ms/1000);return s>=3600?String.format(Locale.ROOT,"%d:%02d:%02d",s/3600,s/60%60,s%60):String.format(Locale.ROOT,"%02d:%02d",s/60,s%60);}
    private void setControls(boolean show){controlsVisible=show;header.setVisibility(show&&!locked?View.VISIBLE:View.GONE);tools.setVisibility(show&&!locked?View.VISIBLE:View.GONE);}
    private void armHide(){handler.removeCallbacks(autoHide);if(player!=null&&player.isPlaying()&&!locked)handler.postDelayed(autoHide,3500);}
    private void toggleControls(){if(locked)return;setControls(!controlsVisible);if(controlsVisible)armHide();}
    private void decoderMenu(){handler.removeCallbacks(autoHide);new AlertDialog.Builder(this).setTitle("Decoder").setItems(new String[]{"Media3 • HW / Auto","LibVLC • HW","LibVLC • SW"},(d,w)->{if(w==1)openVlc(false);else if(w==2)openVlc(true);else armHide();}).setOnDismissListener(d->armHide()).show();}
    private void audioTrackMenu(){if(player!=null)new androidx.media3.ui.TrackSelectionDialogBuilder(this,tr("مسارات الصوت","Audio tracks"),player,C.TRACK_TYPE_AUDIO).build().show();}
    private void ratioMenu(){new AlertDialog.Builder(this).setTitle(tr("نسبة العرض","Aspect ratio")).setItems(new String[]{"Fit","Zoom","Stretch"},(d,w)->applyResize(w)).show();}
    private void subtitleMenu(){if(player==null||current==null)return;new AlertDialog.Builder(this).setTitle(tr("الترجمة","Subtitles")).setItems(new String[]{tr("مسارات مدمجة","Embedded tracks"),tr("فتح ملف ترجمة","Open subtitle file")},(d,w)->{if(w==0)new androidx.media3.ui.TrackSelectionDialogBuilder(this,"Subtitles",player,C.TRACK_TYPE_TEXT).build().show();else pick(true);}).show();}
    private void pick(boolean text){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");
        if(!text)i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"video/*","audio/*","application/octet-stream","application/ogg"});
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try{startActivityForResult(i,text?SUBTITLE:OPEN);}catch(Exception e){Toast.makeText(this,"لا يوجد منتقي ملفات",Toast.LENGTH_LONG).show();}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(request==103){android.graphics.Bitmap b=screenshot;screenshot=null;if(b!=null){if(result==RESULT_OK&&data!=null&&data.getData()!=null){Uri dest=data.getData();new Thread(()->{try(java.io.OutputStream out=getContentResolver().openOutputStream(dest,"w")){if(out==null||!b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out))throw new java.io.IOException("PNG");runOnUiThread(()->Toast.makeText(this,"تم حفظ الصورة",Toast.LENGTH_SHORT).show());}catch(Exception e){runOnUiThread(()->Toast.makeText(this,"تعذر حفظ الصورة",Toast.LENGTH_SHORT).show());}finally{b.recycle();}}).start();}else b.recycle();}return;}if(result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri u=data.getData();
        try{getContentResolver().takePersistableUriPermission(u,data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
        if(request==OPEN){fromLibrary=false;openMedia(u,true);}
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
        stopService(new Intent(this,BackgroundService.class));savePosition();fallbackAttempted=false;current=u;subtitle=null;subtitleMime=null;if(ProPrefs.flag(this,"auto_subtitle",true)){Uri found=TreeScanner.subtitleFor(this,u);if(found!=null){String ext=TreeScanner.extension(displayName(found));if("srt".equals(ext))subtitleMime=MimeTypes.APPLICATION_SUBRIP;else if("ass".equals(ext)||"ssa".equals(ext))subtitleMime=MimeTypes.TEXT_SSA;else if("vtt".equals(ext))subtitleMime=MimeTypes.TEXT_VTT;if(subtitleMime!=null)subtitle=found;}}aPoint=bPoint=-1;title.setText(displayName(u));ProPrefs.record(this,u);
        long pos=resume&&ProPrefs.flag(this,"resume",true)?getPreferences(MODE_PRIVATE).getLong("p:"+u,0):0;
        prepare(u,pos,ProPrefs.flag(this,"autoplay",true));
    }
    private void prepare(Uri u,long pos,boolean play){
        renderedFrame=false;
        MediaItem.Builder item=new MediaItem.Builder().setUri(u);
        if(subtitle!=null)item.setSubtitleConfigurations(Collections.singletonList(new MediaItem.SubtitleConfiguration.Builder(subtitle).setMimeType(subtitleMime).setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()));
        player.setMediaItem(item.build(),pos);player.prepare();player.setPlayWhenReady(play);
    }
    private void savePosition(){if(player==null||current==null)return;long p=player.getCurrentPosition(),d=player.getDuration();if(player.getPlaybackState()==Player.STATE_ENDED||(d>0&&d-p<1000))p=0;if("content".equals(current.getScheme())||"file".equals(current.getScheme()))getPreferences(MODE_PRIVATE).edit().putLong("p:"+current,p).apply();}
    private void chooseSpeed(){new AlertDialog.Builder(this).setTitle("سرعة التشغيل").setItems(ProPrefs.speeds,(d,w)->player.setPlaybackSpeed(ProPrefs.rates[w])).show();}
    private void applyPrefs(){if(player==null)return;player.setHandleAudioBecomingNoisy(ProPrefs.flag(this,"noisy",true));int speed=Math.max(0,Math.min(ProPrefs.rates.length-1,ProPrefs.number(this,"speed",3)));if(appliedSpeed!=speed){player.setPlaybackSpeed(ProPrefs.rates[speed]);appliedSpeed=speed;}int resize=ProPrefs.number(this,"resize",0);if(appliedResize!=resize){applyResize(resize);appliedResize=resize;}if(screen.getSubtitleView()!=null){int size=Math.max(0,Math.min(3,ProPrefs.number(this,"subtitle_size",1)));float[] sizes={.035f,.0533f,.07f,.09f};screen.getSubtitleView().setFractionalTextSize(sizes[size]);int[] colors={Color.WHITE,Color.YELLOW,Color.CYAN};int c=colors[Math.max(0,Math.min(2,ProPrefs.number(this,"subtitle_color",0)))];screen.getSubtitleView().setStyle(new CaptionStyleCompat(c,ProPrefs.flag(this,"subtitle_bg",true)?0xcc000000:Color.TRANSPARENT,Color.TRANSPARENT,CaptionStyleCompat.EDGE_TYPE_OUTLINE,Color.BLACK,null));}}
    private void applyResize(int n){screen.setResizeMode(n==1?AspectRatioFrameLayout.RESIZE_MODE_ZOOM:n==2?AspectRatioFrameLayout.RESIZE_MODE_FILL:AspectRatioFrameLayout.RESIZE_MODE_FIT);}
    void setSleepForTest(long millis){sleepUntil=millis>0?android.os.SystemClock.elapsedRealtime()+millis:0;}
    private void sleepMenu(){String[] a={"إلغاء المؤقت","5 دقائق","15 دقيقة","30 دقيقة","60 دقيقة","90 دقيقة"};int[] m={0,5,15,30,60,90};new AlertDialog.Builder(this).setTitle("مؤقت النوم").setItems(a,(d,w)->{setSleepForTest(m[w]*60000L);status.setText(w==0?"المؤقت متوقف":"المؤقت: "+m[w]+" دقيقة");}).show();}
    private void abMenu(){new AlertDialog.Builder(this).setTitle("تكرار A–B").setMessage("A: "+(aPoint<0?"—":aPoint/1000+"s")+"   B: "+(bPoint<0?"—":bPoint/1000+"s")).setItems(new String[]{"تعيين البداية A","تعيين النهاية B","إلغاء التكرار"},(d,w)->{if(w==0){aPoint=player.getCurrentPosition();bPoint=-1;status.setText("تم تحديد A");}else if(w==1){long n=player.getCurrentPosition();if(aPoint>=0&&n>aPoint+300){bPoint=n;player.seekTo(aPoint);status.setText("تكرار A–B");}else Toast.makeText(this,"حدد A أولًا ثم B بعده",Toast.LENGTH_SHORT).show();}else{aPoint=bPoint=-1;status.setText("تم إلغاء التكرار");}}).show();}
    private void setLocked(boolean value){locked=value;handler.removeCallbacks(autoHide);unlock.setVisibility(value?View.VISIBLE:View.GONE);setControls(!value);if(!value)armHide();}
    private void showMore(){handler.removeCallbacks(autoHide);final android.app.Dialog dialog=new android.app.Dialog(this);ScrollView scroll=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(8),dp(12),dp(8),dp(12));body.setBackground(Ui.shape(this,0xff202124,14));scroll.addView(body);
      String[] labels={tr("الصوت","Audio track"),tr("الترجمة","Subtitle"),tr("قائمة التشغيل","Playing queue"),tr("نسبة العرض","Aspect ratio"),tr("الإعدادات","Settings"),tr("المعلومات","Information"),tr("مشاركة","Share"),tr("المحرك","Decoder"),tr("مؤقت النوم","Sleep timer"),"A–B",tr("عشوائي","Shuffle"),tr("لقطة","Snapshot"),tr("قص / تحويل","Export"),"PiP",tr("تدوير","Rotate"),tr("فتح ملف","Open file"),tr("المفضلة","Favorite"),tr("الخلفية","Background"),tr("معادل الصوت","Equalizer"),tr("قفل","Lock")};
      String[] icons={"music","subtitle","list","ratio","settings","info","share","decoder","clock","repeat","repeat","view","file","view","refresh","folder","star","music","equalizer","lock"};
      Runnable[] actions={this::audioTrackMenu,this::subtitleMenu,this::queueMenu,this::ratioMenu,()->startActivity(new Intent(this,SettingsActivity.class)),this::infoMenu,this::shareMedia,this::decoderMenu,this::sleepMenu,this::abMenu,()->{shuffle=!shuffle;Toast.makeText(this,shuffle?"Shuffle on":"Shuffle off",Toast.LENGTH_SHORT).show();},this::captureFrame,this::exportMedia,this::enterPip,()->setRequestedOrientation(getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE?android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE),()->pick(false),()->{if(current!=null)Toast.makeText(this,ProPrefs.toggle(this,current)?"Favorite added":"Favorite removed",Toast.LENGTH_SHORT).show();},this::background,this::audioEffects,()->setLocked(true)};
      for(int r=0;r<5;r++){LinearLayout row=new LinearLayout(this);for(int c=0;c<4;c++){int i=r*4+c;row.addView(Ui.shortcut(this,icons[i],labels[i],INK,()->{dialog.dismiss();actions[i].run();}),new LinearLayout.LayoutParams(0,dp(80),1));}body.addView(row);}dialog.setContentView(scroll);android.view.Window w=dialog.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setGravity(Gravity.BOTTOM);w.setLayout(-1,Math.min(dp(440),(int)(getResources().getDisplayMetrics().heightPixels*.75)));}dialog.setOnDismissListener(d->armHide());dialog.show();if(w!=null)w.setLayout(-1,Math.min(dp(440),(int)(getResources().getDisplayMetrics().heightPixels*.75)));
    }
    private void queueMenu(){if(libraryQueue.isEmpty()){Toast.makeText(this,"No queue",Toast.LENGTH_SHORT).show();return;}String[] names=new String[libraryQueue.size()];for(int i=0;i<names.length;i++)names[i]=displayName(libraryQueue.get(i));new AlertDialog.Builder(this).setTitle("Playing queue").setSingleChoiceItems(names,libraryQueue.indexOf(current),(d,w)->{openMedia(libraryQueue.get(w),true);d.dismiss();}).setNegativeButton("Close",null).show();}
    private void infoMenu(){if(current==null||player==null)return;new AlertDialog.Builder(this).setTitle(displayName(current)).setMessage("Duration: "+timeText(player.getDuration())+"\nVideo: "+player.getVideoSize().width+" × "+player.getVideoSize().height+"\nEngine: Media3").setPositiveButton("OK",null).show();}
    private void shareMedia(){if(current==null||!"content".equals(current.getScheme()))return;Intent i=new Intent(Intent.ACTION_SEND).setType(getContentResolver().getType(current)==null?"*/*":getContentResolver().getType(current)).putExtra(Intent.EXTRA_STREAM,current).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);i.setClipData(android.content.ClipData.newRawUri("Media",current));startActivity(Intent.createChooser(i,"Share media"));}
    private void captureFrame(){if(player==null||!renderedFrame){Toast.makeText(this,"شغّل فيديو أولًا",Toast.LENGTH_SHORT).show();return;}View v=screen.getVideoSurfaceView();if(!(v instanceof android.view.SurfaceView)||v.getWidth()<1||v.getHeight()<1){Toast.makeText(this,"لقطة الفيديو غير متاحة",Toast.LENGTH_SHORT).show();return;}int w=v.getWidth(),h=v.getHeight();float scale=Math.min(1f,1920f/Math.max(w,h));android.graphics.Bitmap b=android.graphics.Bitmap.createBitmap(Math.max(1,(int)(w*scale)),Math.max(1,(int)(h*scale)),android.graphics.Bitmap.Config.ARGB_8888);try{android.view.PixelCopy.request((android.view.SurfaceView)v,b,result->{if(result!=android.view.PixelCopy.SUCCESS||isFinishing()){b.recycle();return;}if(screenshot!=null)screenshot.recycle();screenshot=b;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/png").putExtra(Intent.EXTRA_TITLE,"XPlayer-frame.png");startActivityForResult(i,103);},handler);}catch(Exception e){b.recycle();Toast.makeText(this,"تعذر التقاط الفيديو",Toast.LENGTH_SHORT).show();}}
    private void openVlc(boolean sw){if(current==null)return;player.pause();startActivity(new Intent(this,VlcActivity.class).setData(current).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).putExtra("software",sw).putExtra("position",player.getCurrentPosition()));}
    private void background(){if(current==null)return;try{Intent i=new Intent(this,BackgroundService.class).setAction("xplayer.BACKGROUND").setData(current).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).putExtra("position",player.getCurrentPosition()).putExtra("title",displayName(current));if(fromLibrary){int index=libraryQueue.indexOf(current);java.util.ArrayList<Uri> q=new java.util.ArrayList<>();if(index>=0){for(int n=index;n<libraryQueue.size()&&q.size()<500;n++)q.add(libraryQueue.get(n));}i.putParcelableArrayListExtra("queue",q);}i.putExtra("speed",player.getPlaybackParameters().speed);i.putExtra("shuffle",shuffle);player.pause();startService(i);finish();}catch(Exception e){Toast.makeText(this,"Background unavailable: "+e.getMessage(),Toast.LENGTH_LONG).show();}}
    private void exportMedia(){if(current==null)return;if(!"content".equals(current.getScheme())&&!"file".equals(current.getScheme())){Toast.makeText(this,"Local files only",Toast.LENGTH_SHORT).show();return;}player.pause();startActivity(new Intent(this,ExportActivity.class).setData(current).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}
    private static class VirtualizerHolder {android.media.audiofx.Virtualizer v;VirtualizerHolder(int session){v=new android.media.audiofx.Virtualizer(0,session);v.setEnabled(true);}}
    private void releaseEffects(){if(surround!=null){surround.v.release();surround=null;}if(eq!=null){eq.release();eq=null;}if(bass!=null){bass.release();bass=null;}if(boost!=null){boost.release();boost=null;}effectSession=-1;}
    private void audioEffects(){if(player==null||player.getAudioSessionId()<=0){Toast.makeText(this,"شغّل ملفًا صوتيًا أولًا",Toast.LENGTH_SHORT).show();return;}int session=player.getAudioSessionId();if(effectSession!=session){releaseEffects();effectSession=session;try{eq=new Equalizer(0,session);eq.setEnabled(true);}catch(Exception ignored){eq=null;}try{bass=new BassBoost(0,session);bass.setEnabled(true);}catch(Exception ignored){bass=null;}try{boost=new LoudnessEnhancer(session);boost.setEnabled(true);}catch(Exception ignored){boost=null;}try{surround=new VirtualizerHolder(session);}catch(Exception ignored){surround=null;}}
       LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(16),dp(8),dp(16),dp(8));
       if(eq!=null)try{short[] range=eq.getBandLevelRange();for(short i=0;i<eq.getNumberOfBands();i++){final short band=i;content.addView(label(eq.getCenterFreq(i)/1000+" Hz",13,INK));SeekBar bar=new SeekBar(this);bar.setMax(range[1]-range[0]);bar.setProgress(eq.getBandLevel(i)-range[0]);content.addView(bar);bar.setOnSeekBarChangeListener(listener(v->{try{if(eq!=null)eq.setBandLevel(band,(short)(range[0]+v));}catch(Exception ignored){}}));}}catch(Exception ignored){}
       if(bass!=null)try{if(bass.getStrengthSupported()){content.addView(label("Bass boost",13,INK));SeekBar b=new SeekBar(this);b.setMax(1000);b.setProgress(bass.getRoundedStrength());content.addView(b);b.setOnSeekBarChangeListener(listener(v->{try{if(bass!=null)bass.setStrength((short)v);}catch(Exception ignored){}}));}}catch(Exception ignored){}
       if(boost!=null){content.addView(label("تعزيز الصوت (حتى +6 dB؛ احذر الصوت المرتفع)",13,INK));SeekBar b=new SeekBar(this);b.setMax(600);b.setProgress(0);content.addView(b);b.setOnSeekBarChangeListener(listener(v->{try{if(boost!=null)boost.setTargetGain(v);}catch(Exception ignored){}}));}
       if(surround!=null)try{if(surround.v.getStrengthSupported()){content.addView(label("Virtualizer",13,INK));SeekBar b=new SeekBar(this);b.setMax(1000);b.setProgress(surround.v.getRoundedStrength());content.addView(b);b.setOnSeekBarChangeListener(listener(v->{try{if(surround!=null)surround.v.setStrength((short)v);}catch(Exception ignored){}}));}}catch(Exception ignored){}if(content.getChildCount()==0)content.addView(label("مؤثرات الصوت غير مدعومة على هذا الجهاز",14,INK));ScrollView sc=new ScrollView(this);sc.addView(content);new AlertDialog.Builder(this).setTitle("مؤثرات الصوت").setView(sc).setNeutralButton("إيقاف المؤثرات",(d,w)->releaseEffects()).setPositiveButton("تم",null).show();}
    private interface IntAction{void accept(int v);}
    private SeekBar.OnSeekBarChangeListener listener(IntAction c){return new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}public void onProgressChanged(SeekBar b,int p,boolean user){if(user)c.accept(p);}};}

    private void setFullscreen(boolean f){full=f;getWindow().getDecorView().setSystemUiVisibility(f?5894:0);setControls(true);armHide();}
    private void enterPip(){if(Build.VERSION.SDK_INT>=26&&getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)&&current!=null){try{enterPictureInPictureMode(new PictureInPictureParams.Builder().setAspectRatio(new Rational(16,9)).build());}catch(Exception e){Toast.makeText(this,"الوضع العائم غير متاح",Toast.LENGTH_SHORT).show();}}else Toast.makeText(this,"الوضع العائم غير مدعوم أو لا يوجد ملف",Toast.LENGTH_SHORT).show();}
    @Override public void onPictureInPictureModeChanged(boolean inPip,Configuration c){super.onPictureInPictureModeChanged(inPip,c);setControls(!inPip);unlock.setVisibility(!inPip&&locked?View.VISIBLE:View.GONE);}
    @Override public void onBackPressed(){if(locked){setLocked(false);return;}super.onBackPressed();}
    @Override public boolean dispatchTouchEvent(MotionEvent e){
        if(locked||dragging||player==null)return super.dispatchTouchEvent(e);
        if(controlsVisible&&e.getPointerCount()==1){float y=e.getY();if(y<header.getHeight()||y>videoFrame.getHeight()-tools.getHeight())return super.dispatchTouchEvent(e); }
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
            if(e.getActionMasked()==MotionEvent.ACTION_UP){if(horizontal||vertical){setControls(true);armHide();return true;}if(Math.abs(e.getX()-downX)<dp(20)&&Math.abs(e.getY()-downY)<dp(20)&&ProPrefs.flag(this,"double_tap",true)){long now=android.os.SystemClock.elapsedRealtime();if(now-lastTap<300){seekBy(e.getX()>screen.getWidth()/2f?stepMs():-stepMs());lastTap=0;return true;}lastTap=now;}}
        }
        return super.dispatchTouchEvent(e);
    }
    @Override protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);if(current!=null){b.putString("uri",current.toString());b.putLong("position",player.getCurrentPosition());b.putBoolean("play",player.getPlayWhenReady());}}
    @Override protected void onResume(){super.onResume();if(player!=null)applyPrefs();}
    @Override protected void onPause(){savePosition();super.onPause();}
    @Override protected void onStop(){if(Build.VERSION.SDK_INT<26||!isInPictureInPictureMode())if(player!=null)player.pause();super.onStop();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);if(screenshot!=null){screenshot.recycle();screenshot=null;}releaseEffects();savePosition();if(player!=null){screen.setPlayer(null);player.release();player=null;}super.onDestroy();}
}
