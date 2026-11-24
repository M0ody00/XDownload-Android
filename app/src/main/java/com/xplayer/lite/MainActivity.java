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
import java.util.ArrayList;
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
    private int repeatMode=Player.REPEAT_MODE_OFF;
    private NowPlaying nowPlaying;
    private boolean foregroundVisible=false,vlcInFront=false;
    private long lastMediaUpdate=0;
    private android.graphics.Bitmap screenshot;
    private VirtualizerHolder surround;
    static void setLibraryQueue(java.util.List<Uri> list,Uri selected){libraryQueue.clear();libraryQueue.addAll(list);if(!libraryQueue.contains(selected))libraryQueue.add(selected);}
    private void moveTrack(int step){if(!fromLibrary||current==null)return;int index=libraryQueue.indexOf(current);if(index<0)return;int i=index+step;if(shuffle&&step>0&&libraryQueue.size()>1){i=new java.util.Random().nextInt(libraryQueue.size()-1);if(i>=index)i++;}else if(repeatMode==Player.REPEAT_MODE_ALL&&!libraryQueue.isEmpty())i=(i+libraryQueue.size())%libraryQueue.size();if(i>=0&&i<libraryQueue.size()){boolean wasPlaying=player.getPlayWhenReady();openMedia(libraryQueue.get(i),!ProPrefs.flag(this,"resume_first",false));if(wasPlaying)player.play();}else Toast.makeText(this,tr("لا يوجد ملف آخر","No more media"),Toast.LENGTH_SHORT).show();}

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
    private TextView speedBadge,decoderBadge,osd;
    private LinearLayout quick;
    private View dimLayer;
    private boolean transferred=false,internalLaunch=false,mirror=false,flip=false,night=false,muted=false,decoderOpening=false;
    private float previousVolume=1f;
    private long backAt=0;
    private final Runnable hideOsd=()->{if(osd!=null)osd.setVisibility(View.GONE);};
    private boolean dragging=false,controlsVisible=true,gestureOnControls=false;
    private final Runnable autoHide=()->{if(player!=null&&player.isPlaying()&&!locked&&!dragging)setControls(false);};
    private Equalizer eq;
    private BassBoost bass;
    private LoudnessEnhancer boost;
    private int effectSession=-1;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable clock=new Runnable(){public void run(){if(player==null)return;updateProgress();if(android.os.SystemClock.elapsedRealtime()-lastMediaUpdate>=1000)updateMediaNotification();if(sleepUntil>0&&android.os.SystemClock.elapsedRealtime()>=sleepUntil){sleepUntil=0;player.pause();status.setText("انتهى مؤقت النوم");}if(aPoint>=0&&bPoint>aPoint&&player.getCurrentPosition()>=bPoint)player.seekTo(aPoint);handler.postDelayed(this,ProPrefs.flag(MainActivity.this,"lite_mode",false)&&bPoint<0?1000:200);}};
    private long stepMs(){int[] steps={5,10,15,30};return steps[Math.max(0,Math.min(3,ProPrefs.number(this,"seek",1)))]*1000L;}
    private void seekBy(long d){long end=player.getDuration(),n=Math.max(0,player.getCurrentPosition()+d);player.seekTo(end>0?Math.min(end,n):n);}

    private float downX,downY,oldBrightness;
    private int oldVolume;private float oldGain;private int appliedOrientation=-1;
    private AudioManager audio;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable bg(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(12));return d;}
    private TextView label(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setPadding(dp(12),dp(8),dp(12),dp(8));return t;}
    private Button button(String s,boolean primary){Button b=new Button(this);b.setText(s);b.setTextSize(13);b.setAllCaps(false);b.setTextColor(primary?BG:INK);b.setBackground(bg(primary?ProPrefs.accent(this):PANEL));b.setMinHeight(dp(44));return b;}
    private void addButton(LinearLayout row,Button b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(46),1);p.setMargins(dp(3),dp(4),dp(3),dp(4));row.addView(b,p);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);repeatMode=ProPrefs.repeat(this);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        videoFrame=new FrameLayout(this);videoFrame.setBackgroundColor(Color.BLACK);videoFrame.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        screen=new PlayerView(this);screen.setBackgroundColor(Color.BLACK);screen.setUseController(false);screen.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);videoFrame.addView(screen,new FrameLayout.LayoutParams(-1,-1));screen.setOnClickListener(v->toggleControls());dimLayer=new View(this);dimLayer.setBackgroundColor(0x55000000);dimLayer.setVisibility(View.GONE);videoFrame.addView(dimLayer,new FrameLayout.LayoutParams(-1,-1));
        header=new LinearLayout(this);header.setOrientation(LinearLayout.VERTICAL);header.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xdc000000,0x00000000}));header.setPadding(dp(6),dp(4),dp(6),dp(12));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(Ui.action(this,"back","Back",INK,this::onBackPressed),new LinearLayout.LayoutParams(dp(44),dp(48)));
        title=label("X Player Pro",17,INK);title.setSingleLine(true);title.setPadding(dp(4),0,dp(4),0);title.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);title.setEllipsize(android.text.TextUtils.TruncateAt.END);top.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        decoderBadge=label("HW",15,INK);decoderBadge.setPadding(0,0,0,0);decoderBadge.setSingleLine(true);decoderBadge.setGravity(Gravity.CENTER);decoderBadge.setContentDescription("Decoder");decoderBadge.setOnClickListener(v->decoderMenu());top.addView(decoderBadge,new LinearLayout.LayoutParams(dp(64),dp(48)));
        top.addView(Ui.action(this,"more","Player tools",INK,this::showMore),new LinearLayout.LayoutParams(dp(44),dp(48)));header.addView(top);
        quick=new LinearLayout(this);quick.setGravity(Gravity.CENTER_VERTICAL);HorizontalScrollView rail=new HorizontalScrollView(this);rail.setHorizontalScrollBarEnabled(false);rail.setFillViewport(false);rail.addView(quick,new FrameLayout.LayoutParams(-2,-2));header.addView(rail,new LinearLayout.LayoutParams(-1,-2));buildQuickTools();
        videoFrame.addView(header,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
        tools=new LinearLayout(this);tools.setOrientation(LinearLayout.VERTICAL);tools.setPadding(dp(12),dp(16),dp(12),dp(12));tools.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,new int[]{0xe6000000,0x00000000}));
        status=label("",12,INK);status.setVisibility(View.GONE);tools.addView(status);
        LinearLayout progress=new LinearLayout(this);progress.setGravity(Gravity.CENTER_VERTICAL);elapsed=label("00:00",12,INK);elapsed.setPadding(0,0,0,0);progress.addView(elapsed,new LinearLayout.LayoutParams(dp(52),-2));timeline=new SeekBar(this);timeline.setMax(1000);timeline.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.BLUE));timeline.setThumbTintList(android.content.res.ColorStateList.valueOf(0xff65b6fb));progress.addView(timeline,new LinearLayout.LayoutParams(0,dp(40),1));total=label("00:00",12,INK);total.setPadding(0,0,0,0);total.setGravity(Gravity.RIGHT);progress.addView(total,new LinearLayout.LayoutParams(dp(52),-2));tools.addView(progress);
        timeline.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){dragging=true;handler.removeCallbacks(autoHide);}public void onStopTrackingTouch(SeekBar b){if(player!=null&&player.getDuration()>0)player.seekTo(player.getDuration()*b.getProgress()/1000);dragging=false;armHide();}public void onProgressChanged(SeekBar b,int n,boolean user){if(user&&player!=null&&player.getDuration()>0){long pos=player.getDuration()*n/1000;elapsed.setText(timeText(pos));if(ProPrefs.flag(MainActivity.this,"seek_preview",true))showOsd(timeText(pos));}}});
        LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER);
        controls.addView(Ui.action(this,"lock","Lock controls",INK,()->setLocked(true)),new LinearLayout.LayoutParams(dp(48),dp(54)));
        controls.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        controls.addView(Ui.action(this,"prev","Previous file",INK,()->{moveTrack(-1);armHide();}),new LinearLayout.LayoutParams(dp(48),dp(54)));
        playIcon=Ui.action(this,"play","Play or pause",INK,()->{if(player!=null){if(player.isPlaying())player.pause();else player.play();armHide();}});controls.addView(playIcon,new LinearLayout.LayoutParams(dp(64),dp(64)));
        controls.addView(Ui.action(this,"next","Next file",INK,()->{moveTrack(1);armHide();}),new LinearLayout.LayoutParams(dp(48),dp(54)));
        controls.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        controls.addView(Ui.action(this,"ratio","Aspect ratio",INK,()->ratioMenu()),new LinearLayout.LayoutParams(dp(48),dp(54)));tools.addView(controls);
        videoFrame.addView(tools,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        unlock=label("Unlock",14,INK);unlock.setBackground(Ui.shape(this,0xaa202020,12));unlock.setVisibility(View.GONE);FrameLayout.LayoutParams lockPos=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.RIGHT);lockPos.setMargins(0,dp(12),dp(12),0);videoFrame.addView(unlock,lockPos);osd=label("",20,INK);osd.setGravity(Gravity.CENTER);osd.setBackground(Ui.shape(this,0xd921252b,14));osd.setVisibility(View.GONE);osd.setPadding(dp(18),dp(12),dp(18),dp(12));videoFrame.addView(osd,new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER));unlock.setOnClickListener(v->setLocked(false));
        setContentView(videoFrame);full=ProPrefs.flag(this,"immersive",true);getWindow().getDecorView().setSystemUiVisibility(full?5894:0);
        DefaultRenderersFactory renderers=new DefaultRenderersFactory(this).setEnableDecoderFallback(true);
        androidx.media3.datasource.DefaultHttpDataSource.Factory http=new androidx.media3.datasource.DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(false);String ua=ProPrefs.string(this,"user_agent","").trim();if(!ua.isEmpty())http.setUserAgent(ua);player=new ExoPlayer.Builder(this,renderers).setMediaSourceFactory(new androidx.media3.exoplayer.source.DefaultMediaSourceFactory(new androidx.media3.datasource.DefaultDataSource.Factory(this,http))).build();screen.setPlayer(player);
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true);
        player.setHandleAudioBecomingNoisy(true);
        player.addListener(new Player.Listener(){
            @Override public void onPlaybackStateChanged(int state){
                if(state==Player.STATE_BUFFERING)status.setText("جارٍ تجهيز الملف…");
                else if(state==Player.STATE_READY)status.setText("الملف جاهز • Ready");
                else if(state==Player.STATE_ENDED){status.setText("انتهى التشغيل");savePosition();if((repeatMode==Player.REPEAT_MODE_ALL||ProPrefs.flag(MainActivity.this,"auto_next",true))&&fromLibrary&&libraryQueue.indexOf(current)>=0&&(repeatMode==Player.REPEAT_MODE_ALL||shuffle&&libraryQueue.size()>1||libraryQueue.indexOf(current)<libraryQueue.size()-1))moveTrack(1);else if(repeatMode==Player.REPEAT_MODE_ALL&&!fromLibrary){player.seekTo(0);player.play();}}
            }
            @Override public void onIsPlayingChanged(boolean playing){if(playIcon!=null){playIcon.setImageDrawable(new Ui.Glyph(playing?"pause":"play",INK));if(playing)armHide();else{handler.removeCallbacks(autoHide);if(!locked)setControls(true);}}
                if(playing){if(ProPrefs.flag(MainActivity.this,"keep_screen",true)&&!ProPrefs.flag(MainActivity.this,"lite_mode",false))getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);status.setText("جارٍ التشغيل");}
                else {getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);if(player.getPlaybackState()==Player.STATE_READY)status.setText("متوقف مؤقتًا");}
            }
            @Override public void onEvents(Player p,Player.Events events){updateMediaNotification();}
            @Override public void onRenderedFirstFrame(){renderedFrame=true;}
            @Override public void onPlayerError(PlaybackException e){status.setText("تعذّر التشغيل: "+e.getErrorCodeName());if(current!=null&&!fallbackAttempted&&ProPrefs.flag(MainActivity.this,"auto_decoder",true)&&("content".equals(current.getScheme())||"file".equals(current.getScheme()))&&(e.errorCode==PlaybackException.ERROR_CODE_DECODER_INIT_FAILED||e.errorCode==PlaybackException.ERROR_CODE_DECODING_FAILED||e.errorCode==PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED||e.errorCode==PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES||e.errorCode==PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED)){fallbackAttempted=true;handler.post(()->{if(!isFinishing()&&player!=null)openVlc(true);});return;}new AlertDialog.Builder(MainActivity.this).setTitle("تعذّر فتح الملف").setMessage("قد تكون صيغة الترميز غير مدعومة على هذا الجهاز أو الملف تالفًا.\n"+e.getErrorCodeName()).setPositiveButton("حسنًا",null).setNeutralButton("LibVLC / SW",(d,w)->openVlc(true)).show();}
        });
        if(state!=null && state.getString("uri")!=null){fromLibrary=getIntent().getBooleanExtra("library",false);current=Uri.parse(state.getString("uri"));openMedia(current,false);player.seekTo(state.getLong("position"));player.setPlayWhenReady(state.getBoolean("play"));}
        else if(getIntent().getBooleanExtra("resume_background",false))restoreBackground();else handle(getIntent());
        applyPrefs();handler.post(clock);armHide();
    }
    private void buildQuickTools(){if(quick==null)return;quick.removeAllViews();speedBadge=null;for(String id:ProPrefs.toolOrder(this)){int i=java.util.Arrays.asList(ProPrefs.TOOL_IDS).indexOf(id);if(i<0)continue;LinearLayout tile=new LinearLayout(this);tile.setOrientation(LinearLayout.VERTICAL);tile.setGravity(Gravity.CENTER_HORIZONTAL);tile.setPadding(dp(3),dp(5),dp(3),dp(5));FrameLayout circle=new FrameLayout(this);circle.setBackground(Ui.shape(this,0xc02a3036,24));tile.addView(circle,new LinearLayout.LayoutParams(dp(44),dp(44)));if(id.equals("speed")){speedBadge=Ui.text(this,"1×",15,INK);speedBadge.setGravity(Gravity.CENTER);circle.addView(speedBadge,new FrameLayout.LayoutParams(-1,-1));}else{ImageView icon=new ImageView(this);icon.setImageDrawable(new Ui.Glyph(ProPrefs.TOOL_ICONS[i],INK));icon.setPadding(dp(9),dp(9),dp(9),dp(9));circle.addView(icon,new FrameLayout.LayoutParams(-1,-1));}String name=tr(ProPrefs.TOOL_AR[i],ProPrefs.TOOL_EN[i]);TextView text=Ui.text(this,name,12,INK);text.setGravity(Gravity.CENTER);text.setMaxLines(2);text.setPadding(0,dp(4),0,0);tile.addView(text,new LinearLayout.LayoutParams(-1,-2));tile.setTag(id);tile.setContentDescription(ProPrefs.TOOL_EN[i]);tile.setOnClickListener(v->{armHide();quickAction(id);});quick.addView(tile,new LinearLayout.LayoutParams(dp(76),-2));}refreshLoopIcon();}
    private void quickAction(String id){if(player==null)return;switch(id){case "audio":audioTrackMenu();break;case "speed":chooseSpeed();break;case "background":background();break;case "repeat":repeatMenu();break;case "eq":audioEffects();break;case "night":night=!night;dimLayer.setVisibility(night?View.VISIBLE:View.GONE);showOsd(night?"Night ON":"Night OFF");break;case "shuffle":shuffle=!shuffle;showOsd(shuffle?"Shuffle ON":"Shuffle OFF");break;case "mute":if(!muted)previousVolume=player.getVolume();muted=!muted;player.setVolume(muted?0:previousVolume);showOsd(muted?"Mute":"Sound ON");break;case "sleep":sleepMenu();break;case "ab":abMenu();break;case "mirror":mirror=!mirror;transformVideo();break;case "flip":flip=!flip;transformVideo();break;case "snapshot":captureFrame();break;case "rotate":setRequestedOrientation(getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE?android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);break;case "pip":enterPip();break;case "subtitle":subtitleMenu();break;case "ratio":ratioMenu();break;case "customize":customizeTools();break;}}
    private void transformVideo(){View v=screen.getVideoSurfaceView();if(v!=null){v.setScaleX(mirror?-1f:1f);v.setScaleY(flip?-1f:1f);}showOsd((mirror?"Mirror ON":"Mirror OFF")+" · "+(flip?"Flip ON":"Flip OFF"));}
    private void customizeTools(){ArrayList<String> old=ProPrefs.toolOrder(this);String[] labels=java.util.Arrays.copyOf(ProPrefs.TOOL_EN,ProPrefs.TOOL_EN.length-1);boolean[] chosen=new boolean[labels.length];for(int i=0;i<chosen.length;i++){labels[i]=tr(ProPrefs.TOOL_AR[i],ProPrefs.TOOL_EN[i]);chosen[i]=old.contains(ProPrefs.TOOL_IDS[i]);}new AlertDialog.Builder(this).setTitle(tr("تخصيص الأدوات","Customize tools")).setMultiChoiceItems(labels,chosen,(d,w,on)->chosen[w]=on).setNegativeButton("Cancel",null).setPositiveButton("Done",(d,w)->{ArrayList<String> selected=new ArrayList<>();for(int i=0;i<chosen.length;i++)if(chosen[i])selected.add(ProPrefs.TOOL_IDS[i]);ProPrefs.save(this,"quick_tools",selected);buildQuickTools();}).show();}
    boolean osdVisibleForTest(){return osd!=null&&osd.getVisibility()==View.VISIBLE;}
    private void showOsd(String text){if(osd==null||!ProPrefs.flag(this,"gesture_osd",true))return;osd.setText(text);osd.setVisibility(View.VISIBLE);handler.removeCallbacks(hideOsd);handler.postDelayed(hideOsd,1200);}
    void volumeOsdForTest(){showVolume();}
    private void showVolume(){if(audio==null)return;int max=Math.max(1,audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));showOsd(tr("الصوت","Volume")+"  "+Math.round((!ProPrefs.flag(this,"system_volume",true)&&player!=null?player.getVolume():audio.getStreamVolume(AudioManager.STREAM_MUSIC)/(float)max)*100f)+"%");}
    @Override public boolean onKeyDown(int key,android.view.KeyEvent e){if(audio!=null&&(key==android.view.KeyEvent.KEYCODE_VOLUME_UP||key==android.view.KeyEvent.KEYCODE_VOLUME_DOWN)){if(!ProPrefs.flag(this,"system_volume",true)&&player!=null)player.setVolume(Math.max(0,Math.min(1,player.getVolume()+(key==android.view.KeyEvent.KEYCODE_VOLUME_UP?.05f:-.05f))));else audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,key==android.view.KeyEvent.KEYCODE_VOLUME_UP?AudioManager.ADJUST_RAISE:AudioManager.ADJUST_LOWER,ProPrefs.flag(this,"system_volume_panel",false)?AudioManager.FLAG_SHOW_UI:0);showVolume();return true;}return super.onKeyDown(key,e);}
    @Override public boolean onKeyUp(int key,android.view.KeyEvent e){if(key==android.view.KeyEvent.KEYCODE_VOLUME_UP||key==android.view.KeyEvent.KEYCODE_VOLUME_DOWN)return true;return super.onKeyUp(key,e);}
    void setRepeatForTest(int mode){repeatMode=Math.max(0,Math.min(2,mode));mode=repeatMode;ProPrefs.get(this).edit().putInt("repeat_mode",mode).apply();applyRepeat();refreshLoopIcon();updateMediaNotification();}
    int repeatForTest(){return repeatMode;}
    private void applyRepeat(){if(player!=null)player.setRepeatMode(repeatMode==Player.REPEAT_MODE_ONE?Player.REPEAT_MODE_ONE:Player.REPEAT_MODE_OFF);}
    private void refreshLoopIcon(){if(quick==null)return;View tile=quick.findViewWithTag("repeat");if(tile instanceof LinearLayout){LinearLayout row=(LinearLayout)tile;if(row.getChildCount()>1&&row.getChildAt(1) instanceof TextView){TextView label=(TextView)row.getChildAt(1);label.setText(repeatMode==1?tr("إعادة ملف","Repeat one"):repeatMode==2?tr("إعادة الكل","Repeat all"):tr("التكرار","Loop"));label.setTextColor(repeatMode==0?INK:Ui.BLUE);}}}
    private void repeatMenu(){new AlertDialog.Builder(this).setTitle(tr("التكرار","Loop")).setSingleChoiceItems(new String[]{tr("إيقاف التكرار","Off"),tr("إعادة الملف الحالي","Repeat one"),tr("إعادة كل الملفات","Repeat all")},repeatMode,(d,w)->{setRepeatForTest(w);d.dismiss();showOsd(new String[]{"Loop off","Repeat one","Repeat all"}[w]);}).setNegativeButton(tr("إلغاء","Cancel"),null).show();}
    private void releaseNowPlaying(){if(nowPlaying!=null){nowPlaying.release();nowPlaying=null;}}
    boolean notificationActiveForTest(){return nowPlaying!=null&&nowPlaying.isPublished();}
    android.media.session.MediaSession.Token notificationTokenForTest(){return nowPlaying==null?null:nowPlaying.token();}
    private void updateMediaNotification(){lastMediaUpdate=android.os.SystemClock.elapsedRealtime();if(!foregroundVisible||transferred||vlcInFront||isFinishing()||current==null||player==null||!ProPrefs.flag(this,"show_media_notification",true)){releaseNowPlaying();return;}
      if(nowPlaying==null)nowPlaying=new NowPlaying(this,new NowPlaying.Controls(){public void play(){if(player!=null)player.play();}public void pause(){if(player!=null)player.pause();}public void previous(){moveTrack(-1);}public void next(){moveTrack(1);}public void seek(long pos){if(player!=null)player.seekTo(pos);}public void close(){if(player!=null){savePosition();player.pause();}releaseNowPlaying();finish();}});
      int index=libraryQueue.indexOf(current);boolean has=fromLibrary&&index>=0;boolean wrap=repeatMode==Player.REPEAT_MODE_ALL&&libraryQueue.size()>1;
      nowPlaying.update(current,title.getText().toString(),player.getCurrentPosition(),player.getDuration(),player.isPlaying(),player.getPlaybackParameters().speed,has&&(index>0||wrap),has&&(index<libraryQueue.size()-1||wrap||shuffle&&libraryQueue.size()>1));
    }
    private String tr(String ar,String en){return ProPrefs.label(this,ar,en);}
    private void updateProgress(){if(timeline==null||player==null||dragging)return;if(speedBadge!=null){String rate=String.format(Locale.ROOT,"%s×",new java.text.DecimalFormat("0.##",java.text.DecimalFormatSymbols.getInstance(Locale.ROOT)).format(player.getPlaybackParameters().speed));if(!rate.contentEquals(speedBadge.getText()))speedBadge.setText(rate);}long d=player.getDuration(),pos=Math.max(0,player.getCurrentPosition());elapsed.setText(timeText(pos));total.setText(timeText(d));timeline.setProgress(d>0?(int)Math.min(1000,pos*1000/d):0);}
    private String timeText(long ms){long s=Math.max(0,ms/1000);return s>=3600?String.format(Locale.ROOT,"%d:%02d:%02d",s/3600,s/60%60,s%60):String.format(Locale.ROOT,"%02d:%02d",s/60,s%60);}
    private void setControls(boolean show){if(Build.VERSION.SDK_INT>=26&&isInPictureInPictureMode())show=false;controlsVisible=show;boolean visible=show&&!locked;for(View v:new View[]{header,tools}){v.animate().cancel();if(visible){if(v.getVisibility()!=View.VISIBLE){v.setAlpha(0);v.setVisibility(View.VISIBLE);}v.animate().alpha(1).setDuration(120).start();}else{v.setAlpha(1);v.setVisibility(View.GONE);}}}
    private void armHide(){handler.removeCallbacks(autoHide);if(player!=null&&player.isPlaying()&&!locked)handler.postDelayed(autoHide,new int[]{2000,3500,5000,8000}[Math.max(0,Math.min(3,ProPrefs.number(this,"control_timeout",1)))]);}
    private void toggleControls(){if(locked||(Build.VERSION.SDK_INT>=26&&isInPictureInPictureMode()))return;setControls(!controlsVisible);if(controlsVisible)armHide();}
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
        super.onActivityResult(request,result,data);if(request==104){vlcInFront=false;if(result==RESULT_OK&&data!=null&&player!=null){if(data.getData()!=null&&!data.getData().equals(current)){decoderOpening=true;openMedia(data.getData(),false);decoderOpening=false;player.pause();}player.seekTo(Math.max(0,data.getLongExtra("position",0)));player.setPlaybackSpeed(data.getFloatExtra("speed",player.getPlaybackParameters().speed));}return;}if(request==103){android.graphics.Bitmap b=screenshot;screenshot=null;if(b!=null){if(result==RESULT_OK&&data!=null&&data.getData()!=null){Uri dest=data.getData();new Thread(()->{try(java.io.OutputStream out=getContentResolver().openOutputStream(dest,"w")){if(out==null||!b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out))throw new java.io.IOException("PNG");runOnUiThread(()->Toast.makeText(this,"تم حفظ الصورة",Toast.LENGTH_SHORT).show());}catch(Exception e){runOnUiThread(()->Toast.makeText(this,"تعذر حفظ الصورة",Toast.LENGTH_SHORT).show());}finally{b.recycle();}}).start();}else b.recycle();}return;}if(result!=RESULT_OK||data==null||data.getData()==null)return;
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
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);if(!"xplayer.FOREGROUND".equals(i.getAction()))setIntent(i);handle(i);}
    private void handle(Intent i){
        if(i==null||"xplayer.FOREGROUND".equals(i.getAction()))return;if(i.getBooleanExtra("resume_background",false)){restoreBackground();return;}fromLibrary=i.getBooleanExtra("library",false);Uri u=null;
        if(Intent.ACTION_VIEW.equals(i.getAction()))u=i.getData();
        else if(Intent.ACTION_SEND.equals(i.getAction()))u=i.getParcelableExtra(Intent.EXTRA_STREAM);
        if(u!=null){String s=u.getScheme();boolean local="content".equals(s)||"file".equals(s);boolean remote=ProPrefs.flag(this,"play_links",true)&&i.getBooleanExtra("network",false)&&u.getHost()!=null&&("https".equalsIgnoreCase(s)||"rtsp".equalsIgnoreCase(s)||("http".equalsIgnoreCase(s)&&ProPrefs.flag(this,"http",false)));if(local||remote)openMedia(u,true);}
    }
    void openMedia(Uri u,boolean resume){
        stopService(new Intent(this,BackgroundService.class));savePosition();fallbackAttempted=false;current=u;subtitle=null;subtitleMime=null;if(ProPrefs.flag(this,"auto_subtitle",true)){Uri found=TreeScanner.subtitleFor(this,u);if(found!=null){String ext=TreeScanner.extension(displayName(found));if("srt".equals(ext))subtitleMime=MimeTypes.APPLICATION_SUBRIP;else if("ass".equals(ext)||"ssa".equals(ext))subtitleMime=MimeTypes.TEXT_SSA;else if("vtt".equals(ext))subtitleMime=MimeTypes.TEXT_VTT;if(subtitleMime!=null)subtitle=found;}}aPoint=bPoint=-1;title.setText(displayName(u));ProPrefs.record(this,u);
        long pos=resume&&ProPrefs.flag(this,"resume",true)?getPreferences(MODE_PRIVATE).getLong("p:"+u,0):0;
        prepare(u,pos,ProPrefs.flag(this,"autoplay",true));int mode=ProPrefs.decoder(this,displayName(u),!"content".equals(u.getScheme())&&!"file".equals(u.getScheme()));if(mode!=0&&!decoderOpening){fallbackAttempted=true;handler.post(()->{if(!isFinishing()&&u.equals(current))openVlc(mode==2);});}
    }
    private void prepare(Uri u,long pos,boolean play){
        renderedFrame=false;
        MediaItem.Builder item=new MediaItem.Builder().setUri(u).setMediaMetadata(new androidx.media3.common.MediaMetadata.Builder().setTitle(displayName(u)).build());
        if(subtitle!=null)item.setSubtitleConfigurations(Collections.singletonList(new MediaItem.SubtitleConfiguration.Builder(subtitle).setMimeType(subtitleMime).setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()));
        player.setMediaItem(item.build(),pos);applyRepeat();player.prepare();player.setPlayWhenReady(play);
    }
    private void savePosition(){if(player==null||current==null||transferred)return;long p=player.getCurrentPosition(),d=player.getDuration();if(player.getPlaybackState()==Player.STATE_ENDED||(d>0&&d-p<1000))p=0;if("content".equals(current.getScheme())&&p>0)getSharedPreferences("played_times",MODE_PRIVATE).edit().putLong(current.toString(),System.currentTimeMillis()).apply();if("content".equals(current.getScheme())||"file".equals(current.getScheme()))getPreferences(MODE_PRIVATE).edit().putLong("p:"+current,p).apply();}
    private void chooseSpeed(){new AlertDialog.Builder(this).setTitle("سرعة التشغيل").setItems(ProPrefs.speeds,(d,w)->player.setPlaybackSpeed(ProPrefs.rates[w])).show();}
    private void applyPrefs(){if(player==null)return;repeatMode=ProPrefs.repeat(this);applyRepeat();int orient=ProPrefs.number(this,"orientation",0);if(appliedOrientation!=orient){appliedOrientation=orient;setRequestedOrientation(orient==1?android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:orient==2?android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);}full=ProPrefs.flag(this,"immersive",true);if(Build.VERSION.SDK_INT<26||!isInPictureInPictureMode())getWindow().getDecorView().setSystemUiVisibility(full?5894:0);if(!ProPrefs.flag(this,"eq_enabled",true))releaseEffects();else if(!ProPrefs.flag(this,"volume_boost",true)&&boost!=null){boost.release();boost=null;}buildQuickTools();player.setHandleAudioBecomingNoisy(ProPrefs.flag(this,"noisy",true));String lang=ProPrefs.string(this,"preferred_audio","");String sublang=ProPrefs.string(this,"preferred_subtitle","");player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setPreferredAudioLanguage(lang.isEmpty()?null:lang).setPreferredTextLanguage(sublang.isEmpty()?null:sublang).build());int speed=Math.max(0,Math.min(ProPrefs.rates.length-1,ProPrefs.number(this,"speed",3)));if(appliedSpeed!=speed){player.setPlaybackSpeed(ProPrefs.rates[speed]);appliedSpeed=speed;}int resize=ProPrefs.number(this,"resize",0);if(appliedResize!=resize){applyResize(resize);appliedResize=resize;}if(screen.getSubtitleView()!=null){screen.getSubtitleView().setBottomPaddingFraction(new float[]{.03f,.08f,.15f,.23f}[Math.max(0,Math.min(3,ProPrefs.number(this,"subtitle_padding",1)))]);int size=Math.max(0,Math.min(3,ProPrefs.number(this,"subtitle_size",1)));float[] sizes={.035f,.0533f,.07f,.09f};screen.getSubtitleView().setFractionalTextSize(sizes[size]);int[] colors={Color.WHITE,Color.YELLOW,Color.CYAN};int c=colors[Math.max(0,Math.min(2,ProPrefs.number(this,"subtitle_color",0)))];screen.getSubtitleView().setStyle(new CaptionStyleCompat(c,ProPrefs.flag(this,"subtitle_bg",true)?0xcc000000:Color.TRANSPARENT,Color.TRANSPARENT,CaptionStyleCompat.EDGE_TYPE_OUTLINE,Color.BLACK,new android.graphics.Typeface[]{null,android.graphics.Typeface.SANS_SERIF,android.graphics.Typeface.SERIF,android.graphics.Typeface.MONOSPACE}[Math.max(0,Math.min(3,ProPrefs.number(this,"subtitle_font",0)))]));}}
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
    private void openVlc(boolean sw){if(current==null||vlcInFront)return;vlcInFront=true;player.pause();releaseNowPlaying();Intent i=new Intent(this,VlcActivity.class).setData(current).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).putExtra("software",sw).putExtra("position",player.getCurrentPosition()).putExtra("speed",player.getPlaybackParameters().speed);if(subtitle!=null)i.putExtra("subtitle",subtitle.toString());if(fromLibrary){ArrayList<Uri> q=new ArrayList<>();int index=libraryQueue.indexOf(current);if(index>=0){int start=Math.max(0,index-200);for(int n=start;n<libraryQueue.size()&&q.size()<500;n++)q.add(libraryQueue.get(n));}i.putParcelableArrayListExtra("queue",q);}startActivityForResult(i,104); }
    private boolean isAudio(){if(current==null)return false;String mime=null;try{mime=getContentResolver().getType(current);}catch(Exception ignored){}return TreeScanner.kind(displayName(current),mime)==2;}
    private boolean transferBackground(){if(transferred)return true;if(current==null||player==null)return false;try{Intent i=new Intent(this,BackgroundService.class).setAction("xplayer.BACKGROUND").setData(current).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).putExtra("position",player.getCurrentPosition()).putExtra("title",displayName(current)).putExtra("speed",player.getPlaybackParameters().speed).putExtra("shuffle",shuffle).putExtra("repeat",repeatMode).putExtra("volume",player.getVolume()).putExtra("sleep_until",sleepUntil);
      if(fromLibrary){ArrayList<Uri> q=new ArrayList<>();int index=libraryQueue.indexOf(current);if(index>=0){int begin=Math.max(0,index-200);for(int n=begin;n<libraryQueue.size()&&q.size()<500;n++)q.add(libraryQueue.get(n));}i.putParcelableArrayListExtra("queue",q);}
      savePosition();transferred=true;releaseNowPlaying();startService(i);player.pause();return true;}catch(Exception e){transferred=false;updateMediaNotification();Toast.makeText(this,"Background unavailable: "+e.getMessage(),Toast.LENGTH_LONG).show();return false;}}
    private void background(){if(transferBackground())finish();}
    private void restoreBackground(){ExoPlayer bg=BackgroundService.active;if(bg==null||bg==player||bg.getCurrentMediaItem()==null)return;MediaItem m=bg.getCurrentMediaItem();if(m.localConfiguration==null)return;Uri u=m.localConfiguration.uri;long pos=bg.getCurrentPosition();float speed=bg.getPlaybackParameters().speed;float volume=bg.getVolume();boolean playing=bg.getPlayWhenReady();int repeat=bg.getRepeatMode();ArrayList<Uri> q=new ArrayList<>();for(int n=0;n<bg.getMediaItemCount();n++){MediaItem item=bg.getMediaItemAt(n);if(item.localConfiguration!=null)q.add(item.localConfiguration.uri);}setLibraryQueue(q,u);fromLibrary=q.size()>1;transferred=true;decoderOpening=true;openMedia(u,false);decoderOpening=false;player.seekTo(pos);player.setPlaybackSpeed(speed);appliedSpeed=ProPrefs.number(this,"speed",3);player.setVolume(volume);repeatMode=repeat;applyRepeat();player.setPlayWhenReady(playing);transferred=false;updateMediaNotification();}
    @Override protected void onUserLeaveHint(){super.onUserLeaveHint();if(!internalLaunch&&!isFinishing()&&current!=null&&player!=null&&player.getPlayWhenReady()){int mode=ProPrefs.number(this,"background_mode",0);if(mode==4&&!isAudio()){enterPip();return;}if(ProPrefs.backgroundAllowed(mode,isAudio()))transferBackground();}}
    @Override public void startActivityForResult(Intent i,int request){internalLaunch=true;super.startActivityForResult(i,request);}
    @Override public void startActivity(Intent i){internalLaunch=true;super.startActivity(i);}
    private void exportMedia(){if(current==null)return;if(!"content".equals(current.getScheme())&&!"file".equals(current.getScheme())){Toast.makeText(this,"Local files only",Toast.LENGTH_SHORT).show();return;}player.pause();startActivity(new Intent(this,ExportActivity.class).setData(current).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}
    private static class VirtualizerHolder {android.media.audiofx.Virtualizer v;VirtualizerHolder(int session){v=new android.media.audiofx.Virtualizer(0,session);v.setEnabled(true);}}
    private void releaseEffects(){if(surround!=null){surround.v.release();surround=null;}if(eq!=null){eq.release();eq=null;}if(bass!=null){bass.release();bass=null;}if(boost!=null){boost.release();boost=null;}effectSession=-1;}
    private void audioEffects(){if(!ProPrefs.flag(this,"eq_enabled",true)){Toast.makeText(this,"Enable equalizer in Audio settings",Toast.LENGTH_SHORT).show();return;}if(player==null||player.getAudioSessionId()<=0){Toast.makeText(this,"شغّل ملفًا صوتيًا أولًا",Toast.LENGTH_SHORT).show();return;}int session=player.getAudioSessionId();if(effectSession!=session){releaseEffects();effectSession=session;try{eq=new Equalizer(0,session);eq.setEnabled(true);}catch(Exception ignored){eq=null;}try{bass=new BassBoost(0,session);bass.setEnabled(true);}catch(Exception ignored){bass=null;}try{if(ProPrefs.flag(this,"volume_boost",true)){boost=new LoudnessEnhancer(session);boost.setEnabled(true);}}catch(Exception ignored){boost=null;}try{surround=new VirtualizerHolder(session);}catch(Exception ignored){surround=null;}}
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
    @Override public void onBackPressed(){if(locked){setLocked(false);return;}if(ProPrefs.flag(this,"double_back",false)&&android.os.SystemClock.elapsedRealtime()-backAt>1800){backAt=android.os.SystemClock.elapsedRealtime();showOsd(tr("اضغط رجوع مرة أخرى","Press back again"));return;}int mode=ProPrefs.number(this,"background_mode",0);if(current!=null&&player!=null&&player.getPlayWhenReady()){if(ProPrefs.backgroundAllowed(mode,isAudio())){if(transferBackground())finish();return;}if(mode==1){new AlertDialog.Builder(this).setTitle(tr("متابعة التشغيل؟","Keep playing?")).setItems(new String[]{tr("تشغيل في الخلفية","Background"),tr("إيقاف وإغلاق","Stop and close")},(d,w)->{if(w==0){if(transferBackground())finish();}else finish();}).show();return;}}super.onBackPressed();}
    @Override public boolean dispatchTouchEvent(MotionEvent e){
        if(locked||dragging||player==null)return super.dispatchTouchEvent(e);
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN){float y=e.getY();gestureOnControls=controlsVisible&&(y<header.getHeight()||y>videoFrame.getHeight()-tools.getHeight());}if(gestureOnControls)return super.dispatchTouchEvent(e);
        if(current!=null&&audio!=null&&ProPrefs.flag(this,"gestures",true)&&e.getPointerCount()==2){float span=(float)Math.hypot(e.getX(0)-e.getX(1),e.getY(0)-e.getY(1));if(e.getActionMasked()==MotionEvent.ACTION_POINTER_DOWN)initialSpan=span;else if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&initialSpan>0&&Math.abs(span-initialSpan)>dp(40)){applyResize(span>initialSpan?1:0);initialSpan=span;}return true;}
        if(full&&current!=null&&audio!=null&&ProPrefs.flag(this,"gestures",true)&&e.getPointerCount()==1){
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();downPosition=player.getCurrentPosition();horizontal=vertical=false;oldVolume=audio.getStreamVolume(AudioManager.STREAM_MUSIC);oldGain=player.getVolume();oldBrightness=getWindow().getAttributes().screenBrightness;if(oldBrightness<0)oldBrightness=android.provider.Settings.System.getInt(getContentResolver(),android.provider.Settings.System.SCREEN_BRIGHTNESS,128)/255f;}
            if(e.getActionMasked()==MotionEvent.ACTION_MOVE){
                float dy=downY-e.getY();float dx=e.getX()-downX;float height=Math.max(1,screen.getHeight());
                if(!vertical&&ProPrefs.flag(this,"seek_gesture",true)&&Math.abs(dx)>dp(35)&&Math.abs(dx)>Math.abs(dy)*1.5f){horizontal=true;long dur=player.getDuration();if(dur>0){long target=Math.max(0,Math.min(dur,downPosition+(long)(dx/Math.max(1,screen.getWidth())*60000)));player.seekTo(target);if(ProPrefs.flag(this,"seek_preview",true))showOsd(timeText(target));}return true;}
                if(!horizontal&&ProPrefs.flag(this,"vertical_gestures",true)&&Math.abs(dy)>dp(30)&&Math.abs(dy)>Math.abs(dx)*1.5f){vertical=true;
                    if(downX>screen.getWidth()/2f){int max=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);if(ProPrefs.flag(this,"system_volume",true))audio.setStreamVolume(AudioManager.STREAM_MUSIC,Math.max(0,Math.min(max,oldVolume+Math.round(dy/height*max))),0);else player.setVolume(Math.max(0,Math.min(1,oldGain+dy/height)));showVolume();}
                    else{WindowManager.LayoutParams p=getWindow().getAttributes();p.screenBrightness=Math.max(.05f,Math.min(1f,oldBrightness+dy/height));getWindow().setAttributes(p);showOsd(tr("الإضاءة","Brightness")+"  "+Math.round(p.screenBrightness*100)+"%");}
                }
            }
            if(e.getActionMasked()==MotionEvent.ACTION_UP){if(horizontal||vertical){MotionEvent cancel=MotionEvent.obtain(e);cancel.setAction(MotionEvent.ACTION_CANCEL);super.dispatchTouchEvent(cancel);cancel.recycle();setControls(true);armHide();return true;}if(Math.abs(e.getX()-downX)<dp(20)&&Math.abs(e.getY()-downY)<dp(20)&&ProPrefs.flag(this,"double_tap",true)){long now=android.os.SystemClock.elapsedRealtime();if(now-lastTap<300){seekBy(e.getX()>screen.getWidth()/2f?stepMs():-stepMs());lastTap=0;MotionEvent cancel=MotionEvent.obtain(e);cancel.setAction(MotionEvent.ACTION_CANCEL);super.dispatchTouchEvent(cancel);cancel.recycle();setControls(true);armHide();return true;}lastTap=now;}}
        }
        return super.dispatchTouchEvent(e);
    }
    @Override protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);if(current!=null){b.putString("uri",current.toString());b.putLong("position",player.getCurrentPosition());b.putBoolean("play",player.getPlayWhenReady());}}
    @Override protected void onResume(){super.onResume();foregroundVisible=true;internalLaunch=false;if(player!=null){if(transferred&&BackgroundService.active!=null)restoreBackground();transferred=false;applyPrefs();updateMediaNotification();}}
    @Override protected void onPause(){savePosition();if(!internalLaunch&&!isFinishing()&&player!=null&&current!=null&&player.getPlayWhenReady()&&ProPrefs.backgroundAllowed(ProPrefs.number(this,"background_mode",0),isAudio())){android.os.PowerManager pm=(android.os.PowerManager)getSystemService(POWER_SERVICE);if(pm!=null&&!pm.isInteractive())transferBackground();}super.onPause();}
    @Override protected void onStop(){foregroundVisible=false;if(Build.VERSION.SDK_INT<26||!isInPictureInPictureMode())if(player!=null){if(!internalLaunch&&!isFinishing()&&!transferred&&current!=null&&player.getPlayWhenReady()&&ProPrefs.backgroundAllowed(ProPrefs.number(this,"background_mode",0),isAudio()))transferBackground();player.pause();releaseNowPlaying();}super.onStop();}
    @Override protected void onDestroy(){foregroundVisible=false;releaseNowPlaying();handler.removeCallbacksAndMessages(null);if(screenshot!=null){screenshot.recycle();screenshot=null;}releaseEffects();savePosition();if(player!=null){screen.setPlayer(null);player.release();player=null;}super.onDestroy();}
}
