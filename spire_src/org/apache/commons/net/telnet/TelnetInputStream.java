package org.apache.commons.net.telnet;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;

final class TelnetInputStream extends BufferedInputStream implements Runnable {
   private static final int EOF = -1;
   private static final int WOULD_BLOCK = -2;
   static final int _STATE_DATA = 0;
   static final int _STATE_IAC = 1;
   static final int _STATE_WILL = 2;
   static final int _STATE_WONT = 3;
   static final int _STATE_DO = 4;
   static final int _STATE_DONT = 5;
   static final int _STATE_SB = 6;
   static final int _STATE_SE = 7;
   static final int _STATE_CR = 8;
   static final int _STATE_IAC_SB = 9;
   private boolean __hasReachedEOF;
   private volatile boolean __isClosed;
   private boolean __readIsWaiting;
   private int __receiveState;
   private int __queueHead;
   private int __queueTail;
   private int __bytesAvailable;
   private final int[] __queue;
   private final TelnetClient __client;
   private final Thread __thread;
   private IOException __ioException;
   private final int[] __suboption = new int[512];
   private int __suboption_count = 0;
   private volatile boolean __threaded;

   TelnetInputStream(InputStream input, TelnetClient client, boolean readerThread) {
      super(input);
      this.__client = client;
      this.__receiveState = 0;
      this.__isClosed = true;
      this.__hasReachedEOF = false;
      this.__queue = new int[2049];
      this.__queueHead = 0;
      this.__queueTail = 0;
      this.__bytesAvailable = 0;
      this.__ioException = null;
      this.__readIsWaiting = false;
      this.__threaded = false;
      if (readerThread) {
         this.__thread = new Thread(this);
      } else {
         this.__thread = null;
      }
   }

   TelnetInputStream(InputStream input, TelnetClient client) {
      this(input, client, true);
   }

   void _start() {
      if (this.__thread != null) {
         this.__isClosed = false;
         int priority = Thread.currentThread().getPriority() + 1;
         if (priority > 10) {
            priority = 10;
         }

         this.__thread.setPriority(priority);
         this.__thread.setDaemon(true);
         this.__thread.start();
         this.__threaded = true;
      }
   }

   private int __read(boolean mayBlock) throws IOException {
      while (mayBlock || super.available() != 0) {
         int ch;
         if ((ch = super.read()) < 0) {
            return -1;
         }

         ch &= 255;
         synchronized (this.__client) {
            this.__client._processAYTResponse();
         }

         this.__client._spyRead(ch);
         switch (this.__receiveState) {
            case 1:
               switch (ch) {
                  case 240:
                     this.__receiveState = 0;
                     continue;
                  case 241:
                  case 242:
                  case 243:
                  case 244:
                  case 245:
                  case 246:
                  case 247:
                  case 248:
                  case 249:
                  default:
                     this.__receiveState = 0;
                     this.__client._processCommand(ch);
                     continue;
                  case 250:
                     this.__suboption_count = 0;
                     this.__receiveState = 6;
                     continue;
                  case 251:
                     this.__receiveState = 2;
                     continue;
                  case 252:
                     this.__receiveState = 3;
                     continue;
                  case 253:
                     this.__receiveState = 4;
                     continue;
                  case 254:
                     this.__receiveState = 5;
                     continue;
                  case 255:
                     this.__receiveState = 0;
                     return ch;
               }
            case 2:
               synchronized (this.__client) {
                  this.__client._processWill(ch);
                  this.__client._flushOutputStream();
               }

               this.__receiveState = 0;
               continue;
            case 3:
               synchronized (this.__client) {
                  this.__client._processWont(ch);
                  this.__client._flushOutputStream();
               }

               this.__receiveState = 0;
               continue;
            case 4:
               synchronized (this.__client) {
                  this.__client._processDo(ch);
                  this.__client._flushOutputStream();
               }

               this.__receiveState = 0;
               continue;
            case 5:
               synchronized (this.__client) {
                  this.__client._processDont(ch);
                  this.__client._flushOutputStream();
               }

               this.__receiveState = 0;
               continue;
            case 6:
               switch (ch) {
                  case 255:
                     this.__receiveState = 9;
                     continue;
                  default:
                     if (this.__suboption_count < this.__suboption.length) {
                        this.__suboption[this.__suboption_count++] = ch;
                     }

                     this.__receiveState = 6;
                     continue;
               }
            case 7:
            default:
               break;
            case 8:
               if (ch == 0) {
                  continue;
               }
            case 0:
               if (ch == 255) {
                  this.__receiveState = 1;
                  continue;
               }

               if (ch == 13) {
                  synchronized (this.__client) {
                     if (this.__client._requestedDont(0)) {
                        this.__receiveState = 8;
                     } else {
                        this.__receiveState = 0;
                     }
                  }
               } else {
                  this.__receiveState = 0;
               }
               break;
            case 9:
               switch (ch) {
                  case 240:
                     synchronized (this.__client) {
                        this.__client._processSuboption(this.__suboption, this.__suboption_count);
                        this.__client._flushOutputStream();
                     }

                     this.__receiveState = 0;
                     continue;
                  case 255:
                     if (this.__suboption_count < this.__suboption.length) {
                        this.__suboption[this.__suboption_count++] = ch;
                     }
                  default:
                     this.__receiveState = 6;
                     continue;
               }
         }

         return ch;
      }

      return -2;
   }

   private boolean __processChar(int ch) throws InterruptedException {
      synchronized (this.__queue) {
         boolean bufferWasEmpty = this.__bytesAvailable == 0;

         while (this.__bytesAvailable >= this.__queue.length - 1) {
            if (!this.__threaded) {
               throw new IllegalStateException("Queue is full! Cannot process another character.");
            }

            this.__queue.notify();

            try {
               this.__queue.wait();
            } catch (InterruptedException var6) {
               throw var6;
            }
         }

         if (this.__readIsWaiting && this.__threaded) {
            this.__queue.notify();
         }

         this.__queue[this.__queueTail] = ch;
         this.__bytesAvailable++;
         if (++this.__queueTail >= this.__queue.length) {
            this.__queueTail = 0;
         }

         return bufferWasEmpty;
      }
   }

   @Override
   public int read() throws IOException {
      synchronized (this.__queue) {
         while (this.__ioException == null) {
            if (this.__bytesAvailable != 0) {
               int ch = this.__queue[this.__queueHead];
               if (++this.__queueHead >= this.__queue.length) {
                  this.__queueHead = 0;
               }

               this.__bytesAvailable--;
               if (this.__bytesAvailable == 0 && this.__threaded) {
                  this.__queue.notify();
               }

               return ch;
            }

            if (this.__hasReachedEOF) {
               return -1;
            }

            if (this.__threaded) {
               this.__queue.notify();

               try {
                  this.__readIsWaiting = true;
                  this.__queue.wait();
                  this.__readIsWaiting = false;
               } catch (InterruptedException var11) {
                  throw new InterruptedIOException("Fatal thread interruption during read.");
               }
            } else {
               this.__readIsWaiting = true;
               boolean mayBlock = true;

               do {
                  int chx;
                  try {
                     if ((chx = this.__read(mayBlock)) < 0 && chx != -2) {
                        return chx;
                     }
                  } catch (InterruptedIOException var13) {
                     InterruptedIOException e = var13;
                     synchronized (this.__queue) {
                        this.__ioException = e;
                        this.__queue.notifyAll();

                        try {
                           this.__queue.wait(100L);
                        } catch (InterruptedException var9) {
                        }
                     }

                     return -1;
                  }

                  try {
                     if (chx != -2) {
                        this.__processChar(chx);
                     }
                  } catch (InterruptedException var12) {
                     if (this.__isClosed) {
                        return -1;
                     }
                  }

                  mayBlock = false;
               } while (super.available() > 0 && this.__bytesAvailable < this.__queue.length - 1);

               this.__readIsWaiting = false;
            }
         }

         IOException e = this.__ioException;
         this.__ioException = null;
         throw e;
      }
   }

   @Override
   public int read(byte[] buffer) throws IOException {
      return this.read(buffer, 0, buffer.length);
   }

   @Override
   public int read(byte[] buffer, int offset, int length) throws IOException {
      if (length < 1) {
         return 0;
      } else {
         synchronized (this.__queue) {
            if (length > this.__bytesAvailable) {
               length = this.__bytesAvailable;
            }
         }

         int ch;
         if ((ch = this.read()) == -1) {
            return -1;
         } else {
            int off = offset;

            do {
               buffer[offset++] = (byte)ch;
            } while (--length > 0 && (ch = this.read()) != -1);

            return offset - off;
         }
      }
   }

   @Override
   public boolean markSupported() {
      return false;
   }

   @Override
   public int available() throws IOException {
      synchronized (this.__queue) {
         return this.__threaded ? this.__bytesAvailable : this.__bytesAvailable + super.available();
      }
   }

   @Override
   public void close() throws IOException {
      super.close();
      synchronized (this.__queue) {
         this.__hasReachedEOF = true;
         this.__isClosed = true;
         if (this.__thread != null && this.__thread.isAlive()) {
            this.__thread.interrupt();
         }

         this.__queue.notifyAll();
      }
   }

   @Override
   public void run() {
      try {
         while (!this.__isClosed) {
            int ch;
            try {
               if ((ch = this.__read(true)) < 0) {
                  break;
               }
            } catch (InterruptedIOException var13) {
               InterruptedIOException e = var13;
               synchronized (this.__queue) {
                  this.__ioException = e;
                  this.__queue.notifyAll();

                  try {
                     this.__queue.wait(100L);
                  } catch (InterruptedException var11) {
                     if (this.__isClosed) {
                        break;
                     }
                  }
                  continue;
               }
            } catch (RuntimeException var14) {
               super.close();
               break;
            }

            boolean notify = false;

            try {
               notify = this.__processChar(ch);
            } catch (InterruptedException var10) {
               if (this.__isClosed) {
                  break;
               }
            }

            if (notify) {
               this.__client.notifyInputListener();
            }
         }
      } catch (IOException var15) {
         IOException ioe = var15;
         synchronized (this.__queue) {
            this.__ioException = ioe;
         }

         this.__client.notifyInputListener();
      }

      synchronized (this.__queue) {
         this.__isClosed = true;
         this.__hasReachedEOF = true;
         this.__queue.notify();
      }

      this.__threaded = false;
   }
}
