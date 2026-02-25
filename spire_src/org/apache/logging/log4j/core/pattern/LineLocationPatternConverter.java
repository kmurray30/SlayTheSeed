package org.apache.logging.log4j.core.pattern;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.impl.LocationAware;

@Plugin(name = "LineLocationPatternConverter", category = "Converter")
@ConverterKeys({"L", "line"})
public final class LineLocationPatternConverter extends LogEventPatternConverter implements LocationAware {
   private static final LineLocationPatternConverter INSTANCE = new LineLocationPatternConverter();

   private LineLocationPatternConverter() {
      super("Line", "line");
   }

   public static LineLocationPatternConverter newInstance(final String[] options) {
      return INSTANCE;
   }

   @Override
   public void format(final LogEvent event, final StringBuilder output) {
      StackTraceElement element = event.getSource();
      if (element != null) {
         output.append(element.getLineNumber());
      }
   }

   @Override
   public boolean requiresLocation() {
      return true;
   }
}
