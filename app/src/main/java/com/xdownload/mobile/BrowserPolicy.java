package com.xdownload.mobile;
import java.util.Locale;
final class BrowserPolicy {
 private static final String[] HOSTS={"doubleclick.net","googlesyndication.com","google-analytics.com","googleadservices.com","adnxs.com","scorecardresearch.com","taboola.com","outbrain.com","criteo.com","criteo.net"};
 static boolean block(String host,boolean main){if(main||host==null)return false;String h=host.toLowerCase(Locale.ROOT);while(h.endsWith("."))h=h.substring(0,h.length()-1);for(String item:HOSTS)if(h.equals(item)||h.endsWith("."+item))return true;return false;}
}