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
public final class LocalPlayerActivity extends SecureActivity {
    final ArrayList<Store.Job> queue=new ArrayList<>();
    final Handler handler=new Handler(Looper.getMainLooper());
    VideoView video;
    SeekBar seek;
    TextView title, status, time, position, subtitle;LinearLayout playerRoot;FrameLayout mediaScreen;Button pipButton,subtitleButton;boolean pipRequested=false;java.util.List<SrtTrack.Cue> cues=Collections.emptyList();String cueJob="";
    Button play, repeat, speedButton, timerButton, backgroundButton;ImageView playIcon;LinearLayout playerChrome,playerControls;boolean cinema=false;
    MediaPlayer preparedPlayer;final AudioEffects effects=new AudioEffects();float playbackSpeed=1f;long sleepDeadline=0;
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
        bg=0xff000000;panel=0xff17191c;fg=0xfff4f4f5;int[] palette={0xff438ee9,0xff087b5f,0xff8054ce,0xffc65b13,0xffbe4149,0xff626b78};accent=palette[Math.max(0,Math.min(5,prefs.getInt("accent",0)))];
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
        LinearLayout root=new LinearLayout(this);playerRoot=root;root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(bg);root.setPadding(dp(6),dp(4),dp(6),dp(8));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        playerChrome=header;header.addView(PremiumUi.action(this,"back",tr("رجوع","Back"),fg,this::finish),new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout titles=new LinearLayout(this);titles.setOrientation(1);title=text("",14,fg);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);title.setGravity(Gravity.START);titles.addView(title);position=text("",11,0xffa7a9af);position.setGravity(Gravity.START);titles.addView(position);header.addView(titles,new LinearLayout.LayoutParams(0,-2,1));header.addView(PremiumUi.action(this,"more",tr("أدوات المشغل","Player tools"),fg,this::playerMenu),new LinearLayout.LayoutParams(dp(44),dp(44)));root.addView(header);
        FrameLayout screen=new FrameLayout(this);mediaScreen=screen;screen.setBackgroundColor(android.graphics.Color.BLACK);
        video=new VideoView(this);video.setAudioFocusRequest(0);
        screen.addView(video,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));video.setOnTouchListener(this::gesture);subtitle=text("",15,android.graphics.Color.WHITE);subtitle.setBackgroundColor(0xaa000000);subtitle.setPadding(dp(8),dp(4),dp(8),dp(4));subtitle.setVisibility(View.GONE);FrameLayout.LayoutParams sub=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);sub.bottomMargin=dp(12);screen.addView(subtitle,sub);root.addView(screen,new LinearLayout.LayoutParams(-1,0,1));
        playerControls=new LinearLayout(this);playerControls.setOrientation(1);playerControls.setPadding(dp(8),dp(2),dp(8),dp(4));status=text("",12,0xffa7a9af);playerControls.addView(status,new LinearLayout.LayoutParams(-1,-2));
        seek=new SeekBar(this);seek.setMax(1);seek.setEnabled(false);seek.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));seek.setThumbTintList(android.content.res.ColorStateList.valueOf(accent));playerControls.addView(seek,new LinearLayout.LayoutParams(-1,dp(34)));
        time=text("00:00 / 00:00",12,fg);playerControls.addView(time);
        LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER);controls.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);item(controls,PremiumUi.action(this,"prev",tr("السابق","Previous"),fg,()->move(-1)));play=button(tr("تشغيل","Play"),this::togglePlayback);playIcon=PremiumUi.action(this,"play",tr("تشغيل","Play"),accent,this::togglePlayback);item(controls,playIcon);item(controls,PremiumUi.action(this,"next",tr("التالي","Next"),fg,()->move(1)));playerControls.addView(controls);
        LinearLayout quick=new LinearLayout(this);item(quick,PremiumUi.action(this,"repeat",tr("التكرار","Repeat"),fg,this::chooseRepeat));item(quick,PremiumUi.action(this,"list",tr("قائمة التشغيل","Playlist"),fg,this::chooseTrack));item(quick,PremiumUi.action(this,"ratio",tr("ملء الشاشة","Cinema mode"),fg,this::toggleCinema));item(quick,PremiumUi.action(this,"more",tr("أدوات","Tools"),fg,this::playerMenu));playerControls.addView(quick);root.addView(playerControls);
        repeat=button(repeatLabel(),this::chooseRepeat);speedButton=button(speedLabel(),this::chooseSpeed);timerButton=button(tr("مؤقت النوم","Sleep timer"),this::chooseSleep);backgroundButton=button(tr("تشغيل الصوت في الخلفية","Play audio in background"),this::handoffAudio);pipButton=button(tr("نافذة عائمة","Picture-in-picture"),this::enterPip);subtitleButton=button(tr("ترجمة SRT / ASS","SRT / ASS subtitles"),this::chooseSubtitle);setContentView(root);

        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar b){dragging=true;}
            public void onProgressChanged(SeekBar b,int progress,boolean user){if(user&&ready)time.setText(clock(progress)+" / "+clock(video.getDuration()));}
            public void onStopTrackingTouch(SeekBar b){if(ready){video.seekTo(b.getProgress());resumeMs=b.getProgress();finished=false;}dragging=false;refresh();}
        });
        video.setOnPreparedListener(mp->{if(!visible)return;preparedPlayer=mp;effects.attach(this,mp.getAudioSessionId());ready=true;finished=false;int duration=video.getDuration();seek.setEnabled(duration>0);seek.setMax(Math.max(1,duration));if(resumeMs>0&&duration>0)video.seekTo(Math.min(resumeMs,Math.max(0,duration-1)));status.setText(tr("جاهز للتشغيل","Ready"));if(wantPlay)startPlayback();refresh();});
        video.setOnCompletionListener(mp->{if(!visible)return;queue.get(index).playbackMs=0;Store.get(this).save();int next=nextIndex(index,queue.size(),mode,true);if(next==index){video.seekTo(0);finished=false;startPlayback();}else if(next>=0){index=next;load(queue.get(index).playbackMs,true);}else{finished=true;wantPlay=false;releaseFocus();status.setText(tr("انتهى التشغيل","Playback finished"));updatePlay();refresh();}});
        video.setOnErrorListener((mp,what,extra)->{effects.release();preparedPlayer=null;ready=false;wantPlay=false;finished=false;seek.setEnabled(false);releaseFocus();status.setText(tr("تعذر تشغيل الملف. جرّب فتحه بمشغل خارجي.","Cannot play this format or file. Try an external player."));updatePlay();return true;});
    }
    void togglePlayback(){if(wantPlay||(ready&&video.isPlaying()))pausePlayback();else startPlayback();}
    void toggleCinema(){if(isInPictureInPictureMode())return;cinema=!cinema;playerChrome.setVisibility(cinema?View.GONE:View.VISIBLE);playerControls.setVisibility(cinema?View.GONE:View.VISIBLE);getWindow().getDecorView().setSystemUiVisibility(cinema?View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY:0);}
    @Override public void onBackPressed(){if(cinema){toggleCinema();return;}super.onBackPressed();}
    void playerMenu(){if(queue.isEmpty())return;ArrayList<String> labels=new ArrayList<>();ArrayList<Runnable> actions=new ArrayList<>();labels.add(repeatLabel());actions.add(this::chooseRepeat);labels.add(tr("قائمة التشغيل","Playlist"));actions.add(this::chooseTrack);labels.add(tr("سرعة التشغيل","Speed")+" • "+speedLabel());actions.add(this::chooseSpeed);labels.add(tr("مؤقت النوم","Sleep timer"));actions.add(this::chooseSleep);if(isVideo(queue.get(index))){labels.add(tr("نافذة عائمة","Picture-in-picture"));actions.add(this::enterPip);labels.add(tr("ترجمة SRT / ASS","Subtitles"));actions.add(this::chooseSubtitle);}if(BackgroundAudioService.isAudio(queue.get(index))){labels.add(tr("تشغيل بالخلفية","Background audio"));actions.add(this::handoffAudio);}labels.add(tr("معادل الصوت","Equalizer"));actions.add(this::chooseEffects);labels.add(tr("توافق الملف","Compatibility"));actions.add(this::compatibility);new AlertDialog.Builder(this).setTitle(tr("أدوات المشغل","Player tools")).setItems(labels.toArray(new String[0]),(d,w)->actions.get(w).run()).setNegativeButton(tr("إغلاق","Close"),null).show();}
    String repeatLabel(){return mode==1?tr("تكرار ملف","Repeat one"):mode==2?tr("تكرار الكل","Repeat all"):tr("بدون تكرار","Repeat off");}
    void chooseRepeat(){new AlertDialog.Builder(this).setTitle(tr("وضع التكرار","Repeat mode")).setSingleChoiceItems(new String[]{tr("بدون تكرار","Off"),tr("تكرار ملف واحد","One"),tr("تكرار الكل","All")},mode,(d,w)->{mode=w;Store.settings(this).edit().putInt("player_repeat",mode).apply();repeat.setText(repeatLabel());d.dismiss();}).setNegativeButton(tr("إلغاء","Cancel"),null).show();}
    void chooseTrack(){String[] names=new String[queue.size()];for(int i=0;i<names.length;i++)names[i]=queue.get(i).name;new AlertDialog.Builder(this).setTitle(tr("قائمة التشغيل","Playlist")).setSingleChoiceItems(names,index,(d,w)->{rememberPosition();index=w;d.dismiss();load(queue.get(index).playbackMs,true);}).setNegativeButton(tr("إلغاء","Cancel"),null).show();}
    void move(int direction){
        if(queue.isEmpty())return;int next=direction>0?nextIndex(index,queue.size(),mode,false):index>0?index-1:mode==2?queue.size()-1:-1;
        if(next<0){Toast.makeText(this,tr("لا يوجد ملف آخر","No more tracks"),Toast.LENGTH_SHORT).show();return;}rememberPosition();index=next;load(queue.get(index).playbackMs,true);
    }
    void load(int offset,boolean autoplay){
        if(!visible||queue.isEmpty())return;
        video.stopPlayback();effects.release();preparedPlayer=null;releaseFocus();ready=false;finished=false;dragging=false;resumeMs=Math.max(0,offset);wantPlay=autoplay;
        Store.Job job=queue.get(index);backgroundButton.setVisibility(BackgroundAudioService.isAudio(job)?View.VISIBLE:View.GONE);pipButton.setEnabled(isVideo(job)&&getPackageManager().hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE));subtitleButton.setEnabled(isVideo(job));if(!job.id.equals(cueJob)){cues=Collections.emptyList();cueJob="";subtitle.setVisibility(View.GONE);}title.setText(job.name);position.setText((index+1)+" / "+queue.size());
        seek.setEnabled(false);seek.setMax(1);seek.setProgress(0);time.setText("00:00 / 00:00");
        status.setText(tr("جارٍ تجهيز الملف…","Preparing media…"));updatePlay();
        try{video.setVideoURI(Uri.parse(job.uri));}catch(RuntimeException e){ready=false;wantPlay=false;status.setText(tr("الملف غير متاح","File unavailable"));updatePlay();}
    }
    void compatibility(){if(queue.isEmpty())return;Store.Job job=queue.get(index);Uri uri=Uri.parse(job.uri);new AlertDialog.Builder(this).setTitle(tr("توافق الملف","Format compatibility")).setItems(new String[]{tr("فتح بمشغل آخر","Open in another player"),tr("تحويل إلى صيغة متوافقة","Convert to compatible format")},(d,w)->{try{if(w==0){Intent open=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,job.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);open.setClipData(ClipData.newRawUri("Media",uri));startActivity(Intent.createChooser(open,tr("اختر مشغلًا","Choose player")));}else startActivity(new Intent(this,isVideo(job)?ExportActivity.class:AudioStudioActivity.class).setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}catch(RuntimeException ex){Toast.makeText(this,tr("لا يوجد تطبيق مناسب","No compatible application"),Toast.LENGTH_SHORT).show();}}).show();}
    void chooseEffects(){if(preparedPlayer==null||!ready){Toast.makeText(this,tr("شغّل ملفًا أولًا","Play a file first"),Toast.LENGTH_SHORT).show();return;}effects.dialog(this,en);}
    void handoffAudio(){if(!ready||queue.isEmpty()||!BackgroundAudioService.isAudio(queue.get(index)))return;int offset=Math.max(0,video.getCurrentPosition());rememberPosition();pausePlayback();try{BackgroundAudioService.launch(this,queue.get(index).id,offset,sleepDeadline);finish();}catch(RuntimeException ex){status.setText(tr("تعذر تشغيل الخدمة","Could not start audio service"));}}
    void startPlayback(){
        if(!visible||video==null)return;wantPlay=true;stopService(new Intent(this,BackgroundAudioService.class));
        if(!ready){updatePlay();return;}
        if(!focusHeld){focusHeld=audio!=null&&audio.requestAudioFocus(focus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;}
        if(!focusHeld){wantPlay=false;status.setText(tr("الصوت مستخدم في تطبيق آخر","Audio focus unavailable"));updatePlay();return;}
        if(finished){video.seekTo(0);finished=false;}
        video.start();applySpeed();status.setText(tr("قيد التشغيل","Playing"));updatePlay();
    }
    void pausePlayback(){wantPlay=false;if(video!=null&&ready)video.pause();releaseFocus();if(status!=null&&ready)status.setText(tr("متوقف مؤقتًا","Paused"));updatePlay();}
    void releaseFocus(){if(focusHeld&&audio!=null&&focus!=null)audio.abandonAudioFocusRequest(focus);focusHeld=false;}
    void updatePlay(){if(play!=null)play.setText(wantPlay?tr("إيقاف مؤقت","Pause"):tr("تشغيل","Play"));if(playIcon!=null){playIcon.setImageDrawable(new PremiumUi.Glyph(wantPlay?"pause":"play",accent));playIcon.setContentDescription(wantPlay?tr("إيقاف مؤقت","Pause"):tr("تشغيل","Play"));}}
    void refresh(){if(sleepDeadline>0&&SystemClock.elapsedRealtime()>=sleepDeadline){sleepDeadline=0;timerButton.setText(tr("مؤقت النوم","Sleep timer"));pausePlayback();}if(video==null||!ready||dragging)return;int duration=video.getDuration(),pos=video.getCurrentPosition();if(duration>0){seek.setMax(duration);seek.setProgress(Math.max(0,Math.min(pos,duration)));time.setText(clock(pos)+" / "+clock(duration));}if(subtitle!=null){String line=SrtTrack.at(cues,pos);if(!line.contentEquals(subtitle.getText()))subtitle.setText(line);subtitle.setVisibility(line.isEmpty()?View.GONE:View.VISIBLE);}}
    void rememberPosition(){if(!ready||queue.isEmpty()||video==null)return;int pos=video.getCurrentPosition(),duration=video.getDuration();queue.get(index).playbackMs=finished||duration>0&&duration-pos<1500?0:Math.max(0,pos);Store.get(this).save();}
    String speedLabel(){return String.format(Locale.US,"%.2f×",playbackSpeed);}
    void chooseSpeed(){int[] speeds={50,75,100,125,150,175,200};String[] labels={"0.50×","0.75×","1.00×","1.25×","1.50×","1.75×","2.00×"};int chosen=2;for(int i=0;i<speeds.length;i++)if(Math.round(playbackSpeed*100)==speeds[i])chosen=i;new AlertDialog.Builder(this).setTitle(tr("سرعة التشغيل","Playback speed")).setSingleChoiceItems(labels,chosen,(d,w)->{playbackSpeed=speeds[w]/100f;Store.settings(this).edit().putInt("player_speed",speeds[w]).apply();speedButton.setText(speedLabel());if(ready&&video.isPlaying())applySpeed();d.dismiss();}).setNegativeButton(tr("إلغاء","Cancel"),null).show();}
    void applySpeed(){if(preparedPlayer==null)return;try{preparedPlayer.setPlaybackParams(preparedPlayer.getPlaybackParams().setSpeed(playbackSpeed));}catch(RuntimeException ex){playbackSpeed=1f;speedButton.setText(speedLabel());Toast.makeText(this,tr("السرعة غير مدعومة لهذا الملف","Speed not supported for this file"),Toast.LENGTH_SHORT).show();}}
    void chooseSleep(){int[] minutes={0,5,15,30,60};new AlertDialog.Builder(this).setTitle(tr("مؤقت إيقاف التشغيل","Sleep timer")).setItems(new String[]{tr("إلغاء المؤقت","Off"),"5 min","15 min","30 min","60 min"},(d,w)->{sleepDeadline=minutes[w]==0?0:SystemClock.elapsedRealtime()+minutes[w]*60000L;timerButton.setText(minutes[w]==0?tr("مؤقت النوم","Sleep timer"):minutes[w]+" min");}).show();}
    float touchX,touchY,touchBrightness;int touchVolume,touchPosition;long lastTap;boolean swipe;
    boolean gesture(View v,android.view.MotionEvent event){if(!ready||isInPictureInPictureMode()||queue.isEmpty()||!isVideo(queue.get(index)))return false;float x=event.getX(),y=event.getY(),width=Math.max(1,v.getWidth()),height=Math.max(1,v.getHeight());switch(event.getActionMasked()){
     case MotionEvent.ACTION_DOWN:touchX=x;touchY=y;touchPosition=video.getCurrentPosition();touchVolume=audio.getStreamVolume(AudioManager.STREAM_MUSIC);touchBrightness=getWindow().getAttributes().screenBrightness;if(touchBrightness<0)touchBrightness=0.5f;swipe=false;return true;
     case MotionEvent.ACTION_MOVE:float dx=x-touchX,dy=y-touchY;if(Math.abs(dx)+Math.abs(dy)>dp(24))swipe=true;if(swipe){if(Math.abs(dx)>Math.abs(dy)){int target=(int)Math.max(0,Math.min(video.getDuration(),touchPosition+dx/width*60000));time.setText(clock(target)+" / "+clock(video.getDuration()));}else if(touchX>width/2){int max=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);try{audio.setStreamVolume(AudioManager.STREAM_MUSIC,Math.max(0,Math.min(max,Math.round(touchVolume-dy/height*max))),0);}catch(SecurityException ignored){}}else{WindowManager.LayoutParams params=getWindow().getAttributes();params.screenBrightness=Math.max(0.05f,Math.min(1f,touchBrightness-dy/height));getWindow().setAttributes(params);}}return true;
     case MotionEvent.ACTION_UP:float mx=x-touchX,my=y-touchY;if(swipe&&Math.abs(mx)>Math.abs(my)){video.seekTo((int)Math.max(0,Math.min(video.getDuration(),touchPosition+mx/width*60000)));finished=false;}else if(!swipe){long now=SystemClock.elapsedRealtime();if(now-lastTap<350){video.seekTo(Math.max(0,Math.min(video.getDuration(),video.getCurrentPosition()+(x<width/2?-10000:10000))));lastTap=0;}else{lastTap=now;handler.postDelayed(()->{if(lastTap==now&&visible&&!isInPictureInPictureMode()){lastTap=0;toggleCinema();}},360);}v.performClick();}return true;
     case MotionEvent.ACTION_CANCEL:return true;default:return true;}}
    static boolean isVideo(Store.Job job){return LocalPlayerActivity.playable(job)&&"Videos".equals(HttpEngine.category(job.name,job.mime));}
    void enterPip(){if(screenLockEnabled()){Toast.makeText(this,tr("أوقف قفل التطبيق لاستخدام النافذة العائمة","Disable screen lock to use picture-in-picture"),Toast.LENGTH_SHORT).show();return;}if(!ready||queue.isEmpty()||!isVideo(queue.get(index))||!getPackageManager().hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)){Toast.makeText(this,tr("النافذة العائمة غير متاحة","Picture-in-picture unavailable"),Toast.LENGTH_SHORT).show();return;}try{if(cinema)toggleCinema();pipRequested=true;android.app.PictureInPictureParams params=new android.app.PictureInPictureParams.Builder().setAspectRatio(new android.util.Rational(16,9)).build();if(!enterPictureInPictureMode(params)){pipRequested=false;setPipLayout(false);}}catch(RuntimeException e){pipRequested=false;setPipLayout(false);Toast.makeText(this,tr("تعذر فتح النافذة العائمة","Could not enter picture-in-picture"),Toast.LENGTH_SHORT).show();}}
    @Override public void onPictureInPictureModeChanged(boolean inPip,android.content.res.Configuration config){super.onPictureInPictureModeChanged(inPip,config);pipRequested=inPip;setPipLayout(inPip);}
    void setPipLayout(boolean pip){if(playerRoot==null||mediaScreen==null)return;for(int i=0;i<playerRoot.getChildCount();i++){View child=playerRoot.getChildAt(i);if(child!=mediaScreen)child.setVisibility(pip?View.GONE:View.VISIBLE);}if(!pip&&!queue.isEmpty())backgroundButton.setVisibility(BackgroundAudioService.isAudio(queue.get(index))?View.VISIBLE:View.GONE);playerRoot.setPadding(pip?0:dp(10),pip?0:dp(6),pip?0:dp(10),pip?0:dp(8));}
    void chooseSubtitle(){new AlertDialog.Builder(this).setTitle(tr("الترجمة المحلية","Local subtitles")).setItems(new String[]{tr("اختيار ملف SRT / ASS","Choose SRT / ASS file"),tr("إيقاف الترجمة","Disable subtitles")},(d,w)->{if(w==1){cues=Collections.emptyList();cueJob="";subtitle.setVisibility(View.GONE);return;}try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),93);}catch(RuntimeException ex){Toast.makeText(this,tr("لا يوجد منتقي ملفات","No file picker"),Toast.LENGTH_SHORT).show();}}).show();}
    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(req!=93||result!=RESULT_OK||data==null||data.getData()==null||queue.isEmpty())return;Uri uri=data.getData();String id=queue.get(index).id;new Thread(()->{try(java.io.InputStream in=getContentResolver().openInputStream(uri)){java.util.List<SrtTrack.Cue> parsed=SrtTrack.read(in);handler.post(()->{if(isFinishing()||isDestroyed()||queue.isEmpty()||!id.equals(queue.get(index).id))return;cues=parsed;cueJob=id;Toast.makeText(this,tr("تم تحميل الترجمة","Subtitles loaded")+": "+parsed.size(),Toast.LENGTH_SHORT).show();});}catch(Exception e){handler.post(()->{if(!isFinishing()&&!isDestroyed())Toast.makeText(this,tr("ملف ترجمة غير صالح أو كبير","Invalid or oversized subtitle"),Toast.LENGTH_SHORT).show();});}},"xdownload-srt").start();}
    static String clock(int ms){int sec=Math.max(0,ms)/1000;return sec>=3600?String.format(Locale.US,"%d:%02d:%02d",sec/3600,sec/60%60,sec%60):String.format(Locale.US,"%02d:%02d",sec/60,sec%60);}
    @Override protected void onResume(){super.onResume();if(!screenAccessAllowed()||video==null)return;if(visible){pipRequested=false;setPipLayout(false);return;}if(cinema)toggleCinema();visible=true;load(resumeMs,first);first=false;handler.removeCallbacks(ticker);handler.post(ticker);}
    @Override protected void onScreenUnlocked(){if(video!=null&&!visible){visible=true;load(resumeMs,first);first=false;handler.removeCallbacks(ticker);handler.post(ticker);}}
    @Override protected void onPause(){if(!isInPictureInPictureMode()&&!pipRequested)pauseAndRelease();super.onPause();}
    @Override protected void onStop(){pauseAndRelease();pipRequested=false;super.onStop();}
    void pauseAndRelease(){if(!visible)return;visible=false;handler.removeCallbacks(ticker);if(video!=null){if(ready){rememberPosition();resumeMs=queue.get(index).playbackMs;}video.stopPlayback();effects.release();preparedPlayer=null;}ready=false;wantPlay=false;releaseFocus();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);effects.release();if(video!=null)video.stopPlayback();releaseFocus();super.onDestroy();}
}
