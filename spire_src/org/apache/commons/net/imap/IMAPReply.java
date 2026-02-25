package org.apache.commons.net.imap;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.net.MalformedServerReplyException;

public final class IMAPReply {
   public static final int OK = 0;
   public static final int NO = 1;
   public static final int BAD = 2;
   public static final int CONT = 3;
   public static final int PARTIAL = 3;
   private static final String IMAP_OK = "OK";
   private static final String IMAP_NO = "NO";
   private static final String IMAP_BAD = "BAD";
   private static final String IMAP_UNTAGGED_PREFIX = "* ";
   private static final String IMAP_CONTINUATION_PREFIX = "+";
   private static final String TAGGED_RESPONSE = "^\\w+ (\\S+).*";
   private static final Pattern TAGGED_PATTERN = Pattern.compile("^\\w+ (\\S+).*");
   private static final String UNTAGGED_RESPONSE = "^\\* (\\S+).*";
   private static final Pattern UNTAGGED_PATTERN = Pattern.compile("^\\* (\\S+).*");
   private static final Pattern LITERAL_PATTERN = Pattern.compile("\\{(\\d+)\\}$");

   private IMAPReply() {
   }

   public static boolean isUntagged(String line) {
      return line.startsWith("* ");
   }

   public static boolean isContinuation(String line) {
      return line.startsWith("+");
   }

   public static int getReplyCode(String line) throws IOException {
      return getReplyCode(line, TAGGED_PATTERN);
   }

   public static int literalCount(String line) {
      Matcher m = LITERAL_PATTERN.matcher(line);
      return m.find() ? Integer.parseInt(m.group(1)) : -1;
   }

   public static int getUntaggedReplyCode(String line) throws IOException {
      return getReplyCode(line, UNTAGGED_PATTERN);
   }

   private static int getReplyCode(String line, Pattern pattern) throws IOException {
      if (isContinuation(line)) {
         return 3;
      } else {
         Matcher m = pattern.matcher(line);
         if (m.matches()) {
            String code = m.group(1);
            if (code.equals("OK")) {
               return 0;
            }

            if (code.equals("BAD")) {
               return 2;
            }

            if (code.equals("NO")) {
               return 1;
            }
         }

         throw new MalformedServerReplyException("Received unexpected IMAP protocol response from server: '" + line + "'.");
      }
   }

   public static boolean isSuccess(int replyCode) {
      return replyCode == 0;
   }

   public static boolean isContinuation(int replyCode) {
      return replyCode == 3;
   }
}
