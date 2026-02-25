package org.apache.commons.net.telnet;

public class SimpleOptionHandler extends TelnetOptionHandler {
   public SimpleOptionHandler(int optcode, boolean initlocal, boolean initremote, boolean acceptlocal, boolean acceptremote) {
      super(optcode, initlocal, initremote, acceptlocal, acceptremote);
   }

   public SimpleOptionHandler(int optcode) {
      super(optcode, false, false, false, false);
   }
}
