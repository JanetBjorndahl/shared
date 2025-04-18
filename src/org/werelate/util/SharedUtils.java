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

   /* 
    * Create a Document object from a Builder and an XML string
    * @param builder
    * @param text
    * @param addHeader  whether or not the XML header needs to be added to the text
    * @return Document
    */
   public static Document parseText(Builder builder, String text, boolean addHeader) throws ParsingException, IOException {
      return builder.build(new StringReader((addHeader ? XML_HEADER  : "") + text));
   }

   /* 
    * Prepare a title string for writing to the database 
    * @param title (string)
    * @return String
    */
   public static String SqlTitle(String title) {
      if (title != null) {
         title = title.replace(" ","_").replace("\"","\\\"");
      }
      return title;
   }

   public static String removeIndexNumber(String title) {
      if (title != null) {
         title = title.replaceAll("\\s*\\(\\d+\\)$", "").trim();
      }
      return title;
   }

   /**
    * Return whether the specified string is null or has no content
    * @param s
    * @return boolean
    */
   public static boolean isEmpty(String s) {
      return (s == null || s.trim().length() == 0);
   }

   /* 
    * Return the minimum integer, allowing for null inputs 
    * @param i1
    * @param i2
    * @return Integer
    */
   public static Integer minInteger(Integer i1, Integer i2) {
      if (i1 == null) {
         return i2;
      }
      if (i2 == null) {
         return i1;
      }
      return Math.min(i1, i2); 
   }

   /* 
    * Return the maximum integer, allowing for null inputs 
    * @param i1
    * @param i2
    * @return Integer
   */
   public static Integer maxInteger(Integer i1, Integer i2) {
      if (i1 == null) {
         return i2;
      }
      if (i2 == null) {
         return i1;
      }
      return Math.max(i1, i2); 
   }

   // The rules in this function match the check in the wiki (StructuredData.php isUnknownName)
   public static boolean isUnknownName(String name) {
      if (name==null) {
         return true;
      }
      String[] unknownNames = {"", "unknown", "unk", "n.n.", "n.n", "nn", "nn.", "n n", "fnu", "lnu", "father", "mother"};
      String checkName = name.replace("?","").replace("_","").replace("-","").trim().toLowerCase();
      for (String unknownName : unknownNames) {
         if (checkName.equals(unknownName)) {
            return true;
         }
      }
      return false;
   } 

}
