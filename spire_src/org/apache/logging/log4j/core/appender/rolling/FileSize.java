package org.apache.logging.log4j.core.appender.rolling;

import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.status.StatusLogger;

public final class FileSize {
   private static final Logger LOGGER = StatusLogger.getLogger();
   private static final long KB = 1024L;
   private static final long MB = 1048576L;
   private static final long GB = 1073741824L;
   private static final Pattern VALUE_PATTERN = Pattern.compile("([0-9]+([\\.,][0-9]+)?)\\s*(|K|M|G)B?", 2);

   private FileSize() {
   }

   public static long parse(final String string, final long defaultValue) {
      Matcher matcher = VALUE_PATTERN.matcher(string);
      if (matcher.matches()) {
         try {
            double value = NumberFormat.getNumberInstance(Locale.ROOT).parse(matcher.group(1)).doubleValue();
            String units = matcher.group(3);
            if (units.isEmpty()) {
               return (long)value;
            } else if (units.equalsIgnoreCase("K")) {
               return (long)(value * 1024.0);
            } else if (units.equalsIgnoreCase("M")) {
               return (long)(value * 1048576.0);
            } else if (units.equalsIgnoreCase("G")) {
               return (long)(value * 1.0737418E9F);
            } else {
               LOGGER.error("FileSize units not recognized: " + string);
               return defaultValue;
            }
         } catch (ParseException var7) {
            LOGGER.error("FileSize unable to parse numeric part: " + string, (Throwable)var7);
            return defaultValue;
         }
      } else {
         LOGGER.error("FileSize unable to parse bytes: " + string);
         return defaultValue;
      }
   }
}
