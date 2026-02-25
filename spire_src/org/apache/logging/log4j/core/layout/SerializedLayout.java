package org.apache.logging.log4j.core.layout;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

@Deprecated
@Plugin(name = "SerializedLayout", category = "Core", elementType = "layout", printObject = true)
public final class SerializedLayout extends AbstractLayout<LogEvent> {
   private static byte[] serializedHeader;

   private SerializedLayout() {
      super(null, null, null);
      LOGGER.warn(
         "SerializedLayout is deprecated due to the inherent security weakness in Java Serialization, see https://www.owasp.org/index.php/Deserialization_of_untrusted_data Consider using another layout, e.g. JsonLayout"
      );
   }

   @Override
   public byte[] toByteArray(final LogEvent event) {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();

      try (ObjectOutputStream oos = new SerializedLayout.PrivateObjectOutputStream(baos)) {
         oos.writeObject(event);
         oos.reset();
      } catch (IOException var16) {
         LOGGER.error("Serialization of LogEvent failed.", (Throwable)var16);
      }

      return baos.toByteArray();
   }

   public LogEvent toSerializable(final LogEvent event) {
      return event;
   }

   @Deprecated
   @PluginFactory
   public static SerializedLayout createLayout() {
      return new SerializedLayout();
   }

   @Override
   public byte[] getHeader() {
      return serializedHeader;
   }

   @Override
   public String getContentType() {
      return "application/octet-stream";
   }

   static {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();

      try {
         new ObjectOutputStream(baos).close();
         serializedHeader = baos.toByteArray();
      } catch (Exception var2) {
         LOGGER.error("Unable to generate Object stream header", (Throwable)var2);
      }
   }

   private class PrivateObjectOutputStream extends ObjectOutputStream {
      public PrivateObjectOutputStream(final OutputStream os) throws IOException {
         super(os);
      }

      @Override
      protected void writeStreamHeader() {
      }
   }
}
