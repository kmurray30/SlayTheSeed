package org.apache.commons.net.ftp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.Inet6Address;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.net.util.Base64;

public class FTPHTTPClient extends FTPClient {
   private final String proxyHost;
   private final int proxyPort;
   private final String proxyUsername;
   private final String proxyPassword;
   private static final byte[] CRLF = new byte[]{13, 10};
   private final Base64 base64 = new Base64();
   private String tunnelHost;

   public FTPHTTPClient(String proxyHost, int proxyPort, String proxyUser, String proxyPass) {
      this.proxyHost = proxyHost;
      this.proxyPort = proxyPort;
      this.proxyUsername = proxyUser;
      this.proxyPassword = proxyPass;
      this.tunnelHost = null;
   }

   public FTPHTTPClient(String proxyHost, int proxyPort) {
      this(proxyHost, proxyPort, null, null);
   }

   @Deprecated
   @Override
   protected Socket _openDataConnection_(int command, String arg) throws IOException {
      return super._openDataConnection_(command, arg);
   }

   @Override
   protected Socket _openDataConnection_(String command, String arg) throws IOException {
      if (this.getDataConnectionMode() != 2) {
         throw new IllegalStateException("Only passive connection mode supported");
      } else {
         boolean isInet6Address = this.getRemoteAddress() instanceof Inet6Address;
         String passiveHost = null;
         boolean attemptEPSV = this.isUseEPSVwithIPv4() || isInet6Address;
         if (attemptEPSV && this.epsv() == 229) {
            this._parseExtendedPassiveModeReply(this._replyLines.get(0));
            passiveHost = this.tunnelHost;
         } else {
            if (isInet6Address) {
               return null;
            }

            if (this.pasv() != 227) {
               return null;
            }

            this._parsePassiveModeReply(this._replyLines.get(0));
            passiveHost = this.getPassiveHost();
         }

         Socket socket = this._socketFactory_.createSocket(this.proxyHost, this.proxyPort);
         InputStream is = socket.getInputStream();
         OutputStream os = socket.getOutputStream();
         this.tunnelHandshake(passiveHost, this.getPassivePort(), is, os);
         if (this.getRestartOffset() > 0L && !this.restart(this.getRestartOffset())) {
            socket.close();
            return null;
         } else if (!FTPReply.isPositivePreliminary(this.sendCommand(command, arg))) {
            socket.close();
            return null;
         } else {
            return socket;
         }
      }
   }

   @Override
   public void connect(String host, int port) throws SocketException, IOException {
      this._socket_ = this._socketFactory_.createSocket(this.proxyHost, this.proxyPort);
      this._input_ = this._socket_.getInputStream();
      this._output_ = this._socket_.getOutputStream();

      Reader socketIsReader;
      try {
         socketIsReader = this.tunnelHandshake(host, port, this._input_, this._output_);
      } catch (Exception var6) {
         IOException ioe = new IOException("Could not connect to " + host + " using port " + port);
         ioe.initCause(var6);
         throw ioe;
      }

      super._connectAction_(socketIsReader);
   }

   private BufferedReader tunnelHandshake(String host, int port, InputStream input, OutputStream output) throws IOException, UnsupportedEncodingException {
      String connectString = "CONNECT " + host + ":" + port + " HTTP/1.1";
      String hostString = "Host: " + host + ":" + port;
      this.tunnelHost = host;
      output.write(connectString.getBytes("UTF-8"));
      output.write(CRLF);
      output.write(hostString.getBytes("UTF-8"));
      output.write(CRLF);
      if (this.proxyUsername != null && this.proxyPassword != null) {
         String auth = this.proxyUsername + ":" + this.proxyPassword;
         String header = "Proxy-Authorization: Basic " + this.base64.encodeToString(auth.getBytes("UTF-8"));
         output.write(header.getBytes("UTF-8"));
      }

      output.write(CRLF);
      List<String> response = new ArrayList<>();
      BufferedReader reader = new BufferedReader(new InputStreamReader(input, this.getCharset()));

      for (String line = reader.readLine(); line != null && line.length() > 0; line = reader.readLine()) {
         response.add(line);
      }

      int size = response.size();
      if (size == 0) {
         throw new IOException("No response from proxy");
      } else {
         String code = null;
         String resp = response.get(0);
         if (resp.startsWith("HTTP/") && resp.length() >= 12) {
            code = resp.substring(9, 12);
            if ("200".equals(code)) {
               return reader;
            } else {
               StringBuilder msg = new StringBuilder();
               msg.append("HTTPTunnelConnector: connection failed\r\n");
               msg.append("Response received from the proxy:\r\n");

               for (String line : response) {
                  msg.append(line);
                  msg.append("\r\n");
               }

               throw new IOException(msg.toString());
            }
         } else {
            throw new IOException("Invalid response from proxy: " + resp);
         }
      }
   }
}
