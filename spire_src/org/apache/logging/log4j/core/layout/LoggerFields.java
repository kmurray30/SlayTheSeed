package org.apache.logging.log4j.core.layout;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.util.KeyValuePair;
import org.apache.logging.log4j.message.StructuredDataId;

@Plugin(name = "LoggerFields", category = "Core", printObject = true)
public final class LoggerFields {
   private final Map<String, String> map;
   private final String sdId;
   private final String enterpriseId;
   private final boolean discardIfAllFieldsAreEmpty;

   private LoggerFields(final Map<String, String> map, final String sdId, final String enterpriseId, final boolean discardIfAllFieldsAreEmpty) {
      this.sdId = sdId;
      this.enterpriseId = enterpriseId;
      this.map = Collections.unmodifiableMap(map);
      this.discardIfAllFieldsAreEmpty = discardIfAllFieldsAreEmpty;
   }

   public Map<String, String> getMap() {
      return this.map;
   }

   @Override
   public String toString() {
      return this.map.toString();
   }

   @PluginFactory
   public static LoggerFields createLoggerFields(
      @PluginElement("LoggerFields") final KeyValuePair[] keyValuePairs,
      @PluginAttribute("sdId") final String sdId,
      @PluginAttribute("enterpriseId") final String enterpriseId,
      @PluginAttribute("discardIfAllFieldsAreEmpty") final boolean discardIfAllFieldsAreEmpty
   ) {
      Map<String, String> map = new HashMap<>();

      for (KeyValuePair keyValuePair : keyValuePairs) {
         map.put(keyValuePair.getKey(), keyValuePair.getValue());
      }

      return new LoggerFields(map, sdId, enterpriseId, discardIfAllFieldsAreEmpty);
   }

   public StructuredDataId getSdId() {
      if (this.enterpriseId != null && this.sdId != null) {
         int eId = Integer.parseInt(this.enterpriseId);
         return new StructuredDataId(this.sdId, eId, null, null);
      } else {
         return null;
      }
   }

   public boolean getDiscardIfAllFieldsAreEmpty() {
      return this.discardIfAllFieldsAreEmpty;
   }
}
