package org.apache.commons.net.telnet;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import org.apache.commons.net.SocketClient;

class Telnet extends SocketClient {
   static final boolean debug = false;
   static final boolean debugoptions = false;
   static final byte[] _COMMAND_DO = new byte[]{-1, -3};
   static final byte[] _COMMAND_DONT = new byte[]{-1, -2};
   static final byte[] _COMMAND_WILL = new byte[]{-1, -5};
   static final byte[] _COMMAND_WONT = new byte[]{-1, -4};
   static final byte[] _COMMAND_SB = new byte[]{-1, -6};
   static final byte[] _COMMAND_SE = new byte[]{-1, -16};
   static final int _WILL_MASK = 1;
   static final int _DO_MASK = 2;
   static final int _REQUESTED_WILL_MASK = 4;
   static final int _REQUESTED_DO_MASK = 8;
   static final int DEFAULT_PORT = 23;
   int[] _doResponse;
   int[] _willResponse;
   int[] _options;
   protected static final int TERMINAL_TYPE = 24;
   protected static final int TERMINAL_TYPE_SEND = 1;
   protected static final int TERMINAL_TYPE_IS = 0;
   static final byte[] _COMMAND_IS = new byte[]{24, 0};
   private String terminalType = null;
   private final TelnetOptionHandler[] optionHandlers;
   static final byte[] _COMMAND_AYT = new byte[]{-1, -10};
   private final Object aytMonitor = new Object();
   private volatile boolean aytFlag = true;
   private volatile OutputStream spyStream = null;
   private TelnetNotificationHandler __notifhand = null;

   Telnet() {
      this.setDefaultPort(23);
      this._doResponse = new int[256];
      this._willResponse = new int[256];
      this._options = new int[256];
      this.optionHandlers = new TelnetOptionHandler[256];
   }

   Telnet(String termtype) {
      this.setDefaultPort(23);
      this._doResponse = new int[256];
      this._willResponse = new int[256];
      this._options = new int[256];
      this.terminalType = termtype;
      this.optionHandlers = new TelnetOptionHandler[256];
   }

   boolean _stateIsWill(int option) {
      return (this._options[option] & 1) != 0;
   }

   boolean _stateIsWont(int option) {
      return !this._stateIsWill(option);
   }

   boolean _stateIsDo(int option) {
      return (this._options[option] & 2) != 0;
   }

   boolean _stateIsDont(int option) {
      return !this._stateIsDo(option);
   }

   boolean _requestedWill(int option) {
      return (this._options[option] & 4) != 0;
   }

   boolean _requestedWont(int option) {
      return !this._requestedWill(option);
   }

   boolean _requestedDo(int option) {
      return (this._options[option] & 8) != 0;
   }

   boolean _requestedDont(int option) {
      return !this._requestedDo(option);
   }

   void _setWill(int option) throws IOException {
      this._options[option] = this._options[option] | 1;
      if (this._requestedWill(option) && this.optionHandlers[option] != null) {
         this.optionHandlers[option].setWill(true);
         int[] subneg = this.optionHandlers[option].startSubnegotiationLocal();
         if (subneg != null) {
            this._sendSubnegotiation(subneg);
         }
      }
   }

   void _setDo(int option) throws IOException {
      this._options[option] = this._options[option] | 2;
      if (this._requestedDo(option) && this.optionHandlers[option] != null) {
         this.optionHandlers[option].setDo(true);
         int[] subneg = this.optionHandlers[option].startSubnegotiationRemote();
         if (subneg != null) {
            this._sendSubnegotiation(subneg);
         }
      }
   }

   void _setWantWill(int option) {
      this._options[option] = this._options[option] | 4;
   }

   void _setWantDo(int option) {
      this._options[option] = this._options[option] | 8;
   }

   void _setWont(int option) {
      this._options[option] = this._options[option] & -2;
      if (this.optionHandlers[option] != null) {
         this.optionHandlers[option].setWill(false);
      }
   }

   void _setDont(int option) {
      this._options[option] = this._options[option] & -3;
      if (this.optionHandlers[option] != null) {
         this.optionHandlers[option].setDo(false);
      }
   }

   void _setWantWont(int option) {
      this._options[option] = this._options[option] & -5;
   }

   void _setWantDont(int option) {
      this._options[option] = this._options[option] & -9;
   }

   void _processCommand(int command) {
      if (this.__notifhand != null) {
         this.__notifhand.receivedNegotiation(5, command);
      }
   }

   void _processDo(int option) throws IOException {
      if (this.__notifhand != null) {
         this.__notifhand.receivedNegotiation(1, option);
      }

      boolean acceptNewState = false;
      if (this.optionHandlers[option] != null) {
         acceptNewState = this.optionHandlers[option].getAcceptLocal();
      } else if (option == 24 && this.terminalType != null && this.terminalType.length() > 0) {
         acceptNewState = true;
      }

      if (this._willResponse[option] > 0) {
         this._willResponse[option]--;
         if (this._willResponse[option] > 0 && this._stateIsWill(option)) {
            this._willResponse[option]--;
         }
      }

      if (this._willResponse[option] == 0) {
         if (this._requestedWont(option)) {
            switch (option) {
               default:
                  if (acceptNewState) {
                     this._setWantWill(option);
                     this._sendWill(option);
                  } else {
                     this._willResponse[option]++;
                     this._sendWont(option);
                  }
            }
         } else {
            switch (option) {
            }
         }
      }

      this._setWill(option);
   }

   void _processDont(int option) throws IOException {
      if (this.__notifhand != null) {
         this.__notifhand.receivedNegotiation(2, option);
      }

      if (this._willResponse[option] > 0) {
         this._willResponse[option]--;
         if (this._willResponse[option] > 0 && this._stateIsWont(option)) {
            this._willResponse[option]--;
         }
      }

      if (this._willResponse[option] == 0 && this._requestedWill(option)) {
         switch (option) {
         }

         if (this._stateIsWill(option) || this._requestedWill(option)) {
            this._sendWont(option);
         }

         this._setWantWont(option);
      }

      this._setWont(option);
   }

   void _processWill(int option) throws IOException {
      if (this.__notifhand != null) {
         this.__notifhand.receivedNegotiation(3, option);
      }

      boolean acceptNewState = false;
      if (this.optionHandlers[option] != null) {
         acceptNewState = this.optionHandlers[option].getAcceptRemote();
      }

      if (this._doResponse[option] > 0) {
         this._doResponse[option]--;
         if (this._doResponse[option] > 0 && this._stateIsDo(option)) {
            this._doResponse[option]--;
         }
      }

      if (this._doResponse[option] == 0 && this._requestedDont(option)) {
         switch (option) {
            default:
               if (acceptNewState) {
                  this._setWantDo(option);
                  this._sendDo(option);
               } else {
                  this._doResponse[option]++;
                  this._sendDont(option);
               }
         }
      }

      this._setDo(option);
   }

   void _processWont(int option) throws IOException {
      if (this.__notifhand != null) {
         this.__notifhand.receivedNegotiation(4, option);
      }

      if (this._doResponse[option] > 0) {
         this._doResponse[option]--;
         if (this._doResponse[option] > 0 && this._stateIsDont(option)) {
            this._doResponse[option]--;
         }
      }

      if (this._doResponse[option] == 0 && this._requestedDo(option)) {
         switch (option) {
         }

         if (this._stateIsDo(option) || this._requestedDo(option)) {
            this._sendDont(option);
         }

         this._setWantDont(option);
      }

      this._setDont(option);
   }

   void _processSuboption(int[] suboption, int suboptionLength) throws IOException {
      if (suboptionLength > 0) {
         if (this.optionHandlers[suboption[0]] != null) {
            int[] responseSuboption = this.optionHandlers[suboption[0]].answerSubnegotiation(suboption, suboptionLength);
            this._sendSubnegotiation(responseSuboption);
         } else if (suboptionLength > 1 && suboption[0] == 24 && suboption[1] == 1) {
            this._sendTerminalType();
         }
      }
   }

   final synchronized void _sendTerminalType() throws IOException {
      if (this.terminalType != null) {
         this._output_.write(_COMMAND_SB);
         this._output_.write(_COMMAND_IS);
         this._output_.write(this.terminalType.getBytes(this.getCharset()));
         this._output_.write(_COMMAND_SE);
         this._output_.flush();
      }
   }

   final synchronized void _sendSubnegotiation(int[] subn) throws IOException {
      if (subn != null) {
         this._output_.write(_COMMAND_SB);

         for (int element : subn) {
            byte b = (byte)element;
            if (b == -1) {
               this._output_.write(b);
            }

            this._output_.write(b);
         }

         this._output_.write(_COMMAND_SE);
         this._output_.flush();
      }
   }

   final synchronized void _sendCommand(byte cmd) throws IOException {
      this._output_.write(255);
      this._output_.write(cmd);
      this._output_.flush();
   }

   final synchronized void _processAYTResponse() {
      if (!this.aytFlag) {
         synchronized (this.aytMonitor) {
            this.aytFlag = true;
            this.aytMonitor.notifyAll();
         }
      }
   }

   @Override
   protected void _connectAction_() throws IOException {
      for (int ii = 0; ii < 256; ii++) {
         this._doResponse[ii] = 0;
         this._willResponse[ii] = 0;
         this._options[ii] = 0;
         if (this.optionHandlers[ii] != null) {
            this.optionHandlers[ii].setDo(false);
            this.optionHandlers[ii].setWill(false);
         }
      }

      super._connectAction_();
      this._input_ = new BufferedInputStream(this._input_);
      this._output_ = new BufferedOutputStream(this._output_);

      for (int iix = 0; iix < 256; iix++) {
         if (this.optionHandlers[iix] != null) {
            if (this.optionHandlers[iix].getInitLocal()) {
               this._requestWill(this.optionHandlers[iix].getOptionCode());
            }

            if (this.optionHandlers[iix].getInitRemote()) {
               this._requestDo(this.optionHandlers[iix].getOptionCode());
            }
         }
      }
   }

   final synchronized void _sendDo(int option) throws IOException {
      this._output_.write(_COMMAND_DO);
      this._output_.write(option);
      this._output_.flush();
   }

   final synchronized void _requestDo(int option) throws IOException {
      if ((this._doResponse[option] != 0 || !this._stateIsDo(option)) && !this._requestedDo(option)) {
         this._setWantDo(option);
         this._doResponse[option]++;
         this._sendDo(option);
      }
   }

   final synchronized void _sendDont(int option) throws IOException {
      this._output_.write(_COMMAND_DONT);
      this._output_.write(option);
      this._output_.flush();
   }

   final synchronized void _requestDont(int option) throws IOException {
      if ((this._doResponse[option] != 0 || !this._stateIsDont(option)) && !this._requestedDont(option)) {
         this._setWantDont(option);
         this._doResponse[option]++;
         this._sendDont(option);
      }
   }

   final synchronized void _sendWill(int option) throws IOException {
      this._output_.write(_COMMAND_WILL);
      this._output_.write(option);
      this._output_.flush();
   }

   final synchronized void _requestWill(int option) throws IOException {
      if ((this._willResponse[option] != 0 || !this._stateIsWill(option)) && !this._requestedWill(option)) {
         this._setWantWill(option);
         this._doResponse[option]++;
         this._sendWill(option);
      }
   }

   final synchronized void _sendWont(int option) throws IOException {
      this._output_.write(_COMMAND_WONT);
      this._output_.write(option);
      this._output_.flush();
   }

   final synchronized void _requestWont(int option) throws IOException {
      if ((this._willResponse[option] != 0 || !this._stateIsWont(option)) && !this._requestedWont(option)) {
         this._setWantWont(option);
         this._doResponse[option]++;
         this._sendWont(option);
      }
   }

   final synchronized void _sendByte(int b) throws IOException {
      this._output_.write(b);
      this._spyWrite(b);
   }

   final boolean _sendAYT(long timeout) throws IOException, IllegalArgumentException, InterruptedException {
      boolean retValue = false;
      synchronized (this.aytMonitor) {
         synchronized (this) {
            this.aytFlag = false;
            this._output_.write(_COMMAND_AYT);
            this._output_.flush();
         }

         this.aytMonitor.wait(timeout);
         if (!this.aytFlag) {
            retValue = false;
            this.aytFlag = true;
         } else {
            retValue = true;
         }

         return retValue;
      }
   }

   void addOptionHandler(TelnetOptionHandler opthand) throws InvalidTelnetOptionException, IOException {
      int optcode = opthand.getOptionCode();
      if (TelnetOption.isValidOption(optcode)) {
         if (this.optionHandlers[optcode] == null) {
            this.optionHandlers[optcode] = opthand;
            if (this.isConnected()) {
               if (opthand.getInitLocal()) {
                  this._requestWill(optcode);
               }

               if (opthand.getInitRemote()) {
                  this._requestDo(optcode);
               }
            }
         } else {
            throw new InvalidTelnetOptionException("Already registered option", optcode);
         }
      } else {
         throw new InvalidTelnetOptionException("Invalid Option Code", optcode);
      }
   }

   void deleteOptionHandler(int optcode) throws InvalidTelnetOptionException, IOException {
      if (TelnetOption.isValidOption(optcode)) {
         if (this.optionHandlers[optcode] == null) {
            throw new InvalidTelnetOptionException("Unregistered option", optcode);
         } else {
            TelnetOptionHandler opthand = this.optionHandlers[optcode];
            this.optionHandlers[optcode] = null;
            if (opthand.getWill()) {
               this._requestWont(optcode);
            }

            if (opthand.getDo()) {
               this._requestDont(optcode);
            }
         }
      } else {
         throw new InvalidTelnetOptionException("Invalid Option Code", optcode);
      }
   }

   void _registerSpyStream(OutputStream spystream) {
      this.spyStream = spystream;
   }

   void _stopSpyStream() {
      this.spyStream = null;
   }

   void _spyRead(int ch) {
      OutputStream spy = this.spyStream;
      if (spy != null) {
         try {
            if (ch != 13) {
               if (ch == 10) {
                  spy.write(13);
               }

               spy.write(ch);
               spy.flush();
            }
         } catch (IOException var4) {
            this.spyStream = null;
         }
      }
   }

   void _spyWrite(int ch) {
      if (!this._stateIsDo(1) || !this._requestedDo(1)) {
         OutputStream spy = this.spyStream;
         if (spy != null) {
            try {
               spy.write(ch);
               spy.flush();
            } catch (IOException var4) {
               this.spyStream = null;
            }
         }
      }
   }

   public void registerNotifHandler(TelnetNotificationHandler notifhand) {
      this.__notifhand = notifhand;
   }

   public void unregisterNotifHandler() {
      this.__notifhand = null;
   }
}
