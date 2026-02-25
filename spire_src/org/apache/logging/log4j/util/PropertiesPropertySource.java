package org.apache.logging.log4j.util;

import java.util.Properties;
import java.util.Map.Entry;

public class PropertiesPropertySource implements PropertySource {
   private static final String PREFIX = "log4j2.";
   private final Properties properties;

   public PropertiesPropertySource(final Properties properties) {
      this.properties = properties;
   }

   @Override
   public int getPriority() {
      return 0;
   }

   @Override
   public void forEach(final BiConsumer<String, String> action) {
      for (Entry<Object, Object> entry : this.properties.entrySet()) {
         action.accept((String)entry.getKey(), (String)entry.getValue());
      }
   }

   @Override
   public CharSequence getNormalForm(final Iterable<? extends CharSequence> tokens) {
      return "log4j2." + PropertySource.Util.joinAsCamelCase(tokens);
   }
}
