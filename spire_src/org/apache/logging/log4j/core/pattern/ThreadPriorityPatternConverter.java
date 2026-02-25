package org.apache.logging.log4j.core.pattern;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.util.PerformanceSensitive;

@Plugin(name = "ThreadPriorityPatternConverter", category = "Converter")
@ConverterKeys({"tp", "threadPriority"})
@PerformanceSensitive("allocation")
public final class ThreadPriorityPatternConverter extends LogEventPatternConverter {
   private static final ThreadPriorityPatternConverter INSTANCE = new ThreadPriorityPatternConverter();

   private ThreadPriorityPatternConverter() {
      super("ThreadPriority", "threadPriority");
   }

   public static ThreadPriorityPatternConverter newInstance(final String[] options) {
      return INSTANCE;
   }

   @Override
   public void format(final LogEvent event, final StringBuilder toAppendTo) {
      toAppendTo.append(event.getThreadPriority());
   }
}
