package org.apache.logging.log4j.core.net.ssl;

import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

class FilePasswordProvider implements PasswordProvider {
   private final Path passwordPath;

   public FilePasswordProvider(final String passwordFile) throws NoSuchFileException {
      this.passwordPath = Paths.get(passwordFile);
      if (!Files.exists(this.passwordPath)) {
         throw new NoSuchFileException("PasswordFile '" + passwordFile + "' does not exist");
      }
   }

   @Override
   public char[] getPassword() {
      byte[] bytes = null;

      char[] var5;
      try {
         bytes = Files.readAllBytes(this.passwordPath);
         ByteBuffer bb = ByteBuffer.wrap(bytes);
         CharBuffer decoded = Charset.defaultCharset().decode(bb);
         char[] result = new char[decoded.limit()];
         decoded.get(result, 0, result.length);
         ((Buffer)decoded).rewind();
         decoded.put(new char[result.length]);
         var5 = result;
      } catch (IOException var9) {
         throw new IllegalStateException("Could not read password from " + this.passwordPath + ": " + var9, var9);
      } finally {
         if (bytes != null) {
            Arrays.fill(bytes, (byte)0);
         }
      }

      return var5;
   }
}
