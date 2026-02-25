package org.apache.logging.log4j.core.pattern;

import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.util.PerformanceSensitive;

@Plugin(name = "FileDatePatternConverter", category = "FileConverter")
@ConverterKeys({"d", "date"})
@PerformanceSensitive("allocation")
public final class FileDatePatternConverter {
   private FileDatePatternConverter() {
   }

   public static PatternConverter newInstance(final String[] options) {
      return options != null && options.length != 0 ? DatePatternConverter.newInstance(options) : DatePatternConverter.newInstance(new String[]{"yyyy-MM-dd"});
   }
}
