package com.xdownload.mobile;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;
import java.security.MessageDigest;

final class HttpEngine {
 interface Observer { boolean proceed(); void progress(long bytes,long total,String etag,String modified)throws IOException; }
 static final class Info { final String name,mime,etag,modified;final long size;final boolean ranges;
  Info(String name,String mime,long size,String etag,String modified,boolean ranges){this.name=name;this.mime=mime;this.size=size;this.etag=etag;this.modified=modified;this.ranges=ranges;} }
 static URL validated(String value,boolean http)throws IOException{try{URL u=new URL(value.trim());String protocol=u.getProtocol();if(!("https".equalsIgnoreCase(protocol)||http&&"http".equalsIgnoreCase(protocol))||u.getHost().isEmpty()||u.getUserInfo()!=null)throw new IOException("Use an authorized HTTPS download link");return u;}catch(IllegalArgumentException e){throw new IOException("Invalid URL");}}
 static HttpURLConnection connect(String url,boolean allowHttp,String method,long offset,String validator)throws IOException{
  URL u=validated(url,allowHttp);for(int i=0;i<6;i++){HttpURLConnection c=(HttpURLConnection)u.openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(15000);c.setRequestMethod(method);c.setRequestProperty("Accept-Encoding","identity");c.setRequestProperty("User-Agent","XDownloadPro/1.0");if(offset>0){c.setRequestProperty("Range","bytes="+offset+"-");if(validator!=null&&!validator.isEmpty())c.setRequestProperty("If-Range",validator);}
  int code;try{code=c.getResponseCode();}catch(IOException e){c.disconnect();throw e;}if(code>=300&&code<400&&code!=304){String loc=c.getHeaderField("Location");c.disconnect();if(loc==null)throw new IOException("Redirect without target");URL next=validated(new URL(u,loc).toString(),allowHttp);if("https".equals(u.getProtocol())&&!"https".equals(next.getProtocol()))throw new IOException("Unsafe HTTPS downgrade");u=next;continue;}return c;}throw new IOException("Too many redirects");}
 static String safeName(String s){if(s==null)s="download";s=s.replaceAll("[\\\\/\\p{Cntrl}:*?\"<>|]","_").trim();while(s.startsWith("."))s=s.substring(1);if(s.isEmpty())s="download";if(s.length()>150){int i=s.lastIndexOf('.');String ext=i>0&&s.length()-i<15?s.substring(i):"";s=s.substring(0,Math.min(130,s.length()))+ext;}return s;}
 static String filename(HttpURLConnection c){String disp=c.getHeaderField("Content-Disposition");if(disp!=null){Matcher star=Pattern.compile("(?i)filename\\*=UTF-8''([^;]+)").matcher(disp);if(star.find())try{return safeName(URLDecoder.decode(star.group(1).replace("+","%2B"),"UTF-8"));}catch(Exception ignored){}Matcher m=Pattern.compile("(?i)filename\\s*=\\s*(?:\"([^\"]+)\"|([^;]+))").matcher(disp);if(m.find())return safeName(m.group(1)!=null?m.group(1):m.group(2));}String path=c.getURL().getPath();try{return safeName(URLDecoder.decode(path.substring(path.lastIndexOf('/')+1).replace("+","%2B"),"UTF-8"));}catch(Exception e){return "download";}}
 static Info info(HttpURLConnection c){String mime=c.getContentType();if(mime==null)mime="application/octet-stream";mime=mime.split(";")[0].trim();return new Info(filename(c),mime,c.getContentLengthLong(),str(c.getHeaderField("ETag")),str(c.getHeaderField("Last-Modified")),"bytes".equalsIgnoreCase(c.getHeaderField("Accept-Ranges")));}
 static String str(String s){return s==null?"":s;}
 static Info probe(String url,boolean http)throws IOException{HttpURLConnection c=connect(url,http,"HEAD",0,null);try{int status=c.getResponseCode();if(status>=200&&status<300)return info(c);if(status!=405&&status!=501&&status!=403)throw new IOException("HTTP "+status);}finally{c.disconnect();}c=connect(url,http,"GET",0,null);try{int status=c.getResponseCode();if(status<200||status>=300)throw new IOException("HTTP "+status);return info(c);}finally{c.disconnect();}}
 static String validator(String etag,String modified){return etag!=null&&!etag.isEmpty()&&!etag.startsWith("W/")?etag:modified==null?"":modified;}
 static boolean html(String mime){return "text/html".equalsIgnoreCase(mime)||"application/xhtml+xml".equalsIgnoreCase(mime);}
 static long download(String url,boolean http,File part,String etag,String modified,Observer o,long rate)throws IOException{
  long offset=part.exists()?part.length():0;String validator=validator(etag,modified);if(validator.isEmpty())offset=0;
  HttpURLConnection c=connect(url,http,"GET",offset,validator);
  try{int status=c.getResponseCode();if(status!=200&&status!=206)throw new IOException("HTTP "+status);Info i=info(c);if(html(i.mime))throw new IOException("This is a web page, not a downloadable file. Open its authorized download link.");
   long total=i.size;if(status==206){Matcher m=Pattern.compile("bytes (\\d+)-(\\d+)/(\\d+)").matcher(str(c.getHeaderField("Content-Range")));if(!m.matches()||Long.parseLong(m.group(1))!=offset||Long.parseLong(m.group(2))<offset)throw new IOException("Invalid resume range");total=Long.parseLong(m.group(3));if(offset>0&&!validator.equals(validator(i.etag,i.modified)))throw new IOException("Remote file changed; restart required");if(Long.parseLong(m.group(2))!=total-1)throw new IOException("Incomplete range from server");}
   else offset=0;
   if(!o.proceed())return -1;
   try(RandomAccessFile out=new RandomAccessFile(part,"rw");InputStream in=c.getInputStream()){out.setLength(offset);out.seek(offset);long done=offset;long start=System.nanoTime(),last=0;o.progress(done,total,i.etag,i.modified);byte[] buf=new byte[65536];int count;
    while(o.proceed()&&(count=in.read(buf))!=-1){out.write(buf,0,count);done+=count;if(total>=0&&done>total)throw new IOException("Response exceeded expected size");if(rate>0){long target=(long)((done-offset)*1000000000.0/rate);long delay=(target-(System.nanoTime()-start))/1000000;while(delay>0&&o.proceed()){try{Thread.sleep(Math.min(delay,200));}catch(InterruptedException e){Thread.currentThread().interrupt();return -1;}delay=(target-(System.nanoTime()-start))/1000000;}}
    if(System.nanoTime()-last>350000000){o.progress(done,total,i.etag,i.modified);last=System.nanoTime();}}
    out.getFD().sync();o.progress(done,total,i.etag,i.modified);if(!o.proceed())return -1;if(total>=0&&done!=total)throw new IOException("Incomplete response");return done;
   }
  }finally{c.disconnect();}
 }
 static String sha256(File f)throws Exception{MessageDigest md=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(f)){byte[] buf=new byte[65536];int n;while((n=in.read(buf))!=-1)md.update(buf,0,n);}StringBuilder s=new StringBuilder();for(byte b:md.digest())s.append(String.format(Locale.US,"%02x",b&255));return s.toString();}
 static String category(String name,String mime){String n=name.toLowerCase(Locale.ROOT);if(mime.startsWith("video/"))return "Videos";if(mime.startsWith("audio/"))return "Music";if(mime.startsWith("image/"))return "Images";if(n.matches(".*\\.(apk|xapk|apks|obb)$"))return "Apps";if(n.matches(".*\\.(zip|rar|7z|iso|tar|gz)$"))return "Archives";if(n.matches(".*\\.(pdf|docx?|xlsx?|pptx?|txt)$"))return "Documents";return "Other";}
}