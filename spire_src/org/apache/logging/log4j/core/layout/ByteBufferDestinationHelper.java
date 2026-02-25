package org.apache.logging.log4j.core.layout;

import java.nio.Buffer;
import java.nio.ByteBuffer;

public final class ByteBufferDestinationHelper {
   private ByteBufferDestinationHelper() {
   }

   public static void writeToUnsynchronized(final ByteBuffer source, final ByteBufferDestination destination) {
      ByteBuffer destBuff;
      for (destBuff = destination.getByteBuffer(); source.remaining() > destBuff.remaining(); destBuff = destination.drain(destBuff)) {
         int originalLimit = source.limit();
         ((Buffer)source).limit(Math.min(source.limit(), source.position() + destBuff.remaining()));
         destBuff.put(source);
         ((Buffer)source).limit(originalLimit);
      }

      destBuff.put(source);
   }

   public static void writeToUnsynchronized(final byte[] data, int offset, int length, final ByteBufferDestination destination) {
      ByteBuffer buffer;
      for (buffer = destination.getByteBuffer(); length > buffer.remaining(); buffer = destination.drain(buffer)) {
         int chunk = buffer.remaining();
         buffer.put(data, offset, chunk);
         offset += chunk;
         length -= chunk;
      }

      buffer.put(data, offset, length);
   }
}
