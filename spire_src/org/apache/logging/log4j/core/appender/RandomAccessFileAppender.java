package org.apache.logging.log4j.core.appender;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.core.net.Advertiser;
import org.apache.logging.log4j.core.util.Booleans;
import org.apache.logging.log4j.core.util.Integers;

@Plugin(name = "RandomAccessFile", category = "Core", elementType = "appender", printObject = true)
public final class RandomAccessFileAppender extends AbstractOutputStreamAppender<RandomAccessFileManager> {
   private final String fileName;
   private Object advertisement;
   private final Advertiser advertiser;

   private RandomAccessFileAppender(
      final String name,
      final Layout<? extends Serializable> layout,
      final Filter filter,
      final RandomAccessFileManager manager,
      final String filename,
      final boolean ignoreExceptions,
      final boolean immediateFlush,
      final Advertiser advertiser,
      final Property[] properties
   ) {
      super(name, layout, filter, ignoreExceptions, immediateFlush, properties, manager);
      if (advertiser != null) {
         Map<String, String> configuration = new HashMap<>(layout.getContentFormat());
         configuration.putAll(manager.getContentFormat());
         configuration.put("contentType", layout.getContentType());
         configuration.put("name", name);
         this.advertisement = advertiser.advertise(configuration);
      }

      this.fileName = filename;
      this.advertiser = advertiser;
   }

   @Override
   public boolean stop(final long timeout, final TimeUnit timeUnit) {
      this.setStopping();
      super.stop(timeout, timeUnit, false);
      if (this.advertiser != null) {
         this.advertiser.unadvertise(this.advertisement);
      }

      this.setStopped();
      return true;
   }

   public String getFileName() {
      return this.fileName;
   }

   public int getBufferSize() {
      return this.getManager().getBufferSize();
   }

   @Deprecated
   public static <B extends RandomAccessFileAppender.Builder<B>> RandomAccessFileAppender createAppender(
      final String fileName,
      final String append,
      final String name,
      final String immediateFlush,
      final String bufferSizeStr,
      final String ignore,
      final Layout<? extends Serializable> layout,
      final Filter filter,
      final String advertise,
      final String advertiseURI,
      final Configuration configuration
   ) {
      boolean isAppend = Booleans.parseBoolean(append, true);
      boolean isFlush = Booleans.parseBoolean(immediateFlush, true);
      boolean ignoreExceptions = Booleans.parseBoolean(ignore, true);
      boolean isAdvertise = Boolean.parseBoolean(advertise);
      int bufferSize = Integers.parseInt(bufferSizeStr, 262144);
      return ((RandomAccessFileAppender.Builder)((RandomAccessFileAppender.Builder)((RandomAccessFileAppender.Builder)((RandomAccessFileAppender.Builder)((RandomAccessFileAppender.Builder)((RandomAccessFileAppender.Builder)((RandomAccessFileAppender.Builder)newBuilder()
                              .setAdvertise(isAdvertise)
                              .setAdvertiseURI(advertiseURI)
                              .setAppend(isAppend)
                              .withBufferSize(bufferSize))
                           .setConfiguration(configuration))
                        .setFileName(fileName)
                        .setFilter(filter))
                     .setIgnoreExceptions(ignoreExceptions))
                  .withImmediateFlush(isFlush))
               .setLayout(layout))
            .setName(name))
         .build();
   }

   @PluginBuilderFactory
   public static <B extends RandomAccessFileAppender.Builder<B>> B newBuilder() {
      return new RandomAccessFileAppender.Builder<B>().asBuilder();
   }

   public static class Builder<B extends RandomAccessFileAppender.Builder<B>>
      extends AbstractOutputStreamAppender.Builder<B>
      implements org.apache.logging.log4j.core.util.Builder<RandomAccessFileAppender> {
      @PluginBuilderAttribute("fileName")
      private String fileName;
      @PluginBuilderAttribute("append")
      private boolean append = true;
      @PluginBuilderAttribute("advertise")
      private boolean advertise;
      @PluginBuilderAttribute("advertiseURI")
      private String advertiseURI;

      public Builder() {
         this.withBufferSize(262144);
      }

      public RandomAccessFileAppender build() {
         String name = this.getName();
         if (name == null) {
            RandomAccessFileAppender.LOGGER.error("No name provided for RandomAccessFileAppender");
            return null;
         } else if (this.fileName == null) {
            RandomAccessFileAppender.LOGGER.error("No filename provided for RandomAccessFileAppender with name {}", name);
            return null;
         } else {
            Layout<? extends Serializable> layout = this.getOrCreateLayout();
            boolean immediateFlush = this.isImmediateFlush();
            RandomAccessFileManager manager = RandomAccessFileManager.getFileManager(
               this.fileName, this.append, immediateFlush, this.getBufferSize(), this.advertiseURI, layout, null
            );
            return manager == null
               ? null
               : new RandomAccessFileAppender(
                  name,
                  layout,
                  this.getFilter(),
                  manager,
                  this.fileName,
                  this.isIgnoreExceptions(),
                  immediateFlush,
                  this.advertise ? this.getConfiguration().getAdvertiser() : null,
                  this.getPropertyArray()
               );
         }
      }

      public B setFileName(final String fileName) {
         this.fileName = fileName;
         return this.asBuilder();
      }

      public B setAppend(final boolean append) {
         this.append = append;
         return this.asBuilder();
      }

      public B setAdvertise(final boolean advertise) {
         this.advertise = advertise;
         return this.asBuilder();
      }

      public B setAdvertiseURI(final String advertiseURI) {
         this.advertiseURI = advertiseURI;
         return this.asBuilder();
      }
   }
}
