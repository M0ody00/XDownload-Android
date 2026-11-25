package com.xdownload.mobile;
import java.io.*;
import java.util.*;
import org.apache.commons.compress.archivers.sevenz.*;
import com.github.junrar.Archive;
import com.github.junrar.ArchiveOptions;
import com.github.junrar.rarfile.FileHeader;
/** Extracts only regular files to a fresh private directory. No archive links or absolute paths. */
final class ArchiveTools{
 static final long MAX=512L*1024*1024,RESERVE=16L*1024*1024;static final int MAX_ENTRIES=1000,MEMORY_KIB=65536;
 static int extract(File source,File root)throws Exception{
  byte[] sig=new byte[8];int n;try(InputStream in=new FileInputStream(source)){n=in.read(sig);}
  if(n>=6&&sig[0]==0x37&&sig[1]==0x7a&&(sig[2]&255)==0xbc&&(sig[3]&255)==0xaf&&sig[4]==0x27&&sig[5]==0x1c)return seven(source,root);
  if(n>=7&&sig[0]=='R'&&sig[1]=='a'&&sig[2]=='r'&&sig[3]=='!'&&sig[4]==0x1a&&sig[5]==7&&(sig[6]==0||sig[6]==1))return rar(source,root);
  if(n>=4&&sig[0]=='P'&&sig[1]=='K')return FileTools.extractZip(source,root);
  throw new IOException("Unsupported archive signature");
 }
 static void prepare(File root)throws IOException{if(root.exists()||!root.mkdirs())throw new IOException("Fresh extraction folder required");}
 static File target(File root,String name)throws IOException{
  if(name==null||name.isEmpty()||name.length()>2048||name.indexOf('\0')>=0||name.indexOf('\\')>=0||name.startsWith("/")||name.indexOf(':')>=0)throw new IOException("Unsafe archive path");
  String[] parts=name.split("/",-1);if(parts.length>32)throw new IOException("Archive directory depth limit");for(String part:parts)if(part.equals("..")||part.equals("."))throw new IOException("Unsafe archive path");
  File f=new File(root,name);if(!f.getCanonicalPath().startsWith(root.getCanonicalPath()+File.separator))throw new IOException("Unsafe archive path");return f;
 }
 static void directory(File f)throws IOException{if(!f.isDirectory()&&!f.mkdirs())throw new IOException("Cannot create archive folder");}
 static void checkSize(long size,long total)throws IOException{if(size<0||size>MAX-total)throw new IOException("Archive expansion limit");}
 static final class Bounded extends FilterOutputStream{
  final File root;final long limit;long count;
  Bounded(OutputStream o,File root,long limit){super(o);this.root=root;this.limit=limit;}
  void check(int n)throws IOException{if(Thread.currentThread().isInterrupted())throw new IOException("Cancelled");if(n>limit-count||root.getUsableSpace()<RESERVE)throw new IOException("Archive expansion/storage limit");count+=n;}
  @Override public void write(int b)throws IOException{check(1);out.write(b);}
  @Override public void write(byte[] b,int off,int len)throws IOException{check(len);out.write(b,off,len);}
 }
 static int seven(File source,File root)throws Exception{
  prepare(root);int entries=0;long total=0;byte[] buf=new byte[65536];
  try(SevenZFile z=SevenZFile.builder().setFile(source).setMaxMemoryLimitKiB(MEMORY_KIB).get()){
   SevenZArchiveEntry entry;while((entry=z.getNextEntry())!=null){if(++entries>MAX_ENTRIES)throw new IOException("Archive entry limit");if(entry.isAntiItem())throw new IOException("Unsupported archive entry");File f=target(root,entry.getName());if(entry.isDirectory()){directory(f);continue;}long size=entry.getSize();checkSize(size,total);directory(f.getParentFile());if(!f.createNewFile())throw new IOException("Duplicate archive path");
    try(Bounded out=new Bounded(new FileOutputStream(f),root,size)){int n;while((n=z.read(buf))!=-1){out.write(buf,0,n);}if(out.count!=size)throw new IOException("Incomplete archive entry");}total+=size;
   }
  }if(entries==0)throw new IOException("Empty archive");return entries;
 }
 static int rar(File source,File root)throws Exception{
  prepare(root);int entries=0;long total=0;
  try(Archive archive=new Archive(source,ArchiveOptions.builder().maxDictionarySize(64L*1024*1024).build())){
   if(archive.isEncrypted())throw new IOException("Encrypted RAR unsupported");
   for(FileHeader entry:archive.getFileHeaders()){if(++entries>MAX_ENTRIES)throw new IOException("Archive entry limit");if(entry.isEncrypted()||entry.isSplitBefore()||entry.isSplitAfter()||entry.getRedirection()!=null)throw new IOException("Encrypted, split or linked RAR entry unsupported");
    File f=target(root,entry.getFileName().replace('\\','/'));if(entry.isDirectory()){directory(f);continue;}long size=entry.getFullUnpackSize();checkSize(size,total);directory(f.getParentFile());if(!f.createNewFile())throw new IOException("Duplicate archive path");
    try(Bounded out=new Bounded(new FileOutputStream(f),root,size)){archive.extractFile(entry,out);if(out.count!=size)throw new IOException("Incomplete archive entry");}total+=size;
   }
  }if(entries==0)throw new IOException("Empty archive");return entries;
 }
}
