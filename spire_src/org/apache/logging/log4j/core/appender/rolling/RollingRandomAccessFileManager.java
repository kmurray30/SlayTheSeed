package org.apache.logging.log4j.core.appender.rolling;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AppenderLoggingException;
import org.apache.logging.log4j.core.appender.ConfigurationFactoryData;
import org.apache.logging.log4j.core.appender.ManagerFactory;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.util.FileUtils;
import org.apache.logging.log4j.core.util.NullOutputStream;

public class RollingRandomAccessFileManager extends RollingFileManager {
   public static final int DEFAULT_BUFFER_SIZE = 262144;
   private static final RollingRandomAccessFileManager.RollingRandomAccessFileManagerFactory FACTORY = new RollingRandomAccessFileManager.RollingRandomAccessFileManagerFactory(
      
   );
   private RandomAccessFile randomAccessFile;

   @Deprecated
   public RollingRandomAccessFileManager(
      final LoggerContext loggerContext,
      final RandomAccessFile raf,
      final String fileName,
      final String pattern,
      final OutputStream os,
      final boolean append,
      final boolean immediateFlush,
      final int bufferSize,
      final long size,
      final long time,
      final TriggeringPolicy policy,
      final RolloverStrategy strategy,
      final String advertiseURI,
      final Layout<? extends Serializable> layout,
      final boolean writeHeader
   ) {
      this(
         loggerContext,
         raf,
         fileName,
         pattern,
         os,
         append,
         immediateFlush,
         bufferSize,
         size,
         time,
         policy,
         strategy,
         advertiseURI,
         layout,
         null,
         null,
         null,
         writeHeader
      );
   }

   public RollingRandomAccessFileManager(
      final LoggerContext loggerContext,
      final RandomAccessFile raf,
      final String fileName,
      final String pattern,
      final OutputStream os,
      final boolean append,
      final boolean immediateFlush,
      final int bufferSize,
      final long size,
      final long initialTime,
      final TriggeringPolicy policy,
      final RolloverStrategy strategy,
      final String advertiseURI,
      final Layout<? extends Serializable> layout,
      final String filePermissions,
      final String fileOwner,
      final String fileGroup,
      final boolean writeHeader
   ) {
      super(
         loggerContext,
         fileName,
         pattern,
         os,
         append,
         false,
         size,
         initialTime,
         policy,
         strategy,
         advertiseURI,
         layout,
         filePermissions,
         fileOwner,
         fileGroup,
         writeHeader,
         ByteBuffer.wrap(new byte[bufferSize])
      );
      this.randomAccessFile = raf;
      this.writeHeader();
   }

   private void writeHeader() {
      if (this.layout != null) {
         byte[] header = this.layout.getHeader();
         if (header != null) {
            try {
               if (this.randomAccessFile != null && this.randomAccessFile.length() == 0L) {
                  this.randomAccessFile.write(header, 0, header.length);
               }
            } catch (IOException var3) {
               this.logError("Unable to write header", var3);
            }
         }
      }
   }

   public static RollingRandomAccessFileManager getRollingRandomAccessFileManager(
      final String fileName,
      final String filePattern,
      final boolean isAppend,
      final boolean immediateFlush,
      final int bufferSize,
      final TriggeringPolicy policy,
      final RolloverStrategy strategy,
      final String advertiseURI,
      final Layout<? extends Serializable> layout,
      final String filePermissions,
      final String fileOwner,
      final String fileGroup,
      final Configuration configuration
   ) {
      if (strategy instanceof DirectWriteRolloverStrategy && fileName != null) {
         LOGGER.error("The fileName attribute must not be specified with the DirectWriteRolloverStrategy");
         return null;
      } else {
         String name = fileName == null ? filePattern : fileName;
         return narrow(
            RollingRandomAccessFileManager.class,
            getManager(
               name,
               new RollingRandomAccessFileManager.FactoryData(
                  fileName,
                  filePattern,
                  isAppend,
                  immediateFlush,
                  bufferSize,
                  policy,
                  strategy,
                  advertiseURI,
                  layout,
                  filePermissions,
                  fileOwner,
                  fileGroup,
                  configuration
               ),
               FACTORY
            )
         );
      }
   }

   @Deprecated
   public Boolean isEndOfBatch() {
      return Boolean.FALSE;
   }

   @Deprecated
   public void setEndOfBatch(final boolean endOfBatch) {
   }

   @Override
   protected synchronized void write(final byte[] bytes, final int offset, final int length, final boolean immediateFlush) {
      super.write(bytes, offset, length, immediateFlush);
   }

   @Override
   protected synchronized void writeToDestination(final byte[] bytes, final int offset, final int length) {
      try {
         if (this.randomAccessFile == null) {
            String fileName = this.getFileName();
            File file = new File(fileName);
            FileUtils.makeParentDirs(file);
            this.createFileAfterRollover(fileName);
         }

         this.randomAccessFile.write(bytes, offset, length);
         this.size += length;
      } catch (IOException var6) {
         String msg = "Error writing to RandomAccessFile " + this.getName();
         throw new AppenderLoggingException(msg, var6);
      }
   }

   @Override
   protected void createFileAfterRollover() throws IOException {
      this.createFileAfterRollover(this.getFileName());
   }

   private void createFileAfterRollover(final String fileName) throws IOException {
      this.randomAccessFile = new RandomAccessFile(fileName, "rw");
      if (this.isAttributeViewEnabled()) {
         this.defineAttributeView(Paths.get(fileName));
      }

      if (this.isAppend()) {
         this.randomAccessFile.seek(this.randomAccessFile.length());
      }

      this.writeHeader();
   }

   @Override
   public synchronized void flush() {
      this.flushBuffer(this.byteBuffer);
   }

   @Override
   public synchronized boolean closeOutputStream() {
      this.flush();
      if (this.randomAccessFile != null) {
         try {
            this.randomAccessFile.close();
            return true;
         } catch (IOException var2) {
            this.logError("Unable to close RandomAccessFile", var2);
            return false;
         }
      } else {
         return true;
      }
   }

   @Override
   public int getBufferSize() {
      return this.byteBuffer.capacity();
   }

   @Override
   public void updateData(final Object data) {
      RollingRandomAccessFileManager.FactoryData factoryData = (RollingRandomAccessFileManager.FactoryData)data;
      this.setRolloverStrategy(factoryData.getRolloverStrategy());
      this.setPatternProcessor(new PatternProcessor(factoryData.getPattern(), this.getPatternProcessor()));
      this.setTriggeringPolicy(factoryData.getTriggeringPolicy());
   }

   private static class FactoryData extends ConfigurationFactoryData {
      private final String fileName;
      private final String pattern;
      private final boolean append;
      private final boolean immediateFlush;
      private final int bufferSize;
      private final TriggeringPolicy policy;
      private final RolloverStrategy strategy;
      private final String advertiseURI;
      private final Layout<? extends Serializable> layout;
      private final String filePermissions;
      private final String fileOwner;
      private final String fileGroup;

      public FactoryData(
         final String fileName,
         final String pattern,
         final boolean append,
         final boolean immediateFlush,
         final int bufferSize,
         final TriggeringPolicy policy,
         final RolloverStrategy strategy,
         final String advertiseURI,
         final Layout<? extends Serializable> layout,
         final String filePermissions,
         final String fileOwner,
         final String fileGroup,
         final Configuration configuration
      ) {
         super(configuration);
         this.fileName = fileName;
         this.pattern = pattern;
         this.append = append;
         this.immediateFlush = immediateFlush;
         this.bufferSize = bufferSize;
         this.policy = policy;
         this.strategy = strategy;
         this.advertiseURI = advertiseURI;
         this.layout = layout;
         this.filePermissions = filePermissions;
         this.fileOwner = fileOwner;
         this.fileGroup = fileGroup;
      }

      public String getPattern() {
         return this.pattern;
      }

      public TriggeringPolicy getTriggeringPolicy() {
         return this.policy;
      }

      public RolloverStrategy getRolloverStrategy() {
         return this.strategy;
      }
   }

   private static class RollingRandomAccessFileManagerFactory
      implements ManagerFactory<RollingRandomAccessFileManager, RollingRandomAccessFileManager.FactoryData> {
      private RollingRandomAccessFileManagerFactory() {
      }

      public RollingRandomAccessFileManager createManager(final String name, final RollingRandomAccessFileManager.FactoryData data) {
         File file = null;
         long size = 0L;
         long time = System.currentTimeMillis();
         RandomAccessFile raf = null;
         if (data.fileName != null) {
            file = new File(name);
            if (!data.append) {
               file.delete();
            }

            size = data.append ? file.length() : 0L;
            if (file.exists()) {
               time = file.lastModified();
            }

            try {
               FileUtils.makeParentDirs(file);
               raf = new RandomAccessFile(name, "rw");
               if (data.append) {
                  long length = raf.length();
                  RollingRandomAccessFileManager.LOGGER.trace("RandomAccessFile {} seek to {}", name, length);
                  raf.seek(length);
               } else {
                  RollingRandomAccessFileManager.LOGGER.trace("RandomAccessFile {} set length to 0", name);
                  raf.setLength(0L);
               }
            } catch (IOException var12) {
               RollingRandomAccessFileManager.LOGGER.error("Cannot access RandomAccessFile " + var12, (Throwable)var12);
               if (raf != null) {
                  try {
                     raf.close();
                  } catch (IOException var11) {
                     RollingRandomAccessFileManager.LOGGER.error("Cannot close RandomAccessFile {}", name, var11);
                  }
               }

               return null;
            }
         }

         boolean writeHeader = !data.append || file == null || !file.exists();
         RollingRandomAccessFileManager rrm = new RollingRandomAccessFileManager(
            data.getLoggerContext(),
            raf,
            name,
            data.pattern,
            NullOutputStream.getInstance(),
            data.append,
            data.immediateFlush,
            data.bufferSize,
            size,
            time,
            data.policy,
            data.strategy,
            data.advertiseURI,
            data.layout,
            data.filePermissions,
            data.fileOwner,
            data.fileGroup,
            writeHeader
         );
         if (rrm.isAttributeViewEnabled()) {
            rrm.defineAttributeView(file.toPath());
         }

         return rrm;
      }
   }
}
