package com.xdownload.mobile;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import java.io.IOException;
/** Only Android-owned MediaStore items; no silent destructive fallback. */
final class TrashManager{
 static boolean supported(Uri uri){return Build.VERSION.SDK_INT>=30&&uri!=null&&"content".equals(uri.getScheme())&&"media".equals(uri.getAuthority())&&uri.getLastPathSegment()!=null&&uri.getLastPathSegment().matches("[0-9]+");}
 static Bundle args(){Bundle b=new Bundle();b.putInt(MediaStore.QUERY_ARG_MATCH_TRASHED,MediaStore.MATCH_INCLUDE);return b;}
 static boolean state(Context c,Uri uri)throws IOException{if(!supported(uri))throw new IOException("Trash unsupported");try(Cursor q=c.getContentResolver().query(uri,new String[]{MediaStore.MediaColumns.IS_TRASHED},args(),null)){if(q==null||!q.moveToFirst())throw new IOException("File missing");return q.getInt(0)==1;}}
 static void change(Context c,Uri uri,boolean trash)throws IOException{if(!supported(uri))throw new IOException("Trash unsupported");if(state(c,uri)==trash)return;ContentValues v=new ContentValues();v.put(MediaStore.MediaColumns.IS_TRASHED,trash?1:0);if(c.getContentResolver().update(uri,v,args())!=1||state(c,uri)!=trash)throw new IOException("Trash operation not confirmed");}
}
