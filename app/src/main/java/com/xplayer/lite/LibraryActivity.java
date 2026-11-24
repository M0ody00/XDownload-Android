package com.xplayer.lite;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.*;
import android.util.LruCache;
import android.util.Size;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

public class LibraryActivity extends Activity {
    private static final int BG=Ui.BLACK,PANEL=Ui.PANEL,INK=Ui.WHITE,MUTED=Ui.MUTED,ACCENT=Ui.BLUE;
    static final int VIDEO=0,AUDIO=1,ALL=2;
    private final ExecutorService scanner=Executors.newSingleThreadExecutor();
    private final ExecutorService thumbnails=new ThreadPoolExecutor(2,2,0L,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<Runnable>(40),new ThreadPoolExecutor.DiscardOldestPolicy());
    private final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(4*1024*1024){@Override protected int sizeOf(String k,Bitmap b){return b.getByteCount();}};
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ArrayList<Media> items=new ArrayList<>();
    private final ArrayList<Row> rows=new ArrayList<>();
    private GridView list;
    private FrameLayout contentFrame;
    private LinearLayout profile,shortcuts,nav;
    private TextView heading;
    private ImageView backIcon;
    private boolean grid=false,ascending=true,profileOpen=false;
    private int viewMode=0;
    private final int[] navColors={Ui.BLUE,Ui.MUTED,Ui.MUTED,Ui.MUTED};
    private int appliedLanguage=-1,appliedAccent=-1;
    private TextView summary,crumb;
    private Button permission,modeButton;
    private final Button[] tabs=new Button[3];
    private EditText search;
    private RowAdapter adapter;
    private int tab=VIDEO, sort=0, generation=0;
    private boolean folders=true, observing=false;
    private volatile boolean dead=false;
    private String folder=null, keyword="";
    private int collection=0;
    private String playlist=null;
    private boolean privateUnlocked=false;
    private Runnable afterUnlock;
    private final HashSet<String> favoriteSet=new HashSet<>();
    private final Runnable refreshTask=()->refresh();
    private final ContentObserver observer=new ContentObserver(handler){@Override public void onChange(boolean self){handler.removeCallbacks(refreshTask);handler.postDelayed(refreshTask,350);}};
    static final class Media {
        final Uri uri; final String name,path,volume,mime; final long duration,size,date; final boolean video;
        Media(Uri u,String n,String p,String v,String m,long d,long s,long date,boolean video){uri=u;name=n;path=p;volume=v;mime=m;duration=d;size=s;this.date=date;this.video=video;}
        String key(){return volume+"|"+path;}
    }
    static final class Row {
        final Media media;final String key,name,detail;final int count;
        Row(Media m,String k,String n,String d,int c){media=m;key=k;name=n;detail=d;count=c;}
    }
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private GradientDrawable shape(int c){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(12));return d;}
    private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);return t;}
    private Button btn(String s){Button b=new Button(this);b.setText(s);b.setTextSize(13);b.setAllCaps(false);b.setTextColor(INK);b.setBackground(shape(PANEL));b.setMinHeight(dp(44));return b;}
    private void add(LinearLayout r,Button b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(44),1);p.setMargins(dp(3),dp(3),dp(3),dp(3));r.addView(b,p);}
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        appliedLanguage=ProPrefs.number(this,"language",0);appliedAccent=ProPrefs.number(this,"accent",0);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);root.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);setContentView(root);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(8),0,dp(4),0);
        backIcon=Ui.action(this,"back","Back",INK,()->{if(profileOpen){showProfile(false);return;}folder=null;collection=0;search.setText("");render();});top.addView(backIcon,new LinearLayout.LayoutParams(dp(44),dp(54)));backIcon.setVisibility(View.GONE);
        heading=text(tr("المجلدات","Folders"),23,INK);heading.setTypeface(null,Typeface.BOLD);heading.setPadding(dp(8),0,0,0);top.addView(heading,new LinearLayout.LayoutParams(0,dp(54),1));heading.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(Ui.action(this,"folder","Media filter",INK,()->filterMenu()),new LinearLayout.LayoutParams(dp(44),dp(48)));
        top.addView(Ui.action(this,"search","Search",INK,()->toggleSearch()),new LinearLayout.LayoutParams(dp(44),dp(48)));
        top.addView(Ui.action(this,"view","View and sort",INK,()->viewDialog()),new LinearLayout.LayoutParams(dp(44),dp(48)));root.addView(top);
        shortcuts=new LinearLayout(this);shortcuts.setOrientation(LinearLayout.HORIZONTAL);HorizontalScrollView hs=new HorizontalScrollView(this);hs.setHorizontalScrollBarEnabled(false);hs.addView(shortcuts);root.addView(hs,new LinearLayout.LayoutParams(-1,dp(78)));
        quick("music",tr("الموسيقى","Music"),()->setTab(AUDIO));quick("list",tr("القوائم","Playlists"),this::playlistsMenu);quick("star",tr("المفضلة","Favorites"),()->selectCollection(1));quick("lock",tr("الخصوصية","Privacy"),()->authenticate(()->{privateUnlocked=true;selectCollection(0);}));
        quick("network",tr("الشبكة","Network"),this::networkMenu);quick("file",tr("فتح ملف","Open file"),this::pick);quick("clock",tr("السجل","History"),()->selectCollection(2));quick("refresh",tr("تحديث","Refresh"),this::refresh);quick("folder",tr("إضافة مجلد","Add folder"),this::chooseTree);
        search=new EditText(this);search.setSingleLine(true);search.setTextColor(INK);search.setHintTextColor(MUTED);search.setHint(tr("بحث عن ملف أو مجلد","Search files and folders"));search.setTextSize(16);search.setPadding(dp(14),0,dp(14),0);search.setBackground(Ui.shape(this,PANEL,9));LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(46));sp.setMargins(dp(14),0,dp(14),dp(6));root.addView(search,sp);search.setVisibility(View.GONE);
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){keyword=s.toString().trim().toLowerCase(Locale.ROOT);render();}public void afterTextChanged(Editable e){}});
        crumb=heading;modeButton=btn("View");modeButton.setVisibility(View.GONE);
        permission=btn(tr("السماح بالوصول إلى الوسائط","Allow media access"));root.addView(permission,new LinearLayout.LayoutParams(-1,dp(48)));permission.setOnClickListener(v->requestAccess(false));
        summary=text("",12,MUTED);summary.setPadding(dp(16),dp(2),dp(16),dp(4));root.addView(summary);summary.setVisibility(View.GONE);
        contentFrame=new FrameLayout(this);root.addView(contentFrame,new LinearLayout.LayoutParams(-1,0,1));
        list=new GridView(this);list.setNumColumns(1);list.setVerticalSpacing(dp(2));list.setHorizontalSpacing(dp(8));list.setPadding(dp(10),0,dp(10),dp(64));list.setClipToPadding(false);list.setId(0x12340001);adapter=new RowAdapter();list.setAdapter(adapter);contentFrame.addView(list,new FrameLayout.LayoutParams(-1,-1));
        list.setOnItemClickListener((parent,v,pos,id)->{if(pos>=rows.size())return;Row r=rows.get(pos);if(r.media==null){folder=r.key;search.setText("");render();list.setSelection(0);}else launch(r.media);});
        list.setOnItemLongClickListener((parent,v,pos,id)->{if(pos>=rows.size())return false;Row r=rows.get(pos);if(r.media==null)folderMenu(r);else details(r.media);return true;});
        ImageView resume=Ui.action(this,"play",tr("استكمال المشاهدة","Resume playback"),Color.WHITE,()->resumeRecent());resume.setBackground(Ui.shape(this,Ui.BLUE,32));FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(dp(56),dp(56),Gravity.BOTTOM|Gravity.RIGHT);fp.setMargins(0,0,dp(16),dp(14));contentFrame.addView(resume,fp);
        profile=new LinearLayout(this);profile.setOrientation(LinearLayout.VERTICAL);profile.setBackgroundColor(BG);profile.setVisibility(View.GONE);contentFrame.addView(profile,new FrameLayout.LayoutParams(-1,-1));buildProfile();
        nav=new LinearLayout(this);nav.setBackgroundColor(0xff131313);root.addView(nav,new LinearLayout.LayoutParams(-1,dp(64)));updateNav(0);
        sort=getPreferences(MODE_PRIVATE).getInt("sort",0);ascending=getPreferences(MODE_PRIVATE).getBoolean("ascending",sort==0);grid=getPreferences(MODE_PRIVATE).getBoolean("grid",false);viewMode=getPreferences(MODE_PRIVATE).getInt("view_mode",0);folders=viewMode!=1;list.setNumColumns(grid?2:1);tab=ALL;render();
        if(!canRead()&&!getPreferences(MODE_PRIVATE).getBoolean("asked",false))requestAccess(true);
    }
    private String tr(String ar,String en){return ProPrefs.label(this,ar,en);}
    private void quick(String icon,String label,Runnable r){shortcuts.addView(Ui.shortcut(this,icon,label,Ui.BLUE,r),new LinearLayout.LayoutParams(dp(76),-1));}
    private void updateNav(int selected){nav.removeAllViews();String[] icons={"folder","music","network","person"};String[] titles={tr("محلي","Local"),tr("الموسيقى","Music"),tr("الشبكة","Network"),tr("أنا","Me")};for(int i=0;i<4;i++){final int n=i;nav.addView(Ui.shortcut(this,icons[i],titles[i],i==selected?Ui.BLUE:Ui.MUTED,()->{if(n==3){showProfile(true);}else if(n==2){networkMenu();}else{showProfile(false);setTab(n==1?AUDIO:ALL);}}),new LinearLayout.LayoutParams(0,-1,1));}}
    private void setTab(int t){tab=t;folder=null;collection=0;search.setText("");render();updateNav(t==AUDIO?1:0);}
    private void selectCollection(int c){showProfile(false);collection=c;folder=null;tab=ALL;search.setText("");render();}
    private void filterMenu(){new AlertDialog.Builder(this).setTitle(tr("الوسائط","Media filter")).setSingleChoiceItems(new String[]{tr("الفيديو","Videos"),tr("الموسيقى","Music"),tr("الكل","All media")},tab,(d,n)->{setTab(n);d.dismiss();}).setNegativeButton(tr("إلغاء","Cancel"),null).show();}
    private void toggleSearch(){if(profileOpen)showProfile(false);boolean visible=search.getVisibility()==View.VISIBLE;search.setVisibility(visible?View.GONE:View.VISIBLE);if(visible){search.setText("");((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(),0);}else{search.requestFocus();((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(search,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);}}
    private void networkMenu(){new AlertDialog.Builder(this).setTitle(tr("الشبكة","Network")).setItems(new String[]{tr("فتح رابط","Open URL"),tr("الشبكة المحلية / DLNA","Local network / DLNA")},(d,w)->{if(w==0)openNetwork();else startActivity(new Intent(this,NetworkBrowserActivity.class));}).show();}
    private void showProfile(boolean open){profileOpen=open;profile.setVisibility(open?View.VISIBLE:View.GONE);list.setVisibility(open?View.GONE:View.VISIBLE);shortcuts.setVisibility(open?View.GONE:View.VISIBLE);if(open){heading.setText(tr("أنا","Me"));backIcon.setVisibility(View.GONE);search.setVisibility(View.GONE);updateNav(3);}else{render();updateNav(tab==AUDIO?1:0);}}
    private void buildProfile(){ScrollView s=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(16),dp(10),dp(16),dp(20));s.addView(body);profile.addView(s,new LinearLayout.LayoutParams(-1,-1));LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setBackground(Ui.shape(this,PANEL,10));body.addView(card);
      String[] titles={tr("المجلدات المحمية","Private folders"),tr("قوائم الفيديو","Video playlists"),tr("المفضلة","Favorites"),tr("الشبكة المحلية","Local network"),tr("مشغل الموسيقى","Music player"),tr("إدارة الوسائط","Media manager")};String[] icons={"lock","list","star","network","music","folder"};
      Runnable[] actions={()->authenticate(()->{privateUnlocked=true;selectCollection(0);}),this::playlistsMenu,()->selectCollection(1),this::networkMenu,()->{showProfile(false);setTab(AUDIO);},()->{showProfile(false);collectionMenu();}};
      for(int row=0;row<2;row++){LinearLayout rr=new LinearLayout(this);for(int col=0;col<3;col++){int i=row*3+col;rr.addView(Ui.shortcut(this,icons[i],titles[i],Ui.BLUE,actions[i]),new LinearLayout.LayoutParams(0,dp(100),1));}card.addView(rr);}
      profileRow(body,"settings",tr("الإعدادات","Settings"),()->startActivity(new Intent(this,SettingsActivity.class)));
      profileRow(body,"view",tr("المظهر","App appearance"),()->startActivity(new Intent(this,SettingsActivity.class).putExtra("page",0)));
      profileRow(body,"info",tr("حول التطبيق","About & developer"),()->startActivity(new Intent(this,SettingsActivity.class).putExtra("page",7)));
      profileRow(body,"clock",tr("سجل المشاهدة","Playback history"),()->selectCollection(2));
    }
    private void profileRow(LinearLayout b,String icon,String label,Runnable action){LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(dp(12),0,dp(12),0);r.setBackground(Ui.shape(this,PANEL,8));ImageView i=new ImageView(this);i.setImageDrawable(new Ui.Glyph(icon,Ui.MUTED));r.addView(i,new LinearLayout.LayoutParams(dp(25),dp(25)));TextView t=text(label,17,INK);t.setPadding(dp(16),0,0,0);r.addView(t,new LinearLayout.LayoutParams(0,-2,1));r.addView(text("›",22,MUTED));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(58));p.topMargin=dp(8);b.addView(r,p);r.setOnClickListener(v->action.run());}
    private void resumeRecent(){ArrayList<String> history=ProPrefs.list(this,"history");HashSet<String> locked=new HashSet<>(ProPrefs.list(this,"protected_folders"));for(String u:history)for(Media m:items)if(m.uri.toString().equals(u)&&(!locked.contains(m.key())||privateUnlocked)){launch(m);return;}for(Row r:rows)if(r.media!=null){launch(r.media);return;}Toast.makeText(this,tr("لا توجد وسائط حديثة","No recent media"),Toast.LENGTH_SHORT).show();}
    private void viewDialog(){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(20),dp(8),dp(20),dp(8));int[] state={viewMode,grid?1:0,sort,ascending?1:0};addChoices(box,tr("طريقة العرض","View mode"),new String[]{tr("كل المجلدات","All folders"),tr("الملفات","Files"),tr("المجلدات","Folders")},state,0);addChoices(box,tr("التخطيط","Layout"),new String[]{tr("قائمة","List"),tr("شبكة","Grid")},state,1);addChoices(box,tr("الترتيب","Sort"),new String[]{tr("الاسم","Title"),tr("التاريخ","Date"),tr("الحجم","Size"),tr("المدة","Length"),tr("المسار","Path"),tr("النوع","Type")},state,2);addChoices(box,tr("الاتجاه","Order"),new String[]{tr("تنازلي","Descending"),tr("تصاعدي","Ascending")},state,3);ScrollView sc=new ScrollView(this);sc.addView(box);new AlertDialog.Builder(this).setTitle(tr("عرض وترتيب","View & sort")).setView(sc).setNegativeButton(tr("إلغاء","Cancel"),null).setPositiveButton(tr("تم","Done"),(d,w)->{viewMode=state[0];grid=state[1]==1;sort=state[2];ascending=state[3]==1;folders=viewMode!=1;folder=null;getPreferences(MODE_PRIVATE).edit().putInt("view_mode",viewMode).putBoolean("grid",grid).putInt("sort",sort).putBoolean("ascending",ascending).apply();ProPrefs.get(this).edit().putBoolean("folders",folders).apply();list.setNumColumns(grid?2:1);list.setAdapter(adapter);render();}).show();}
    private void addChoices(LinearLayout box,String title,String[] opts,int[] state,int index){TextView t=text(title,17,INK);t.setPadding(0,dp(10),0,dp(4));box.addView(t);RadioGroup rg=new RadioGroup(this);rg.setOrientation(opts.length>3?RadioGroup.VERTICAL:RadioGroup.HORIZONTAL);for(int i=0;i<opts.length;i++){RadioButton b=new RadioButton(this);b.setText(opts[i]);b.setTextSize(13);b.setId(100+index*10+i);rg.addView(b);if(state[index]==i)b.setChecked(true);}rg.setOnCheckedChangeListener((g,id)->state[index]=id-100-index*10);box.addView(rg);}
    private boolean granted(String p){return checkSelfPermission(p)==PackageManager.PERMISSION_GRANTED;}
    private boolean canVideo(){return Build.VERSION.SDK_INT>=33?(granted(Manifest.permission.READ_MEDIA_VIDEO)||(Build.VERSION.SDK_INT>=34&&granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED))):granted(Manifest.permission.READ_EXTERNAL_STORAGE);}
    private boolean canAudio(){return Build.VERSION.SDK_INT>=33?granted(Manifest.permission.READ_MEDIA_AUDIO):granted(Manifest.permission.READ_EXTERNAL_STORAGE);}
    private boolean canRead(){return canVideo()||canAudio();}
    private boolean fullRead(){return Build.VERSION.SDK_INT>=33?granted(Manifest.permission.READ_MEDIA_VIDEO)&&canAudio():canRead();}
    private void requestAccess(boolean first){
        ArrayList<String> perms=new ArrayList<>();
        if(Build.VERSION.SDK_INT>=33){
            if(!granted(Manifest.permission.READ_MEDIA_VIDEO))perms.add(Manifest.permission.READ_MEDIA_VIDEO);
            if(!canAudio())perms.add(Manifest.permission.READ_MEDIA_AUDIO);
            if(Build.VERSION.SDK_INT>=34&&!granted(Manifest.permission.READ_MEDIA_VIDEO))perms.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED);
        }else if(!canRead())perms.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        if(perms.isEmpty()){refresh();return;}
        if(!first && getPreferences(MODE_PRIVATE).getBoolean("asked",false)){
            boolean rationale=false;for(String p:perms)rationale|=shouldShowRequestPermissionRationale(p);
            if(!rationale&&!canRead()){new AlertDialog.Builder(this).setMessage("اسمح بالوصول إلى الفيديو والصوت من إعدادات التطبيق لعرض المكتبة تلقائيًا. يمكنك أيضًا فتح الملفات يدويًا.").setPositiveButton("الإعدادات",(d,w)->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())))).setNegativeButton("إلغاء",null).show();return;}
        }
        getPreferences(MODE_PRIVATE).edit().putBoolean("asked",true).apply();requestPermissions(perms.toArray(new String[0]),31);
    }
    @Override public void onRequestPermissionsResult(int req,String[] p,int[] results){super.onRequestPermissionsResult(req,p,results);if(req==31)refresh();}
    @Override protected void onResume(){super.onResume();if(appliedLanguage!=ProPrefs.number(this,"language",0)||appliedAccent!=ProPrefs.number(this,"accent",0)){recreate();return;}folders=ProPrefs.flag(this,"folders",true);if(!observing&&ProPrefs.flag(this,"auto_scan",true)){try{getContentResolver().registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,true,observer);getContentResolver().registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,true,observer);observing=true;}catch(Exception e){getContentResolver().unregisterContentObserver(observer);}}refresh();}
    @Override protected void onStop(){if(privateUnlocked){privateUnlocked=false;folder=null;render();}super.onStop();}
    @Override protected void onPause(){handler.removeCallbacks(refreshTask);if(observing){getContentResolver().unregisterContentObserver(observer);observing=false;}super.onPause();}
    @Override protected void onDestroy(){dead=true;generation++;handler.removeCallbacksAndMessages(null);scanner.shutdownNow();thumbnails.shutdownNow();cache.evictAll();super.onDestroy();}
    void refresh(){
        if(dead)return;
        permission.setVisibility(fullRead()?View.GONE:View.VISIBLE);
        final ArrayList<String> trees=ProPrefs.list(this,"scan_trees");if(!canRead()&&trees.isEmpty()){generation++;items.clear();render();summary.setText("اسمح بالوصول لفحص مكتبة الهاتف، أو افتح ملفًا يدويًا.");return;}
        final int id=++generation;final boolean video=canVideo(),audio=canAudio();summary.setText("جارٍ فحص ملفات الهاتف…");
        scanner.execute(()->{ArrayList<Media> result=new ArrayList<>();boolean error=false;if(video)error|=!query(true,result);if(audio)error|=!query(false,result);for(String tree:trees)TreeScanner.scan(this,Uri.parse(tree),result);final boolean failed=error;
            runOnUiThread(()->{if(dead||id!=generation)return;items.clear();items.addAll(result);render();if(failed)summary.append(" • بعض الملفات غير متاحة");});});
    }
    private boolean query(boolean video,ArrayList<Media> out){
        Uri collection=video?MediaStore.Video.Media.EXTERNAL_CONTENT_URI:MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        ArrayList<String> fields=new ArrayList<>(Arrays.asList("_id","_display_name","mime_type","duration","_size","date_modified"));
        if(Build.VERSION.SDK_INT>=29){fields.add("relative_path");fields.add("volume_name");}else fields.add("_data");
        try(Cursor c=getContentResolver().query(collection,fields.toArray(new String[0]),null,null,null)){
            if(c==null)return false;
            while(c.moveToNext()){
                long id=c.getLong(0);String name=c.getString(1);if(name==null||name.isEmpty())continue;
                String path=c.getString(6),volume="external";
                if(Build.VERSION.SDK_INT>=29){volume=c.getString(7);if(volume==null)volume="external";}
                else if(path!=null){int slash=path.lastIndexOf('/');path=slash>=0?path.substring(0,slash+1):"";}
                if(path==null)path="";Uri uri=ContentUris.withAppendedId(collection,id);
                out.add(new Media(uri,name,path,volume,c.getString(2),c.getLong(3),c.getLong(4),c.getLong(5),video));
            }
            return true;
        }catch(Exception e){android.util.Log.w("XPlayerLibrary","Cannot query media",e);return false;}
    }
    private String folderName(String p){String q=p.replaceAll("/+$","");int i=q.lastIndexOf('/');return q.isEmpty()?"التخزين الرئيسي":q.substring(i+1);}
    private String duration(long ms){long s=Math.max(0,ms/1000);return s>=3600?String.format(Locale.ROOT,"%d:%02d:%02d",s/3600,(s/60)%60,s%60):String.format(Locale.ROOT,"%d:%02d",s/60,s%60);}
    private String size(long bytes){return android.text.format.Formatter.formatShortFileSize(this,Math.max(0,bytes));}
    private Comparator<Media> comparator(){return (a,b)->{int c=0;switch(sort){case 1:c=Long.compare(a.date,b.date);break;case 2:c=Long.compare(a.size,b.size);break;case 3:c=Long.compare(a.duration,b.duration);break;case 4:c=a.path.compareToIgnoreCase(b.path);break;case 5:c=TreeScanner.extension(a.name).compareToIgnoreCase(TreeScanner.extension(b.name));break;default:c=a.name.compareToIgnoreCase(b.name);break;}return c!=0?(ascending?c:-c):a.name.compareToIgnoreCase(b.name);};}
    private void render(){
        if(adapter==null)return;
        favoriteSet.clear();favoriteSet.addAll(ProPrefs.list(this,"favorites"));
        
        ArrayList<Media> selected=new ArrayList<>();ArrayList<String> groupUris=collection==3&&playlist!=null?ProPrefs.list(this,ProPrefs.playlistKey(playlist)):collection==1?ProPrefs.list(this,"favorites"):collection==2?ProPrefs.list(this,"history"):null;HashSet<String> protectedFolders=new HashSet<>(ProPrefs.list(this,"protected_folders"));String[] excluded=ProPrefs.get(this).getString("excluded","").toLowerCase(Locale.ROOT).split("\\r?\\n");for(Media m:items){if(!privateUnlocked&&protectedFolders.contains(m.key()))continue;if(groupUris!=null&&!groupUris.contains(m.uri.toString()))continue;boolean hidden=false;for(String x:excluded)if(!x.trim().isEmpty()&&m.path.toLowerCase(Locale.ROOT).contains(x.trim())){hidden=true;break;}if(hidden)continue;if(tab==VIDEO&&!m.video||tab==AUDIO&&m.video)continue;if(folder!=null&&!folder.equals(m.key()))continue;if(!keyword.isEmpty()&&!m.name.toLowerCase(Locale.ROOT).contains(keyword)&&!m.path.toLowerCase(Locale.ROOT).contains(keyword))continue;selected.add(m);}
        if(collection==2||collection==3)Collections.sort(selected,(a,b)->Integer.compare(groupUris.indexOf(a.uri.toString()),groupUris.indexOf(b.uri.toString())));else Collections.sort(selected,comparator());rows.clear();
        boolean group=collection==0&&folders&&folder==null&&keyword.isEmpty();
        if(group){
            LinkedHashMap<String,ArrayList<Media>> map=new LinkedHashMap<>();for(Media m:selected){ArrayList<Media> bucket=map.get(m.key());if(bucket==null){bucket=new ArrayList<>();map.put(m.key(),bucket);}bucket.add(m);}
            for(Map.Entry<String,ArrayList<Media>> e:map.entrySet()){ArrayList<Media> a=e.getValue();Media m=a.get(0);long total=0;for(Media file:a)total+=Math.max(0,file.size);rows.add(new Row(null,e.getKey(),folderName(m.path),a.size()+" "+tr("ملف","media files")+"  ·  "+size(total),a.size()));}
            if(sort==0)Collections.sort(rows,(a,b)->ascending?a.name.compareToIgnoreCase(b.name):b.name.compareToIgnoreCase(a.name));
        }else for(Media m:selected)rows.add(new Row(m,m.key(),m.name,size(m.size),1));
        String location=tr("المجلدات","Folders");if(folder!=null){int sep=folder.indexOf('|');location=folderName(sep<0?folder:folder.substring(sep+1));}else if(!group)location=tr("الملفات","Files");
        if(!profileOpen){heading.setText(collection==1?tr("المفضلة","Favorites"):collection==2?tr("السجل","History"):collection==3?playlist:tab==AUDIO&&folder==null?tr("الموسيقى","Music"):location);backIcon.setVisibility(folder!=null||collection!=0?View.VISIBLE:View.GONE);shortcuts.setVisibility(folder==null&&collection==0?View.VISIBLE:View.GONE);}
        summary.setVisibility(selected.isEmpty()&&!profileOpen?View.VISIBLE:View.GONE);summary.setText(selected.isEmpty()?"لا توجد وسائط ظاهرة • جرّب تحديث المكتبة":"الوسائط: "+selected.size()+(group?"  •  المجلدات: "+rows.size():""));
        adapter.notifyDataSetChanged();
    }
    private void launch(Media m){
        ArrayList<Uri> queue=new ArrayList<>();for(Row r:rows)if(r.media!=null)queue.add(r.media.uri);
        MainActivity.setLibraryQueue(queue,m.uri);
        startActivity(new Intent(this,MainActivity.class).setAction(Intent.ACTION_VIEW).setData(m.uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).putExtra("library",true));
    }
    private void details(Media m){new AlertDialog.Builder(this).setTitle(m.name).setItems(new String[]{"تشغيل",ProPrefs.favorite(this,m.uri)?"إزالة من المفضلة":"إضافة إلى المفضلة","التفاصيل والمشاركة","إضافة إلى قائمة تشغيل","إزالة من القائمة الحالية","إعادة تسمية","نقل إلى سلة المهملات"},(d,w)->{if(w==0)launch(m);else if(w==1){ProPrefs.toggle(this,m.uri);render();}else if(w==2)mediaDetails(m);else if(w==3)addToPlaylist(m);else if(w==4&&collection==3&&playlist!=null){ProPrefs.playlistRemove(this,playlist,m.uri);render();}else if(w==5)renameMedia(m);else if(w==6)trashMedia(m);}).setNegativeButton("إلغاء",null).show();}
    private Media pendingRename;private String pendingName;
    private void renameMedia(Media m){if(Build.VERSION.SDK_INT<30){Toast.makeText(this,"استخدم مدير الملفات لإعادة التسمية على هذا النظام",Toast.LENGTH_LONG).show();return;}EditText input=new EditText(this);input.setSingleLine(true);input.setText(m.name);new AlertDialog.Builder(this).setTitle("إعادة تسمية الملف").setView(input).setPositiveButton("حفظ",(d,w)->{String name=input.getText().toString().trim();if(name.isEmpty()||name.length()>240||name.contains("/")||name.contains("\\")||name.equals(".")||name.equals("..")){Toast.makeText(this,"اسم غير صالح",Toast.LENGTH_SHORT).show();return;}pendingRename=m;pendingName=name;try{startIntentSenderForResult(MediaStore.createWriteRequest(getContentResolver(),Collections.singletonList(m.uri)).getIntentSender(),93,null,0,0,0);}catch(Exception e){pendingRename=null;Toast.makeText(this,"إعادة التسمية غير متاحة لهذا الملف",Toast.LENGTH_LONG).show();}}).setNegativeButton("إلغاء",null).show();}
    private void trashMedia(Media m){if(Build.VERSION.SDK_INT<30){Toast.makeText(this,"استخدم مدير الملفات على هذا النظام",Toast.LENGTH_LONG).show();return;}new AlertDialog.Builder(this).setTitle("نقل إلى سلة المهملات؟").setMessage(m.name).setPositiveButton("متابعة",(d,w)->{try{startIntentSenderForResult(MediaStore.createTrashRequest(getContentResolver(),Collections.singletonList(m.uri),true).getIntentSender(),94,null,0,0,0);}catch(Exception e){Toast.makeText(this,"تعذر نقل الملف إلى السلة",Toast.LENGTH_LONG).show();}}).setNegativeButton("إلغاء",null).show();}
    private void collectionMenu(){new AlertDialog.Builder(this).setTitle("المكتبة").setItems(new String[]{"كل الوسائط","المفضلة","السجل","قوائم التشغيل","المجلدات المحمية","قفل المجلدات مجددًا","إضافة مجلد للفحص","إدارة مجلدات الفحص"},(d,n)->{if(n<3){collection=n;folder=null;if(n!=0)tab=ALL;render();}else if(n==3)playlistsMenu();else if(n==4)authenticate(()->{privateUnlocked=true;collection=0;folder=null;render();});else if(n==5){privateUnlocked=false;folder=null;render();}else if(n==6)chooseTree();else manageTrees();}).show();}
    private void chooseTree(){try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION),95);}catch(Exception e){Toast.makeText(this,"لا يوجد منتقي مجلدات",Toast.LENGTH_SHORT).show();}}
    private void manageTrees(){ArrayList<String> a=ProPrefs.list(this,"scan_trees");if(a.isEmpty()){chooseTree();return;}new AlertDialog.Builder(this).setTitle("المجلدات المضافة").setItems(a.toArray(new String[0]),(d,w)->new AlertDialog.Builder(this).setMessage("إزالة المجلد من الفحص فقط؟").setPositiveButton("إزالة",(x,y)->{String item=a.remove(w);ProPrefs.save(this,"scan_trees",a);try{getContentResolver().releasePersistableUriPermission(Uri.parse(item),Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}getSharedPreferences("tree_subtitles",MODE_PRIVATE).edit().clear().apply();refresh();}).setNegativeButton("إلغاء",null).show()).setNeutralButton("إضافة", (d,w)->chooseTree()).setNegativeButton("إغلاق",null).show();}
    private void playlistsMenu(){ArrayList<String> names=ProPrefs.playlists(this);ArrayList<String> options=new ArrayList<>(names);options.add("＋ قائمة جديدة");new AlertDialog.Builder(this).setTitle("قوائم التشغيل").setItems(options.toArray(new String[0]),(d,n)->{if(n==names.size())createPlaylist(null);else{String name=names.get(n);new AlertDialog.Builder(this).setTitle(name).setItems(new String[]{"عرض وتشغيل","حذف القائمة فقط"},(a,b)->{if(b==0){playlist=name;collection=3;tab=ALL;folder=null;search.setText("");render();}else new AlertDialog.Builder(this).setMessage("حذف القائمة دون حذف الملفات؟").setPositiveButton("حذف",(x,y)->{ProPrefs.deletePlaylist(this,name);collection=0;render();}).setNegativeButton("إلغاء",null).show();}).show();}}).show();}
    private void createPlaylist(Media media){EditText e=new EditText(this);e.setSingleLine(true);new AlertDialog.Builder(this).setTitle("اسم قائمة التشغيل").setView(e).setPositiveButton("إنشاء",(d,w)->{String name=e.getText().toString().trim();if(!ProPrefs.createPlaylist(this,name)){Toast.makeText(this,"اسم غير صالح أو موجود",Toast.LENGTH_SHORT).show();return;}if(media!=null)ProPrefs.playlistAdd(this,name,media.uri);}).setNegativeButton("إلغاء",null).show();}
    private void addToPlaylist(Media media){ArrayList<String> names=ProPrefs.playlists(this);ArrayList<String> options=new ArrayList<>(names);options.add("＋ قائمة جديدة");new AlertDialog.Builder(this).setTitle("إضافة إلى قائمة").setItems(options.toArray(new String[0]),(d,n)->{if(n==names.size())createPlaylist(media);else Toast.makeText(this,ProPrefs.playlistAdd(this,names.get(n),media.uri)?"تمت الإضافة":"موجود بالفعل",Toast.LENGTH_SHORT).show();}).show();}
    private void folderMenu(Row r){ArrayList<String> keys=ProPrefs.list(this,"protected_folders");boolean protectedNow=keys.contains(r.key);new AlertDialog.Builder(this).setTitle(r.name).setMessage("الحماية تخفي المجلد داخل مكتبة X Player فقط، ولا تشفر الملفات أو تمنع تطبيقات أخرى من رؤيتها.").setPositiveButton(protectedNow?"إزالة الحماية":"حماية المجلد",(d,w)->authenticate(()->{ArrayList<String> a=ProPrefs.list(this,"protected_folders");if(protectedNow)a.remove(r.key);else if(!a.contains(r.key))a.add(r.key);ProPrefs.save(this,"protected_folders",a);privateUnlocked=false;folder=null;render();})).setNegativeButton("إلغاء",null).show();}
    private void authenticate(Runnable action){android.app.KeyguardManager kg=(android.app.KeyguardManager)getSystemService(KEYGUARD_SERVICE);if(kg==null||!kg.isDeviceSecure()){Toast.makeText(this,"فعّل قفل الشاشة الآمن أولًا",Toast.LENGTH_LONG).show();return;}
      if(Build.VERSION.SDK_INT>=30){try{new android.hardware.biometrics.BiometricPrompt.Builder(this).setTitle("فتح المجلدات المحمية").setAllowedAuthenticators(android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG|android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL).build().authenticate(new android.os.CancellationSignal(),getMainExecutor(),new android.hardware.biometrics.BiometricPrompt.AuthenticationCallback(){@Override public void onAuthenticationSucceeded(android.hardware.biometrics.BiometricPrompt.AuthenticationResult result){action.run();}});return;}catch(Exception ignored){}}
      Intent i=kg.createConfirmDeviceCredentialIntent("فتح المجلدات المحمية","تحقق من هويتك");if(i!=null){afterUnlock=action;startActivityForResult(i,91);}}

    private void mediaDetails(Media m){new AlertDialog.Builder(this).setTitle(m.name).setMessage("المجلد: "+m.path+"\nالنوع: "+m.mime+"\nالحجم: "+size(m.size)+"\nالمدة: "+duration(m.duration)).setPositiveButton("تشغيل",(d,w)->launch(m)).setNeutralButton("مشاركة",(d,w)->{Intent i=new Intent(Intent.ACTION_SEND).setType(m.mime==null?(m.video?"video/*":"audio/*"):m.mime).putExtra(Intent.EXTRA_STREAM,m.uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);i.setClipData(ClipData.newRawUri(m.name,m.uri));startActivity(Intent.createChooser(i,"مشاركة"));}).setNegativeButton("إغلاق",null).show();}
    private void openNetwork(){EditText e=new EditText(this);e.setSingleLine(true);e.setHint("https://…");e.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);new AlertDialog.Builder(this).setTitle("فتح رابط وسائط").setMessage("HTTPS / RTSP / SMB / FTP / SFTP / NFS — trusted sources only.").setView(e).setPositiveButton("تشغيل",(d,w)->{Uri u=Uri.parse(e.getText().toString().trim());String s=u.getScheme();if(u.getHost()!=null&&("smb".equalsIgnoreCase(s)||"ftp".equalsIgnoreCase(s)||"sftp".equalsIgnoreCase(s)||"nfs".equalsIgnoreCase(s))){startActivity(new Intent(this,VlcActivity.class).setData(u));return;}if(u.getHost()==null||!("https".equalsIgnoreCase(s)||"rtsp".equalsIgnoreCase(s)||("http".equalsIgnoreCase(s)&&ProPrefs.flag(this,"http",false)))){Toast.makeText(this,"الرابط غير صالح أو HTTP غير مسموح",Toast.LENGTH_LONG).show();return;}startActivity(new Intent(this,MainActivity.class).setAction(Intent.ACTION_VIEW).setData(u).putExtra("network",true));}).setNegativeButton("إلغاء",null).show();}
    private void pick(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);try{startActivityForResult(i,22);}catch(Exception e){Toast.makeText(this,"لا يوجد مدير ملفات",Toast.LENGTH_SHORT).show();}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==95){if(result==RESULT_OK&&data!=null&&data.getData()!=null){Uri tree=data.getData();try{getContentResolver().takePersistableUriPermission(tree,data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION);ArrayList<String> a=ProPrefs.list(this,"scan_trees");if(!a.contains(tree.toString())){a.add(tree.toString());ProPrefs.save(this,"scan_trees",a);}refresh();}catch(Exception e){Toast.makeText(this,"تعذر حفظ صلاحية المجلد",Toast.LENGTH_LONG).show();}}return;}if(request==93){Media m=pendingRename;String name=pendingName;pendingRename=null;pendingName=null;if(result==RESULT_OK&&m!=null){try{ContentValues values=new ContentValues();values.put(MediaStore.MediaColumns.DISPLAY_NAME,name);int changed=getContentResolver().update(m.uri,values,null,null);Toast.makeText(this,changed>0?"تمت إعادة التسمية":"لم يتم تعديل الملف",Toast.LENGTH_SHORT).show();}catch(Exception e){Toast.makeText(this,"فشل تعديل الاسم",Toast.LENGTH_LONG).show();}refresh();}return;}if(request==94){if(result==RESULT_OK)refresh();return;}if(request==91){Runnable action=afterUnlock;afterUnlock=null;if(result==RESULT_OK&&action!=null)action.run();return;}if(request!=22||result!=RESULT_OK||data==null||data.getData()==null)return;Uri u=data.getData();try{getContentResolver().takePersistableUriPermission(u,data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}MainActivity.setLibraryQueue(new ArrayList<>(),u);startActivity(new Intent(this,MainActivity.class).setAction(Intent.ACTION_VIEW).setData(u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}
    @Override public void onBackPressed(){if(profileOpen){showProfile(false);return;}if(folder!=null){folder=null;render();}else if(!keyword.isEmpty())search.setText("");else if(collection!=0){collection=0;render();}else super.onBackPressed();}
    int mediaCountForTest(){return items.size();}
    boolean hasMediaForTest(String n){for(Media m:items)if(m.name.equals(n))return true;return false;}
    boolean hasFolderForTest(String n){for(Row r:rows)if(r.media==null&&r.name.equals(n))return true;return false;}
    void audioForTest(){tab=AUDIO;folder=null;render();}
    int rowsForTest(){return rows.size();}
    private final class RowAdapter extends BaseAdapter{
        public int getCount(){return rows.size();}public Object getItem(int pos){return rows.get(pos);}public long getItemId(int pos){return pos;}
        public View getView(int pos,View convert,ViewGroup parent){
            Row r=rows.get(pos);Holder h;
            if(convert==null||((Holder)convert.getTag()).grid!=grid){
                LinearLayout layout=new LinearLayout(LibraryActivity.this);layout.setOrientation(grid?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);layout.setGravity(Gravity.CENTER_VERTICAL);layout.setPadding(dp(6),dp(7),dp(6),dp(7));layout.setMinimumHeight(dp(grid?124:66));
                FrameLayout frame=new FrameLayout(LibraryActivity.this);frame.setBackground(Ui.shape(LibraryActivity.this,0xff24272b,5));layout.addView(frame,new LinearLayout.LayoutParams(grid?-1:dp(92),dp(grid?90:58)));
                ImageView icon=new ImageView(LibraryActivity.this);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);icon.setPadding(dp(8),dp(5),dp(8),dp(5));frame.addView(icon,new FrameLayout.LayoutParams(-1,-1));
                ImageView image=new ImageView(LibraryActivity.this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setVisibility(View.GONE);frame.addView(image,new FrameLayout.LayoutParams(-1,-1));
                TextView time=text("",11,Color.WHITE);time.setBackground(Ui.shape(LibraryActivity.this,0xb9000000,3));time.setPadding(dp(3),dp(1),dp(3),dp(1));FrameLayout.LayoutParams tp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.RIGHT);frame.addView(time,tp);LinearLayout labels=new LinearLayout(LibraryActivity.this);labels.setOrientation(LinearLayout.VERTICAL);labels.setPadding(dp(12),0,dp(4),0);layout.addView(labels,grid?new LinearLayout.LayoutParams(-1,-2):new LinearLayout.LayoutParams(0,-2,1));
                TextView name=text("",16,INK);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);labels.addView(name);TextView detail=text("",12,MUTED);detail.setMaxLines(2);labels.addView(detail);
                ImageView more=Ui.action(LibraryActivity.this,"more","File options",MUTED,()->{});if(!grid)layout.addView(more,new LinearLayout.LayoutParams(dp(40),dp(48)));h=new Holder();h.grid=grid;h.more=more;h.time=time;h.icon=icon;h.image=image;h.name=name;h.detail=detail;layout.setTag(h);convert=layout;
            }else h=(Holder)convert.getTag();
            h.name.setText(r.media!=null&&favoriteSet.contains(r.media.uri.toString())?"♥ "+r.name:r.name);h.detail.setText(r.detail);h.image.setImageDrawable(null);h.image.setVisibility(View.GONE);h.icon.setVisibility(View.VISIBLE);h.icon.setImageDrawable(new Ui.Glyph(r.media==null?"folder":r.media.video?"play":"music",r.media==null?0xff4b555e:Ui.MUTED));h.time.setVisibility(r.media!=null&&r.media.duration>0?View.VISIBLE:View.GONE);if(r.media!=null)h.time.setText(duration(r.media.duration));h.more.setOnClickListener(v->{if(r.media==null)folderMenu(r);else details(r.media);});h.image.setTag(null);
            if(ProPrefs.flag(LibraryActivity.this,"thumbnails",true)&&!ProPrefs.flag(LibraryActivity.this,"lite_mode",false)&&r.media!=null&&r.media.video&&Build.VERSION.SDK_INT>=29){Uri u=r.media.uri;String k=u.toString()+":"+r.media.date;h.image.setTag(k);Bitmap hit=cache.get(k);if(hit!=null){h.image.setImageBitmap(hit);h.image.setVisibility(View.VISIBLE);h.icon.setVisibility(View.GONE);}else if(!dead){final Holder holder=h;try{thumbnails.execute(()->{if(dead)return;try{Bitmap bitmap=getContentResolver().loadThumbnail(u,new Size(160,100),null);if(bitmap==null||dead)return;cache.put(k,bitmap);runOnUiThread(()->{if(!dead&&k.equals(holder.image.getTag())){holder.image.setImageBitmap(bitmap);holder.image.setVisibility(View.VISIBLE);holder.icon.setVisibility(View.GONE);}});}catch(Exception ignored){}});}catch(RejectedExecutionException ignored){}}}
            return convert;
        }
    }
    private static final class Holder{ImageView image,icon,more;TextView name,detail,time;boolean grid;}
}
