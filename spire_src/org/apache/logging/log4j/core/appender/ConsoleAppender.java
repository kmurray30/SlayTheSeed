package org.apache.logging.log4j.core.appender;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.core.config.plugins.validation.constraints.Required;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.core.util.Booleans;
import org.apache.logging.log4j.core.util.CloseShieldOutputStream;
import org.apache.logging.log4j.core.util.Loader;
import org.apache.logging.log4j.core.util.Throwables;
import org.apache.logging.log4j.util.PropertiesUtil;

@Plugin(name = "Console", category = "Core", elementType = "appender", printObject = true)
public final class ConsoleAppender extends AbstractOutputStreamAppender<OutputStreamManager> {
   public static final String PLUGIN_NAME = "Console";
   private static final String JANSI_CLASS = "org.fusesource.jansi.WindowsAnsiOutputStream";
   private static ConsoleAppender.ConsoleManagerFactory factory = new ConsoleAppender.ConsoleManagerFactory();
   private static final ConsoleAppender.Target DEFAULT_TARGET = ConsoleAppender.Target.SYSTEM_OUT;
   private static final AtomicInteger COUNT = new AtomicInteger();
   private final ConsoleAppender.Target target;

   private ConsoleAppender(
      final String name,
      final Layout<? extends Serializable> layout,
      final Filter filter,
      final OutputStreamManager manager,
      final boolean ignoreExceptions,
      final ConsoleAppender.Target target,
      final Property[] properties
   ) {
      super(name, layout, filter, ignoreExceptions, true, properties, manager);
      this.target = target;
   }

   @Deprecated
   public static ConsoleAppender createAppender(
      Layout<? extends Serializable> layout, final Filter filter, final String targetStr, final String name, final String follow, final String ignore
   ) {
      if (name == null) {
         LOGGER.error("No name provided for ConsoleAppender");
         return null;
      } else {
         if (layout == null) {
            layout = PatternLayout.createDefaultLayout();
         }

         boolean isFollow = Boolean.parseBoolean(follow);
         boolean ignoreExceptions = Booleans.parseBoolean(ignore, true);
         ConsoleAppender.Target target = targetStr == null ? DEFAULT_TARGET : ConsoleAppender.Target.valueOf(targetStr);
         return new ConsoleAppender(name, layout, filter, getManager(target, isFollow, false, layout), ignoreExceptions, target, null);
      }
   }

   @Deprecated
   public static ConsoleAppender createAppender(
      Layout<? extends Serializable> layout,
      final Filter filter,
      ConsoleAppender.Target target,
      final String name,
      final boolean follow,
      final boolean direct,
      final boolean ignoreExceptions
   ) {
      if (name == null) {
         LOGGER.error("No name provided for ConsoleAppender");
         return null;
      } else {
         if (layout == null) {
            layout = PatternLayout.createDefaultLayout();
         }

         target = target == null ? ConsoleAppender.Target.SYSTEM_OUT : target;
         if (follow && direct) {
            LOGGER.error("Cannot use both follow and direct on ConsoleAppender");
            return null;
         } else {
            return new ConsoleAppender(name, layout, filter, getManager(target, follow, direct, layout), ignoreExceptions, target, null);
         }
      }
   }

   public static ConsoleAppender createDefaultAppenderForLayout(final Layout<? extends Serializable> layout) {
      return new ConsoleAppender(
         "DefaultConsole-" + COUNT.incrementAndGet(), layout, null, getDefaultManager(DEFAULT_TARGET, false, false, layout), true, DEFAULT_TARGET, null
      );
   }

   @PluginBuilderFactory
   public static <B extends ConsoleAppender.Builder<B>> B newBuilder() {
      return new ConsoleAppender.Builder<B>().asBuilder();
   }

   private static OutputStreamManager getDefaultManager(
      final ConsoleAppender.Target target, final boolean follow, final boolean direct, final Layout<? extends Serializable> layout
   ) {
      OutputStream os = getOutputStream(follow, direct, target);
      String managerName = target.name() + '.' + follow + '.' + direct + "-" + COUNT.get();
      return OutputStreamManager.getManager(managerName, new ConsoleAppender.FactoryData(os, managerName, layout), factory);
   }

   private static OutputStreamManager getManager(
      final ConsoleAppender.Target target, final boolean follow, final boolean direct, final Layout<? extends Serializable> layout
   ) {
      OutputStream os = getOutputStream(follow, direct, target);
      String managerName = target.name() + '.' + follow + '.' + direct;
      return OutputStreamManager.getManager(managerName, new ConsoleAppender.FactoryData(os, managerName, layout), factory);
   }

   private static OutputStream getOutputStream(final boolean follow, final boolean direct, final ConsoleAppender.Target target) {
      String enc = Charset.defaultCharset().name();

      CloseShieldOutputStream var12;
      try {
         OutputStream outputStream = (OutputStream)(target == ConsoleAppender.Target.SYSTEM_OUT
            ? (direct ? new FileOutputStream(FileDescriptor.out) : (follow ? new PrintStream(new ConsoleAppender.SystemOutStream(), true, enc) : System.out))
            : (direct ? new FileOutputStream(FileDescriptor.err) : (follow ? new PrintStream(new ConsoleAppender.SystemErrStream(), true, enc) : System.err)));
         var12 = new CloseShieldOutputStream(outputStream);
      } catch (UnsupportedEncodingException var11) {
         throw new IllegalStateException("Unsupported default encoding " + enc, var11);
      }

      PropertiesUtil propsUtil = PropertiesUtil.getProperties();
      if (propsUtil.isOsWindows() && !propsUtil.getBooleanProperty("log4j.skipJansi", true) && !direct) {
         try {
            Class<?> clazz = Loader.loadClass("org.fusesource.jansi.WindowsAnsiOutputStream");
            Constructor<?> constructor = clazz.getConstructor(OutputStream.class);
            return new CloseShieldOutputStream((OutputStream)constructor.newInstance(var12));
         } catch (ClassNotFoundException var8) {
            LOGGER.debug("Jansi is not installed, cannot find {}", "org.fusesource.jansi.WindowsAnsiOutputStream");
         } catch (NoSuchMethodException var9) {
            LOGGER.warn("{} is missing the proper constructor", "org.fusesource.jansi.WindowsAnsiOutputStream");
         } catch (Exception var10) {
            LOGGER.warn(
               "Unable to instantiate {} due to {}", "org.fusesource.jansi.WindowsAnsiOutputStream", clean(Throwables.getRootCause(var10).toString()).trim()
            );
         }

         return var12;
      } else {
         return var12;
      }
   }

   private static String clean(final String string) {
      return string.replace('\u0000', ' ');
   }

   public ConsoleAppender.Target getTarget() {
      return this.target;
   }

   public static class Builder<B extends ConsoleAppender.Builder<B>>
      extends AbstractOutputStreamAppender.Builder<B>
      implements org.apache.logging.log4j.core.util.Builder<ConsoleAppender> {
      @PluginBuilderAttribute
      @Required
      private ConsoleAppender.Target target = ConsoleAppender.DEFAULT_TARGET;
      @PluginBuilderAttribute
      private boolean follow;
      @PluginBuilderAttribute
      private boolean direct;

      public B setTarget(final ConsoleAppender.Target aTarget) {
         this.target = aTarget;
         return this.asBuilder();
      }

      public B setFollow(final boolean shouldFollow) {
         this.follow = shouldFollow;
         return this.asBuilder();
      }

      public B setDirect(final boolean shouldDirect) {
         this.direct = shouldDirect;
         return this.asBuilder();
      }

      public ConsoleAppender build() {
         if (this.follow && this.direct) {
            throw new IllegalArgumentException("Cannot use both follow and direct on ConsoleAppender '" + this.getName() + "'");
         } else {
            Layout<? extends Serializable> layout = this.getOrCreateLayout(this.target.getDefaultCharset());
            return new ConsoleAppender(
               this.getName(),
               layout,
               this.getFilter(),
               ConsoleAppender.getManager(this.target, this.follow, this.direct, layout),
               this.isIgnoreExceptions(),
               this.target,
               this.getPropertyArray()
            );
         }
      }
   }

   private static class ConsoleManagerFactory implements ManagerFactory<OutputStreamManager, ConsoleAppender.FactoryData> {
      private ConsoleManagerFactory() {
      }

      public OutputStreamManager createManager(final String name, final ConsoleAppender.FactoryData data) {
         return new OutputStreamManager(data.os, data.name, data.layout, true);
      }
   }

   private static class FactoryData {
      private final OutputStream os;
      private final String name;
      private final Layout<? extends Serializable> layout;

      public FactoryData(final OutputStream os, final String type, final Layout<? extends Serializable> layout) {
         this.os = os;
         this.name = type;
         this.layout = layout;
      }
   }

   private static class SystemErrStream extends OutputStream {
      public SystemErrStream() {
      }

      @Override
      public void close() {
      }

      @Override
      public void flush() {
         System.err.flush();
      }

      @Override
      public void write(final byte[] b) throws IOException {
         System.err.write(b);
      }

      @Override
      public void write(final byte[] b, final int off, final int len) throws IOException {
         System.err.write(b, off, len);
      }

      @Override
      public void write(final int b) {
         System.err.write(b);
      }
   }

   private static class SystemOutStream extends OutputStream {
      public SystemOutStream() {
      }

      @Override
      public void close() {
      }

      @Override
      public void flush() {
         System.out.flush();
      }

      @Override
      public void write(final byte[] b) throws IOException {
         System.out.write(b);
      }

      @Override
      public void write(final byte[] b, final int off, final int len) throws IOException {
         System.out.write(b, off, len);
      }

      @Override
      public void write(final int b) throws IOException {
         System.out.write(b);
      }
   }

   public static enum Target {
      SYSTEM_OUT {
         @Override
         public Charset getDefaultCharset() {
            return this.getCharset("sun.stdout.encoding", Charset.defaultCharset());
         }
      },
      SYSTEM_ERR {
         @Override
         public Charset getDefaultCharset() {
            return this.getCharset("sun.stderr.encoding", Charset.defaultCharset());
         }
      };

      private Target() {
      }

      public abstract Charset getDefaultCharset();

      protected Charset getCharset(final String property, final Charset defaultCharset) {
         return new PropertiesUtil(PropertiesUtil.getSystemProperties()).getCharsetProperty(property, defaultCharset);
      }
   }
}
