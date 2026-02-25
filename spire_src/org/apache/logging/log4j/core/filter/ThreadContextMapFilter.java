package org.apache.logging.log4j.core.filter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.ContextDataInjector;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAliases;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.impl.ContextDataInjectorFactory;
import org.apache.logging.log4j.core.util.KeyValuePair;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.util.IndexedReadOnlyStringMap;
import org.apache.logging.log4j.util.PerformanceSensitive;
import org.apache.logging.log4j.util.ReadOnlyStringMap;

@Plugin(name = "ThreadContextMapFilter", category = "Core", elementType = "filter", printObject = true)
@PluginAliases("ContextMapFilter")
@PerformanceSensitive("allocation")
public class ThreadContextMapFilter extends MapFilter {
   private final ContextDataInjector injector = ContextDataInjectorFactory.createInjector();
   private final String key;
   private final String value;
   private final boolean useMap;

   public ThreadContextMapFilter(final Map<String, List<String>> pairs, final boolean oper, final Filter.Result onMatch, final Filter.Result onMismatch) {
      super(pairs, oper, onMatch, onMismatch);
      if (pairs.size() == 1) {
         Iterator<Entry<String, List<String>>> iter = pairs.entrySet().iterator();
         Entry<String, List<String>> entry = iter.next();
         if (entry.getValue().size() == 1) {
            this.key = entry.getKey();
            this.value = entry.getValue().get(0);
            this.useMap = false;
         } else {
            this.key = null;
            this.value = null;
            this.useMap = true;
         }
      } else {
         this.key = null;
         this.value = null;
         this.useMap = true;
      }
   }

   @Override
   public Filter.Result filter(final Logger logger, final Level level, final Marker marker, final String msg, final Object... params) {
      return this.filter();
   }

   @Override
   public Filter.Result filter(final Logger logger, final Level level, final Marker marker, final Object msg, final Throwable t) {
      return this.filter();
   }

   @Override
   public Filter.Result filter(final Logger logger, final Level level, final Marker marker, final Message msg, final Throwable t) {
      return this.filter();
   }

   private Filter.Result filter() {
      boolean match = false;
      if (this.useMap) {
         ReadOnlyStringMap currentContextData = null;
         IndexedReadOnlyStringMap map = this.getStringMap();

         for (int i = 0; i < map.size(); i++) {
            if (currentContextData == null) {
               currentContextData = this.currentContextData();
            }

            String toMatch = currentContextData.getValue(map.getKeyAt(i));
            match = toMatch != null && map.<List>getValueAt(i).contains(toMatch);
            if (!this.isAnd() && match || this.isAnd() && !match) {
               break;
            }
         }
      } else {
         match = this.value.equals(this.currentContextData().getValue(this.key));
      }

      return match ? this.onMatch : this.onMismatch;
   }

   private ReadOnlyStringMap currentContextData() {
      return this.injector.rawContextData();
   }

   @Override
   public Filter.Result filter(final LogEvent event) {
      return super.filter(event.getContextData()) ? this.onMatch : this.onMismatch;
   }

   @Override
   public Filter.Result filter(final Logger logger, final Level level, final Marker marker, final String msg, final Object p0) {
      return this.filter();
   }

   @Override
   public Filter.Result filter(final Logger logger, final Level level, final Marker marker, final String msg, final Object p0, final Object p1) {
      return this.filter();
   }

   @Override
   public Filter.Result filter(final Logger logger, final Level level, final Marker marker, final String msg, final Object p0, final Object p1, final Object p2) {
      return this.filter();
   }

   @Override
   public Filter.Result filter(
      final Logger logger, final Level level, final Marker marker, final String msg, final Object p0, final Object p1, final Object p2, final Object p3
   ) {
      return this.filter();
   }

   @Override
   public Filter.Result filter(
      final Logger logger,
      final Level level,
      final Marker marker,
      final String msg,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4
   ) {
      return this.filter();
   }

   @Override
   public Filter.Result filter(
      final Logger logger,
      final Level level,
      final Marker marker,
      final String msg,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5
   ) {
      return this.filter();
   }

   @Override
   public Filter.Result filter(
      final Logger logger,
      final Level level,
      final Marker marker,
      final String msg,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6
   ) {
      return this.filter();
   }

   @Override
   public Filter.Result filter(
      final Logger logger,
      final Level level,
      final Marker marker,
      final String msg,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6,
      final Object p7
   ) {
      return this.filter();
   }

   @Override
   public Filter.Result filter(
      final Logger logger,
      final Level level,
      final Marker marker,
      final String msg,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6,
      final Object p7,
      final Object p8
   ) {
      return this.filter();
   }

   @Override
   public Filter.Result filter(
      final Logger logger,
      final Level level,
      final Marker marker,
      final String msg,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6,
      final Object p7,
      final Object p8,
      final Object p9
   ) {
      return this.filter();
   }

   @PluginFactory
   public static ThreadContextMapFilter createFilter(
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
            LOGGER.error("ThreadContextMapFilter is not configured with any valid key value pairs");
            return null;
         } else {
            boolean isAnd = oper == null || !oper.equalsIgnoreCase("or");
            return new ThreadContextMapFilter(map, isAnd, match, mismatch);
         }
      } else {
         LOGGER.error("key and value pairs must be specified for the ThreadContextMapFilter");
         return null;
      }
   }
}
