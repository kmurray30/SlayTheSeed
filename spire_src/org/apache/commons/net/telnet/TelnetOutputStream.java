package org.apache.commons.net.telnet;

import java.io.IOException;
import java.io.OutputStream;

final class TelnetOutputStream extends OutputStream {
   private final TelnetClient __client;
   private final boolean __convertCRtoCRLF = true;
   private boolean __lastWasCR = false;

   TelnetOutputStream(TelnetClient client) {
      this.__client = client;
   }

   @Override
   public void write(int ch) throws IOException {
      synchronized (this.__client) {
         ch &= 255;
         if (this.__client._requestedWont(0)) {
            if (this.__lastWasCR) {
               this.__client._sendByte(10);
               if (ch == 10) {
                  this.__lastWasCR = false;
                  return;
               }
            }

            switch (ch) {
               case 10:
                  if (!this.__lastWasCR) {
                     this.__client._sendByte(13);
                  }

                  this.__client._sendByte(ch);
                  this.__lastWasCR = false;
                  break;
               case 13:
                  this.__client._sendByte(13);
                  this.__lastWasCR = true;
                  break;
               case 255:
                  this.__client._sendByte(255);
                  this.__client._sendByte(255);
                  this.__lastWasCR = false;
                  break;
               default:
                  this.__client._sendByte(ch);
                  this.__lastWasCR = false;
            }
         } else if (ch == 255) {
            this.__client._sendByte(ch);
            this.__client._sendByte(255);
         } else {
            this.__client._sendByte(ch);
         }
      }
   }

   @Override
   public void write(byte[] buffer) throws IOException {
      this.write(buffer, 0, buffer.length);
   }

   @Override
   public void write(byte[] buffer, int offset, int length) throws IOException {
      synchronized (this.__client) {
         while (length-- > 0) {
            this.write(buffer[offset++]);
         }
      }
   }

   @Override
   public void flush() throws IOException {
      this.__client._flushOutputStream();
   }

   @Override
   public void close() throws IOException {
      this.__client._closeOutputStream();
   }
}
