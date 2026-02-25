package com.badlogic.gdx.net;

import com.badlogic.gdx.Net;
import com.badlogic.gdx.utils.GdxRuntimeException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;

public class NetJavaSocketImpl implements Socket {
   private java.net.Socket socket;

   public NetJavaSocketImpl(Net.Protocol protocol, String host, int port, SocketHints hints) {
      try {
         this.socket = new java.net.Socket();
         this.applyHints(hints);
         InetSocketAddress address = new InetSocketAddress(host, port);
         if (hints != null) {
            this.socket.connect(address, hints.connectTimeout);
         } else {
            this.socket.connect(address);
         }
      } catch (Exception var6) {
         throw new GdxRuntimeException("Error making a socket connection to " + host + ":" + port, var6);
      }
   }

   public NetJavaSocketImpl(java.net.Socket socket, SocketHints hints) {
      this.socket = socket;
      this.applyHints(hints);
   }

   private void applyHints(SocketHints hints) {
      if (hints != null) {
         try {
            this.socket.setPerformancePreferences(hints.performancePrefConnectionTime, hints.performancePrefLatency, hints.performancePrefBandwidth);
            this.socket.setTrafficClass(hints.trafficClass);
            this.socket.setTcpNoDelay(hints.tcpNoDelay);
            this.socket.setKeepAlive(hints.keepAlive);
            this.socket.setSendBufferSize(hints.sendBufferSize);
            this.socket.setReceiveBufferSize(hints.receiveBufferSize);
            this.socket.setSoLinger(hints.linger, hints.lingerDuration);
            this.socket.setSoTimeout(hints.socketTimeout);
         } catch (Exception var3) {
            throw new GdxRuntimeException("Error setting socket hints.", var3);
         }
      }
   }

   @Override
   public boolean isConnected() {
      return this.socket != null ? this.socket.isConnected() : false;
   }

   @Override
   public InputStream getInputStream() {
      try {
         return this.socket.getInputStream();
      } catch (Exception var2) {
         throw new GdxRuntimeException("Error getting input stream from socket.", var2);
      }
   }

   @Override
   public OutputStream getOutputStream() {
      try {
         return this.socket.getOutputStream();
      } catch (Exception var2) {
         throw new GdxRuntimeException("Error getting output stream from socket.", var2);
      }
   }

   @Override
   public String getRemoteAddress() {
      return this.socket.getRemoteSocketAddress().toString();
   }

   @Override
   public void dispose() {
      if (this.socket != null) {
         try {
            this.socket.close();
            this.socket = null;
         } catch (Exception var2) {
            throw new GdxRuntimeException("Error closing socket.", var2);
         }
      }
   }
}
