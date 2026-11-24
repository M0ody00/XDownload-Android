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
    private boolean full=false;
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
        TextView logo=label("X PLAYER LITE",24,ACCENT);logo.setTypeface(null,Typeface.BOLD);header.addView(logo);
        title=label("اختر فيديو أو ملف صوتي",15,INK);title.setMaxLines(2);header.addView(title);
        root.addView(header);
        screen=new PlayerView(this);screen.setBackgroundColor(Color.BLACK);screen.setUseController(true);screen.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        root.addView(screen,new LinearLayout.LayoutParams(-1,0,1));
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
        root.addView(tools);setContentView(root);
        DefaultRenderersFactory renderers=new DefaultRenderersFactory(this).setEnableDecoderFallback(true);
        player=new ExoPlayer.Builder(this,renderers).build();screen.setPlayer(player);
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true);
        player.setHandleAudioBecomingNoisy(true);
        player.addListener(new Player.Listener(){
            @Override public void onPlaybackStateChanged(int state){
                if(state==Player.STATE_BUFFERING)status.setText("جارٍ تجهيز الملف…");
                else if(state==Player.STATE_READY)status.setText("الملف جاهز • Ready");
                else if(state==Player.STATE_ENDED){status.setText("انتهى التشغيل");savePosition();if(fromLibrary&&libraryQueue.indexOf(current)>=0&&libraryQueue.indexOf(current)<libraryQueue.size()-1)moveTrack(1);}
            }
            @Override public void onIsPlayingChanged(boolean playing){
                if(playing){getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);status.setText("جارٍ التشغيل");}
                else {getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);if(player.getPlaybackState()==Player.STATE_READY)status.setText("متوقف مؤقتًا");}
            }
            @Override public void onRenderedFirstFrame(){renderedFrame=true;}
            @Override public void onPlayerError(PlaybackException e){status.setText("تعذّر التشغيل: "+e.getErrorCodeName());new AlertDialog.Builder(MainActivity.this).setTitle("تعذّر فتح الملف").setMessage("قد تكون صيغة الترميز غير مدعومة على هذا الجهاز أو الملف تالفًا.\n"+e.getErrorCodeName()).setPositiveButton("حسنًا",null).show();}
        });
        open.setOnClickListener(v->pick(false));
        subs.setOnClickListener(v->{if(current==null){Toast.makeText(this,"افتح ملفًا أولًا",Toast.LENGTH_SHORT).show();return;}pick(true);});
        speed.setOnClickListener(v->chooseSpeed());
        back.setOnClickListener(v->player.seekTo(Math.max(0,player.getCurrentPosition()-10000)));
        next.setOnClickListener(v->{long d=player.getDuration();long p=player.getCurrentPosition()+10000;player.seekTo(d>0?Math.min(d,p):p);});
        fullscreen.setOnClickListener(v->setFullscreen(!full));
        pip.setOnClickListener(v->enterPip());
        if(state!=null && state.getString("uri")!=null){fromLibrary=getIntent().getBooleanExtra("library",false);current=Uri.parse(state.getString("uri"));openMedia(current,false);player.seekTo(state.getLong("position"));player.setPlayWhenReady(state.getBoolean("play"));}
        else handle(getIntent());
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
        if(u!=null && ("content".equals(u.getScheme())||"file".equals(u.getScheme())))openMedia(u,true);
    }
    void openMedia(Uri u,boolean resume){
        savePosition();current=u;subtitle=null;subtitleMime=null;title.setText(displayName(u));
        long pos=resume?getPreferences(MODE_PRIVATE).getLong("p:"+u,0):0;
        prepare(u,pos,true);
    }
    private void prepare(Uri u,long pos,boolean play){
        renderedFrame=false;
        MediaItem.Builder item=new MediaItem.Builder().setUri(u);
        if(subtitle!=null)item.setSubtitleConfigurations(Collections.singletonList(new MediaItem.SubtitleConfiguration.Builder(subtitle).setMimeType(subtitleMime).setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()));
        player.setMediaItem(item.build(),pos);player.prepare();player.setPlayWhenReady(play);
    }
    private void savePosition(){if(player==null||current==null)return;long p=player.getCurrentPosition(),d=player.getDuration();if(player.getPlaybackState()==Player.STATE_ENDED||(d>0&&d-p<1000))p=0;getPreferences(MODE_PRIVATE).edit().putLong("p:"+current,p).apply();}
    private void chooseSpeed(){String[] items={"0.5×","0.75×","1×","1.25×","1.5×","2×"};float[] rates={.5f,.75f,1f,1.25f,1.5f,2f};new AlertDialog.Builder(this).setTitle("سرعة التشغيل").setItems(items,(d,w)->player.setPlaybackSpeed(rates[w])).show();}
    private void setFullscreen(boolean f){full=f;header.setVisibility(f?View.GONE:View.VISIBLE);tools.setVisibility(f?View.GONE:View.VISIBLE);getWindow().getDecorView().setSystemUiVisibility(f?5894:0);screen.showController();}
    private void enterPip(){if(Build.VERSION.SDK_INT>=26&&getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)&&current!=null){try{enterPictureInPictureMode(new PictureInPictureParams.Builder().setAspectRatio(new Rational(16,9)).build());}catch(Exception e){Toast.makeText(this,"الوضع العائم غير متاح",Toast.LENGTH_SHORT).show();}}else Toast.makeText(this,"الوضع العائم غير مدعوم أو لا يوجد ملف",Toast.LENGTH_SHORT).show();}
    @Override public void onPictureInPictureModeChanged(boolean inPip,Configuration c){super.onPictureInPictureModeChanged(inPip,c);screen.setUseController(!inPip);header.setVisibility(inPip||full?View.GONE:View.VISIBLE);tools.setVisibility(inPip||full?View.GONE:View.VISIBLE);}
    @Override public void onBackPressed(){if(full){setFullscreen(false);return;}super.onBackPressed();}
    @Override public boolean dispatchTouchEvent(MotionEvent e){
        if(full&&current!=null&&audio!=null&&e.getPointerCount()==1){
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();oldVolume=audio.getStreamVolume(AudioManager.STREAM_MUSIC);oldBrightness=getWindow().getAttributes().screenBrightness;if(oldBrightness<0)oldBrightness=.5f;}
            if(e.getActionMasked()==MotionEvent.ACTION_MOVE){
                float dy=downY-e.getY();float dx=e.getX()-downX;float height=Math.max(1,screen.getHeight());
                if(Math.abs(dy)>dp(30)&&Math.abs(dy)>Math.abs(dx)*1.5f){
                    if(downX>screen.getWidth()/2f){int max=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);audio.setStreamVolume(AudioManager.STREAM_MUSIC,Math.max(0,Math.min(max,oldVolume+Math.round(dy/height*max))),0);}
                    else{WindowManager.LayoutParams p=getWindow().getAttributes();p.screenBrightness=Math.max(.05f,Math.min(1f,oldBrightness+dy/height));getWindow().setAttributes(p);}
                }
            }
        }
        return super.dispatchTouchEvent(e);
    }
    @Override protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);if(current!=null){b.putString("uri",current.toString());b.putLong("position",player.getCurrentPosition());b.putBoolean("play",player.getPlayWhenReady());}}
    @Override protected void onPause(){savePosition();super.onPause();}
    @Override protected void onStop(){if(Build.VERSION.SDK_INT<26||!isInPictureInPictureMode())if(player!=null)player.pause();super.onStop();}
    @Override protected void onDestroy(){savePosition();if(player!=null){screen.setPlayer(null);player.release();player=null;}super.onDestroy();}
}
