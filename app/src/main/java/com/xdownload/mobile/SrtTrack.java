package com.xdownload.mobile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
/** Bounded plain-text UTF-8 SRT parser. Does not execute markup or fetch URLs. */
final class SrtTrack{
 static final int MAX_BYTES=512*1024,MAX_CUES=5000;
 static final Pattern TIME=Pattern.compile("(\\d{1,3}):(\\d{2}):(\\d{2})[,.](\\d{3})");
 static final class Cue{final long start,end;final String text;Cue(long a,long b,String t){start=a;end=b;text=t;}}
 static long millis(String time)throws IOException{Matcher m=TIME.matcher(time.trim());if(!m.matches())throw new IOException("Invalid SRT timestamp");int min=Integer.parseInt(m.group(2)),sec=Integer.parseInt(m.group(3));if(min>59||sec>59)throw new IOException("Invalid SRT timestamp");return ((Long.parseLong(m.group(1))*60+min)*60+sec)*1000+Integer.parseInt(m.group(4));}
 static List<Cue> read(InputStream in)throws IOException{if(in==null)throw new IOException("Missing SRT");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1){if(out.size()+n>MAX_BYTES)throw new IOException("SRT size limit");out.write(buf,0,n);}String raw=new String(out.toByteArray(),StandardCharsets.UTF_8).replace("\r\n","\n").replace('\r','\n');if(raw.startsWith("\ufeff"))raw=raw.substring(1);ArrayList<Cue> cues=new ArrayList<>();for(String block:raw.split("\\n[ \\t]*\\n")){String[] lines=block.trim().split("\\n");int idx=lines.length>0&&lines[0].trim().matches("\\d+")?1:0;if(lines.length<=idx+1)continue;String[] range=lines[idx].split("-->",-1);if(range.length!=2)continue;long a,b;try{a=millis(range[0]);b=millis(range[1].trim().split("\\s+")[0]);}catch(Exception e){continue;}if(b<=a)continue;StringBuilder text=new StringBuilder();for(int k=idx+1;k<lines.length;k++){if(text.length()>0)text.append('\n');text.append(lines[k]);if(text.length()>1000){text.setLength(1000);break;}}String plain=text.toString().replaceAll("<[^>]{1,100}>","").trim();if(plain.isEmpty())continue;if(cues.size()>=MAX_CUES)throw new IOException("SRT cue limit");cues.add(new Cue(a,b,plain));}if(cues.isEmpty())throw new IOException("No valid SRT cues");cues.sort(Comparator.comparingLong(x->x.start));return Collections.unmodifiableList(cues);}
 static String at(List<Cue> cues,long ms){StringBuilder result=new StringBuilder();for(Cue cue:cues){if(cue.start>ms)break;if(ms<cue.end){if(result.length()>0)result.append('\n');result.append(cue.text);if(result.length()>1500){result.setLength(1500);break;}}}return result.toString();}
}
