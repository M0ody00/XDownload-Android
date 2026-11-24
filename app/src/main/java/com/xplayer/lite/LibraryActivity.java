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
    private static final int BG=0xff121923,PANEL=0xff202d3b,INK=0xfff5f7fa,MUTED=0xffa5b5c7,ACCENT=0xff44e7b2;
    static final int VIDEO=0,AUDIO=1,ALL=2;
    private final ExecutorService scanner=Executors.newSingleThreadExecutor();
    private final ExecutorService thumbnails=Executors.newFixedThreadPool(2);
    private final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(4*1024*1024){@Override protected int sizeOf(String k,Bitmap b){return b.getByteCount();}};
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ArrayList<Media> items=new ArrayList<>();
    private final ArrayList<Row> rows=new ArrayList<>();
    private ListView list;
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
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);root.setPadding(dp(10),dp(8),dp(10),dp(6));setContentView(root);
        TextView heading=text("X PLAYER PRO",25,ProPrefs.accent(this));heading.setTypeface(null,Typeface.BOLD);heading.setPadding(dp(6),dp(6),0,dp(4));root.addView(heading);
        LinearLayout top=new LinearLayout(this);
        Button refresh=btn("تحديث ↻"),open=btn("فتح ملف"),order=btn("الترتيب");
        add(top,refresh);add(top,open);add(top,order);root.addView(top);
        LinearLayout shortcuts=new LinearLayout(this);Button settings=btn(ProPrefs.label(this,"الإعدادات","Settings")), collections=btn(ProPrefs.label(this,"المكتبة","Library")), network=btn(ProPrefs.label(this,"رابط","Network"));
        add(shortcuts,settings);add(shortcuts,collections);add(shortcuts,network);root.addView(shortcuts);
        settings.setOnClickListener(v->startActivity(new Intent(this,SettingsActivity.class)));
        collections.setOnClickListener(v->collectionMenu());
        network.setOnClickListener(v->new AlertDialog.Builder(this).setItems(new String[]{"Open URL","Browse local network / DLNA"},(d,w)->{if(w==0)openNetwork();else startActivity(new Intent(this,NetworkBrowserActivity.class));}).show());
        LinearLayout filter=new LinearLayout(this);String[] names={ProPrefs.label(this,"الفيديو","Videos"),ProPrefs.label(this,"الموسيقى","Music"),ProPrefs.label(this,"الكل","All")};
        for(int i=0;i<3;i++){final int id=i;tabs[i]=btn(names[i]);add(filter,tabs[i]);tabs[i].setOnClickListener(v->{tab=id;folder=null;collection=0;render();});}root.addView(filter);
        search=new EditText(this);search.setSingleLine(true);search.setTextColor(INK);search.setHintTextColor(MUTED);search.setHint("بحث عن ملف أو مجلد");search.setTextSize(15);search.setPadding(dp(12),0,dp(12),0);search.setBackground(shape(PANEL));root.addView(search,new LinearLayout.LayoutParams(-1,dp(47)));
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){keyword=s.toString().trim().toLowerCase(Locale.ROOT);render();}public void afterTextChanged(Editable e){}});
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);crumb=text("المجلدات",16,INK);crumb.setPadding(dp(5),0,0,0);crumb.setOnClickListener(v->{folder=null;render();});bar.addView(crumb,new LinearLayout.LayoutParams(0,dp(45),1));modeButton=btn("عرض الملفات");bar.addView(modeButton,new LinearLayout.LayoutParams(dp(120),dp(42)));root.addView(bar);
        modeButton.setOnClickListener(v->{if(folder!=null){folder=null;folders=true;}else folders=!folders;render();});
        permission=btn("السماح بالوصول إلى الوسائط");root.addView(permission,new LinearLayout.LayoutParams(-1,dp(48)));permission.setOnClickListener(v->requestAccess(false));
        summary=text("جارٍ قراءة الجهاز…",13,MUTED);summary.setPadding(dp(6),dp(5),dp(6),dp(6));root.addView(summary);
        list=new ListView(this);list.setDivider(null);list.setCacheColorHint(Color.TRANSPARENT);list.setId(0x12340001);adapter=new RowAdapter();list.setAdapter(adapter);root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        list.setOnItemClickListener((p,v,pos,id)->{Row r=rows.get(pos);if(r.media==null){folder=r.key;search.setText("");render();list.setSelection(0);}else launch(r.media);});
        list.setOnItemLongClickListener((p,v,pos,id)->{Row r=rows.get(pos);if(r.media==null){folderMenu(r);return true;}details(r.media);return true;});
        refresh.setOnClickListener(v->refresh());open.setOnClickListener(v->pick());order.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("ترتيب الملفات").setSingleChoiceItems(new String[]{"الاسم أ – ي","الأحدث أولاً","الحجم الأكبر","المدة الأطول"},sort,(d,n)->{sort=n;getPreferences(MODE_PRIVATE).edit().putInt("sort",n).apply();d.dismiss();render();}).setNegativeButton("إلغاء",null).show());
        sort=getPreferences(MODE_PRIVATE).getInt("sort",0);folders=ProPrefs.flag(this,"folders",true);render();
        if(!canRead()&&!getPreferences(MODE_PRIVATE).getBoolean("asked",false))requestAccess(true);
    }
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
    @Override protected void onResume(){super.onResume();folders=ProPrefs.flag(this,"folders",true);if(!observing&&ProPrefs.flag(this,"auto_scan",true)){try{getContentResolver().registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,true,observer);getContentResolver().registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,true,observer);observing=true;}catch(Exception e){getContentResolver().unregisterContentObserver(observer);}}refresh();}
    @Override protected void onStop(){if(privateUnlocked){privateUnlocked=false;folder=null;render();}super.onStop();}
    @Override protected void onPause(){handler.removeCallbacks(refreshTask);if(observing){getContentResolver().unregisterContentObserver(observer);observing=false;}super.onPause();}
    @Override protected void onDestroy(){dead=true;generation++;handler.removeCallbacksAndMessages(null);scanner.shutdownNow();thumbnails.shutdownNow();cache.evictAll();super.onDestroy();}
    void refresh(){
        if(dead)return;
        permission.setVisibility(fullRead()?View.GONE:View.VISIBLE);
        if(!canRead()){generation++;items.clear();render();summary.setText("اسمح بالوصول لفحص مكتبة الهاتف، أو افتح ملفًا يدويًا.");return;}
        final int id=++generation;final boolean video=canVideo(),audio=canAudio();summary.setText("جارٍ فحص ملفات الهاتف…");
        scanner.execute(()->{ArrayList<Media> result=new ArrayList<>();boolean error=false;if(video)error|=!query(true,result);if(audio)error|=!query(false,result);final boolean failed=error;
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
    private Comparator<Media> comparator(){return (a,b)->{int c=0;switch(sort){case 1:c=Long.compare(b.date,a.date);break;case 2:c=Long.compare(b.size,a.size);break;case 3:c=Long.compare(b.duration,a.duration);break;default:break;}return c!=0?c:a.name.compareToIgnoreCase(b.name);};}
    private void render(){
        if(adapter==null)return;
        favoriteSet.clear();favoriteSet.addAll(ProPrefs.list(this,"favorites"));
        for(int i=0;i<tabs.length;i++){tabs[i].setBackground(shape(i==tab?ProPrefs.accent(this):PANEL));tabs[i].setTextColor(i==tab?BG:INK);}
        ArrayList<Media> selected=new ArrayList<>();ArrayList<String> groupUris=collection==3&&playlist!=null?ProPrefs.list(this,ProPrefs.playlistKey(playlist)):collection==1?ProPrefs.list(this,"favorites"):collection==2?ProPrefs.list(this,"history"):null;HashSet<String> protectedFolders=new HashSet<>(ProPrefs.list(this,"protected_folders"));String[] excluded=ProPrefs.get(this).getString("excluded","").toLowerCase(Locale.ROOT).split("\\r?\\n");for(Media m:items){if(!privateUnlocked&&protectedFolders.contains(m.key()))continue;if(groupUris!=null&&!groupUris.contains(m.uri.toString()))continue;boolean hidden=false;for(String x:excluded)if(!x.trim().isEmpty()&&m.path.toLowerCase(Locale.ROOT).contains(x.trim())){hidden=true;break;}if(hidden)continue;if(tab==VIDEO&&!m.video||tab==AUDIO&&m.video)continue;if(folder!=null&&!folder.equals(m.key()))continue;if(!keyword.isEmpty()&&!m.name.toLowerCase(Locale.ROOT).contains(keyword)&&!m.path.toLowerCase(Locale.ROOT).contains(keyword))continue;selected.add(m);}
        if(collection==2||collection==3)Collections.sort(selected,(a,b)->Integer.compare(groupUris.indexOf(a.uri.toString()),groupUris.indexOf(b.uri.toString())));else Collections.sort(selected,comparator());rows.clear();
        boolean group=collection==0&&folders&&folder==null&&keyword.isEmpty();
        if(group){
            LinkedHashMap<String,ArrayList<Media>> map=new LinkedHashMap<>();for(Media m:selected){ArrayList<Media> bucket=map.get(m.key());if(bucket==null){bucket=new ArrayList<>();map.put(m.key(),bucket);}bucket.add(m);}
            for(Map.Entry<String,ArrayList<Media>> e:map.entrySet()){ArrayList<Media> a=e.getValue();Media m=a.get(0);rows.add(new Row(null,e.getKey(),folderName(m.path),m.path+"  •  "+a.size()+" ملف",a.size()));}
            if(sort==0)Collections.sort(rows,(a,b)->a.name.compareToIgnoreCase(b.name));
        }else for(Media m:selected)rows.add(new Row(m,m.key(),m.name,duration(m.duration)+"  •  "+size(m.size)+"  •  "+folderName(m.path),1));
        String location="المجلدات";if(folder!=null){int sep=folder.indexOf('|');location="‹ "+folderName(sep<0?folder:folder.substring(sep+1));}else if(!group)location="الملفات";
        crumb.setText(collection==1?"المفضلة":collection==2?"السجل":collection==3?playlist:location);modeButton.setText(folder!=null?"رجوع للمجلدات":folders?"عرض الملفات":"عرض المجلدات");
        summary.setText(selected.isEmpty()?"لا توجد وسائط ظاهرة • جرّب تحديث المكتبة":"الوسائط: "+selected.size()+(group?"  •  المجلدات: "+rows.size():""));
        adapter.notifyDataSetChanged();
    }
    private void launch(Media m){
        ArrayList<Uri> queue=new ArrayList<>();for(Row r:rows)if(r.media!=null)queue.add(r.media.uri);
        MainActivity.setLibraryQueue(queue,m.uri);
        startActivity(new Intent(this,MainActivity.class).setAction(Intent.ACTION_VIEW).setData(m.uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).putExtra("library",true));
    }
    private void details(Media m){new AlertDialog.Builder(this).setTitle(m.name).setItems(new String[]{"تشغيل",ProPrefs.favorite(this,m.uri)?"إزالة من المفضلة":"إضافة إلى المفضلة","التفاصيل والمشاركة","إضافة إلى قائمة تشغيل","إزالة من القائمة الحالية"},(d,w)->{if(w==0)launch(m);else if(w==1){ProPrefs.toggle(this,m.uri);render();}else if(w==2)mediaDetails(m);else if(w==3)addToPlaylist(m);else if(collection==3&&playlist!=null){ProPrefs.playlistRemove(this,playlist,m.uri);render();}}).setNegativeButton("إلغاء",null).show();}
    private void collectionMenu(){new AlertDialog.Builder(this).setTitle("المكتبة").setItems(new String[]{"كل الوسائط","المفضلة","السجل","قوائم التشغيل","المجلدات المحمية","قفل المجلدات مجددًا"},(d,n)->{if(n<3){collection=n;folder=null;if(n!=0)tab=ALL;render();}else if(n==3)playlistsMenu();else if(n==4)authenticate(()->{privateUnlocked=true;collection=0;folder=null;render();});else{privateUnlocked=false;folder=null;render();}}).show();}
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
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==91){Runnable action=afterUnlock;afterUnlock=null;if(result==RESULT_OK&&action!=null)action.run();return;}if(request!=22||result!=RESULT_OK||data==null||data.getData()==null)return;Uri u=data.getData();try{getContentResolver().takePersistableUriPermission(u,data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}MainActivity.setLibraryQueue(new ArrayList<>(),u);startActivity(new Intent(this,MainActivity.class).setAction(Intent.ACTION_VIEW).setData(u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}
    @Override public void onBackPressed(){if(folder!=null){folder=null;render();}else if(!keyword.isEmpty())search.setText("");else if(collection!=0){collection=0;render();}else super.onBackPressed();}
    int mediaCountForTest(){return items.size();}
    boolean hasMediaForTest(String n){for(Media m:items)if(m.name.equals(n))return true;return false;}
    boolean hasFolderForTest(String n){for(Row r:rows)if(r.media==null&&r.name.equals(n))return true;return false;}
    void audioForTest(){tab=AUDIO;folder=null;render();}
    int rowsForTest(){return rows.size();}
    private final class RowAdapter extends BaseAdapter{
        public int getCount(){return rows.size();}public Object getItem(int pos){return rows.get(pos);}public long getItemId(int pos){return pos;}
        public View getView(int pos,View convert,ViewGroup parent){
            Row r=rows.get(pos);Holder h;
            if(convert==null){
                LinearLayout layout=new LinearLayout(LibraryActivity.this);layout.setOrientation(LinearLayout.HORIZONTAL);layout.setGravity(Gravity.CENTER_VERTICAL);layout.setPadding(dp(6),dp(7),dp(6),dp(7));
                FrameLayout frame=new FrameLayout(LibraryActivity.this);frame.setBackground(shape(PANEL));layout.addView(frame,new LinearLayout.LayoutParams(dp(78),dp(53)));
                TextView icon=text("",25,ACCENT);icon.setGravity(Gravity.CENTER);frame.addView(icon,new FrameLayout.LayoutParams(-1,-1));
                ImageView image=new ImageView(LibraryActivity.this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setVisibility(View.GONE);frame.addView(image,new FrameLayout.LayoutParams(-1,-1));
                LinearLayout labels=new LinearLayout(LibraryActivity.this);labels.setOrientation(LinearLayout.VERTICAL);labels.setPadding(dp(12),0,dp(4),0);layout.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
                TextView name=text("",15,INK);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);labels.addView(name);TextView detail=text("",12,MUTED);detail.setMaxLines(2);labels.addView(detail);
                h=new Holder();h.icon=icon;h.image=image;h.name=name;h.detail=detail;layout.setTag(h);convert=layout;
            }else h=(Holder)convert.getTag();
            h.name.setText(r.media!=null&&favoriteSet.contains(r.media.uri.toString())?"♥ "+r.name:r.name);h.detail.setText(r.detail);h.image.setImageDrawable(null);h.image.setVisibility(View.GONE);h.icon.setVisibility(View.VISIBLE);h.icon.setText(r.media==null?"▰":r.media.video?"▶":"♪");h.image.setTag(null);
            if(ProPrefs.flag(LibraryActivity.this,"thumbnails",true)&&r.media!=null&&r.media.video&&Build.VERSION.SDK_INT>=29){Uri u=r.media.uri;String k=u.toString()+":"+r.media.date;h.image.setTag(k);Bitmap hit=cache.get(k);if(hit!=null){h.image.setImageBitmap(hit);h.image.setVisibility(View.VISIBLE);h.icon.setVisibility(View.GONE);}else if(!dead){final Holder holder=h;try{thumbnails.execute(()->{if(dead)return;try{Bitmap bitmap=getContentResolver().loadThumbnail(u,new Size(160,100),null);if(bitmap==null||dead)return;cache.put(k,bitmap);runOnUiThread(()->{if(!dead&&k.equals(holder.image.getTag())){holder.image.setImageBitmap(bitmap);holder.image.setVisibility(View.VISIBLE);holder.icon.setVisibility(View.GONE);}});}catch(Exception ignored){}});}catch(RejectedExecutionException ignored){}}}
            return convert;
        }
    }
    private static final class Holder{ImageView image;TextView icon,name,detail;}
}
