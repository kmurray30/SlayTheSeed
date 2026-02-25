package com.badlogic.gdx.files;

import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.StreamUtils;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;

public class FileHandle {
   protected File file;
   protected Files.FileType type;

   protected FileHandle() {
   }

   public FileHandle(String fileName) {
      this.file = new File(fileName);
      this.type = Files.FileType.Absolute;
   }

   public FileHandle(File file) {
      this.file = file;
      this.type = Files.FileType.Absolute;
   }

   protected FileHandle(String fileName, Files.FileType type) {
      this.type = type;
      this.file = new File(fileName);
   }

   protected FileHandle(File file, Files.FileType type) {
      this.file = file;
      this.type = type;
   }

   public String path() {
      return this.file.getPath().replace('\\', '/');
   }

   public String name() {
      return this.file.getName();
   }

   public String extension() {
      String name = this.file.getName();
      int dotIndex = name.lastIndexOf(46);
      return dotIndex == -1 ? "" : name.substring(dotIndex + 1);
   }

   public String nameWithoutExtension() {
      String name = this.file.getName();
      int dotIndex = name.lastIndexOf(46);
      return dotIndex == -1 ? name : name.substring(0, dotIndex);
   }

   public String pathWithoutExtension() {
      String path = this.file.getPath().replace('\\', '/');
      int dotIndex = path.lastIndexOf(46);
      return dotIndex == -1 ? path : path.substring(0, dotIndex);
   }

   public Files.FileType type() {
      return this.type;
   }

   public File file() {
      return this.type == Files.FileType.External ? new File(Gdx.files.getExternalStoragePath(), this.file.getPath()) : this.file;
   }

   public InputStream read() {
      if (this.type != Files.FileType.Classpath
         && (this.type != Files.FileType.Internal || this.file().exists())
         && (this.type != Files.FileType.Local || this.file().exists())) {
         try {
            return new FileInputStream(this.file());
         } catch (Exception var2) {
            if (this.file().isDirectory()) {
               throw new GdxRuntimeException("Cannot open a stream to a directory: " + this.file + " (" + this.type + ")", var2);
            } else {
               throw new GdxRuntimeException("Error reading file: " + this.file + " (" + this.type + ")", var2);
            }
         }
      } else {
         InputStream input = FileHandle.class.getResourceAsStream("/" + this.file.getPath().replace('\\', '/'));
         if (input == null) {
            throw new GdxRuntimeException("File not found: " + this.file + " (" + this.type + ")");
         } else {
            return input;
         }
      }
   }

   public BufferedInputStream read(int bufferSize) {
      return new BufferedInputStream(this.read(), bufferSize);
   }

   public Reader reader() {
      return new InputStreamReader(this.read());
   }

   public Reader reader(String charset) {
      InputStream stream = this.read();

      try {
         return new InputStreamReader(stream, charset);
      } catch (UnsupportedEncodingException var4) {
         StreamUtils.closeQuietly(stream);
         throw new GdxRuntimeException("Error reading file: " + this, var4);
      }
   }

   public BufferedReader reader(int bufferSize) {
      return new BufferedReader(new InputStreamReader(this.read()), bufferSize);
   }

   public BufferedReader reader(int bufferSize, String charset) {
      try {
         return new BufferedReader(new InputStreamReader(this.read(), charset), bufferSize);
      } catch (UnsupportedEncodingException var4) {
         throw new GdxRuntimeException("Error reading file: " + this, var4);
      }
   }

   public String readString() {
      return this.readString(null);
   }

   public String readString(String charset) {
      StringBuilder output = new StringBuilder(this.estimateLength());
      InputStreamReader reader = null;

      try {
         if (charset == null) {
            reader = new InputStreamReader(this.read());
         } else {
            reader = new InputStreamReader(this.read(), charset);
         }

         char[] buffer = new char[256];

         while (true) {
            int length = reader.read(buffer);
            if (length == -1) {
               return output.toString();
            }

            output.append(buffer, 0, length);
         }
      } catch (IOException var9) {
         throw new GdxRuntimeException("Error reading layout file: " + this, var9);
      } finally {
         StreamUtils.closeQuietly(reader);
      }
   }

   public byte[] readBytes() {
      InputStream input = this.read();

      byte[] ex;
      try {
         ex = StreamUtils.copyStreamToByteArray(input, this.estimateLength());
      } catch (IOException var6) {
         throw new GdxRuntimeException("Error reading file: " + this, var6);
      } finally {
         StreamUtils.closeQuietly(input);
      }

      return ex;
   }

   private int estimateLength() {
      int length = (int)this.length();
      return length != 0 ? length : 512;
   }

   public int readBytes(byte[] bytes, int offset, int size) {
      InputStream input = this.read();
      int position = 0;

      try {
         while (true) {
            int count = input.read(bytes, offset + position, size - position);
            if (count <= 0) {
               return position - offset;
            }

            position += count;
         }
      } catch (IOException var10) {
         throw new GdxRuntimeException("Error reading file: " + this, var10);
      } finally {
         StreamUtils.closeQuietly(input);
      }
   }

   public OutputStream write(boolean append) {
      if (this.type == Files.FileType.Classpath) {
         throw new GdxRuntimeException("Cannot write to a classpath file: " + this.file);
      } else if (this.type == Files.FileType.Internal) {
         throw new GdxRuntimeException("Cannot write to an internal file: " + this.file);
      } else {
         this.parent().mkdirs();

         try {
            return new FileOutputStream(this.file(), append);
         } catch (Exception var3) {
            if (this.file().isDirectory()) {
               throw new GdxRuntimeException("Cannot open a stream to a directory: " + this.file + " (" + this.type + ")", var3);
            } else {
               throw new GdxRuntimeException("Error writing file: " + this.file + " (" + this.type + ")", var3);
            }
         }
      }
   }

   public OutputStream write(boolean append, int bufferSize) {
      return new BufferedOutputStream(this.write(append), bufferSize);
   }

   public void write(InputStream input, boolean append) {
      OutputStream output = null;

      try {
         output = this.write(append);
         StreamUtils.copyStream(input, output);
      } catch (Exception var8) {
         throw new GdxRuntimeException("Error stream writing to file: " + this.file + " (" + this.type + ")", var8);
      } finally {
         StreamUtils.closeQuietly(input);
         StreamUtils.closeQuietly(output);
      }
   }

   public Writer writer(boolean append) {
      return this.writer(append, null);
   }

   public Writer writer(boolean append, String charset) {
      if (this.type == Files.FileType.Classpath) {
         throw new GdxRuntimeException("Cannot write to a classpath file: " + this.file);
      } else if (this.type == Files.FileType.Internal) {
         throw new GdxRuntimeException("Cannot write to an internal file: " + this.file);
      } else {
         this.parent().mkdirs();

         try {
            FileOutputStream output = new FileOutputStream(this.file(), append);
            return charset == null ? new OutputStreamWriter(output) : new OutputStreamWriter(output, charset);
         } catch (IOException var4) {
            if (this.file().isDirectory()) {
               throw new GdxRuntimeException("Cannot open a stream to a directory: " + this.file + " (" + this.type + ")", var4);
            } else {
               throw new GdxRuntimeException("Error writing file: " + this.file + " (" + this.type + ")", var4);
            }
         }
      }
   }

   public void writeString(String string, boolean append) {
      this.writeString(string, append, null);
   }

   public void writeString(String string, boolean append, String charset) {
      Writer writer = null;

      try {
         writer = this.writer(append, charset);
         writer.write(string);
      } catch (Exception var9) {
         throw new GdxRuntimeException("Error writing file: " + this.file + " (" + this.type + ")", var9);
      } finally {
         StreamUtils.closeQuietly(writer);
      }
   }

   public void writeBytes(byte[] bytes, boolean append) {
      OutputStream output = this.write(append);

      try {
         output.write(bytes);
      } catch (IOException var8) {
         throw new GdxRuntimeException("Error writing file: " + this.file + " (" + this.type + ")", var8);
      } finally {
         StreamUtils.closeQuietly(output);
      }
   }

   public void writeBytes(byte[] bytes, int offset, int length, boolean append) {
      OutputStream output = this.write(append);

      try {
         output.write(bytes, offset, length);
      } catch (IOException var10) {
         throw new GdxRuntimeException("Error writing file: " + this.file + " (" + this.type + ")", var10);
      } finally {
         StreamUtils.closeQuietly(output);
      }
   }

   public FileHandle[] list() {
      if (this.type == Files.FileType.Classpath) {
         throw new GdxRuntimeException("Cannot list a classpath directory: " + this.file);
      } else {
         String[] relativePaths = this.file().list();
         if (relativePaths == null) {
            return new FileHandle[0];
         } else {
            FileHandle[] handles = new FileHandle[relativePaths.length];
            int i = 0;

            for (int n = relativePaths.length; i < n; i++) {
               handles[i] = this.child(relativePaths[i]);
            }

            return handles;
         }
      }
   }

   public FileHandle[] list(FileFilter filter) {
      if (this.type == Files.FileType.Classpath) {
         throw new GdxRuntimeException("Cannot list a classpath directory: " + this.file);
      } else {
         File file = this.file();
         String[] relativePaths = file.list();
         if (relativePaths == null) {
            return new FileHandle[0];
         } else {
            FileHandle[] handles = new FileHandle[relativePaths.length];
            int count = 0;
            int i = 0;

            for (int n = relativePaths.length; i < n; i++) {
               String path = relativePaths[i];
               FileHandle child = this.child(path);
               if (filter.accept(child.file())) {
                  handles[count] = child;
                  count++;
               }
            }

            if (count < relativePaths.length) {
               FileHandle[] newHandles = new FileHandle[count];
               System.arraycopy(handles, 0, newHandles, 0, count);
               handles = newHandles;
            }

            return handles;
         }
      }
   }

   public FileHandle[] list(FilenameFilter filter) {
      if (this.type == Files.FileType.Classpath) {
         throw new GdxRuntimeException("Cannot list a classpath directory: " + this.file);
      } else {
         File file = this.file();
         String[] relativePaths = file.list();
         if (relativePaths == null) {
            return new FileHandle[0];
         } else {
            FileHandle[] handles = new FileHandle[relativePaths.length];
            int count = 0;
            int i = 0;

            for (int n = relativePaths.length; i < n; i++) {
               String path = relativePaths[i];
               if (filter.accept(file, path)) {
                  handles[count] = this.child(path);
                  count++;
               }
            }

            if (count < relativePaths.length) {
               FileHandle[] newHandles = new FileHandle[count];
               System.arraycopy(handles, 0, newHandles, 0, count);
               handles = newHandles;
            }

            return handles;
         }
      }
   }

   public FileHandle[] list(String suffix) {
      if (this.type == Files.FileType.Classpath) {
         throw new GdxRuntimeException("Cannot list a classpath directory: " + this.file);
      } else {
         String[] relativePaths = this.file().list();
         if (relativePaths == null) {
            return new FileHandle[0];
         } else {
            FileHandle[] handles = new FileHandle[relativePaths.length];
            int count = 0;
            int i = 0;

            for (int n = relativePaths.length; i < n; i++) {
               String path = relativePaths[i];
               if (path.endsWith(suffix)) {
                  handles[count] = this.child(path);
                  count++;
               }
            }

            if (count < relativePaths.length) {
               FileHandle[] newHandles = new FileHandle[count];
               System.arraycopy(handles, 0, newHandles, 0, count);
               handles = newHandles;
            }

            return handles;
         }
      }
   }

   public boolean isDirectory() {
      return this.type == Files.FileType.Classpath ? false : this.file().isDirectory();
   }

   public FileHandle child(String name) {
      return this.file.getPath().length() == 0 ? new FileHandle(new File(name), this.type) : new FileHandle(new File(this.file, name), this.type);
   }

   public FileHandle sibling(String name) {
      if (this.file.getPath().length() == 0) {
         throw new GdxRuntimeException("Cannot get the sibling of the root.");
      } else {
         return new FileHandle(new File(this.file.getParent(), name), this.type);
      }
   }

   public FileHandle parent() {
      File parent = this.file.getParentFile();
      if (parent == null) {
         if (this.type == Files.FileType.Absolute) {
            parent = new File("/");
         } else {
            parent = new File("");
         }
      }

      return new FileHandle(parent, this.type);
   }

   public void mkdirs() {
      if (this.type == Files.FileType.Classpath) {
         throw new GdxRuntimeException("Cannot mkdirs with a classpath file: " + this.file);
      } else if (this.type == Files.FileType.Internal) {
         throw new GdxRuntimeException("Cannot mkdirs with an internal file: " + this.file);
      } else {
         this.file().mkdirs();
      }
   }

   public boolean exists() {
      switch (this.type) {
         case Internal:
            if (this.file().exists()) {
               return true;
            }
         case Classpath:
            return FileHandle.class.getResource("/" + this.file.getPath().replace('\\', '/')) != null;
         default:
            return this.file().exists();
      }
   }

   public boolean delete() {
      if (this.type == Files.FileType.Classpath) {
         throw new GdxRuntimeException("Cannot delete a classpath file: " + this.file);
      } else if (this.type == Files.FileType.Internal) {
         throw new GdxRuntimeException("Cannot delete an internal file: " + this.file);
      } else {
         return this.file().delete();
      }
   }

   public boolean deleteDirectory() {
      if (this.type == Files.FileType.Classpath) {
         throw new GdxRuntimeException("Cannot delete a classpath file: " + this.file);
      } else if (this.type == Files.FileType.Internal) {
         throw new GdxRuntimeException("Cannot delete an internal file: " + this.file);
      } else {
         return deleteDirectory(this.file());
      }
   }

   public void emptyDirectory() {
      this.emptyDirectory(false);
   }

   public void emptyDirectory(boolean preserveTree) {
      if (this.type == Files.FileType.Classpath) {
         throw new GdxRuntimeException("Cannot delete a classpath file: " + this.file);
      } else if (this.type == Files.FileType.Internal) {
         throw new GdxRuntimeException("Cannot delete an internal file: " + this.file);
      } else {
         emptyDirectory(this.file(), preserveTree);
      }
   }

   public void copyTo(FileHandle dest) {
      boolean sourceDir = this.isDirectory();
      if (!sourceDir) {
         if (dest.isDirectory()) {
            dest = dest.child(this.name());
         }

         copyFile(this, dest);
      } else {
         if (dest.exists()) {
            if (!dest.isDirectory()) {
               throw new GdxRuntimeException("Destination exists but is not a directory: " + dest);
            }
         } else {
            dest.mkdirs();
            if (!dest.isDirectory()) {
               throw new GdxRuntimeException("Destination directory cannot be created: " + dest);
            }
         }

         if (!sourceDir) {
            dest = dest.child(this.name());
         }

         copyDirectory(this, dest);
      }
   }

   public void moveTo(FileHandle dest) {
      if (this.type == Files.FileType.Classpath) {
         throw new GdxRuntimeException("Cannot move a classpath file: " + this.file);
      } else if (this.type == Files.FileType.Internal) {
         throw new GdxRuntimeException("Cannot move an internal file: " + this.file);
      } else {
         this.copyTo(dest);
         this.delete();
         if (this.exists() && this.isDirectory()) {
            this.deleteDirectory();
         }
      }
   }

   public long length() {
      if (this.type == Files.FileType.Classpath || this.type == Files.FileType.Internal && !this.file.exists()) {
         InputStream input = this.read();

         try {
            return input.available();
         } catch (Exception var7) {
         } finally {
            StreamUtils.closeQuietly(input);
         }

         return 0L;
      } else {
         return this.file().length();
      }
   }

   public long lastModified() {
      return this.file().lastModified();
   }

   @Override
   public boolean equals(Object obj) {
      if (!(obj instanceof FileHandle)) {
         return false;
      } else {
         FileHandle other = (FileHandle)obj;
         return this.type == other.type && this.path().equals(other.path());
      }
   }

   @Override
   public int hashCode() {
      int hash = 1;
      hash = hash * 37 + this.type.hashCode();
      return hash * 67 + this.path().hashCode();
   }

   @Override
   public String toString() {
      return this.file.getPath().replace('\\', '/');
   }

   public static FileHandle tempFile(String prefix) {
      try {
         return new FileHandle(File.createTempFile(prefix, null));
      } catch (IOException var2) {
         throw new GdxRuntimeException("Unable to create temp file.", var2);
      }
   }

   public static FileHandle tempDirectory(String prefix) {
      try {
         File file = File.createTempFile(prefix, null);
         if (!file.delete()) {
            throw new IOException("Unable to delete temp file: " + file);
         } else if (!file.mkdir()) {
            throw new IOException("Unable to create temp directory: " + file);
         } else {
            return new FileHandle(file);
         }
      } catch (IOException var2) {
         throw new GdxRuntimeException("Unable to create temp file.", var2);
      }
   }

   private static void emptyDirectory(File file, boolean preserveTree) {
      if (file.exists()) {
         File[] files = file.listFiles();
         if (files != null) {
            int i = 0;

            for (int n = files.length; i < n; i++) {
               if (!files[i].isDirectory()) {
                  files[i].delete();
               } else if (preserveTree) {
                  emptyDirectory(files[i], true);
               } else {
                  deleteDirectory(files[i]);
               }
            }
         }
      }
   }

   private static boolean deleteDirectory(File file) {
      emptyDirectory(file, false);
      return file.delete();
   }

   private static void copyFile(FileHandle source, FileHandle dest) {
      try {
         dest.write(source.read(), false);
      } catch (Exception var3) {
         throw new GdxRuntimeException(
            "Error copying source file: " + source.file + " (" + source.type + ")\nTo destination: " + dest.file + " (" + dest.type + ")", var3
         );
      }
   }

   private static void copyDirectory(FileHandle sourceDir, FileHandle destDir) {
      destDir.mkdirs();
      FileHandle[] files = sourceDir.list();
      int i = 0;

      for (int n = files.length; i < n; i++) {
         FileHandle srcFile = files[i];
         FileHandle destFile = destDir.child(srcFile.name());
         if (srcFile.isDirectory()) {
            copyDirectory(srcFile, destFile);
         } else {
            copyFile(srcFile, destFile);
         }
      }
   }
}
