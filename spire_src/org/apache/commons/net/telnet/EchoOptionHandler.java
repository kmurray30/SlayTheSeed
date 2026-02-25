package org.apache.commons.net.telnet;

public class EchoOptionHandler extends TelnetOptionHandler {
   public EchoOptionHandler(boolean initlocal, boolean initremote, boolean acceptlocal, boolean acceptremote) {
      super(1, initlocal, initremote, acceptlocal, acceptremote);
   }

   public EchoOptionHandler() {
      super(1, false, false, false, false);
   }
}
