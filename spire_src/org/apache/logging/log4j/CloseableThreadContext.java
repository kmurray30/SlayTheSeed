package org.apache.logging.log4j;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class CloseableThreadContext {
   private CloseableThreadContext() {
   }

   public static CloseableThreadContext.Instance push(final String message) {
      return new CloseableThreadContext.Instance().push(message);
   }

   public static CloseableThreadContext.Instance push(final String message, final Object... args) {
      return new CloseableThreadContext.Instance().push(message, args);
   }

   public static CloseableThreadContext.Instance put(final String key, final String value) {
      return new CloseableThreadContext.Instance().put(key, value);
   }

   public static CloseableThreadContext.Instance pushAll(final List<String> messages) {
      return new CloseableThreadContext.Instance().pushAll(messages);
   }

   public static CloseableThreadContext.Instance putAll(final Map<String, String> values) {
      return new CloseableThreadContext.Instance().putAll(values);
   }

   public static class Instance implements AutoCloseable {
      private int pushCount = 0;
      private final Map<String, String> originalValues = new HashMap<>();

      private Instance() {
      }

      public CloseableThreadContext.Instance push(final String message) {
         ThreadContext.push(message);
         this.pushCount++;
         return this;
      }

      public CloseableThreadContext.Instance push(final String message, final Object[] args) {
         ThreadContext.push(message, args);
         this.pushCount++;
         return this;
      }

      public CloseableThreadContext.Instance put(final String key, final String value) {
         if (!this.originalValues.containsKey(key)) {
            this.originalValues.put(key, ThreadContext.get(key));
         }

         ThreadContext.put(key, value);
         return this;
      }

      public CloseableThreadContext.Instance putAll(final Map<String, String> values) {
         Map<String, String> currentValues = ThreadContext.getContext();
         ThreadContext.putAll(values);

         for (String key : values.keySet()) {
            if (!this.originalValues.containsKey(key)) {
               this.originalValues.put(key, currentValues.get(key));
            }
         }

         return this;
      }

      public CloseableThreadContext.Instance pushAll(final List<String> messages) {
         for (String message : messages) {
            this.push(message);
         }

         return this;
      }

      @Override
      public void close() {
         this.closeStack();
         this.closeMap();
      }

      private void closeMap() {
         for (Iterator<Entry<String, String>> it = this.originalValues.entrySet().iterator(); it.hasNext(); it.remove()) {
            Entry<String, String> entry = it.next();
            String key = entry.getKey();
            String originalValue = entry.getValue();
            if (null == originalValue) {
               ThreadContext.remove(key);
            } else {
               ThreadContext.put(key, originalValue);
            }
         }
      }

      private void closeStack() {
         for (int i = 0; i < this.pushCount; i++) {
            ThreadContext.pop();
         }

         this.pushCount = 0;
      }
   }
}
