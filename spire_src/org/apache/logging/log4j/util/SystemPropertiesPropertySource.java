package org.apache.logging.log4j.util;

import java.util.Objects;
import java.util.Properties;

public class SystemPropertiesPropertySource implements PropertySource {
   private static final int DEFAULT_PRIORITY = 100;
   private static final String PREFIX = "log4j2.";

   @Override
   public int getPriority() {
      return 100;
   }

   @Override
   public void forEach(final BiConsumer<String, String> action) {
      Properties properties;
      try {
         properties = System.getProperties();
      } catch (SecurityException var10) {
         return;
      }

      Object[] keySet;
      synchronized (properties) {
         keySet = properties.keySet().toArray();
      }

      for (Object key : keySet) {
         String keyStr = Objects.toString(key, null);
         action.accept(keyStr, properties.getProperty(keyStr));
      }
   }

   @Override
   public CharSequence getNormalForm(final Iterable<? extends CharSequence> tokens) {
      return "log4j2." + PropertySource.Util.joinAsCamelCase(tokens);
   }
}
