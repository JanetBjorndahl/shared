package org.werelate.util;

import nu.xom.ParsingException;
import nu.xom.Builder;
import nu.xom.Document;
//import com.ibm.icu.text.Normalizer;

import java.io.IOException;
import java.io.StringReader;
//import java.io.UnsupportedEncodingException;
//import java.security.MessageDigest;
//import java.security.NoSuchAlgorithmException;
//import java.util.*;
//import java.util.logging.Logger;
//import java.util.regex.Pattern;
//import java.util.regex.Matcher;
//import java.net.URLEncoder;

/**
 * Created by Dallan Quass
 * Date: Apr 23, 2008
 * Repackaged and enhanced by Janet Bjorndahl
 */
public class SharedUtils
{
   private static final String XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n";

   /* Create a Document object from a Builder and an XML string */
   public static Document parseText(Builder builder, String text, boolean addHeader) throws ParsingException, IOException {
      return builder.build(new StringReader((addHeader ? XML_HEADER  : "") + text));
   }

   /* Prepare a title string for writing to the database */
   public static String SqlTitle(String title) {
      if (title != null) {
         title = title.replace(" ","_").replace("\"","\\\"");
      }
      return title;
   }

}
