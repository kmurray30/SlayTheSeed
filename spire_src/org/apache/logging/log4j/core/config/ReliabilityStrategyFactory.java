package org.apache.logging.log4j.core.config;

import org.apache.logging.log4j.core.util.Loader;
import org.apache.logging.log4j.status.StatusLogger;
import org.apache.logging.log4j.util.PropertiesUtil;

public final class ReliabilityStrategyFactory {
   private ReliabilityStrategyFactory() {
   }

   public static ReliabilityStrategy getReliabilityStrategy(final LoggerConfig loggerConfig) {
      String strategy = PropertiesUtil.getProperties().getStringProperty("log4j.ReliabilityStrategy", "AwaitCompletion");
      if ("AwaitCompletion".equals(strategy)) {
         return new AwaitCompletionReliabilityStrategy(loggerConfig);
      } else if ("AwaitUnconditionally".equals(strategy)) {
         return new AwaitUnconditionallyReliabilityStrategy(loggerConfig);
      } else if ("Locking".equals(strategy)) {
         return new LockingReliabilityStrategy(loggerConfig);
      } else {
         try {
            Class<? extends ReliabilityStrategy> cls = Loader.loadClass(strategy).asSubclass(ReliabilityStrategy.class);
            return cls.getConstructor(LoggerConfig.class).newInstance(loggerConfig);
         } catch (Exception var3) {
            StatusLogger.getLogger()
               .warn("Could not create ReliabilityStrategy for '{}', using default AwaitCompletionReliabilityStrategy: {}", strategy, var3);
            return new AwaitCompletionReliabilityStrategy(loggerConfig);
         }
      }
   }
}
