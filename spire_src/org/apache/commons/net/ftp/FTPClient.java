package org.apache.commons.net.ftp;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.net.MalformedServerReplyException;
import org.apache.commons.net.ftp.parser.DefaultFTPFileEntryParserFactory;
import org.apache.commons.net.ftp.parser.FTPFileEntryParserFactory;
import org.apache.commons.net.ftp.parser.MLSxEntryParser;
import org.apache.commons.net.io.CRLFLineReader;
import org.apache.commons.net.io.CopyStreamAdapter;
import org.apache.commons.net.io.CopyStreamEvent;
import org.apache.commons.net.io.CopyStreamListener;
import org.apache.commons.net.io.FromNetASCIIInputStream;
import org.apache.commons.net.io.SocketInputStream;
import org.apache.commons.net.io.SocketOutputStream;
import org.apache.commons.net.io.ToNetASCIIOutputStream;
import org.apache.commons.net.io.Util;

public class FTPClient extends FTP implements Configurable {
   public static final String FTP_SYSTEM_TYPE = "org.apache.commons.net.ftp.systemType";
   public static final String FTP_SYSTEM_TYPE_DEFAULT = "org.apache.commons.net.ftp.systemType.default";
   public static final String SYSTEM_TYPE_PROPERTIES = "/systemType.properties";
   public static final int ACTIVE_LOCAL_DATA_CONNECTION_MODE = 0;
   public static final int ACTIVE_REMOTE_DATA_CONNECTION_MODE = 1;
   public static final int PASSIVE_LOCAL_DATA_CONNECTION_MODE = 2;
   public static final int PASSIVE_REMOTE_DATA_CONNECTION_MODE = 3;
   private int __dataConnectionMode;
   private int __dataTimeout;
   private int __passivePort;
   private String __passiveHost;
   private final Random __random;
   private int __activeMinPort;
   private int __activeMaxPort;
   private InetAddress __activeExternalHost;
   private InetAddress __reportActiveExternalHost;
   private InetAddress __passiveLocalHost;
   private int __fileType;
   private int __fileFormat;
   private int __fileStructure;
   private int __fileTransferMode;
   private boolean __remoteVerificationEnabled;
   private long __restartOffset;
   private FTPFileEntryParserFactory __parserFactory;
   private int __bufferSize;
   private int __sendDataSocketBufferSize;
   private int __receiveDataSocketBufferSize;
   private boolean __listHiddenFiles;
   private boolean __useEPSVwithIPv4;
   private String __systemName;
   private FTPFileEntryParser __entryParser;
   private String __entryParserKey;
   private FTPClientConfig __configuration;
   private CopyStreamListener __copyStreamListener;
   private long __controlKeepAliveTimeout;
   private int __controlKeepAliveReplyTimeout = 1000;
   private boolean __passiveNatWorkaround = true;
   private static final Pattern __PARMS_PAT = Pattern.compile("(\\d{1,3},\\d{1,3},\\d{1,3},\\d{1,3}),(\\d{1,3}),(\\d{1,3})");
   private boolean __autodetectEncoding = false;
   private HashMap<String, Set<String>> __featuresMap;

   private static Properties getOverrideProperties() {
      return FTPClient.PropertiesSingleton.PROPERTIES;
   }

   public FTPClient() {
      this.__initDefaults();
      this.__dataTimeout = -1;
      this.__remoteVerificationEnabled = true;
      this.__parserFactory = new DefaultFTPFileEntryParserFactory();
      this.__configuration = null;
      this.__listHiddenFiles = false;
      this.__useEPSVwithIPv4 = false;
      this.__random = new Random();
      this.__passiveLocalHost = null;
   }

   private void __initDefaults() {
      this.__dataConnectionMode = 0;
      this.__passiveHost = null;
      this.__passivePort = -1;
      this.__activeExternalHost = null;
      this.__reportActiveExternalHost = null;
      this.__activeMinPort = 0;
      this.__activeMaxPort = 0;
      this.__fileType = 0;
      this.__fileStructure = 7;
      this.__fileFormat = 4;
      this.__fileTransferMode = 10;
      this.__restartOffset = 0L;
      this.__systemName = null;
      this.__entryParser = null;
      this.__entryParserKey = "";
      this.__featuresMap = null;
   }

   static String __parsePathname(String reply) {
      String param = reply.substring(4);
      if (param.startsWith("\"")) {
         StringBuilder sb = new StringBuilder();
         boolean quoteSeen = false;

         for (int i = 1; i < param.length(); i++) {
            char ch = param.charAt(i);
            if (ch == '"') {
               if (quoteSeen) {
                  sb.append(ch);
                  quoteSeen = false;
               } else {
                  quoteSeen = true;
               }
            } else {
               if (quoteSeen) {
                  return sb.toString();
               }

               sb.append(ch);
            }
         }

         if (quoteSeen) {
            return sb.toString();
         }
      }

      return param;
   }

   protected void _parsePassiveModeReply(String reply) throws MalformedServerReplyException {
      Matcher m = __PARMS_PAT.matcher(reply);
      if (!m.find()) {
         throw new MalformedServerReplyException("Could not parse passive host information.\nServer Reply: " + reply);
      } else {
         this.__passiveHost = m.group(1).replace(',', '.');

         try {
            int oct1 = Integer.parseInt(m.group(2));
            int oct2 = Integer.parseInt(m.group(3));
            this.__passivePort = oct1 << 8 | oct2;
         } catch (NumberFormatException var7) {
            throw new MalformedServerReplyException("Could not parse passive port information.\nServer Reply: " + reply);
         }

         if (this.__passiveNatWorkaround) {
            try {
               InetAddress host = InetAddress.getByName(this.__passiveHost);
               if (host.isSiteLocalAddress()) {
                  InetAddress remote = this.getRemoteAddress();
                  if (!remote.isSiteLocalAddress()) {
                     String hostAddress = remote.getHostAddress();
                     this.fireReplyReceived(0, "[Replacing site local address " + this.__passiveHost + " with " + hostAddress + "]\n");
                     this.__passiveHost = hostAddress;
                  }
               }
            } catch (UnknownHostException var6) {
               throw new MalformedServerReplyException("Could not parse passive host information.\nServer Reply: " + reply);
            }
         }
      }
   }

   protected void _parseExtendedPassiveModeReply(String reply) throws MalformedServerReplyException {
      reply = reply.substring(reply.indexOf(40) + 1, reply.indexOf(41)).trim();
      char delim1 = reply.charAt(0);
      char delim2 = reply.charAt(1);
      char delim3 = reply.charAt(2);
      char delim4 = reply.charAt(reply.length() - 1);
      if (delim1 == delim2 && delim2 == delim3 && delim3 == delim4) {
         int port;
         try {
            port = Integer.parseInt(reply.substring(3, reply.length() - 1));
         } catch (NumberFormatException var8) {
            throw new MalformedServerReplyException("Could not parse extended passive host information.\nServer Reply: " + reply);
         }

         this.__passiveHost = this.getRemoteAddress().getHostAddress();
         this.__passivePort = port;
      } else {
         throw new MalformedServerReplyException("Could not parse extended passive host information.\nServer Reply: " + reply);
      }
   }

   private boolean __storeFile(FTPCmd command, String remote, InputStream local) throws IOException {
      return this._storeFile(command.getCommand(), remote, local);
   }

   protected boolean _storeFile(String command, String remote, InputStream local) throws IOException {
      Socket socket = this._openDataConnection_(command, remote);
      if (socket == null) {
         return false;
      } else {
         OutputStream output;
         if (this.__fileType == 0) {
            output = new ToNetASCIIOutputStream(this.getBufferedOutputStream(socket.getOutputStream()));
         } else {
            output = this.getBufferedOutputStream(socket.getOutputStream());
         }

         FTPClient.CSL csl = null;
         if (this.__controlKeepAliveTimeout > 0L) {
            csl = new FTPClient.CSL(this, this.__controlKeepAliveTimeout, this.__controlKeepAliveReplyTimeout);
         }

         try {
            Util.copyStream(local, output, this.getBufferSize(), -1L, this.__mergeListeners(csl), false);
         } catch (IOException var8) {
            Util.closeQuietly(socket);
            if (csl != null) {
               csl.cleanUp();
            }

            throw var8;
         }

         output.close();
         socket.close();
         if (csl != null) {
            csl.cleanUp();
         }

         return this.completePendingCommand();
      }
   }

   private OutputStream __storeFileStream(FTPCmd command, String remote) throws IOException {
      return this._storeFileStream(command.getCommand(), remote);
   }

   protected OutputStream _storeFileStream(String command, String remote) throws IOException {
      Socket socket = this._openDataConnection_(command, remote);
      if (socket == null) {
         return null;
      } else {
         OutputStream output;
         if (this.__fileType == 0) {
            output = new ToNetASCIIOutputStream(this.getBufferedOutputStream(socket.getOutputStream()));
         } else {
            output = socket.getOutputStream();
         }

         return new SocketOutputStream(socket, output);
      }
   }

   @Deprecated
   protected Socket _openDataConnection_(int command, String arg) throws IOException {
      return this._openDataConnection_(FTPCommand.getCommand(command), arg);
   }

   protected Socket _openDataConnection_(FTPCmd command, String arg) throws IOException {
      return this._openDataConnection_(command.getCommand(), arg);
   }

   protected Socket _openDataConnection_(String command, String arg) throws IOException {
      if (this.__dataConnectionMode != 0 && this.__dataConnectionMode != 2) {
         return null;
      } else {
         Socket socket;
         boolean isInet6Address = this.getRemoteAddress() instanceof Inet6Address;
         label207:
         if (this.__dataConnectionMode == 0) {
            ServerSocket server = this._serverSocketFactory_.createServerSocket(this.getActivePort(), 1, this.getHostAddress());

            Object var11;
            try {
               if (isInet6Address) {
                  if (!FTPReply.isPositiveCompletion(this.eprt(this.getReportHostAddress(), server.getLocalPort()))) {
                     return null;
                  }
               } else if (!FTPReply.isPositiveCompletion(this.port(this.getReportHostAddress(), server.getLocalPort()))) {
                  return null;
               }

               if (this.__restartOffset <= 0L || this.restart(this.__restartOffset)) {
                  if (!FTPReply.isPositivePreliminary(this.sendCommand(command, arg))) {
                     return null;
                  }

                  if (this.__dataTimeout >= 0) {
                     server.setSoTimeout(this.__dataTimeout);
                  }

                  socket = server.accept();
                  if (this.__dataTimeout >= 0) {
                     socket.setSoTimeout(this.__dataTimeout);
                  }

                  if (this.__receiveDataSocketBufferSize > 0) {
                     socket.setReceiveBufferSize(this.__receiveDataSocketBufferSize);
                  }

                  if (this.__sendDataSocketBufferSize > 0) {
                     socket.setSendBufferSize(this.__sendDataSocketBufferSize);
                  }
                  break label207;
               }

               var11 = null;
            } finally {
               server.close();
            }

            return (Socket)var11;
         } else {
            boolean attemptEPSV = this.isUseEPSVwithIPv4() || isInet6Address;
            if (attemptEPSV && this.epsv() == 229) {
               this._parseExtendedPassiveModeReply(this._replyLines.get(0));
            } else {
               if (isInet6Address) {
                  return null;
               }

               if (this.pasv() != 227) {
                  return null;
               }

               this._parsePassiveModeReply(this._replyLines.get(0));
            }

            socket = this._socketFactory_.createSocket();
            if (this.__receiveDataSocketBufferSize > 0) {
               socket.setReceiveBufferSize(this.__receiveDataSocketBufferSize);
            }

            if (this.__sendDataSocketBufferSize > 0) {
               socket.setSendBufferSize(this.__sendDataSocketBufferSize);
            }

            if (this.__passiveLocalHost != null) {
               socket.bind(new InetSocketAddress(this.__passiveLocalHost, 0));
            }

            if (this.__dataTimeout >= 0) {
               socket.setSoTimeout(this.__dataTimeout);
            }

            socket.connect(new InetSocketAddress(this.__passiveHost, this.__passivePort), this.connectTimeout);
            if (this.__restartOffset > 0L && !this.restart(this.__restartOffset)) {
               socket.close();
               return null;
            }

            if (!FTPReply.isPositivePreliminary(this.sendCommand(command, arg))) {
               socket.close();
               return null;
            }
         }

         if (this.__remoteVerificationEnabled && !this.verifyRemote(socket)) {
            socket.close();
            throw new IOException(
               "Host attempting data connection "
                  + socket.getInetAddress().getHostAddress()
                  + " is not same as server "
                  + this.getRemoteAddress().getHostAddress()
            );
         } else {
            return socket;
         }
      }
   }

   @Override
   protected void _connectAction_() throws IOException {
      this._connectAction_(null);
   }

   @Override
   protected void _connectAction_(Reader socketIsReader) throws IOException {
      super._connectAction_(socketIsReader);
      this.__initDefaults();
      if (this.__autodetectEncoding) {
         ArrayList<String> oldReplyLines = new ArrayList<>(this._replyLines);
         int oldReplyCode = this._replyCode;
         if (this.hasFeature("UTF8") || this.hasFeature("UTF-8")) {
            this.setControlEncoding("UTF-8");
            this._controlInput_ = new CRLFLineReader(new InputStreamReader(this._input_, this.getControlEncoding()));
            this._controlOutput_ = new BufferedWriter(new OutputStreamWriter(this._output_, this.getControlEncoding()));
         }

         this._replyLines.clear();
         this._replyLines.addAll(oldReplyLines);
         this._replyCode = oldReplyCode;
         this._newReplyString = true;
      }
   }

   public void setDataTimeout(int timeout) {
      this.__dataTimeout = timeout;
   }

   public void setParserFactory(FTPFileEntryParserFactory parserFactory) {
      this.__parserFactory = parserFactory;
   }

   @Override
   public void disconnect() throws IOException {
      super.disconnect();
      this.__initDefaults();
   }

   public void setRemoteVerificationEnabled(boolean enable) {
      this.__remoteVerificationEnabled = enable;
   }

   public boolean isRemoteVerificationEnabled() {
      return this.__remoteVerificationEnabled;
   }

   public boolean login(String username, String password) throws IOException {
      this.user(username);
      if (FTPReply.isPositiveCompletion(this._replyCode)) {
         return true;
      } else {
         return !FTPReply.isPositiveIntermediate(this._replyCode) ? false : FTPReply.isPositiveCompletion(this.pass(password));
      }
   }

   public boolean login(String username, String password, String account) throws IOException {
      this.user(username);
      if (FTPReply.isPositiveCompletion(this._replyCode)) {
         return true;
      } else if (!FTPReply.isPositiveIntermediate(this._replyCode)) {
         return false;
      } else {
         this.pass(password);
         if (FTPReply.isPositiveCompletion(this._replyCode)) {
            return true;
         } else {
            return !FTPReply.isPositiveIntermediate(this._replyCode) ? false : FTPReply.isPositiveCompletion(this.acct(account));
         }
      }
   }

   public boolean logout() throws IOException {
      return FTPReply.isPositiveCompletion(this.quit());
   }

   public boolean changeWorkingDirectory(String pathname) throws IOException {
      return FTPReply.isPositiveCompletion(this.cwd(pathname));
   }

   public boolean changeToParentDirectory() throws IOException {
      return FTPReply.isPositiveCompletion(this.cdup());
   }

   public boolean structureMount(String pathname) throws IOException {
      return FTPReply.isPositiveCompletion(this.smnt(pathname));
   }

   public boolean reinitialize() throws IOException {
      this.rein();
      if (!FTPReply.isPositiveCompletion(this._replyCode)
         && (!FTPReply.isPositivePreliminary(this._replyCode) || !FTPReply.isPositiveCompletion(this.getReply()))) {
         return false;
      } else {
         this.__initDefaults();
         return true;
      }
   }

   public void enterLocalActiveMode() {
      this.__dataConnectionMode = 0;
      this.__passiveHost = null;
      this.__passivePort = -1;
   }

   public void enterLocalPassiveMode() {
      this.__dataConnectionMode = 2;
      this.__passiveHost = null;
      this.__passivePort = -1;
   }

   public boolean enterRemoteActiveMode(InetAddress host, int port) throws IOException {
      if (FTPReply.isPositiveCompletion(this.port(host, port))) {
         this.__dataConnectionMode = 1;
         this.__passiveHost = null;
         this.__passivePort = -1;
         return true;
      } else {
         return false;
      }
   }

   public boolean enterRemotePassiveMode() throws IOException {
      if (this.pasv() != 227) {
         return false;
      } else {
         this.__dataConnectionMode = 3;
         this._parsePassiveModeReply(this._replyLines.get(0));
         return true;
      }
   }

   public String getPassiveHost() {
      return this.__passiveHost;
   }

   public int getPassivePort() {
      return this.__passivePort;
   }

   public int getDataConnectionMode() {
      return this.__dataConnectionMode;
   }

   private int getActivePort() {
      if (this.__activeMinPort <= 0 || this.__activeMaxPort < this.__activeMinPort) {
         return 0;
      } else {
         return this.__activeMaxPort == this.__activeMinPort
            ? this.__activeMaxPort
            : this.__random.nextInt(this.__activeMaxPort - this.__activeMinPort + 1) + this.__activeMinPort;
      }
   }

   private InetAddress getHostAddress() {
      return this.__activeExternalHost != null ? this.__activeExternalHost : this.getLocalAddress();
   }

   private InetAddress getReportHostAddress() {
      return this.__reportActiveExternalHost != null ? this.__reportActiveExternalHost : this.getHostAddress();
   }

   public void setActivePortRange(int minPort, int maxPort) {
      this.__activeMinPort = minPort;
      this.__activeMaxPort = maxPort;
   }

   public void setActiveExternalIPAddress(String ipAddress) throws UnknownHostException {
      this.__activeExternalHost = InetAddress.getByName(ipAddress);
   }

   public void setPassiveLocalIPAddress(String ipAddress) throws UnknownHostException {
      this.__passiveLocalHost = InetAddress.getByName(ipAddress);
   }

   public void setPassiveLocalIPAddress(InetAddress inetAddress) {
      this.__passiveLocalHost = inetAddress;
   }

   public InetAddress getPassiveLocalIPAddress() {
      return this.__passiveLocalHost;
   }

   public void setReportActiveExternalIPAddress(String ipAddress) throws UnknownHostException {
      this.__reportActiveExternalHost = InetAddress.getByName(ipAddress);
   }

   public boolean setFileType(int fileType) throws IOException {
      if (FTPReply.isPositiveCompletion(this.type(fileType))) {
         this.__fileType = fileType;
         this.__fileFormat = 4;
         return true;
      } else {
         return false;
      }
   }

   public boolean setFileType(int fileType, int formatOrByteSize) throws IOException {
      if (FTPReply.isPositiveCompletion(this.type(fileType, formatOrByteSize))) {
         this.__fileType = fileType;
         this.__fileFormat = formatOrByteSize;
         return true;
      } else {
         return false;
      }
   }

   public boolean setFileStructure(int structure) throws IOException {
      if (FTPReply.isPositiveCompletion(this.stru(structure))) {
         this.__fileStructure = structure;
         return true;
      } else {
         return false;
      }
   }

   public boolean setFileTransferMode(int mode) throws IOException {
      if (FTPReply.isPositiveCompletion(this.mode(mode))) {
         this.__fileTransferMode = mode;
         return true;
      } else {
         return false;
      }
   }

   public boolean remoteRetrieve(String filename) throws IOException {
      return this.__dataConnectionMode != 1 && this.__dataConnectionMode != 3 ? false : FTPReply.isPositivePreliminary(this.retr(filename));
   }

   public boolean remoteStore(String filename) throws IOException {
      return this.__dataConnectionMode != 1 && this.__dataConnectionMode != 3 ? false : FTPReply.isPositivePreliminary(this.stor(filename));
   }

   public boolean remoteStoreUnique(String filename) throws IOException {
      return this.__dataConnectionMode != 1 && this.__dataConnectionMode != 3 ? false : FTPReply.isPositivePreliminary(this.stou(filename));
   }

   public boolean remoteStoreUnique() throws IOException {
      return this.__dataConnectionMode != 1 && this.__dataConnectionMode != 3 ? false : FTPReply.isPositivePreliminary(this.stou());
   }

   public boolean remoteAppend(String filename) throws IOException {
      return this.__dataConnectionMode != 1 && this.__dataConnectionMode != 3 ? false : FTPReply.isPositivePreliminary(this.appe(filename));
   }

   public boolean completePendingCommand() throws IOException {
      return FTPReply.isPositiveCompletion(this.getReply());
   }

   public boolean retrieveFile(String remote, OutputStream local) throws IOException {
      return this._retrieveFile(FTPCmd.RETR.getCommand(), remote, local);
   }

   protected boolean _retrieveFile(String command, String remote, OutputStream local) throws IOException {
      Socket socket = this._openDataConnection_(command, remote);
      if (socket == null) {
         return false;
      } else {
         InputStream input;
         if (this.__fileType == 0) {
            input = new FromNetASCIIInputStream(this.getBufferedInputStream(socket.getInputStream()));
         } else {
            input = this.getBufferedInputStream(socket.getInputStream());
         }

         FTPClient.CSL csl = null;
         if (this.__controlKeepAliveTimeout > 0L) {
            csl = new FTPClient.CSL(this, this.__controlKeepAliveTimeout, this.__controlKeepAliveReplyTimeout);
         }

         try {
            Util.copyStream(input, local, this.getBufferSize(), -1L, this.__mergeListeners(csl), false);
         } finally {
            Util.closeQuietly(input);
            Util.closeQuietly(socket);
            if (csl != null) {
               csl.cleanUp();
            }
         }

         return this.completePendingCommand();
      }
   }

   public InputStream retrieveFileStream(String remote) throws IOException {
      return this._retrieveFileStream(FTPCmd.RETR.getCommand(), remote);
   }

   protected InputStream _retrieveFileStream(String command, String remote) throws IOException {
      Socket socket = this._openDataConnection_(command, remote);
      if (socket == null) {
         return null;
      } else {
         InputStream input;
         if (this.__fileType == 0) {
            input = new FromNetASCIIInputStream(this.getBufferedInputStream(socket.getInputStream()));
         } else {
            input = socket.getInputStream();
         }

         return new SocketInputStream(socket, input);
      }
   }

   public boolean storeFile(String remote, InputStream local) throws IOException {
      return this.__storeFile(FTPCmd.STOR, remote, local);
   }

   public OutputStream storeFileStream(String remote) throws IOException {
      return this.__storeFileStream(FTPCmd.STOR, remote);
   }

   public boolean appendFile(String remote, InputStream local) throws IOException {
      return this.__storeFile(FTPCmd.APPE, remote, local);
   }

   public OutputStream appendFileStream(String remote) throws IOException {
      return this.__storeFileStream(FTPCmd.APPE, remote);
   }

   public boolean storeUniqueFile(String remote, InputStream local) throws IOException {
      return this.__storeFile(FTPCmd.STOU, remote, local);
   }

   public OutputStream storeUniqueFileStream(String remote) throws IOException {
      return this.__storeFileStream(FTPCmd.STOU, remote);
   }

   public boolean storeUniqueFile(InputStream local) throws IOException {
      return this.__storeFile(FTPCmd.STOU, null, local);
   }

   public OutputStream storeUniqueFileStream() throws IOException {
      return this.__storeFileStream(FTPCmd.STOU, null);
   }

   public boolean allocate(int bytes) throws IOException {
      return FTPReply.isPositiveCompletion(this.allo(bytes));
   }

   public boolean features() throws IOException {
      return FTPReply.isPositiveCompletion(this.feat());
   }

   public String[] featureValues(String feature) throws IOException {
      if (!this.initFeatureMap()) {
         return null;
      } else {
         Set<String> entries = this.__featuresMap.get(feature.toUpperCase(Locale.ENGLISH));
         return entries != null ? entries.toArray(new String[entries.size()]) : null;
      }
   }

   public String featureValue(String feature) throws IOException {
      String[] values = this.featureValues(feature);
      return values != null ? values[0] : null;
   }

   public boolean hasFeature(String feature) throws IOException {
      return !this.initFeatureMap() ? false : this.__featuresMap.containsKey(feature.toUpperCase(Locale.ENGLISH));
   }

   public boolean hasFeature(String feature, String value) throws IOException {
      if (!this.initFeatureMap()) {
         return false;
      } else {
         Set<String> entries = this.__featuresMap.get(feature.toUpperCase(Locale.ENGLISH));
         return entries != null ? entries.contains(value) : false;
      }
   }

   private boolean initFeatureMap() throws IOException {
      if (this.__featuresMap == null) {
         int replyCode = this.feat();
         if (replyCode == 530) {
            return false;
         }

         boolean success = FTPReply.isPositiveCompletion(replyCode);
         this.__featuresMap = new HashMap<>();
         if (!success) {
            return false;
         }

         for (String l : this.getReplyStrings()) {
            if (l.startsWith(" ")) {
               String value = "";
               int varsep = l.indexOf(32, 1);
               String key;
               if (varsep > 0) {
                  key = l.substring(1, varsep);
                  value = l.substring(varsep + 1);
               } else {
                  key = l.substring(1);
               }

               key = key.toUpperCase(Locale.ENGLISH);
               Set<String> entries = this.__featuresMap.get(key);
               if (entries == null) {
                  entries = new HashSet<>();
                  this.__featuresMap.put(key, entries);
               }

               entries.add(value);
            }
         }
      }

      return true;
   }

   public boolean allocate(int bytes, int recordSize) throws IOException {
      return FTPReply.isPositiveCompletion(this.allo(bytes, recordSize));
   }

   public boolean doCommand(String command, String params) throws IOException {
      return FTPReply.isPositiveCompletion(this.sendCommand(command, params));
   }

   public String[] doCommandAsStrings(String command, String params) throws IOException {
      boolean success = FTPReply.isPositiveCompletion(this.sendCommand(command, params));
      return success ? this.getReplyStrings() : null;
   }

   public FTPFile mlistFile(String pathname) throws IOException {
      boolean success = FTPReply.isPositiveCompletion(this.sendCommand(FTPCmd.MLST, pathname));
      if (success) {
         String entry = this.getReplyStrings()[1].substring(1);
         return MLSxEntryParser.parseEntry(entry);
      } else {
         return null;
      }
   }

   public FTPFile[] mlistDir() throws IOException {
      return this.mlistDir(null);
   }

   public FTPFile[] mlistDir(String pathname) throws IOException {
      FTPListParseEngine engine = this.initiateMListParsing(pathname);
      return engine.getFiles();
   }

   public FTPFile[] mlistDir(String pathname, FTPFileFilter filter) throws IOException {
      FTPListParseEngine engine = this.initiateMListParsing(pathname);
      return engine.getFiles(filter);
   }

   protected boolean restart(long offset) throws IOException {
      this.__restartOffset = 0L;
      return FTPReply.isPositiveIntermediate(this.rest(Long.toString(offset)));
   }

   public void setRestartOffset(long offset) {
      if (offset >= 0L) {
         this.__restartOffset = offset;
      }
   }

   public long getRestartOffset() {
      return this.__restartOffset;
   }

   public boolean rename(String from, String to) throws IOException {
      return !FTPReply.isPositiveIntermediate(this.rnfr(from)) ? false : FTPReply.isPositiveCompletion(this.rnto(to));
   }

   public boolean abort() throws IOException {
      return FTPReply.isPositiveCompletion(this.abor());
   }

   public boolean deleteFile(String pathname) throws IOException {
      return FTPReply.isPositiveCompletion(this.dele(pathname));
   }

   public boolean removeDirectory(String pathname) throws IOException {
      return FTPReply.isPositiveCompletion(this.rmd(pathname));
   }

   public boolean makeDirectory(String pathname) throws IOException {
      return FTPReply.isPositiveCompletion(this.mkd(pathname));
   }

   public String printWorkingDirectory() throws IOException {
      return this.pwd() != 257 ? null : __parsePathname(this._replyLines.get(this._replyLines.size() - 1));
   }

   public boolean sendSiteCommand(String arguments) throws IOException {
      return FTPReply.isPositiveCompletion(this.site(arguments));
   }

   public String getSystemType() throws IOException {
      if (this.__systemName == null) {
         if (FTPReply.isPositiveCompletion(this.syst())) {
            this.__systemName = this._replyLines.get(this._replyLines.size() - 1).substring(4);
         } else {
            String systDefault = System.getProperty("org.apache.commons.net.ftp.systemType.default");
            if (systDefault == null) {
               throw new IOException("Unable to determine system type - response: " + this.getReplyString());
            }

            this.__systemName = systDefault;
         }
      }

      return this.__systemName;
   }

   public String listHelp() throws IOException {
      return FTPReply.isPositiveCompletion(this.help()) ? this.getReplyString() : null;
   }

   public String listHelp(String command) throws IOException {
      return FTPReply.isPositiveCompletion(this.help(command)) ? this.getReplyString() : null;
   }

   public boolean sendNoOp() throws IOException {
      return FTPReply.isPositiveCompletion(this.noop());
   }

   public String[] listNames(String pathname) throws IOException {
      Socket socket = this._openDataConnection_(FTPCmd.NLST, this.getListArguments(pathname));
      if (socket == null) {
         return null;
      } else {
         BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), this.getControlEncoding()));
         ArrayList<String> results = new ArrayList<>();

         String line;
         while ((line = reader.readLine()) != null) {
            results.add(line);
         }

         reader.close();
         socket.close();
         if (this.completePendingCommand()) {
            String[] names = new String[results.size()];
            return results.toArray(names);
         } else {
            return null;
         }
      }
   }

   public String[] listNames() throws IOException {
      return this.listNames(null);
   }

   public FTPFile[] listFiles(String pathname) throws IOException {
      FTPListParseEngine engine = this.initiateListParsing((String)null, pathname);
      return engine.getFiles();
   }

   public FTPFile[] listFiles() throws IOException {
      return this.listFiles((String)null);
   }

   public FTPFile[] listFiles(String pathname, FTPFileFilter filter) throws IOException {
      FTPListParseEngine engine = this.initiateListParsing((String)null, pathname);
      return engine.getFiles(filter);
   }

   public FTPFile[] listDirectories() throws IOException {
      return this.listDirectories((String)null);
   }

   public FTPFile[] listDirectories(String parent) throws IOException {
      return this.listFiles(parent, FTPFileFilters.DIRECTORIES);
   }

   public FTPListParseEngine initiateListParsing() throws IOException {
      return this.initiateListParsing((String)null);
   }

   public FTPListParseEngine initiateListParsing(String pathname) throws IOException {
      return this.initiateListParsing((String)null, pathname);
   }

   public FTPListParseEngine initiateListParsing(String parserKey, String pathname) throws IOException {
      this.__createParser(parserKey);
      return this.initiateListParsing(this.__entryParser, pathname);
   }

   void __createParser(String parserKey) throws IOException {
      if (this.__entryParser == null || parserKey != null && !this.__entryParserKey.equals(parserKey)) {
         if (null != parserKey) {
            this.__entryParser = this.__parserFactory.createFileEntryParser(parserKey);
            this.__entryParserKey = parserKey;
         } else if (null != this.__configuration && this.__configuration.getServerSystemKey().length() > 0) {
            this.__entryParser = this.__parserFactory.createFileEntryParser(this.__configuration);
            this.__entryParserKey = this.__configuration.getServerSystemKey();
         } else {
            String systemType = System.getProperty("org.apache.commons.net.ftp.systemType");
            if (systemType == null) {
               systemType = this.getSystemType();
               Properties override = getOverrideProperties();
               if (override != null) {
                  String newType = override.getProperty(systemType);
                  if (newType != null) {
                     systemType = newType;
                  }
               }
            }

            if (null != this.__configuration) {
               this.__entryParser = this.__parserFactory.createFileEntryParser(new FTPClientConfig(systemType, this.__configuration));
            } else {
               this.__entryParser = this.__parserFactory.createFileEntryParser(systemType);
            }

            this.__entryParserKey = systemType;
         }
      }
   }

   private FTPListParseEngine initiateListParsing(FTPFileEntryParser parser, String pathname) throws IOException {
      Socket socket = this._openDataConnection_(FTPCmd.LIST, this.getListArguments(pathname));
      FTPListParseEngine engine = new FTPListParseEngine(parser, this.__configuration);
      if (socket == null) {
         return engine;
      } else {
         try {
            engine.readServerList(socket.getInputStream(), this.getControlEncoding());
         } finally {
            Util.closeQuietly(socket);
         }

         this.completePendingCommand();
         return engine;
      }
   }

   private FTPListParseEngine initiateMListParsing(String pathname) throws IOException {
      Socket socket = this._openDataConnection_(FTPCmd.MLSD, pathname);
      FTPListParseEngine engine = new FTPListParseEngine(MLSxEntryParser.getInstance(), this.__configuration);
      if (socket == null) {
         return engine;
      } else {
         try {
            engine.readServerList(socket.getInputStream(), this.getControlEncoding());
         } finally {
            Util.closeQuietly(socket);
            this.completePendingCommand();
         }

         return engine;
      }
   }

   protected String getListArguments(String pathname) {
      if (this.getListHiddenFiles()) {
         if (pathname != null) {
            StringBuilder sb = new StringBuilder(pathname.length() + 3);
            sb.append("-a ");
            sb.append(pathname);
            return sb.toString();
         } else {
            return "-a";
         }
      } else {
         return pathname;
      }
   }

   public String getStatus() throws IOException {
      return FTPReply.isPositiveCompletion(this.stat()) ? this.getReplyString() : null;
   }

   public String getStatus(String pathname) throws IOException {
      return FTPReply.isPositiveCompletion(this.stat(pathname)) ? this.getReplyString() : null;
   }

   public String getModificationTime(String pathname) throws IOException {
      return FTPReply.isPositiveCompletion(this.mdtm(pathname)) ? this.getReplyStrings()[0].substring(4) : null;
   }

   public FTPFile mdtmFile(String pathname) throws IOException {
      if (FTPReply.isPositiveCompletion(this.mdtm(pathname))) {
         String reply = this.getReplyStrings()[0].substring(4);
         FTPFile file = new FTPFile();
         file.setName(pathname);
         file.setRawListing(reply);
         file.setTimestamp(MLSxEntryParser.parseGMTdateTime(reply));
         return file;
      } else {
         return null;
      }
   }

   public boolean setModificationTime(String pathname, String timeval) throws IOException {
      return FTPReply.isPositiveCompletion(this.mfmt(pathname, timeval));
   }

   public void setBufferSize(int bufSize) {
      this.__bufferSize = bufSize;
   }

   public int getBufferSize() {
      return this.__bufferSize;
   }

   public void setSendDataSocketBufferSize(int bufSize) {
      this.__sendDataSocketBufferSize = bufSize;
   }

   public int getSendDataSocketBufferSize() {
      return this.__sendDataSocketBufferSize;
   }

   public void setReceieveDataSocketBufferSize(int bufSize) {
      this.__receiveDataSocketBufferSize = bufSize;
   }

   public int getReceiveDataSocketBufferSize() {
      return this.__receiveDataSocketBufferSize;
   }

   @Override
   public void configure(FTPClientConfig config) {
      this.__configuration = config;
   }

   public void setListHiddenFiles(boolean listHiddenFiles) {
      this.__listHiddenFiles = listHiddenFiles;
   }

   public boolean getListHiddenFiles() {
      return this.__listHiddenFiles;
   }

   public boolean isUseEPSVwithIPv4() {
      return this.__useEPSVwithIPv4;
   }

   public void setUseEPSVwithIPv4(boolean selected) {
      this.__useEPSVwithIPv4 = selected;
   }

   public void setCopyStreamListener(CopyStreamListener listener) {
      this.__copyStreamListener = listener;
   }

   public CopyStreamListener getCopyStreamListener() {
      return this.__copyStreamListener;
   }

   public void setControlKeepAliveTimeout(long controlIdle) {
      this.__controlKeepAliveTimeout = controlIdle * 1000L;
   }

   public long getControlKeepAliveTimeout() {
      return this.__controlKeepAliveTimeout / 1000L;
   }

   public void setControlKeepAliveReplyTimeout(int timeout) {
      this.__controlKeepAliveReplyTimeout = timeout;
   }

   public int getControlKeepAliveReplyTimeout() {
      return this.__controlKeepAliveReplyTimeout;
   }

   public void setPassiveNatWorkaround(boolean enabled) {
      this.__passiveNatWorkaround = enabled;
   }

   private OutputStream getBufferedOutputStream(OutputStream outputStream) {
      return this.__bufferSize > 0 ? new BufferedOutputStream(outputStream, this.__bufferSize) : new BufferedOutputStream(outputStream);
   }

   private InputStream getBufferedInputStream(InputStream inputStream) {
      return this.__bufferSize > 0 ? new BufferedInputStream(inputStream, this.__bufferSize) : new BufferedInputStream(inputStream);
   }

   private CopyStreamListener __mergeListeners(CopyStreamListener local) {
      if (local == null) {
         return this.__copyStreamListener;
      } else if (this.__copyStreamListener == null) {
         return local;
      } else {
         CopyStreamAdapter merged = new CopyStreamAdapter();
         merged.addCopyStreamListener(local);
         merged.addCopyStreamListener(this.__copyStreamListener);
         return merged;
      }
   }

   public void setAutodetectUTF8(boolean autodetect) {
      this.__autodetectEncoding = autodetect;
   }

   public boolean getAutodetectUTF8() {
      return this.__autodetectEncoding;
   }

   FTPFileEntryParser getEntryParser() {
      return this.__entryParser;
   }

   @Deprecated
   public String getSystemName() throws IOException {
      if (this.__systemName == null && FTPReply.isPositiveCompletion(this.syst())) {
         this.__systemName = this._replyLines.get(this._replyLines.size() - 1).substring(4);
      }

      return this.__systemName;
   }

   private static class CSL implements CopyStreamListener {
      private final FTPClient parent;
      private final long idle;
      private final int currentSoTimeout;
      private long time = System.currentTimeMillis();
      private int notAcked;

      CSL(FTPClient parent, long idleTime, int maxWait) throws SocketException {
         this.idle = idleTime;
         this.parent = parent;
         this.currentSoTimeout = parent.getSoTimeout();
         parent.setSoTimeout(maxWait);
      }

      @Override
      public void bytesTransferred(CopyStreamEvent event) {
         this.bytesTransferred(event.getTotalBytesTransferred(), event.getBytesTransferred(), event.getStreamSize());
      }

      @Override
      public void bytesTransferred(long totalBytesTransferred, int bytesTransferred, long streamSize) {
         long now = System.currentTimeMillis();
         if (now - this.time > this.idle) {
            try {
               this.parent.__noop();
            } catch (SocketTimeoutException var9) {
               this.notAcked++;
            } catch (IOException var10) {
            }

            this.time = now;
         }
      }

      void cleanUp() throws IOException {
         try {
            while (this.notAcked-- > 0) {
               this.parent.__getReplyNoReport();
            }
         } finally {
            this.parent.setSoTimeout(this.currentSoTimeout);
         }
      }
   }

   private static class PropertiesSingleton {
      static final Properties PROPERTIES;

      static {
         InputStream resourceAsStream = FTPClient.class.getResourceAsStream("/systemType.properties");
         Properties p = null;
         if (resourceAsStream != null) {
            p = new Properties();

            try {
               p.load(resourceAsStream);
            } catch (IOException var11) {
            } finally {
               try {
                  resourceAsStream.close();
               } catch (IOException var10) {
               }
            }
         }

         PROPERTIES = p;
      }
   }
}
