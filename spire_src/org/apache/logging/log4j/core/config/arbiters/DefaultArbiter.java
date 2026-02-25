package org.apache.logging.log4j.core.config.arbiters;

import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;

@Plugin(name = "DefaultArbiter", category = "Core", elementType = "Arbiter", deferChildren = true, printObject = true)
public class DefaultArbiter implements Arbiter {
   @Override
   public boolean isCondition() {
      return true;
   }

   @PluginBuilderFactory
   public static DefaultArbiter.Builder newBuilder() {
      return new DefaultArbiter.Builder();
   }

   public static class Builder implements org.apache.logging.log4j.core.util.Builder<DefaultArbiter> {
      public DefaultArbiter.Builder asBuilder() {
         return this;
      }

      public DefaultArbiter build() {
         return new DefaultArbiter();
      }
   }
}
