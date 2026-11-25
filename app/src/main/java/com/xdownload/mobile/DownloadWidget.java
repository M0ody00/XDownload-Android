package com.xdownload.mobile;
import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.widget.RemoteViews;
/** Privacy-preserving launcher shortcut with aggregate queue counts only. */
public final class DownloadWidget extends AppWidgetProvider{
 @Override public void onUpdate(Context c,AppWidgetManager manager,int[] ids){update(c,manager,ids);}
 static void refresh(Context c){try{AppWidgetManager manager=AppWidgetManager.getInstance(c);if(manager==null)return;int[] ids=manager.getAppWidgetIds(new ComponentName(c,DownloadWidget.class));if(ids.length>0)update(c,manager,ids);}catch(RuntimeException ignored){}}
 static void update(Context c,AppWidgetManager manager,int[] ids){if(ids.length==0)return;int complete=0,active=0;for(Store.Job j:Store.get(c).list()){if(j.state.equals("complete"))complete++;else if(j.state.equals("running")||j.state.equals("queued")||j.state.equals("waiting")||j.state.equals("saving"))active++;}boolean en=Store.settings(c).getBoolean("english",false);for(int id:ids){RemoteViews view=new RemoteViews(c.getPackageName(),R.layout.download_widget);view.setTextViewText(R.id.widget_status,en?active+" active / queued · "+complete+" complete":"نشطة أو منتظرة "+active+" · مكتملة "+complete);view.setOnClickPendingIntent(R.id.widget_root,PendingIntent.getActivity(c,811,new Intent(c,MainActivity.class).putExtra("downloads",true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT));manager.updateAppWidget(id,view);}}
}