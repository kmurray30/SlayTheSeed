package org.apache.commons.net.io;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.Socket;

public final class Util {
   public static final int DEFAULT_COPY_BUFFER_SIZE = 1024;

   private Util() {
   }

   public static final long copyStream(InputStream source, OutputStream dest, int bufferSize, long streamSize, CopyStreamListener listener, boolean flush) throws CopyStreamException {
      long total = 0L;
      byte[] buffer = new byte[bufferSize > 0 ? bufferSize : 1024];

      try {
         int numBytes;
         while ((numBytes = source.read(buffer)) != -1) {
            if (numBytes == 0) {
               int singleByte = source.read();
               if (singleByte < 0) {
                  break;
               }

               dest.write(singleByte);
               if (flush) {
                  dest.flush();
               }

               total++;
               if (listener != null) {
                  listener.bytesTransferred(total, 1, streamSize);
               }
            } else {
               dest.write(buffer, 0, numBytes);
               if (flush) {
                  dest.flush();
               }

               total += numBytes;
               if (listener != null) {
                  listener.bytesTransferred(total, numBytes, streamSize);
               }
            }
         }

         return total;
      } catch (IOException var12) {
         throw new CopyStreamException("IOException caught while copying.", total, var12);
      }
   }

   public static final long copyStream(InputStream source, OutputStream dest, int bufferSize, long streamSize, CopyStreamListener listener) throws CopyStreamException {
      return copyStream(source, dest, bufferSize, streamSize, listener, true);
   }

   public static final long copyStream(InputStream source, OutputStream dest, int bufferSize) throws CopyStreamException {
      return copyStream(source, dest, bufferSize, -1L, null);
   }

   public static final long copyStream(InputStream source, OutputStream dest) throws CopyStreamException {
      return copyStream(source, dest, 1024);
   }

   public static final long copyReader(Reader source, Writer dest, int bufferSize, long streamSize, CopyStreamListener listener) throws CopyStreamException {
      long total = 0L;
      char[] buffer = new char[bufferSize > 0 ? bufferSize : 1024];

      try {
         int numChars;
         while ((numChars = source.read(buffer)) != -1) {
            if (numChars == 0) {
               int singleChar = source.read();
               if (singleChar < 0) {
                  break;
               }

               dest.write(singleChar);
               dest.flush();
               total++;
               if (listener != null) {
                  listener.bytesTransferred(total, 1, streamSize);
               }
            } else {
               dest.write(buffer, 0, numChars);
               dest.flush();
               total += numChars;
               if (listener != null) {
                  listener.bytesTransferred(total, numChars, streamSize);
               }
            }
         }

         return total;
      } catch (IOException var11) {
         throw new CopyStreamException("IOException caught while copying.", total, var11);
      }
   }

   public static final long copyReader(Reader source, Writer dest, int bufferSize) throws CopyStreamException {
      return copyReader(source, dest, bufferSize, -1L, null);
   }

   public static final long copyReader(Reader source, Writer dest) throws CopyStreamException {
      return copyReader(source, dest, 1024);
   }

   public static void closeQuietly(Closeable closeable) {
      if (closeable != null) {
         try {
            closeable.close();
         } catch (IOException var2) {
         }
      }
   }

   public static void closeQuietly(Socket socket) {
      if (socket != null) {
         try {
            socket.close();
         } catch (IOException var2) {
         }
      }
   }
}
