package org.apache.commons.net.smtp;

import java.io.IOException;
import java.net.InetAddress;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.SSLContext;
import org.apache.commons.net.util.Base64;

public class AuthenticatingSMTPClient extends SMTPSClient {
   public AuthenticatingSMTPClient() {
   }

   public AuthenticatingSMTPClient(String protocol) {
      super(protocol);
   }

   public AuthenticatingSMTPClient(String proto, boolean implicit) {
      super(proto, implicit);
   }

   public AuthenticatingSMTPClient(String proto, boolean implicit, String encoding) {
      super(proto, implicit, encoding);
   }

   public AuthenticatingSMTPClient(boolean implicit, SSLContext ctx) {
      super(implicit, ctx);
   }

   public AuthenticatingSMTPClient(String protocol, String encoding) {
      super(protocol, false, encoding);
   }

   public int ehlo(String hostname) throws IOException {
      return this.sendCommand(15, hostname);
   }

   public boolean elogin(String hostname) throws IOException {
      return SMTPReply.isPositiveCompletion(this.ehlo(hostname));
   }

   public boolean elogin() throws IOException {
      InetAddress host = this.getLocalAddress();
      String name = host.getHostName();
      return name == null ? false : SMTPReply.isPositiveCompletion(this.ehlo(name));
   }

   public int[] getEnhancedReplyCode() {
      String reply = this.getReplyString().substring(4);
      String[] parts = reply.substring(0, reply.indexOf(32)).split("\\.");
      int[] res = new int[parts.length];

      for (int i = 0; i < parts.length; i++) {
         res[i] = Integer.parseInt(parts[i]);
      }

      return res;
   }

   public boolean auth(AuthenticatingSMTPClient.AUTH_METHOD method, String username, String password) throws IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidKeySpecException {
      if (!SMTPReply.isPositiveIntermediate(this.sendCommand(14, AuthenticatingSMTPClient.AUTH_METHOD.getAuthName(method)))) {
         return false;
      } else if (method.equals(AuthenticatingSMTPClient.AUTH_METHOD.PLAIN)) {
         return SMTPReply.isPositiveCompletion(
            this.sendCommand(Base64.encodeBase64StringUnChunked(("\u0000" + username + "\u0000" + password).getBytes(this.getCharset())))
         );
      } else if (method.equals(AuthenticatingSMTPClient.AUTH_METHOD.CRAM_MD5)) {
         byte[] serverChallenge = Base64.decodeBase64(this.getReplyString().substring(4).trim());
         Mac hmac_md5 = Mac.getInstance("HmacMD5");
         hmac_md5.init(new SecretKeySpec(password.getBytes(this.getCharset()), "HmacMD5"));
         byte[] hmacResult = this._convertToHexString(hmac_md5.doFinal(serverChallenge)).getBytes(this.getCharset());
         byte[] usernameBytes = username.getBytes(this.getCharset());
         byte[] toEncode = new byte[usernameBytes.length + 1 + hmacResult.length];
         System.arraycopy(usernameBytes, 0, toEncode, 0, usernameBytes.length);
         toEncode[usernameBytes.length] = 32;
         System.arraycopy(hmacResult, 0, toEncode, usernameBytes.length + 1, hmacResult.length);
         return SMTPReply.isPositiveCompletion(this.sendCommand(Base64.encodeBase64StringUnChunked(toEncode)));
      } else if (method.equals(AuthenticatingSMTPClient.AUTH_METHOD.LOGIN)) {
         return !SMTPReply.isPositiveIntermediate(this.sendCommand(Base64.encodeBase64StringUnChunked(username.getBytes(this.getCharset()))))
            ? false
            : SMTPReply.isPositiveCompletion(this.sendCommand(Base64.encodeBase64StringUnChunked(password.getBytes(this.getCharset()))));
      } else {
         return method.equals(AuthenticatingSMTPClient.AUTH_METHOD.XOAUTH)
            ? SMTPReply.isPositiveIntermediate(this.sendCommand(Base64.encodeBase64StringUnChunked(username.getBytes(this.getCharset()))))
            : false;
      }
   }

   private String _convertToHexString(byte[] a) {
      StringBuilder result = new StringBuilder(a.length * 2);

      for (byte element : a) {
         if ((element & 255) <= 15) {
            result.append("0");
         }

         result.append(Integer.toHexString(element & 255));
      }

      return result.toString();
   }

   public static enum AUTH_METHOD {
      PLAIN,
      CRAM_MD5,
      LOGIN,
      XOAUTH;

      public static final String getAuthName(AuthenticatingSMTPClient.AUTH_METHOD method) {
         if (method.equals(PLAIN)) {
            return "PLAIN";
         } else if (method.equals(CRAM_MD5)) {
            return "CRAM-MD5";
         } else if (method.equals(LOGIN)) {
            return "LOGIN";
         } else {
            return method.equals(XOAUTH) ? "XOAUTH" : null;
         }
      }
   }
}
