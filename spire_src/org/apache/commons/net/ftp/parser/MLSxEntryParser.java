package org.apache.commons.net.ftp.parser;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.TimeZone;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPFileEntryParserImpl;

public class MLSxEntryParser extends FTPFileEntryParserImpl {
   private static final MLSxEntryParser PARSER = new MLSxEntryParser();
   private static final HashMap<String, Integer> TYPE_TO_INT = new HashMap<>();
   private static int[] UNIX_GROUPS = new int[]{0, 1, 2};
   private static int[][] UNIX_PERMS = new int[][]{new int[0], {2}, {1}, {2, 1}, {0}, {0, 2}, {0, 1}, {0, 1, 2}};

   @Override
   public FTPFile parseFTPEntry(String entry) {
      if (entry.startsWith(" ")) {
         if (entry.length() > 1) {
            FTPFile file = new FTPFile();
            file.setRawListing(entry);
            file.setName(entry.substring(1));
            return file;
         } else {
            return null;
         }
      } else {
         String[] parts = entry.split(" ", 2);
         if (parts.length == 2 && parts[1].length() != 0) {
            String factList = parts[0];
            if (!factList.endsWith(";")) {
               return null;
            } else {
               FTPFile file = new FTPFile();
               file.setRawListing(entry);
               file.setName(parts[1]);
               String[] facts = factList.split(";");
               boolean hasUnixMode = parts[0].toLowerCase(Locale.ENGLISH).contains("unix.mode=");

               for (String fact : facts) {
                  String[] factparts = fact.split("=", -1);
                  if (factparts.length != 2) {
                     return null;
                  }

                  String factname = factparts[0].toLowerCase(Locale.ENGLISH);
                  String factvalue = factparts[1];
                  if (factvalue.length() != 0) {
                     String valueLowerCase = factvalue.toLowerCase(Locale.ENGLISH);
                     if ("size".equals(factname)) {
                        file.setSize(Long.parseLong(factvalue));
                     } else if ("sizd".equals(factname)) {
                        file.setSize(Long.parseLong(factvalue));
                     } else if ("modify".equals(factname)) {
                        Calendar parsed = parseGMTdateTime(factvalue);
                        if (parsed == null) {
                           return null;
                        }

                        file.setTimestamp(parsed);
                     } else if ("type".equals(factname)) {
                        Integer intType = TYPE_TO_INT.get(valueLowerCase);
                        if (intType == null) {
                           file.setType(3);
                        } else {
                           file.setType(intType);
                        }
                     } else if (!factname.startsWith("unix.")) {
                        if (!hasUnixMode && "perm".equals(factname)) {
                           this.doUnixPerms(file, valueLowerCase);
                        }
                     } else {
                        String unixfact = factname.substring("unix.".length()).toLowerCase(Locale.ENGLISH);
                        if ("group".equals(unixfact)) {
                           file.setGroup(factvalue);
                        } else if ("owner".equals(unixfact)) {
                           file.setUser(factvalue);
                        } else if ("mode".equals(unixfact)) {
                           int off = factvalue.length() - 3;

                           for (int i = 0; i < 3; i++) {
                              int ch = factvalue.charAt(off + i) - '0';
                              if (ch >= 0 && ch <= 7) {
                                 for (int p : UNIX_PERMS[ch]) {
                                    file.setPermission(UNIX_GROUPS[i], p, true);
                                 }
                              }
                           }
                        }
                     }
                  }
               }

               return file;
            }
         } else {
            return null;
         }
      }
   }

   public static Calendar parseGMTdateTime(String timestamp) {
      SimpleDateFormat sdf;
      boolean hasMillis;
      if (timestamp.contains(".")) {
         sdf = new SimpleDateFormat("yyyyMMddHHmmss.SSS");
         hasMillis = true;
      } else {
         sdf = new SimpleDateFormat("yyyyMMddHHmmss");
         hasMillis = false;
      }

      TimeZone GMT = TimeZone.getTimeZone("GMT");
      sdf.setTimeZone(GMT);
      GregorianCalendar gc = new GregorianCalendar(GMT);
      ParsePosition pos = new ParsePosition(0);
      sdf.setLenient(false);
      Date parsed = sdf.parse(timestamp, pos);
      if (pos.getIndex() != timestamp.length()) {
         return null;
      } else {
         gc.setTime(parsed);
         if (!hasMillis) {
            gc.clear(14);
         }

         return gc;
      }
   }

   private void doUnixPerms(FTPFile file, String valueLowerCase) {
      for (char c : valueLowerCase.toCharArray()) {
         switch (c) {
            case 'a':
               file.setPermission(0, 1, true);
            case 'b':
            case 'f':
            case 'g':
            case 'h':
            case 'i':
            case 'j':
            case 'k':
            case 'n':
            case 'o':
            case 'q':
            case 's':
            case 't':
            case 'u':
            case 'v':
            default:
               break;
            case 'c':
               file.setPermission(0, 1, true);
               break;
            case 'd':
               file.setPermission(0, 1, true);
               break;
            case 'e':
               file.setPermission(0, 0, true);
               break;
            case 'l':
               file.setPermission(0, 2, true);
               break;
            case 'm':
               file.setPermission(0, 1, true);
               break;
            case 'p':
               file.setPermission(0, 1, true);
               break;
            case 'r':
               file.setPermission(0, 0, true);
               break;
            case 'w':
               file.setPermission(0, 1, true);
         }
      }
   }

   public static FTPFile parseEntry(String entry) {
      return PARSER.parseFTPEntry(entry);
   }

   public static MLSxEntryParser getInstance() {
      return PARSER;
   }

   static {
      TYPE_TO_INT.put("file", 0);
      TYPE_TO_INT.put("cdir", 1);
      TYPE_TO_INT.put("pdir", 1);
      TYPE_TO_INT.put("dir", 1);
   }
}
