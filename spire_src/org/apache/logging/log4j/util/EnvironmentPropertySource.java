package org.apache.logging.log4j.util;

import java.util.Map;
import java.util.Map.Entry;

public class EnvironmentPropertySource implements PropertySource {
   private static final String PREFIX = "LOG4J_";
   private static final int DEFAULT_PRIORITY = -100;

   @Override
   public int getPriority() {
      return -100;
   }

   @Override
   public void forEach(final BiConsumer<String, String> action) {
      Map<String, String> getenv;
      try {
         getenv = System.getenv();
      } catch (SecurityException var6) {
         LowLevelLogUtil.logException("The system environment variables are not available to Log4j due to security restrictions: " + var6, var6);
         return;
      }

      for (Entry<String, String> entry : getenv.entrySet()) {
         String key = entry.getKey();
         if (key.startsWith("LOG4J_")) {
            action.accept(key.substring("LOG4J_".length()), entry.getValue());
         }
      }
   }

   @Override
   public CharSequence getNormalForm(final Iterable<? extends CharSequence> tokens) {
      StringBuilder sb = new StringBuilder("LOG4J");

      for (CharSequence token : tokens) {
         sb.append('_');

         for (int i = 0; i < token.length(); i++) {
            sb.append(Character.toUpperCase(token.charAt(i)));
         }
      }

      return sb.toString();
   }
}
