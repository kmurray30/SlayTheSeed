package com.badlogic.gdx.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class SharedLibraryLoader {
   public static boolean isWindows = System.getProperty("os.name").contains("Windows");
   public static boolean isLinux = System.getProperty("os.name").contains("Linux");
   public static boolean isMac = System.getProperty("os.name").contains("Mac");
   public static boolean isIos = false;
   public static boolean isAndroid = false;
   public static boolean isARM = System.getProperty("os.arch").startsWith("arm");
   public static boolean is64Bit = System.getProperty("os.arch").equals("amd64") || System.getProperty("os.arch").equals("x86_64");
   public static String abi = System.getProperty("sun.arch.abi") != null ? System.getProperty("sun.arch.abi") : "";
   private static final HashSet<String> loadedLibraries;
   private String nativesJar;

   public SharedLibraryLoader() {
   }

   public SharedLibraryLoader(String nativesJar) {
      this.nativesJar = nativesJar;
   }

   public String crc(InputStream input) {
      if (input == null) {
         throw new IllegalArgumentException("input cannot be null.");
      } else {
         CRC32 crc = new CRC32();
         byte[] buffer = new byte[4096];

         try {
            while (true) {
               int length = input.read(buffer);
               if (length == -1) {
                  break;
               }

               crc.update(buffer, 0, length);
            }
         } catch (Exception var5) {
            StreamUtils.closeQuietly(input);
         }

         return Long.toString(crc.getValue(), 16);
      }
   }

   public String mapLibraryName(String libraryName) {
      if (isWindows) {
         return libraryName + (is64Bit ? "64.dll" : ".dll");
      } else if (isLinux) {
         return "lib" + libraryName + (isARM ? "arm" + abi : "") + (is64Bit ? "64.so" : ".so");
      } else {
         return isMac ? "lib" + libraryName + (is64Bit ? "64.dylib" : ".dylib") : libraryName;
      }
   }

   public void load(String libraryName) {
      if (!isIos) {
         synchronized (SharedLibraryLoader.class) {
            if (!isLoaded(libraryName)) {
               String platformName = this.mapLibraryName(libraryName);

               try {
                  if (isAndroid) {
                     System.loadLibrary(platformName);
                  } else {
                     this.loadFile(platformName);
                  }

                  setLoaded(libraryName);
               } catch (Throwable var6) {
                  throw new GdxRuntimeException(
                     "Couldn't load shared library '" + platformName + "' for target: " + System.getProperty("os.name") + (is64Bit ? ", 64-bit" : ", 32-bit"),
                     var6
                  );
               }
            }
         }
      }
   }

   private InputStream readFile(String path) {
      if (this.nativesJar == null) {
         InputStream input = SharedLibraryLoader.class.getResourceAsStream("/" + path);
         if (input == null) {
            throw new GdxRuntimeException("Unable to read file for extraction: " + path);
         } else {
            return input;
         }
      } else {
         try {
            ZipFile file = new ZipFile(this.nativesJar);
            ZipEntry entry = file.getEntry(path);
            if (entry == null) {
               throw new GdxRuntimeException("Couldn't find '" + path + "' in JAR: " + this.nativesJar);
            } else {
               return file.getInputStream(entry);
            }
         } catch (IOException var4) {
            throw new GdxRuntimeException("Error reading '" + path + "' in JAR: " + this.nativesJar, var4);
         }
      }
   }

   public File extractFile(String sourcePath, String dirName) throws IOException {
      try {
         String sourceCrc = this.crc(this.readFile(sourcePath));
         if (dirName == null) {
            dirName = sourceCrc;
         }

         File extractedFile = this.getExtractedFile(dirName, new File(sourcePath).getName());
         if (extractedFile == null) {
            extractedFile = this.getExtractedFile(UUID.randomUUID().toString(), new File(sourcePath).getName());
            if (extractedFile == null) {
               throw new GdxRuntimeException("Unable to find writable path to extract file. Is the user home directory writable?");
            }
         }

         return this.extractFile(sourcePath, sourceCrc, extractedFile);
      } catch (RuntimeException var5) {
         File file = new File(System.getProperty("java.library.path"), sourcePath);
         if (file.exists()) {
            return file;
         } else {
            throw var5;
         }
      }
   }

   public void extractFileTo(String sourcePath, File dir) throws IOException {
      this.extractFile(sourcePath, this.crc(this.readFile(sourcePath)), new File(dir, new File(sourcePath).getName()));
   }

   private File getExtractedFile(String dirName, String fileName) {
      File idealFile = new File(System.getProperty("java.io.tmpdir") + "/libgdx" + System.getProperty("user.name") + "/" + dirName, fileName);
      if (this.canWrite(idealFile)) {
         return idealFile;
      } else {
         try {
            File file = File.createTempFile(dirName, null);
            if (file.delete()) {
               file = new File(file, fileName);
               if (this.canWrite(file)) {
                  return file;
               }
            }
         } catch (IOException var5) {
         }

         File file = new File(System.getProperty("user.home") + "/.libgdx/" + dirName, fileName);
         if (this.canWrite(file)) {
            return file;
         } else {
            file = new File(".temp/" + dirName, fileName);
            if (this.canWrite(file)) {
               return file;
            } else {
               return System.getenv("APP_SANDBOX_CONTAINER_ID") != null ? idealFile : null;
            }
         }
      }
   }

   private boolean canWrite(File file) {
      File parent = file.getParentFile();
      File testFile;
      if (file.exists()) {
         if (!file.canWrite() || !this.canExecute(file)) {
            return false;
         }

         testFile = new File(parent, UUID.randomUUID().toString());
      } else {
         parent.mkdirs();
         if (!parent.isDirectory()) {
            return false;
         }

         testFile = file;
      }

      boolean ex;
      try {
         new FileOutputStream(testFile).close();
         if (this.canExecute(testFile)) {
            return true;
         }

         ex = false;
      } catch (Throwable var9) {
         return false;
      } finally {
         testFile.delete();
      }

      return ex;
   }

   private boolean canExecute(File file) {
      try {
         Method canExecute = File.class.getMethod("canExecute");
         if ((Boolean)canExecute.invoke(file)) {
            return true;
         } else {
            Method setExecutable = File.class.getMethod("setExecutable", boolean.class, boolean.class);
            setExecutable.invoke(file, true, false);
            return (Boolean)canExecute.invoke(file);
         }
      } catch (Exception var4) {
         return false;
      }
   }

   private File extractFile(String sourcePath, String sourceCrc, File extractedFile) throws IOException {
      String extractedCrc = null;
      if (extractedFile.exists()) {
         try {
            extractedCrc = this.crc(new FileInputStream(extractedFile));
         } catch (FileNotFoundException var9) {
         }
      }

      if (extractedCrc == null || !extractedCrc.equals(sourceCrc)) {
         try {
            InputStream input = this.readFile(sourcePath);
            extractedFile.getParentFile().mkdirs();
            FileOutputStream output = new FileOutputStream(extractedFile);
            byte[] buffer = new byte[4096];

            while (true) {
               int length = input.read(buffer);
               if (length == -1) {
                  input.close();
                  output.close();
                  break;
               }

               output.write(buffer, 0, length);
            }
         } catch (IOException var10) {
            throw new GdxRuntimeException("Error extracting file: " + sourcePath + "\nTo: " + extractedFile.getAbsolutePath(), var10);
         }
      }

      return extractedFile;
   }

   private void loadFile(String sourcePath) {
      String sourceCrc = this.crc(this.readFile(sourcePath));
      String fileName = new File(sourcePath).getName();
      File file = new File(System.getProperty("java.io.tmpdir") + "/libgdx" + System.getProperty("user.name") + "/" + sourceCrc, fileName);
      Throwable ex = this.loadFile(sourcePath, sourceCrc, file);
      if (ex != null) {
         try {
            file = File.createTempFile(sourceCrc, null);
            if (file.delete() && this.loadFile(sourcePath, sourceCrc, file) == null) {
               return;
            }
         } catch (Throwable var7) {
         }

         file = new File(System.getProperty("user.home") + "/.libgdx/" + sourceCrc, fileName);
         if (this.loadFile(sourcePath, sourceCrc, file) != null) {
            file = new File(".temp/" + sourceCrc, fileName);
            if (this.loadFile(sourcePath, sourceCrc, file) != null) {
               file = new File(System.getProperty("java.library.path"), sourcePath);
               if (file.exists()) {
                  System.load(file.getAbsolutePath());
               } else {
                  throw new GdxRuntimeException(ex);
               }
            }
         }
      }
   }

   private Throwable loadFile(String sourcePath, String sourceCrc, File extractedFile) {
      try {
         System.load(this.extractFile(sourcePath, sourceCrc, extractedFile).getAbsolutePath());
         return null;
      } catch (Throwable var5) {
         return var5;
      }
   }

   public static synchronized void setLoaded(String libraryName) {
      loadedLibraries.add(libraryName);
   }

   public static synchronized boolean isLoaded(String libraryName) {
      return loadedLibraries.contains(libraryName);
   }

   static {
      String vm = System.getProperty("java.runtime.name");
      if (vm != null && vm.contains("Android Runtime")) {
         isAndroid = true;
         isWindows = false;
         isLinux = false;
         isMac = false;
         is64Bit = false;
      }

      if (!isAndroid && !isWindows && !isLinux && !isMac) {
         isIos = true;
         is64Bit = false;
      }

      loadedLibraries = new HashSet<>();
   }
}
