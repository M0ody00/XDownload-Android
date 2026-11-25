package com.xdownload.mobile;

import android.app.*;
import android.content.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Local, foreground-only playback of app-owned completed downloads. No network or extra native codec library. */
public final class LocalPlayerActivity extends Activity {
    final ArrayList<Store.Job> queue=new ArrayList<>();
    final Handler handler=new Handler(Looper.getMainLooper());
    VideoView video;
    SeekBar seek;
    TextView title, status, time, position;
    Button play, repeat, speedButton, timerButton;
    MediaPlayer preparedPlayer;float playbackSpeed=1f;long sleepDeadline=0;
    AudioManager audio;
    AudioFocusRequest focus;
    boolean ready, visible, wantPlay, dragging, finished, focusHeld, first=true, en;
    int index=0, mode=0, resumeMs=0;
    int fg,bg,panel,accent=0xff438ee9;
    final Runnable ticker=new Runnable(){public void run(){if(!visible)return;refresh();handler.postDelayed(this,500);}};

    static boolean playable(Store.Job job){
        if(job==null||!"complete".equals(job.state)||job.uri==null||!job.uri.startsWith("content://"))return false;
        String category=HttpEngine.category(job.name,job.mime);
        return "Videos".equals(category)||"Music".equals(category);
    }

    // On completion, off advances to the end, one repeats this item, all wraps.
    static int nextIndex(int current,int count,int repeat,boolean completion){
        if(count<=0||current<0||current>=count)return -1;
        if(completion&&repeat==1)return current;
        if(current+1<count)return current+1;
        return repeat==2?0:-1;
    }

    String tr(String ar,String english){return en?english:ar;}
    int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    TextView text(String value,int size,int color){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);
        t.setGravity(Gravity.CENTER);t.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);return t;
    }
    Button button(String label,Runnable run){
        Button b=new Button(this);b.setText(label);b.setTextColor(fg);b.setTextSize(14);b.setAllCaps(false);
        android.graphics.drawable.GradientDrawable shape=new android.graphics.drawable.GradientDrawable();
        shape.setColor(panel);shape.setCornerRadius(dp(12));
        b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33438ee9),shape,null));
        b.setOnClickListener(v->run.run());return b;
    }
    void item(LinearLayout row,View child){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(48),1);lp.setMargins(dp(3),dp(3),dp(3),dp(3));row.addView(child,lp);}

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        SharedPreferences prefs=Store.settings(this);en=prefs.getBoolean("english",false);
        boolean light=prefs.getBoolean("light",false);bg=light?0xfff3f6fa:0xff0b111a;panel=light?0xffe1e9f2:0xff182332;fg=light?0xff172438:0xfff2f5fb;
        mode=Math.max(0,Math.min(2,prefs.getInt("player_repeat",0)));playbackSpeed=Math.max(50,Math.min(200,prefs.getInt("player_speed",100)))/100f;
        String id=getIntent().getStringExtra("job_id");ArrayList<Store.Job> jobs=Store.get(this).list();Collections.reverse(jobs);
        for(Store.Job job:jobs)if(playable(job)){if(job.id.equals(id))index=queue.size();queue.add(job);}
        if(id==null||queue.isEmpty()||!queue.get(index).id.equals(id)){Toast.makeText(this,tr("الملف غير متاح","File unavailable"),Toast.LENGTH_SHORT).show();finish();return;}
        resumeMs=queue.get(index).playbackMs;audio=getSystemService(AudioManager.class);
        focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener(change->{if(change==AudioManager.AUDIOFOCUS_LOSS||change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT||change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)pausePlayback();},handler)
            .build();

        getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(bg);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(bg);root.setPadding(dp(10),dp(6),dp(10),dp(8));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        Button back=button(tr("رجوع","Back"),this::finish);header.addView(back,new LinearLayout.LayoutParams(dp(82),dp(46)));
        TextView brand=text("X DOWNLOAD • PLAYER",16,accent);header.addView(brand,new LinearLayout.LayoutParams(0,dp(46),1));root.addView(header);
        title=text("",17,fg);title.setMaxLines(2);root.addView(title,new LinearLayout.LayoutParams(-1,-2));
        position=text("",12,accent);root.addView(position);
        FrameLayout screen=new FrameLayout(this);screen.setBackgroundColor(android.graphics.Color.BLACK);
        video=new VideoView(this);video.setAudioFocusRequest(0);
        screen.addView(video,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));root.addView(screen,new LinearLayout.LayoutParams(-1,0,1));
        status=text("",13,fg);root.addView(status,new LinearLayout.LayoutParams(-1,-2));
        seek=new SeekBar(this);seek.setMax(1);seek.setEnabled(false);root.addView(seek,new LinearLayout.LayoutParams(-1,dp(40)));
        time=text("00:00 / 00:00",12,fg);root.addView(time);
        LinearLayout controls=new LinearLayout(this);item(controls,button(tr("السابق","Previous"),()->move(-1)));
        play=button(tr("تشغيل","Play"),()->{if(wantPlay||(ready&&video.isPlaying()))pausePlayback();else startPlayback();});item(controls,play);
        item(controls,button(tr("التالي","Next"),()->move(1)));root.addView(controls);
        LinearLayout extras=new LinearLayout(this);repeat=button(repeatLabel(),this::chooseRepeat);item(extras,repeat);
        item(extras,button(tr("قائمة التشغيل","Playlist"),this::chooseTrack));root.addView(extras);LinearLayout more=new LinearLayout(this);speedButton=button(speedLabel(),this::chooseSpeed);item(more,speedButton);timerButton=button(tr("مؤقت النوم","Sleep timer"),this::chooseSleep);item(more,timerButton);root.addView(more);setContentView(root);

        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar b){dragging=true;}
            public void onProgressChanged(SeekBar b,int progress,boolean user){if(user&&ready)time.setText(clock(progress)+" / "+clock(video.getDuration()));}
            public void onStopTrackingTouch(SeekBar b){if(ready){video.seekTo(b.getProgress());resumeMs=b.getProgress();finished=false;}dragging=false;refresh();}
        });
        video.setOnPreparedListener(mp->{if(!visible)return;preparedPlayer=mp;ready=true;finished=false;int duration=video.getDuration();seek.setEnabled(duration>0);seek.setMax(Math.max(1,duration));if(resumeMs>0&&duration>0)video.seekTo(Math.min(resumeMs,Math.max(0,duration-1)));status.setText(tr("جاهز للتشغيل","Ready"));if(wantPlay)startPlayback();refresh();});
        video.setOnCompletionListener(mp->{if(!visible)return;queue.get(index).playbackMs=0;Store.get(this).save();int next=nextIndex(index,queue.size(),mode,true);if(next==index){video.seekTo(0);finished=false;startPlayback();}else if(next>=0){index=next;load(queue.get(index).playbackMs,true);}else{finished=true;wantPlay=false;releaseFocus();status.setText(tr("انتهى التشغيل","Playback finished"));updatePlay();refresh();}});
        video.setOnErrorListener((mp,what,extra)->{preparedPlayer=null;ready=false;wantPlay=false;finished=false;seek.setEnabled(false);releaseFocus();status.setText(tr("تعذر تشغيل الملف. جرّب فتحه بمشغل خارجي.","Cannot play this format or file. Try an external player."));updatePlay();return true;});
    }
    String repeatLabel(){return mode==1?tr("تكرار ملف","Repeat one"):mode==2?tr("تكرار الكل","Repeat all"):tr("بدون تكرار","Repeat off");}
    void chooseRepeat(){new AlertDialog.Builder(this).setTitle(tr("وضع التكرار","Repeat mode")).setSingleChoiceItems(new String[]{tr("بدون تكرار","Off"),tr("تكرار ملف واحد","One"),tr("تكرار الكل","All")},mode,(d,w)->{mode=w;Store.settings(this).edit().putInt("player_repeat",mode).apply();repeat.setText(repeatLabel());d.dismiss();}).setNegativeButton(tr("إلغاء","Cancel"),null).show();}
    void chooseTrack(){String[] names=new String[queue.size()];for(int i=0;i<names.length;i++)names[i]=queue.get(i).name;new AlertDialog.Builder(this).setTitle(tr("قائمة التشغيل","Playlist")).setSingleChoiceItems(names,index,(d,w)->{rememberPosition();index=w;d.dismiss();load(queue.get(index).playbackMs,true);}).setNegativeButton(tr("إلغاء","Cancel"),null).show();}
    void move(int direction){
        if(queue.isEmpty())return;int next=direction>0?nextIndex(index,queue.size(),mode,false):index>0?index-1:mode==2?queue.size()-1:-1;
        if(next<0){Toast.makeText(this,tr("لا يوجد ملف آخر","No more tracks"),Toast.LENGTH_SHORT).show();return;}rememberPosition();index=next;load(queue.get(index).playbackMs,true);
    }
    void load(int offset,boolean autoplay){
        if(!visible||queue.isEmpty())return;
        video.stopPlayback();preparedPlayer=null;releaseFocus();ready=false;finished=false;dragging=false;resumeMs=Math.max(0,offset);wantPlay=autoplay;
        Store.Job job=queue.get(index);title.setText(job.name);position.setText((index+1)+" / "+queue.size());
        seek.setEnabled(false);seek.setMax(1);seek.setProgress(0);time.setText("00:00 / 00:00");
        status.setText(tr("جارٍ تجهيز الملف…","Preparing media…"));updatePlay();
        try{video.setVideoURI(Uri.parse(job.uri));}catch(RuntimeException e){ready=false;wantPlay=false;status.setText(tr("الملف غير متاح","File unavailable"));updatePlay();}
    }
    void startPlayback(){
        if(!visible||video==null)return;wantPlay=true;
        if(!ready){updatePlay();return;}
        if(!focusHeld){focusHeld=audio!=null&&audio.requestAudioFocus(focus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;}
        if(!focusHeld){wantPlay=false;status.setText(tr("الصوت مستخدم في تطبيق آخر","Audio focus unavailable"));updatePlay();return;}
        if(finished){video.seekTo(0);finished=false;}
        video.start();applySpeed();status.setText(tr("قيد التشغيل","Playing"));updatePlay();
    }
    void pausePlayback(){wantPlay=false;if(video!=null&&ready)video.pause();releaseFocus();if(status!=null&&ready)status.setText(tr("متوقف مؤقتًا","Paused"));updatePlay();}
    void releaseFocus(){if(focusHeld&&audio!=null&&focus!=null)audio.abandonAudioFocusRequest(focus);focusHeld=false;}
    void updatePlay(){if(play!=null)play.setText(wantPlay?tr("إيقاف مؤقت","Pause"):tr("تشغيل","Play"));}
    void refresh(){if(sleepDeadline>0&&SystemClock.elapsedRealtime()>=sleepDeadline){sleepDeadline=0;timerButton.setText(tr("مؤقت النوم","Sleep timer"));pausePlayback();}if(video==null||!ready||dragging)return;int duration=video.getDuration(),pos=video.getCurrentPosition();if(duration>0){seek.setMax(duration);seek.setProgress(Math.max(0,Math.min(pos,duration)));time.setText(clock(pos)+" / "+clock(duration));}}
    void rememberPosition(){if(!ready||queue.isEmpty()||video==null)return;int pos=video.getCurrentPosition(),duration=video.getDuration();queue.get(index).playbackMs=finished||duration>0&&duration-pos<1500?0:Math.max(0,pos);Store.get(this).save();}
    String speedLabel(){return String.format(Locale.US,"%.2f×",playbackSpeed);}
    void chooseSpeed(){int[] speeds={50,75,100,125,150,175,200};String[] labels={"0.50×","0.75×","1.00×","1.25×","1.50×","1.75×","2.00×"};int chosen=2;for(int i=0;i<speeds.length;i++)if(Math.round(playbackSpeed*100)==speeds[i])chosen=i;new AlertDialog.Builder(this).setTitle(tr("سرعة التشغيل","Playback speed")).setSingleChoiceItems(labels,chosen,(d,w)->{playbackSpeed=speeds[w]/100f;Store.settings(this).edit().putInt("player_speed",speeds[w]).apply();speedButton.setText(speedLabel());if(ready&&video.isPlaying())applySpeed();d.dismiss();}).setNegativeButton(tr("إلغاء","Cancel"),null).show();}
    void applySpeed(){if(preparedPlayer==null)return;try{preparedPlayer.setPlaybackParams(preparedPlayer.getPlaybackParams().setSpeed(playbackSpeed));}catch(RuntimeException ex){playbackSpeed=1f;speedButton.setText(speedLabel());Toast.makeText(this,tr("السرعة غير مدعومة لهذا الملف","Speed not supported for this file"),Toast.LENGTH_SHORT).show();}}
    void chooseSleep(){int[] minutes={0,5,15,30,60};new AlertDialog.Builder(this).setTitle(tr("مؤقت إيقاف التشغيل","Sleep timer")).setItems(new String[]{tr("إلغاء المؤقت","Off"),"5 min","15 min","30 min","60 min"},(d,w)->{sleepDeadline=minutes[w]==0?0:SystemClock.elapsedRealtime()+minutes[w]*60000L;timerButton.setText(minutes[w]==0?tr("مؤقت النوم","Sleep timer"):minutes[w]+" min");}).show();}
    static String clock(int ms){int sec=Math.max(0,ms)/1000;return sec>=3600?String.format(Locale.US,"%d:%02d:%02d",sec/3600,sec/60%60,sec%60):String.format(Locale.US,"%02d:%02d",sec/60,sec%60);}
    @Override protected void onResume(){super.onResume();if(video==null)return;visible=true;load(resumeMs,first);first=false;handler.removeCallbacks(ticker);handler.post(ticker);}
    @Override protected void onPause(){visible=false;handler.removeCallbacks(ticker);if(video!=null){if(ready){rememberPosition();resumeMs=queue.get(index).playbackMs;}video.stopPlayback();preparedPlayer=null;}ready=false;wantPlay=false;releaseFocus();super.onPause();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);if(video!=null)video.stopPlayback();releaseFocus();super.onDestroy();}
}
