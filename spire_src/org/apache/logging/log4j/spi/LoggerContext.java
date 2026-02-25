package org.apache.logging.log4j.spi;

import org.apache.logging.log4j.message.MessageFactory;

public interface LoggerContext {
   Object getExternalContext();

   default Object getObject(String key) {
      return null;
   }

   default Object putObject(String key, Object value) {
      return null;
   }

   default Object putObjectIfAbsent(String key, Object value) {
      return null;
   }

   default Object removeObject(String key) {
      return null;
   }

   default boolean removeObject(String key, Object value) {
      return false;
   }

   ExtendedLogger getLogger(String name);

   default ExtendedLogger getLogger(Class<?> cls) {
      String canonicalName = cls.getCanonicalName();
      return this.getLogger(canonicalName != null ? canonicalName : cls.getName());
   }

   ExtendedLogger getLogger(String name, MessageFactory messageFactory);

   default ExtendedLogger getLogger(Class<?> cls, MessageFactory messageFactory) {
      String canonicalName = cls.getCanonicalName();
      return this.getLogger(canonicalName != null ? canonicalName : cls.getName(), messageFactory);
   }

   boolean hasLogger(String name);

   boolean hasLogger(String name, MessageFactory messageFactory);

   boolean hasLogger(String name, Class<? extends MessageFactory> messageFactoryClass);
}
