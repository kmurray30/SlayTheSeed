package org.apache.logging.log4j.core.appender.rolling;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

@Plugin(name = "NoOpTriggeringPolicy", category = "Core", printObject = true)
public class NoOpTriggeringPolicy extends AbstractTriggeringPolicy {
   public static final NoOpTriggeringPolicy INSTANCE = new NoOpTriggeringPolicy();

   @PluginFactory
   public static NoOpTriggeringPolicy createPolicy() {
      return INSTANCE;
   }

   @Override
   public void initialize(final RollingFileManager manager) {
   }

   @Override
   public boolean isTriggeringEvent(final LogEvent logEvent) {
      return false;
   }
}
