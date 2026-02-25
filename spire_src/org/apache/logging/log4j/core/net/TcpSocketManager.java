package org.apache.logging.log4j.core.net;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.appender.AppenderLoggingException;
import org.apache.logging.log4j.core.appender.ManagerFactory;
import org.apache.logging.log4j.core.appender.OutputStreamManager;
import org.apache.logging.log4j.core.util.Closer;
import org.apache.logging.log4j.core.util.Log4jThread;
import org.apache.logging.log4j.core.util.NullOutputStream;
import org.apache.logging.log4j.util.Strings;

public class TcpSocketManager extends AbstractSocketManager {
   public static final int DEFAULT_RECONNECTION_DELAY_MILLIS = 30000;
   private static final int DEFAULT_PORT = 4560;
   private static final TcpSocketManager.TcpSocketManagerFactory<TcpSocketManager, TcpSocketManager.FactoryData> FACTORY = new TcpSocketManager.TcpSocketManagerFactory<>();
   private final int reconnectionDelayMillis;
   private TcpSocketManager.Reconnector reconnector;
   private Socket socket;
   private final SocketOptions socketOptions;
   private final boolean retry;
   private final boolean immediateFail;
   private final int connectTimeoutMillis;

   @Deprecated
   public TcpSocketManager(
      final String name,
      final OutputStream os,
      final Socket socket,
      final InetAddress inetAddress,
      final String host,
      final int port,
      final int connectTimeoutMillis,
      final int reconnectionDelayMillis,
      final boolean immediateFail,
      final Layout<? extends Serializable> layout,
      final int bufferSize
   ) {
      this(name, os, socket, inetAddress, host, port, connectTimeoutMillis, reconnectionDelayMillis, immediateFail, layout, bufferSize, null);
   }

   public TcpSocketManager(
      final String name,
      final OutputStream os,
      final Socket socket,
      final InetAddress inetAddress,
      final String host,
      final int port,
      final int connectTimeoutMillis,
      final int reconnectionDelayMillis,
      final boolean immediateFail,
      final Layout<? extends Serializable> layout,
      final int bufferSize,
      final SocketOptions socketOptions
   ) {
      super(name, os, inetAddress, host, port, layout, true, bufferSize);
      this.connectTimeoutMillis = connectTimeoutMillis;
      this.reconnectionDelayMillis = reconnectionDelayMillis;
      this.socket = socket;
      this.immediateFail = immediateFail;
      this.retry = reconnectionDelayMillis > 0;
      if (socket == null) {
         this.reconnector = this.createReconnector();
         this.reconnector.start();
      }

      this.socketOptions = socketOptions;
   }

   @Deprecated
   public static TcpSocketManager getSocketManager(
      final String host,
      final int port,
      final int connectTimeoutMillis,
      final int reconnectDelayMillis,
      final boolean immediateFail,
      final Layout<? extends Serializable> layout,
      final int bufferSize
   ) {
      return getSocketManager(host, port, connectTimeoutMillis, reconnectDelayMillis, immediateFail, layout, bufferSize, null);
   }

   public static TcpSocketManager getSocketManager(
      final String host,
      int port,
      final int connectTimeoutMillis,
      int reconnectDelayMillis,
      final boolean immediateFail,
      final Layout<? extends Serializable> layout,
      final int bufferSize,
      final SocketOptions socketOptions
   ) {
      if (Strings.isEmpty(host)) {
         throw new IllegalArgumentException("A host name is required");
      } else {
         if (port <= 0) {
            port = 4560;
         }

         if (reconnectDelayMillis == 0) {
            reconnectDelayMillis = 30000;
         }

         return (TcpSocketManager)getManager(
            "TCP:" + host + ':' + port,
            new TcpSocketManager.FactoryData(host, port, connectTimeoutMillis, reconnectDelayMillis, immediateFail, layout, bufferSize, socketOptions),
            FACTORY
         );
      }
   }

   @Override
   protected void write(final byte[] bytes, final int offset, final int length, final boolean immediateFlush) {
      if (this.socket == null) {
         if (this.reconnector != null && !this.immediateFail) {
            this.reconnector.latch();
         }

         if (this.socket == null) {
            throw new AppenderLoggingException("Error writing to " + this.getName() + ": socket not available");
         }
      }

      synchronized (this) {
         try {
            this.writeAndFlush(bytes, offset, length, immediateFlush);
         } catch (IOException var12) {
            String config = this.inetAddress + ":" + this.port;
            if (this.retry && this.reconnector == null) {
               this.reconnector = this.createReconnector();

               try {
                  this.reconnector.reconnect();
               } catch (IOException var11) {
                  LOGGER.debug(
                     "Cannot reestablish socket connection to {}: {}; starting reconnector thread {}",
                     config,
                     var11.getLocalizedMessage(),
                     this.reconnector.getName(),
                     var11
                  );
                  this.reconnector.start();
                  throw new AppenderLoggingException(String.format("Error sending to %s for %s", this.getName(), config), var12);
               }

               try {
                  this.writeAndFlush(bytes, offset, length, immediateFlush);
               } catch (IOException var10) {
                  throw new AppenderLoggingException(String.format("Error writing to %s after reestablishing connection for %s", this.getName(), config), var12);
               }

               return;
            }

            String message = String.format("Error writing to %s for connection %s", this.getName(), config);
            throw new AppenderLoggingException(message, var12);
         }
      }
   }

   private void writeAndFlush(final byte[] bytes, final int offset, final int length, final boolean immediateFlush) throws IOException {
      OutputStream outputStream = this.getOutputStream();
      outputStream.write(bytes, offset, length);
      if (immediateFlush) {
         outputStream.flush();
      }
   }

   @Override
   protected synchronized boolean closeOutputStream() {
      boolean closed = super.closeOutputStream();
      if (this.reconnector != null) {
         this.reconnector.shutdown();
         this.reconnector.interrupt();
         this.reconnector = null;
      }

      Socket oldSocket = this.socket;
      this.socket = null;
      if (oldSocket != null) {
         try {
            oldSocket.close();
         } catch (IOException var4) {
            LOGGER.error("Could not close socket {}", this.socket);
            return false;
         }
      }

      return closed;
   }

   public int getConnectTimeoutMillis() {
      return this.connectTimeoutMillis;
   }

   @Override
   public Map<String, String> getContentFormat() {
      Map<String, String> result = new HashMap<>(super.getContentFormat());
      result.put("protocol", "tcp");
      result.put("direction", "out");
      return result;
   }

   private TcpSocketManager.Reconnector createReconnector() {
      TcpSocketManager.Reconnector recon = new TcpSocketManager.Reconnector(this);
      recon.setDaemon(true);
      recon.setPriority(1);
      return recon;
   }

   protected Socket createSocket(final InetSocketAddress socketAddress) throws IOException {
      return createSocket(socketAddress, this.socketOptions, this.connectTimeoutMillis);
   }

   protected static Socket createSocket(final InetSocketAddress socketAddress, final SocketOptions socketOptions, final int connectTimeoutMillis) throws IOException {
      LOGGER.debug("Creating socket {}", socketAddress.toString());
      Socket newSocket = new Socket();
      if (socketOptions != null) {
         socketOptions.apply(newSocket);
      }

      newSocket.connect(socketAddress, connectTimeoutMillis);
      if (socketOptions != null) {
         socketOptions.apply(newSocket);
      }

      return newSocket;
   }

   public static void setHostResolver(TcpSocketManager.HostResolver resolver) {
      TcpSocketManager.TcpSocketManagerFactory.resolver = resolver;
   }

   public SocketOptions getSocketOptions() {
      return this.socketOptions;
   }

   public Socket getSocket() {
      return this.socket;
   }

   public int getReconnectionDelayMillis() {
      return this.reconnectionDelayMillis;
   }

   @Override
   public String toString() {
      return "TcpSocketManager [reconnectionDelayMillis="
         + this.reconnectionDelayMillis
         + ", reconnector="
         + this.reconnector
         + ", socket="
         + this.socket
         + ", socketOptions="
         + this.socketOptions
         + ", retry="
         + this.retry
         + ", immediateFail="
         + this.immediateFail
         + ", connectTimeoutMillis="
         + this.connectTimeoutMillis
         + ", inetAddress="
         + this.inetAddress
         + ", host="
         + this.host
         + ", port="
         + this.port
         + ", layout="
         + this.layout
         + ", byteBuffer="
         + this.byteBuffer
         + ", count="
         + this.count
         + "]";
   }

   static class FactoryData {
      protected final String host;
      protected final int port;
      protected final int connectTimeoutMillis;
      protected final int reconnectDelayMillis;
      protected final boolean immediateFail;
      protected final Layout<? extends Serializable> layout;
      protected final int bufferSize;
      protected final SocketOptions socketOptions;

      public FactoryData(
         final String host,
         final int port,
         final int connectTimeoutMillis,
         final int reconnectDelayMillis,
         final boolean immediateFail,
         final Layout<? extends Serializable> layout,
         final int bufferSize,
         final SocketOptions socketOptions
      ) {
         this.host = host;
         this.port = port;
         this.connectTimeoutMillis = connectTimeoutMillis;
         this.reconnectDelayMillis = reconnectDelayMillis;
         this.immediateFail = immediateFail;
         this.layout = layout;
         this.bufferSize = bufferSize;
         this.socketOptions = socketOptions;
      }

      @Override
      public String toString() {
         return "FactoryData [host="
            + this.host
            + ", port="
            + this.port
            + ", connectTimeoutMillis="
            + this.connectTimeoutMillis
            + ", reconnectDelayMillis="
            + this.reconnectDelayMillis
            + ", immediateFail="
            + this.immediateFail
            + ", layout="
            + this.layout
            + ", bufferSize="
            + this.bufferSize
            + ", socketOptions="
            + this.socketOptions
            + "]";
      }
   }

   public static class HostResolver {
      public List<InetSocketAddress> resolveHost(String host, int port) throws UnknownHostException {
         InetAddress[] addresses = InetAddress.getAllByName(host);
         List<InetSocketAddress> socketAddresses = new ArrayList<>(addresses.length);

         for (InetAddress address : addresses) {
            socketAddresses.add(new InetSocketAddress(address, port));
         }

         return socketAddresses;
      }
   }

   private class Reconnector extends Log4jThread {
      private final CountDownLatch latch = new CountDownLatch(1);
      private boolean shutdown = false;
      private final Object owner;

      public Reconnector(final OutputStreamManager owner) {
         super("TcpSocketManager-Reconnector");
         this.owner = owner;
      }

      public void latch() {
         try {
            this.latch.await();
         } catch (InterruptedException var2) {
         }
      }

      public void shutdown() {
         this.shutdown = true;
      }

      @Override
      public void run() {
         while (!this.shutdown) {
            try {
               sleep(TcpSocketManager.this.reconnectionDelayMillis);
               this.reconnect();
            } catch (InterruptedException var7) {
               TcpSocketManager.LOGGER.debug("Reconnection interrupted.");
            } catch (ConnectException var8) {
               TcpSocketManager.LOGGER.debug("{}:{} refused connection", TcpSocketManager.this.host, TcpSocketManager.this.port);
            } catch (IOException var9) {
               TcpSocketManager.LOGGER.debug("Unable to reconnect to {}:{}", TcpSocketManager.this.host, TcpSocketManager.this.port);
            } finally {
               this.latch.countDown();
            }
         }
      }

      void reconnect() throws IOException {
         TcpSocketManager.FACTORY;
         List<InetSocketAddress> socketAddresses = TcpSocketManager.TcpSocketManagerFactory.resolver
            .resolveHost(TcpSocketManager.this.host, TcpSocketManager.this.port);
         if (socketAddresses.size() == 1) {
            TcpSocketManager.LOGGER.debug("Reconnecting " + socketAddresses.get(0));
            this.connect(socketAddresses.get(0));
         } else {
            IOException ioe = null;

            for (InetSocketAddress socketAddress : socketAddresses) {
               try {
                  TcpSocketManager.LOGGER.debug("Reconnecting " + socketAddress);
                  this.connect(socketAddress);
                  return;
               } catch (IOException var6) {
                  ioe = var6;
               }
            }

            throw ioe;
         }
      }

      private void connect(InetSocketAddress socketAddress) throws IOException {
         Socket sock = TcpSocketManager.this.createSocket(socketAddress);
         OutputStream newOS = sock.getOutputStream();
         InetAddress prev = TcpSocketManager.this.socket != null ? TcpSocketManager.this.socket.getInetAddress() : null;
         synchronized (this.owner) {
            Closer.closeSilently(TcpSocketManager.this.getOutputStream());
            TcpSocketManager.this.setOutputStream(newOS);
            TcpSocketManager.this.socket = sock;
            TcpSocketManager.this.reconnector = null;
            this.shutdown = true;
         }

         String type = prev != null && prev.getHostAddress().equals(socketAddress.getAddress().getHostAddress()) ? "reestablished" : "established";
         TcpSocketManager.LOGGER
            .debug("Connection to {}:{} {}: {}", TcpSocketManager.this.host, TcpSocketManager.this.port, type, TcpSocketManager.this.socket);
      }

      @Override
      public String toString() {
         return "Reconnector [latch=" + this.latch + ", shutdown=" + this.shutdown + "]";
      }
   }

   protected static class TcpSocketManagerFactory<M extends TcpSocketManager, T extends TcpSocketManager.FactoryData> implements ManagerFactory<M, T> {
      static TcpSocketManager.HostResolver resolver = new TcpSocketManager.HostResolver();

      public M createManager(final String name, final T data) {
         InetAddress inetAddress;
         try {
            inetAddress = InetAddress.getByName(data.host);
         } catch (UnknownHostException var7) {
            TcpSocketManager.LOGGER.error("Could not find address of {}: {}", data.host, var7, var7);
            return null;
         }

         Socket socket = null;

         try {
            socket = this.createSocket(data);
            OutputStream os = socket.getOutputStream();
            return this.createManager(name, os, socket, inetAddress, data);
         } catch (IOException var8) {
            TcpSocketManager.LOGGER.error("TcpSocketManager ({}) caught exception and will continue:", name, var8);
            OutputStream osx = NullOutputStream.getInstance();
            if (data.reconnectDelayMillis == 0) {
               Closer.closeSilently(socket);
               return null;
            } else {
               return this.createManager(name, osx, null, inetAddress, data);
            }
         }
      }

      M createManager(final String name, final OutputStream os, final Socket socket, final InetAddress inetAddress, final T data) {
         return (M)(new TcpSocketManager(
            name,
            os,
            socket,
            inetAddress,
            data.host,
            data.port,
            data.connectTimeoutMillis,
            data.reconnectDelayMillis,
            data.immediateFail,
            data.layout,
            data.bufferSize,
            data.socketOptions
         ));
      }

      Socket createSocket(final T data) throws IOException {
         List<InetSocketAddress> socketAddresses = resolver.resolveHost(data.host, data.port);
         IOException ioe = null;

         for (InetSocketAddress socketAddress : socketAddresses) {
            try {
               return TcpSocketManager.createSocket(socketAddress, data.socketOptions, data.connectTimeoutMillis);
            } catch (IOException var7) {
               ioe = var7;
            }
         }

         throw new IOException(this.errorMessage(data, socketAddresses), ioe);
      }

      protected String errorMessage(final T data, List<InetSocketAddress> socketAddresses) {
         StringBuilder sb = new StringBuilder("Unable to create socket for ");
         sb.append(data.host).append(" at port ").append(data.port);
         if (socketAddresses.size() == 1) {
            if (!socketAddresses.get(0).getAddress().getHostAddress().equals(data.host)) {
               sb.append(" using ip address ").append(socketAddresses.get(0).getAddress().getHostAddress());
               sb.append(" and port ").append(socketAddresses.get(0).getPort());
            }
         } else {
            sb.append(" using ip addresses and ports ");

            for (int i = 0; i < socketAddresses.size(); i++) {
               if (i > 0) {
                  sb.append(", ");
                  sb.append(socketAddresses.get(i).getAddress().getHostAddress());
                  sb.append(":").append(socketAddresses.get(i).getPort());
               }
            }
         }

         return sb.toString();
      }
   }
}
