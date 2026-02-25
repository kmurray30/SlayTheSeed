package org.apache.commons.net.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;

public final class DotTerminatedMessageReader extends BufferedReader {
   private static final char LF = '\n';
   private static final char CR = '\r';
   private static final int DOT = 46;
   private boolean atBeginning = true;
   private boolean eof = false;
   private boolean seenCR;

   public DotTerminatedMessageReader(Reader reader) {
      super(reader);
   }

   @Override
   public int read() throws IOException {
      synchronized (this.lock) {
         if (this.eof) {
            return -1;
         } else {
            int chint = super.read();
            if (chint == -1) {
               this.eof = true;
               return -1;
            } else {
               if (this.atBeginning) {
                  this.atBeginning = false;
                  if (chint == 46) {
                     this.mark(2);
                     chint = super.read();
                     if (chint == -1) {
                        this.eof = true;
                        return 46;
                     }

                     if (chint == 46) {
                        return chint;
                     }

                     if (chint == 13) {
                        chint = super.read();
                        if (chint == -1) {
                           this.reset();
                           return 46;
                        }

                        if (chint == 10) {
                           this.atBeginning = true;
                           this.eof = true;
                           return -1;
                        }
                     }

                     this.reset();
                     return 46;
                  }
               }

               if (this.seenCR) {
                  this.seenCR = false;
                  if (chint == 10) {
                     this.atBeginning = true;
                  }
               }

               if (chint == 13) {
                  this.seenCR = true;
               }

               return chint;
            }
         }
      }
   }

   @Override
   public int read(char[] buffer) throws IOException {
      return this.read(buffer, 0, buffer.length);
   }

   @Override
   public int read(char[] buffer, int offset, int length) throws IOException {
      if (length < 1) {
         return 0;
      } else {
         synchronized (this.lock) {
            int ch;
            if ((ch = this.read()) == -1) {
               return -1;
            } else {
               int off = offset;

               do {
                  buffer[offset++] = (char)ch;
               } while (--length > 0 && (ch = this.read()) != -1);

               return offset - off;
            }
         }
      }
   }

   @Override
   public void close() throws IOException {
      synchronized (this.lock) {
         if (!this.eof) {
            while (this.read() != -1) {
            }
         }

         this.eof = true;
         this.atBeginning = false;
      }
   }

   @Override
   public String readLine() throws IOException {
      StringBuilder sb = new StringBuilder();
      int intch;
      synchronized (this.lock) {
         while ((intch = this.read()) != -1) {
            if (intch == 10 && this.atBeginning) {
               return sb.substring(0, sb.length() - 1);
            }

            sb.append((char)intch);
         }
      }

      String string = sb.toString();
      return string.length() == 0 ? null : string;
   }
}
