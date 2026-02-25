package org.apache.logging.log4j.core.appender;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LoggingException;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.AppenderControl;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAliases;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginConfiguration;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.util.Booleans;

@Plugin(name = "Failover", category = "Core", elementType = "appender", printObject = true)
public final class FailoverAppender extends AbstractAppender {
   private static final int DEFAULT_INTERVAL_SECONDS = 60;
   private final String primaryRef;
   private final String[] failovers;
   private final Configuration config;
   private AppenderControl primary;
   private final List<AppenderControl> failoverAppenders = new ArrayList<>();
   private final long intervalNanos;
   private volatile long nextCheckNanos;

   private FailoverAppender(
      final String name,
      final Filter filter,
      final String primary,
      final String[] failovers,
      final int intervalMillis,
      final Configuration config,
      final boolean ignoreExceptions,
      final Property[] properties
   ) {
      super(name, filter, null, ignoreExceptions, properties);
      this.primaryRef = primary;
      this.failovers = failovers;
      this.config = config;
      this.intervalNanos = TimeUnit.MILLISECONDS.toNanos(intervalMillis);
   }

   @Override
   public void start() {
      Map<String, Appender> map = this.config.getAppenders();
      int errors = 0;
      Appender appender = map.get(this.primaryRef);
      if (appender != null) {
         this.primary = new AppenderControl(appender, null, null);
      } else {
         LOGGER.error("Unable to locate primary Appender " + this.primaryRef);
         errors++;
      }

      for (String name : this.failovers) {
         Appender foAppender = map.get(name);
         if (foAppender != null) {
            this.failoverAppenders.add(new AppenderControl(foAppender, null, null));
         } else {
            LOGGER.error("Failover appender " + name + " is not configured");
         }
      }

      if (this.failoverAppenders.isEmpty()) {
         LOGGER.error("No failover appenders are available");
         errors++;
      }

      if (errors == 0) {
         super.start();
      }
   }

   @Override
   public void append(final LogEvent event) {
      if (!this.isStarted()) {
         this.error("FailoverAppender " + this.getName() + " did not start successfully");
      } else {
         long localCheckNanos = this.nextCheckNanos;
         if (localCheckNanos != 0L && System.nanoTime() - localCheckNanos <= 0L) {
            this.failover(event, null);
         } else {
            this.callAppender(event);
         }
      }
   }

   private void callAppender(final LogEvent event) {
      try {
         this.primary.callAppender(event);
         this.nextCheckNanos = 0L;
      } catch (Exception var3) {
         this.nextCheckNanos = System.nanoTime() + this.intervalNanos;
         this.failover(event, var3);
      }
   }

   private void failover(final LogEvent event, final Exception ex) {
      RuntimeException re = ex != null ? (ex instanceof LoggingException ? (LoggingException)ex : new LoggingException(ex)) : null;
      boolean written = false;
      Exception failoverException = null;

      for (AppenderControl control : this.failoverAppenders) {
         try {
            control.callAppender(event);
            written = true;
            break;
         } catch (Exception var9) {
            if (failoverException == null) {
               failoverException = var9;
            }
         }
      }

      if (!written && !this.ignoreExceptions()) {
         if (re != null) {
            throw re;
         } else {
            throw new LoggingException("Unable to write to failover appenders", failoverException);
         }
      }
   }

   @Override
   public String toString() {
      StringBuilder sb = new StringBuilder(this.getName());
      sb.append(" primary=").append(this.primary).append(", failover={");
      boolean first = true;

      for (String str : this.failovers) {
         if (!first) {
            sb.append(", ");
         }

         sb.append(str);
         first = false;
      }

      sb.append('}');
      return sb.toString();
   }

   @PluginFactory
   public static FailoverAppender createAppender(
      @PluginAttribute("name") final String name,
      @PluginAttribute("primary") final String primary,
      @PluginElement("Failovers") final String[] failovers,
      @PluginAliases("retryInterval") @PluginAttribute("retryIntervalSeconds") final String retryIntervalSeconds,
      @PluginConfiguration final Configuration config,
      @PluginElement("Filter") final Filter filter,
      @PluginAttribute("ignoreExceptions") final String ignore
   ) {
      if (name == null) {
         LOGGER.error("A name for the Appender must be specified");
         return null;
      } else if (primary == null) {
         LOGGER.error("A primary Appender must be specified");
         return null;
      } else if (failovers != null && failovers.length != 0) {
         int seconds = parseInt(retryIntervalSeconds, 60);
         int retryIntervalMillis;
         if (seconds >= 0) {
            retryIntervalMillis = seconds * 1000;
         } else {
            LOGGER.warn("Interval " + retryIntervalSeconds + " is less than zero. Using default");
            retryIntervalMillis = 60000;
         }

         boolean ignoreExceptions = Booleans.parseBoolean(ignore, true);
         return new FailoverAppender(name, filter, primary, failovers, retryIntervalMillis, config, ignoreExceptions, null);
      } else {
         LOGGER.error("At least one failover Appender must be specified");
         return null;
      }
   }
}
