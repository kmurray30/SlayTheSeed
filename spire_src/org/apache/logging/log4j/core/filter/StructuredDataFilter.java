package org.apache.logging.log4j.core.filter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.util.KeyValuePair;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.StructuredDataMessage;
import org.apache.logging.log4j.util.IndexedReadOnlyStringMap;
import org.apache.logging.log4j.util.PerformanceSensitive;
import org.apache.logging.log4j.util.StringBuilders;

@Plugin(name = "StructuredDataFilter", category = "Core", elementType = "filter", printObject = true)
@PerformanceSensitive("allocation")
public final class StructuredDataFilter extends MapFilter {
   private static final int MAX_BUFFER_SIZE = 2048;
   private static ThreadLocal<StringBuilder> threadLocalStringBuilder = new ThreadLocal<>();

   private StructuredDataFilter(final Map<String, List<String>> map, final boolean oper, final Filter.Result onMatch, final Filter.Result onMismatch) {
      super(map, oper, onMatch, onMismatch);
   }

   @Override
   public Filter.Result filter(final Logger logger, final Level level, final Marker marker, final Message msg, final Throwable t) {
      return msg instanceof StructuredDataMessage ? this.filter((StructuredDataMessage)msg) : Filter.Result.NEUTRAL;
   }

   @Override
   public Filter.Result filter(final LogEvent event) {
      Message msg = event.getMessage();
      return msg instanceof StructuredDataMessage ? this.filter((StructuredDataMessage)msg) : super.filter(event);
   }

   protected Filter.Result filter(final StructuredDataMessage message) {
      boolean match = false;
      IndexedReadOnlyStringMap map = this.getStringMap();

      for (int i = 0; i < map.size(); i++) {
         StringBuilder toMatch = this.getValue(message, map.getKeyAt(i));
         if (toMatch != null) {
            match = this.listContainsValue(map.getValueAt(i), toMatch);
         } else {
            match = false;
         }

         if (!this.isAnd() && match || this.isAnd() && !match) {
            break;
         }
      }

      return match ? this.onMatch : this.onMismatch;
   }

   private StringBuilder getValue(final StructuredDataMessage data, final String key) {
      StringBuilder sb = this.getStringBuilder();
      if (key.equalsIgnoreCase("id")) {
         data.getId().formatTo(sb);
         return sb;
      } else if (key.equalsIgnoreCase("id.name")) {
         return this.appendOrNull(data.getId().getName(), sb);
      } else if (key.equalsIgnoreCase("type")) {
         return this.appendOrNull(data.getType(), sb);
      } else if (key.equalsIgnoreCase("message")) {
         data.formatTo(sb);
         return sb;
      } else {
         return this.appendOrNull(data.get(key), sb);
      }
   }

   private StringBuilder getStringBuilder() {
      StringBuilder result = threadLocalStringBuilder.get();
      if (result == null) {
         result = new StringBuilder();
         threadLocalStringBuilder.set(result);
      }

      StringBuilders.trimToMaxSize(result, 2048);
      result.setLength(0);
      return result;
   }

   private StringBuilder appendOrNull(final String value, final StringBuilder sb) {
      if (value == null) {
         return null;
      } else {
         sb.append(value);
         return sb;
      }
   }

   private boolean listContainsValue(final List<String> candidates, final StringBuilder toMatch) {
      if (toMatch == null) {
         for (int i = 0; i < candidates.size(); i++) {
            String candidate = candidates.get(i);
            if (candidate == null) {
               return true;
            }
         }
      } else {
         for (int ix = 0; ix < candidates.size(); ix++) {
            String candidate = candidates.get(ix);
            if (candidate == null) {
               return false;
            }

            if (StringBuilders.equals(candidate, 0, candidate.length(), toMatch, 0, toMatch.length())) {
               return true;
            }
         }
      }

      return false;
   }

   @PluginFactory
   public static StructuredDataFilter createFilter(
      @PluginElement("Pairs") final KeyValuePair[] pairs,
      @PluginAttribute("operator") final String oper,
      @PluginAttribute("onMatch") final Filter.Result match,
      @PluginAttribute("onMismatch") final Filter.Result mismatch
   ) {
      if (pairs != null && pairs.length != 0) {
         Map<String, List<String>> map = new HashMap<>();

         for (KeyValuePair pair : pairs) {
            String key = pair.getKey();
            if (key == null) {
               LOGGER.error("A null key is not valid in MapFilter");
            } else {
               String value = pair.getValue();
               if (value == null) {
                  LOGGER.error("A null value for key " + key + " is not allowed in MapFilter");
               } else {
                  List<String> list = map.get(pair.getKey());
                  if (list != null) {
                     list.add(value);
                  } else {
                     List<String> var13 = new ArrayList();
                     var13.add(value);
                     map.put(pair.getKey(), var13);
                  }
               }
            }
         }

         if (map.isEmpty()) {
            LOGGER.error("StructuredDataFilter is not configured with any valid key value pairs");
            return null;
         } else {
            boolean isAnd = oper == null || !oper.equalsIgnoreCase("or");
            return new StructuredDataFilter(map, isAnd, match, mismatch);
         }
      } else {
         LOGGER.error("keys and values must be specified for the StructuredDataFilter");
         return null;
      }
   }
}
