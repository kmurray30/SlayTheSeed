package com.gikk.twirk;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.SocketTimeoutException;

class InputThread extends Thread {
   private final Twirk connection;
   private final BufferedReader reader;
   private boolean isConnected = true;
   private boolean havePinged = false;

   public InputThread(Twirk connection, BufferedReader reader, BufferedWriter writer) {
      this.connection = connection;
      this.reader = reader;
      this.setName("Twirk-InputThread");
   }

   @Override
   public void run() {
      try {
         while (this.isConnected) {
            try {
               String line = null;

               while ((line = this.reader.readLine()) != null) {
                  this.havePinged = false;

                  try {
                     this.connection.incommingMessage(line);
                  } catch (Exception var3) {
                     System.err.println("Error in handling the incomming Irc Message");
                     var3.printStackTrace();
                  }
               }

               this.isConnected = false;
            } catch (SocketTimeoutException var4) {
               if (!this.havePinged) {
                  this.connection.serverMessage("PING " + System.currentTimeMillis());
                  this.havePinged = true;
               } else {
                  this.isConnected = false;
               }
            } catch (IOException var5) {
               String message = var5.getMessage();
               if (!message.contains("Socket Closed")) {
                  if (!message.contains("Connection reset") && !message.contains("Stream closed")) {
                     var5.printStackTrace();
                  } else {
                     System.err.println(message);
                  }
               }

               this.isConnected = false;
            }
         }
      } catch (Exception var6) {
         var6.printStackTrace();
      }

      if (this.connection.isConnected()) {
         this.connection.disconnect();
      }
   }

   public void end() {
      this.isConnected = false;
   }
}
