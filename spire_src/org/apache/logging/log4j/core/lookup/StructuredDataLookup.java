package org.apache.logging.log4j.core.lookup;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.message.StructuredDataMessage;

@Plugin(name = "sd", category = "Lookup")
public class StructuredDataLookup implements StrLookup {
   @Override
   public String lookup(final String key) {
      return null;
   }

   @Override
   public String lookup(final LogEvent event, final String key) {
      if (event != null && event.getMessage() instanceof StructuredDataMessage) {
         StructuredDataMessage msg = (StructuredDataMessage)event.getMessage();
         if (key.equalsIgnoreCase("id")) {
            return msg.getId().getName();
         } else {
            return key.equalsIgnoreCase("type") ? msg.getType() : msg.get(key);
         }
      } else {
         return null;
      }
   }
}
