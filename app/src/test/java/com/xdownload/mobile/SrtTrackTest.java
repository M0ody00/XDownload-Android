package com.xdownload.mobile;
import org.junit.Test;import static org.junit.Assert.*;import java.io.*;import java.nio.charset.StandardCharsets;import java.util.*;
public class SrtTrackTest{
 @Test public void parsesTimedUtf8AndOverlaps()throws Exception{String raw="1\r\n00:00:01,000 --> 00:00:03,000\r\n<b>مرحبا</b>\r\n\r\n2\r\n00:00:02,000 --> 00:00:04,000\r\nWorld\r\n";List<SrtTrack.Cue> cues=SrtTrack.read(new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));assertEquals("",SrtTrack.at(cues,999));assertEquals("مرحبا",SrtTrack.at(cues,1000));assertEquals("مرحبا\nWorld",SrtTrack.at(cues,2000));assertEquals("World",SrtTrack.at(cues,3000));assertEquals("",SrtTrack.at(cues,4000));}
 @Test public void rejectsOversized()throws Exception{try{SrtTrack.read(new ByteArrayInputStream(new byte[SrtTrack.MAX_BYTES+1]));fail("Must reject oversized input");}catch(IOException expected){}}
}