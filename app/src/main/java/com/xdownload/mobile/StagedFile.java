package com.xdownload.mobile;
import java.io.File;
/** Never publish a staged file after corruption or version mismatch. */
final class StagedFile{static boolean valid(File file,long length,String hash){if(length<0||hash==null||!hash.matches("[a-f0-9]{64}")||!file.isFile()||file.length()!=length)return false;try{return hash.equals(HttpEngine.sha256(file));}catch(Exception ex){return false;}}}