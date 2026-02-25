package org.apache.commons.net.ftp.parser;

import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.apache.commons.net.ftp.FTPClientConfig;

public class VMSVersioningFTPEntryParser extends VMSFTPEntryParser {
   private final Pattern _preparse_pattern_;
   private static final String PRE_PARSE_REGEX = "(.*?);([0-9]+)\\s*.*";

   public VMSVersioningFTPEntryParser() {
      this(null);
   }

   public VMSVersioningFTPEntryParser(FTPClientConfig config) {
      this.configure(config);

      try {
         this._preparse_pattern_ = Pattern.compile("(.*?);([0-9]+)\\s*.*");
      } catch (PatternSyntaxException var3) {
         throw new IllegalArgumentException("Unparseable regex supplied:  (.*?);([0-9]+)\\s*.*");
      }
   }

   @Override
   public List<String> preParse(List<String> original) {
      HashMap<String, Integer> existingEntries = new HashMap<>();
      ListIterator<String> iter = original.listIterator();

      while (iter.hasNext()) {
         String entry = iter.next().trim();
         MatchResult result = null;
         Matcher _preparse_matcher_ = this._preparse_pattern_.matcher(entry);
         if (_preparse_matcher_.matches()) {
            result = _preparse_matcher_.toMatchResult();
            String name = result.group(1);
            String version = result.group(2);
            Integer nv = Integer.valueOf(version);
            Integer existing = existingEntries.get(name);
            if (null != existing && nv < existing) {
               iter.remove();
            } else {
               existingEntries.put(name, nv);
            }
         }
      }

      while (iter.hasPrevious()) {
         String entry = iter.previous().trim();
         MatchResult result = null;
         Matcher _preparse_matcher_ = this._preparse_pattern_.matcher(entry);
         if (_preparse_matcher_.matches()) {
            result = _preparse_matcher_.toMatchResult();
            String name = result.group(1);
            String version = result.group(2);
            Integer nv = Integer.valueOf(version);
            Integer existing = existingEntries.get(name);
            if (null != existing && nv < existing) {
               iter.remove();
            }
         }
      }

      return original;
   }

   @Override
   protected boolean isVersioning() {
      return true;
   }
}
