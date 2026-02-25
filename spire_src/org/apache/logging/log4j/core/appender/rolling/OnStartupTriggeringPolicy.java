package org.apache.logging.log4j.core.appender.rolling;

import java.lang.reflect.Method;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.util.Loader;
import org.apache.logging.log4j.status.StatusLogger;

@Plugin(name = "OnStartupTriggeringPolicy", category = "Core", printObject = true)
public class OnStartupTriggeringPolicy extends AbstractTriggeringPolicy {
   private static final long JVM_START_TIME = initStartTime();
   private final long minSize;

   private OnStartupTriggeringPolicy(final long minSize) {
      this.minSize = minSize;
   }

   private static long initStartTime() {
      try {
         Class<?> factoryClass = Loader.loadSystemClass("java.lang.management.ManagementFactory");
         Method getRuntimeMXBean = factoryClass.getMethod("getRuntimeMXBean");
         Object runtimeMXBean = getRuntimeMXBean.invoke(null);
         Class<?> runtimeMXBeanClass = Loader.loadSystemClass("java.lang.management.RuntimeMXBean");
         Method getStartTime = runtimeMXBeanClass.getMethod("getStartTime");
         Long result = (Long)getStartTime.invoke(runtimeMXBean);
         return result;
      } catch (Throwable var6) {
         StatusLogger.getLogger()
            .error("Unable to call ManagementFactory.getRuntimeMXBean().getStartTime(), using system time for OnStartupTriggeringPolicy", var6);
         return System.currentTimeMillis();
      }
   }

   @Override
   public void initialize(final RollingFileManager manager) {
      if (manager.getFileTime() < JVM_START_TIME && manager.getFileSize() >= this.minSize) {
         StatusLogger.getLogger().debug("Initiating rollover at startup");
         if (this.minSize == 0L) {
            manager.setRenameEmptyFiles(true);
         }

         manager.skipFooter(true);
         manager.rollover();
         manager.skipFooter(false);
      }
   }

   @Override
   public boolean isTriggeringEvent(final LogEvent event) {
      return false;
   }

   @Override
   public String toString() {
      return "OnStartupTriggeringPolicy";
   }

   @PluginFactory
   public static OnStartupTriggeringPolicy createPolicy(@PluginAttribute(value = "minSize", defaultLong = 1L) final long minSize) {
      return new OnStartupTriggeringPolicy(minSize);
   }
}
