package org.apache.commons.net.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;

public final class CRLFLineReader extends BufferedReader {
   private static final char LF = '\n';
   private static final char CR = '\r';

   public CRLFLineReader(Reader reader) {
      super(reader);
   }

   @Override
   public String readLine() throws IOException {
      StringBuilder sb = new StringBuilder();
      boolean prevWasCR = false;
      int intch;
      synchronized (this.lock) {
         while ((intch = this.read()) != -1) {
            if (prevWasCR && intch == 10) {
               return sb.substring(0, sb.length() - 1);
            }

            if (intch == 13) {
               prevWasCR = true;
            } else {
               prevWasCR = false;
            }

            sb.append((char)intch);
         }
      }

      String string = sb.toString();
      return string.length() == 0 ? null : string;
   }
}
