package org.apache.commons.net.telnet;

public interface TelnetNotificationHandler {
   int RECEIVED_DO = 1;
   int RECEIVED_DONT = 2;
   int RECEIVED_WILL = 3;
   int RECEIVED_WONT = 4;
   int RECEIVED_COMMAND = 5;

   void receivedNegotiation(int var1, int var2);
}
