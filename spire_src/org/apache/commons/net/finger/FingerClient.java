package org.apache.commons.net.finger;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import org.apache.commons.net.SocketClient;
import org.apache.commons.net.util.Charsets;

public class FingerClient extends SocketClient {
   public static final int DEFAULT_PORT = 79;
   private static final String __LONG_FLAG = "/W ";
   private transient char[] __buffer = new char[1024];

   public FingerClient() {
      this.setDefaultPort(79);
   }

   public String query(boolean longOutput, String username) throws IOException {
      StringBuilder result = new StringBuilder(this.__buffer.length);
      BufferedReader input = new BufferedReader(new InputStreamReader(this.getInputStream(longOutput, username), this.getCharset()));

      try {
         while (true) {
            int read = input.read(this.__buffer, 0, this.__buffer.length);
            if (read <= 0) {
               return result.toString();
            }

            result.append(this.__buffer, 0, read);
         }
      } finally {
         input.close();
      }
   }

   public String query(boolean longOutput) throws IOException {
      return this.query(longOutput, "");
   }

   public InputStream getInputStream(boolean longOutput, String username) throws IOException {
      return this.getInputStream(longOutput, username, null);
   }

   public InputStream getInputStream(boolean longOutput, String username, String encoding) throws IOException {
      StringBuilder buffer = new StringBuilder(64);
      if (longOutput) {
         buffer.append("/W ");
      }

      buffer.append(username);
      buffer.append("\r\n");
      byte[] encodedQuery = buffer.toString().getBytes(Charsets.toCharset(encoding).name());
      DataOutputStream output = new DataOutputStream(new BufferedOutputStream(this._output_, 1024));
      output.write(encodedQuery, 0, encodedQuery.length);
      output.flush();
      return this._input_;
   }

   public InputStream getInputStream(boolean longOutput) throws IOException {
      return this.getInputStream(longOutput, "");
   }
}
