package org.apache.logging.log4j.spi;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogBuilder;
import org.apache.logging.log4j.LoggingException;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.internal.DefaultLogBuilder;
import org.apache.logging.log4j.message.DefaultFlowMessageFactory;
import org.apache.logging.log4j.message.EntryMessage;
import org.apache.logging.log4j.message.FlowMessageFactory;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.MessageFactory;
import org.apache.logging.log4j.message.MessageFactory2;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.apache.logging.log4j.message.ParameterizedMessageFactory;
import org.apache.logging.log4j.message.ReusableMessageFactory;
import org.apache.logging.log4j.message.SimpleMessage;
import org.apache.logging.log4j.message.StringFormattedMessage;
import org.apache.logging.log4j.status.StatusLogger;
import org.apache.logging.log4j.util.Constants;
import org.apache.logging.log4j.util.LambdaUtil;
import org.apache.logging.log4j.util.LoaderUtil;
import org.apache.logging.log4j.util.MessageSupplier;
import org.apache.logging.log4j.util.PerformanceSensitive;
import org.apache.logging.log4j.util.PropertiesUtil;
import org.apache.logging.log4j.util.StackLocatorUtil;
import org.apache.logging.log4j.util.Strings;
import org.apache.logging.log4j.util.Supplier;

public abstract class AbstractLogger implements ExtendedLogger, LocationAwareLogger, Serializable {
   public static final Marker FLOW_MARKER = MarkerManager.getMarker("FLOW");
   public static final Marker ENTRY_MARKER = MarkerManager.getMarker("ENTER").setParents(FLOW_MARKER);
   public static final Marker EXIT_MARKER = MarkerManager.getMarker("EXIT").setParents(FLOW_MARKER);
   public static final Marker EXCEPTION_MARKER = MarkerManager.getMarker("EXCEPTION");
   public static final Marker THROWING_MARKER = MarkerManager.getMarker("THROWING").setParents(EXCEPTION_MARKER);
   public static final Marker CATCHING_MARKER = MarkerManager.getMarker("CATCHING").setParents(EXCEPTION_MARKER);
   public static final Class<? extends MessageFactory> DEFAULT_MESSAGE_FACTORY_CLASS = createClassForProperty(
      "log4j2.messageFactory", ReusableMessageFactory.class, ParameterizedMessageFactory.class
   );
   public static final Class<? extends FlowMessageFactory> DEFAULT_FLOW_MESSAGE_FACTORY_CLASS = createFlowClassForProperty(
      "log4j2.flowMessageFactory", DefaultFlowMessageFactory.class
   );
   private static final long serialVersionUID = 2L;
   private static final String FQCN = AbstractLogger.class.getName();
   private static final String THROWING = "Throwing";
   private static final String CATCHING = "Catching";
   protected final String name;
   private final MessageFactory2 messageFactory;
   private final FlowMessageFactory flowMessageFactory;
   private static final ThreadLocal<int[]> recursionDepthHolder = new ThreadLocal<>();
   protected final transient ThreadLocal<DefaultLogBuilder> logBuilder;

   public AbstractLogger() {
      this.name = this.getClass().getName();
      this.messageFactory = createDefaultMessageFactory();
      this.flowMessageFactory = createDefaultFlowMessageFactory();
      this.logBuilder = new AbstractLogger.LocalLogBuilder(this);
   }

   public AbstractLogger(final String name) {
      this(name, createDefaultMessageFactory());
   }

   public AbstractLogger(final String name, final MessageFactory messageFactory) {
      this.name = name;
      this.messageFactory = messageFactory == null ? createDefaultMessageFactory() : narrow(messageFactory);
      this.flowMessageFactory = createDefaultFlowMessageFactory();
      this.logBuilder = new AbstractLogger.LocalLogBuilder(this);
   }

   public static void checkMessageFactory(final ExtendedLogger logger, final MessageFactory messageFactory) {
      String name = logger.getName();
      MessageFactory loggerMessageFactory = logger.getMessageFactory();
      if (messageFactory != null && !loggerMessageFactory.equals(messageFactory)) {
         StatusLogger.getLogger()
            .warn(
               "The Logger {} was created with the message factory {} and is now requested with the message factory {}, which may create log events with unexpected formatting.",
               name,
               loggerMessageFactory,
               messageFactory
            );
      } else if (messageFactory == null && !loggerMessageFactory.getClass().equals(DEFAULT_MESSAGE_FACTORY_CLASS)) {
         StatusLogger.getLogger()
            .warn(
               "The Logger {} was created with the message factory {} and is now requested with a null message factory (defaults to {}), which may create log events with unexpected formatting.",
               name,
               loggerMessageFactory,
               DEFAULT_MESSAGE_FACTORY_CLASS.getName()
            );
      }
   }

   @Override
   public void catching(final Level level, final Throwable throwable) {
      this.catching(FQCN, level, throwable);
   }

   protected void catching(final String fqcn, final Level level, final Throwable throwable) {
      if (this.isEnabled(level, CATCHING_MARKER, null, null)) {
         this.logMessageSafely(fqcn, level, CATCHING_MARKER, this.catchingMsg(throwable), throwable);
      }
   }

   @Override
   public void catching(final Throwable throwable) {
      if (this.isEnabled(Level.ERROR, CATCHING_MARKER, null, null)) {
         this.logMessageSafely(FQCN, Level.ERROR, CATCHING_MARKER, this.catchingMsg(throwable), throwable);
      }
   }

   protected Message catchingMsg(final Throwable throwable) {
      return this.messageFactory.newMessage("Catching");
   }

   private static Class<? extends MessageFactory> createClassForProperty(
      final String property,
      final Class<ReusableMessageFactory> reusableParameterizedMessageFactoryClass,
      final Class<ParameterizedMessageFactory> parameterizedMessageFactoryClass
   ) {
      try {
         String fallback = Constants.ENABLE_THREADLOCALS ? reusableParameterizedMessageFactoryClass.getName() : parameterizedMessageFactoryClass.getName();
         String clsName = PropertiesUtil.getProperties().getStringProperty(property, fallback);
         return LoaderUtil.loadClass(clsName).asSubclass(MessageFactory.class);
      } catch (Throwable var5) {
         return parameterizedMessageFactoryClass;
      }
   }

   private static Class<? extends FlowMessageFactory> createFlowClassForProperty(
      final String property, final Class<DefaultFlowMessageFactory> defaultFlowMessageFactoryClass
   ) {
      try {
         String clsName = PropertiesUtil.getProperties().getStringProperty(property, defaultFlowMessageFactoryClass.getName());
         return LoaderUtil.loadClass(clsName).asSubclass(FlowMessageFactory.class);
      } catch (Throwable var3) {
         return defaultFlowMessageFactoryClass;
      }
   }

   private static MessageFactory2 createDefaultMessageFactory() {
      try {
         MessageFactory result = DEFAULT_MESSAGE_FACTORY_CLASS.newInstance();
         return narrow(result);
      } catch (IllegalAccessException | InstantiationException var1) {
         throw new IllegalStateException(var1);
      }
   }

   private static MessageFactory2 narrow(final MessageFactory result) {
      return (MessageFactory2)(result instanceof MessageFactory2 ? (MessageFactory2)result : new MessageFactory2Adapter(result));
   }

   private static FlowMessageFactory createDefaultFlowMessageFactory() {
      try {
         return DEFAULT_FLOW_MESSAGE_FACTORY_CLASS.newInstance();
      } catch (IllegalAccessException | InstantiationException var1) {
         throw new IllegalStateException(var1);
      }
   }

   @Override
   public void debug(final Marker marker, final CharSequence message) {
      this.logIfEnabled(FQCN, Level.DEBUG, marker, message, null);
   }

   @Override
   public void debug(final Marker marker, final CharSequence message, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.DEBUG, marker, message, throwable);
   }

   @Override
   public void debug(final Marker marker, final Message message) {
      this.logIfEnabled(FQCN, Level.DEBUG, marker, message, message != null ? message.getThrowable() : null);
   }

   @Override
   public void debug(final Marker marker, final Message message, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.DEBUG, marker, message, throwable);
   }

   @Override
   public void debug(final Marker marker, final Object message) {
      this.logIfEnabled(FQCN, Level.DEBUG, marker, message, null);
   }

   @Override
   public void debug(final Marker marker, final Object message, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.DEBUG, marker, message, throwable);
   }

   @Override
   public void debug(final Marker marker, final String message) {
      this.logIfEnabled(FQCN, Level.DEBUG, marker, message, (Throwable)null);
   }

   @Override
   public void debug(final Marker marker, final String message, final Object... params) {
      this.logIfEnabled(FQCN, Level.DEBUG, marker, message, params);
   }

   @Override
   public void debug(final Marker marker, final String message, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.DEBUG, marker, message, throwable);
   }

   @Override
   public void debug(final Message message) {
      this.logIfEnabled(FQCN, Level.DEBUG, null, message, message != null ? message.getThrowable() : null);
   }

   @Override
   public void debug(final Message message, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.DEBUG, null, message, throwable);
   }

   @Override
   public void debug(final CharSequence message) {
      this.logIfEnabled(FQCN, Level.DEBUG, null, message, null);
   }

   @Override
   public void debug(final CharSequence message, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.DEBUG, null, message, throwable);
   }

   @Override
   public void debug(final Object message) {
      this.logIfEnabled(FQCN, Level.DEBUG, null, message, null);
   }

   @Override
   public void debug(final Object message, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.DEBUG, null, message, throwable);
   }

   @Override
   public void debug(final String message) {
      this.logIfEnabled(FQCN, Level.DEBUG, null, message, (Throwable)null);
   }

   @Override
   public void debug(final String message, final Object... params) {
      this.logIfEnabled(FQCN, Level.DEBUG, null, message, params);
   }

   @Override
   public void debug(final String message, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.DEBUG, null, message, throwable);
   }

   @Override
   public void debug(final Supplier<?> messageSupplier) {
      this.logIfEnabled(FQCN, Level.DEBUG, null, messageSupplier, (Throwable)null);
   }

   @Override
   public void debug(final Supplier<?> messageSupplier, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.DEBUG, null, messageSupplier, throwable);
   }

   @Override
   public void debug(final Marker marker, final Supplier<?> messageSupplier) {
      this.logIfEnabled(FQCN, Level.DEBUG, marker, messageSupplier, (Throwable)null);
   }

   @Override
   public void debug(final Marker marker, final String message, final Supplier<?>... paramSuppliers) {
      this.logIfEnabled(FQCN, Level.DEBUG, marker, message, paramSuppliers);
   }

   @Override
   public void debug(final Marker marker, final Supplier<?> messageSupplier, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.DEBUG, marker, messageSupplier, throwable);
   }

   @Override
   public void debug(final String message, final Supplier<?>... paramSuppliers) {
      this.logIfEnabled(FQCN, Level.DEBUG, null, message, paramSuppliers);
   }

   @Override
   public void debug(final Marker marker, final MessageSupplier messageSupplier) {
      this.logIfEnabled(FQCN, Level.DEBUG, marker, messageSupplier, (Throwable)null);
   }

   @Override
   public void debug(final Marker marker, final MessageSupplier messageSupplier, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.DEBUG, marker, messageSupplier, throwable);
   }

   @Override
   public void debug(final MessageSupplier messageSupplier) {
      this.logIfEnabled(FQCN, Level.DEBUG, null, messageSupplier, (Throwable)null);
   }

   @Override
   public void debug(final MessageSupplier messageSupplier, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.DEBUG, null, messageSupplier, throwable);
   }

   @Override
   public void debug(final Marker marker, final String message, final Object p0) {
      this.logIfEnabled(FQCN, Level.DEBUG, marker, message, p0);
   }

   @Override
   public void debug(final Marker marker, final String message, final Object p0, final Object p1) {
      this.logIfEnabled(FQCN, Level.DEBUG, marker, message, p0, p1);
   }

   @Override
   public void debug(final Marker marker, final String message, final Object p0, final Object p1, final Object p2) {
      this.logIfEnabled(FQCN, Level.DEBUG, marker, message, p0, p1, p2);
   }

   @Override
   public void debug(final Marker marker, final String message, final Object p0, final Object p1, final Object p2, final Object p3) {
      this.logIfEnabled(FQCN, Level.DEBUG, marker, message, p0, p1, p2, p3);
   }

   @Override
   public void debug(final Marker marker, final String message, final Object p0, final Object p1, final Object p2, final Object p3, final Object p4) {
      this.logIfEnabled(FQCN, Level.DEBUG, marker, message, p0, p1, p2, p3, p4);
   }

   @Override
   public void debug(
      final Marker marker, final String message, final Object p0, final Object p1, final Object p2, final Object p3, final Object p4, final Object p5
   ) {
      this.logIfEnabled(FQCN, Level.DEBUG, marker, message, p0, p1, p2, p3, p4, p5);
   }

   @Override
   public void debug(
      final Marker marker,
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6
   ) {
      this.logIfEnabled(FQCN, Level.DEBUG, marker, message, p0, p1, p2, p3, p4, p5, p6);
   }

   @Override
   public void debug(
      final Marker marker,
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6,
      final Object p7
   ) {
      this.logIfEnabled(FQCN, Level.DEBUG, marker, message, p0, p1, p2, p3, p4, p5, p6, p7);
   }

   @Override
   public void debug(
      final Marker marker,
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6,
      final Object p7,
      final Object p8
   ) {
      this.logIfEnabled(FQCN, Level.DEBUG, marker, message, p0, p1, p2, p3, p4, p5, p6, p7, p8);
   }

   @Override
   public void debug(
      final Marker marker,
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6,
      final Object p7,
      final Object p8,
      final Object p9
   ) {
      this.logIfEnabled(FQCN, Level.DEBUG, marker, message, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9);
   }

   @Override
   public void debug(final String message, final Object p0) {
      this.logIfEnabled(FQCN, Level.DEBUG, null, message, p0);
   }

   @Override
   public void debug(final String message, final Object p0, final Object p1) {
      this.logIfEnabled(FQCN, Level.DEBUG, null, message, p0, p1);
   }

   @Override
   public void debug(final String message, final Object p0, final Object p1, final Object p2) {
      this.logIfEnabled(FQCN, Level.DEBUG, null, message, p0, p1, p2);
   }

   @Override
   public void debug(final String message, final Object p0, final Object p1, final Object p2, final Object p3) {
      this.logIfEnabled(FQCN, Level.DEBUG, null, message, p0, p1, p2, p3);
   }

   @Override
   public void debug(final String message, final Object p0, final Object p1, final Object p2, final Object p3, final Object p4) {
      this.logIfEnabled(FQCN, Level.DEBUG, null, message, p0, p1, p2, p3, p4);
   }

   @Override
   public void debug(final String message, final Object p0, final Object p1, final Object p2, final Object p3, final Object p4, final Object p5) {
      this.logIfEnabled(FQCN, Level.DEBUG, null, message, p0, p1, p2, p3, p4, p5);
   }

   @Override
   public void debug(
      final String message, final Object p0, final Object p1, final Object p2, final Object p3, final Object p4, final Object p5, final Object p6
   ) {
      this.logIfEnabled(FQCN, Level.DEBUG, null, message, p0, p1, p2, p3, p4, p5, p6);
   }

   @Override
   public void debug(
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6,
      final Object p7
   ) {
      this.logIfEnabled(FQCN, Level.DEBUG, null, message, p0, p1, p2, p3, p4, p5, p6, p7);
   }

   @Override
   public void debug(
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6,
      final Object p7,
      final Object p8
   ) {
      this.logIfEnabled(FQCN, Level.DEBUG, null, message, p0, p1, p2, p3, p4, p5, p6, p7, p8);
   }

   @Override
   public void debug(
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6,
      final Object p7,
      final Object p8,
      final Object p9
   ) {
      this.logIfEnabled(FQCN, Level.DEBUG, null, message, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9);
   }

   protected EntryMessage enter(final String fqcn, final String format, final Supplier<?>... paramSuppliers) {
      EntryMessage entryMsg = null;
      if (this.isEnabled(Level.TRACE, ENTRY_MARKER, null, null)) {
         this.logMessageSafely(fqcn, Level.TRACE, ENTRY_MARKER, entryMsg = this.entryMsg(format, paramSuppliers), null);
      }

      return entryMsg;
   }

   @Deprecated
   protected EntryMessage enter(final String fqcn, final String format, final MessageSupplier... paramSuppliers) {
      EntryMessage entryMsg = null;
      if (this.isEnabled(Level.TRACE, ENTRY_MARKER, null, null)) {
         this.logMessageSafely(fqcn, Level.TRACE, ENTRY_MARKER, entryMsg = this.entryMsg(format, paramSuppliers), null);
      }

      return entryMsg;
   }

   protected EntryMessage enter(final String fqcn, final String format, final Object... params) {
      EntryMessage entryMsg = null;
      if (this.isEnabled(Level.TRACE, ENTRY_MARKER, null, null)) {
         this.logMessageSafely(fqcn, Level.TRACE, ENTRY_MARKER, entryMsg = this.entryMsg(format, params), null);
      }

      return entryMsg;
   }

   @Deprecated
   protected EntryMessage enter(final String fqcn, final MessageSupplier messageSupplier) {
      EntryMessage message = null;
      if (this.isEnabled(Level.TRACE, ENTRY_MARKER, null, null)) {
         this.logMessageSafely(fqcn, Level.TRACE, ENTRY_MARKER, message = this.flowMessageFactory.newEntryMessage(messageSupplier.get()), null);
      }

      return message;
   }

   protected EntryMessage enter(final String fqcn, final Message message) {
      EntryMessage flowMessage = null;
      if (this.isEnabled(Level.TRACE, ENTRY_MARKER, null, null)) {
         this.logMessageSafely(fqcn, Level.TRACE, ENTRY_MARKER, flowMessage = this.flowMessageFactory.newEntryMessage(message), null);
      }

      return flowMessage;
   }

   @Deprecated
   @Override
   public void entry() {
      this.entry(FQCN, (Object[])null);
   }

   @Override
   public void entry(final Object... params) {
      this.entry(FQCN, params);
   }

   protected void entry(final String fqcn, final Object... params) {
      if (this.isEnabled(Level.TRACE, ENTRY_MARKER, null, null)) {
         if (params == null) {
            this.logMessageSafely(fqcn, Level.TRACE, ENTRY_MARKER, this.entryMsg(null, (Supplier<?>[])null), null);
         } else {
            this.logMessageSafely(fqcn, Level.TRACE, ENTRY_MARKER, this.entryMsg(null, params), null);
         }
      }
   }

   protected EntryMessage entryMsg(final String format, final Object... params) {
      int count = params == null ? 0 : params.length;
      if (count == 0) {
         return Strings.isEmpty(format) ? this.flowMessageFactory.newEntryMessage(null) : this.flowMessageFactory.newEntryMessage(new SimpleMessage(format));
      } else if (format != null) {
         return this.flowMessageFactory.newEntryMessage(new ParameterizedMessage(format, params));
      } else {
         StringBuilder sb = new StringBuilder();
         sb.append("params(");

         for (int i = 0; i < count; i++) {
            if (i > 0) {
               sb.append(", ");
            }

            Object parm = params[i];
            sb.append(parm instanceof Message ? ((Message)parm).getFormattedMessage() : String.valueOf(parm));
         }

         sb.append(')');
         return this.flowMessageFactory.newEntryMessage(new SimpleMessage(sb));
      }
   }

   protected EntryMessage entryMsg(final String format, final MessageSupplier... paramSuppliers) {
      int count = paramSuppliers == null ? 0 : paramSuppliers.length;
      Object[] params = new Object[count];

      for (int i = 0; i < count; i++) {
         params[i] = paramSuppliers[i].get();
         params[i] = params[i] != null ? ((Message)params[i]).getFormattedMessage() : null;
      }

      return this.entryMsg(format, params);
   }

   protected EntryMessage entryMsg(final String format, final Supplier<?>... paramSuppliers) {
      int count = paramSuppliers == null ? 0 : paramSuppliers.length;
      Object[] params = new Object[count];

      for (int i = 0; i < count; i++) {
         params[i] = paramSuppliers[i].get();
         if (params[i] instanceof Message) {
            params[i] = ((Message)params[i]).getFormattedMessage();
         }
      }

      return this.entryMsg(format, params);
   }

   @Override
   public void error(final Marker marker, final Message message) {
      this.logIfEnabled(FQCN, Level.ERROR, marker, message, message != null ? message.getThrowable() : null);
   }

   @Override
   public void error(final Marker marker, final Message message, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.ERROR, marker, message, throwable);
   }

   @Override
   public void error(final Marker marker, final CharSequence message) {
      this.logIfEnabled(FQCN, Level.ERROR, marker, message, null);
   }

   @Override
   public void error(final Marker marker, final CharSequence message, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.ERROR, marker, message, throwable);
   }

   @Override
   public void error(final Marker marker, final Object message) {
      this.logIfEnabled(FQCN, Level.ERROR, marker, message, null);
   }

   @Override
   public void error(final Marker marker, final Object message, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.ERROR, marker, message, throwable);
   }

   @Override
   public void error(final Marker marker, final String message) {
      this.logIfEnabled(FQCN, Level.ERROR, marker, message, (Throwable)null);
   }

   @Override
   public void error(final Marker marker, final String message, final Object... params) {
      this.logIfEnabled(FQCN, Level.ERROR, marker, message, params);
   }

   @Override
   public void error(final Marker marker, final String message, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.ERROR, marker, message, throwable);
   }

   @Override
   public void error(final Message message) {
      this.logIfEnabled(FQCN, Level.ERROR, null, message, message != null ? message.getThrowable() : null);
   }

   @Override
   public void error(final Message message, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.ERROR, null, message, throwable);
   }

   @Override
   public void error(final CharSequence message) {
      this.logIfEnabled(FQCN, Level.ERROR, null, message, null);
   }

   @Override
   public void error(final CharSequence message, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.ERROR, null, message, throwable);
   }

   @Override
   public void error(final Object message) {
      this.logIfEnabled(FQCN, Level.ERROR, null, message, null);
   }

   @Override
   public void error(final Object message, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.ERROR, null, message, throwable);
   }

   @Override
   public void error(final String message) {
      this.logIfEnabled(FQCN, Level.ERROR, null, message, (Throwable)null);
   }

   @Override
   public void error(final String message, final Object... params) {
      this.logIfEnabled(FQCN, Level.ERROR, null, message, params);
   }

   @Override
   public void error(final String message, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.ERROR, null, message, throwable);
   }

   @Override
   public void error(final Supplier<?> messageSupplier) {
      this.logIfEnabled(FQCN, Level.ERROR, null, messageSupplier, (Throwable)null);
   }

   @Override
   public void error(final Supplier<?> messageSupplier, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.ERROR, null, messageSupplier, throwable);
   }

   @Override
   public void error(final Marker marker, final Supplier<?> messageSupplier) {
      this.logIfEnabled(FQCN, Level.ERROR, marker, messageSupplier, (Throwable)null);
   }

   @Override
   public void error(final Marker marker, final String message, final Supplier<?>... paramSuppliers) {
      this.logIfEnabled(FQCN, Level.ERROR, marker, message, paramSuppliers);
   }

   @Override
   public void error(final Marker marker, final Supplier<?> messageSupplier, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.ERROR, marker, messageSupplier, throwable);
   }

   @Override
   public void error(final String message, final Supplier<?>... paramSuppliers) {
      this.logIfEnabled(FQCN, Level.ERROR, null, message, paramSuppliers);
   }

   @Override
   public void error(final Marker marker, final MessageSupplier messageSupplier) {
      this.logIfEnabled(FQCN, Level.ERROR, marker, messageSupplier, (Throwable)null);
   }

   @Override
   public void error(final Marker marker, final MessageSupplier messageSupplier, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.ERROR, marker, messageSupplier, throwable);
   }

   @Override
   public void error(final MessageSupplier messageSupplier) {
      this.logIfEnabled(FQCN, Level.ERROR, null, messageSupplier, (Throwable)null);
   }

   @Override
   public void error(final MessageSupplier messageSupplier, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.ERROR, null, messageSupplier, throwable);
   }

   @Override
   public void error(final Marker marker, final String message, final Object p0) {
      this.logIfEnabled(FQCN, Level.ERROR, marker, message, p0);
   }

   @Override
   public void error(final Marker marker, final String message, final Object p0, final Object p1) {
      this.logIfEnabled(FQCN, Level.ERROR, marker, message, p0, p1);
   }

   @Override
   public void error(final Marker marker, final String message, final Object p0, final Object p1, final Object p2) {
      this.logIfEnabled(FQCN, Level.ERROR, marker, message, p0, p1, p2);
   }

   @Override
   public void error(final Marker marker, final String message, final Object p0, final Object p1, final Object p2, final Object p3) {
      this.logIfEnabled(FQCN, Level.ERROR, marker, message, p0, p1, p2, p3);
   }

   @Override
   public void error(final Marker marker, final String message, final Object p0, final Object p1, final Object p2, final Object p3, final Object p4) {
      this.logIfEnabled(FQCN, Level.ERROR, marker, message, p0, p1, p2, p3, p4);
   }

   @Override
   public void error(
      final Marker marker, final String message, final Object p0, final Object p1, final Object p2, final Object p3, final Object p4, final Object p5
   ) {
      this.logIfEnabled(FQCN, Level.ERROR, marker, message, p0, p1, p2, p3, p4, p5);
   }

   @Override
   public void error(
      final Marker marker,
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6
   ) {
      this.logIfEnabled(FQCN, Level.ERROR, marker, message, p0, p1, p2, p3, p4, p5, p6);
   }

   @Override
   public void error(
      final Marker marker,
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6,
      final Object p7
   ) {
      this.logIfEnabled(FQCN, Level.ERROR, marker, message, p0, p1, p2, p3, p4, p5, p6, p7);
   }

   @Override
   public void error(
      final Marker marker,
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6,
      final Object p7,
      final Object p8
   ) {
      this.logIfEnabled(FQCN, Level.ERROR, marker, message, p0, p1, p2, p3, p4, p5, p6, p7, p8);
   }

   @Override
   public void error(
      final Marker marker,
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6,
      final Object p7,
      final Object p8,
      final Object p9
   ) {
      this.logIfEnabled(FQCN, Level.ERROR, marker, message, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9);
   }

   @Override
   public void error(final String message, final Object p0) {
      this.logIfEnabled(FQCN, Level.ERROR, null, message, p0);
   }

   @Override
   public void error(final String message, final Object p0, final Object p1) {
      this.logIfEnabled(FQCN, Level.ERROR, null, message, p0, p1);
   }

   @Override
   public void error(final String message, final Object p0, final Object p1, final Object p2) {
      this.logIfEnabled(FQCN, Level.ERROR, null, message, p0, p1, p2);
   }

   @Override
   public void error(final String message, final Object p0, final Object p1, final Object p2, final Object p3) {
      this.logIfEnabled(FQCN, Level.ERROR, null, message, p0, p1, p2, p3);
   }

   @Override
   public void error(final String message, final Object p0, final Object p1, final Object p2, final Object p3, final Object p4) {
      this.logIfEnabled(FQCN, Level.ERROR, null, message, p0, p1, p2, p3, p4);
   }

   @Override
   public void error(final String message, final Object p0, final Object p1, final Object p2, final Object p3, final Object p4, final Object p5) {
      this.logIfEnabled(FQCN, Level.ERROR, null, message, p0, p1, p2, p3, p4, p5);
   }

   @Override
   public void error(
      final String message, final Object p0, final Object p1, final Object p2, final Object p3, final Object p4, final Object p5, final Object p6
   ) {
      this.logIfEnabled(FQCN, Level.ERROR, null, message, p0, p1, p2, p3, p4, p5, p6);
   }

   @Override
   public void error(
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6,
      final Object p7
   ) {
      this.logIfEnabled(FQCN, Level.ERROR, null, message, p0, p1, p2, p3, p4, p5, p6, p7);
   }

   @Override
   public void error(
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6,
      final Object p7,
      final Object p8
   ) {
      this.logIfEnabled(FQCN, Level.ERROR, null, message, p0, p1, p2, p3, p4, p5, p6, p7, p8);
   }

   @Override
   public void error(
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6,
      final Object p7,
      final Object p8,
      final Object p9
   ) {
      this.logIfEnabled(FQCN, Level.ERROR, null, message, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9);
   }

   @Deprecated
   @Override
   public void exit() {
      this.exit(FQCN, null);
   }

   @Deprecated
   @Override
   public <R> R exit(final R result) {
      return this.exit(FQCN, result);
   }

   protected <R> R exit(final String fqcn, final R result) {
      if (this.isEnabled(Level.TRACE, EXIT_MARKER, (CharSequence)null, null)) {
         this.logMessageSafely(fqcn, Level.TRACE, EXIT_MARKER, this.exitMsg(null, result), null);
      }

      return result;
   }

   protected <R> R exit(final String fqcn, final String format, final R result) {
      if (this.isEnabled(Level.TRACE, EXIT_MARKER, (CharSequence)null, null)) {
         this.logMessageSafely(fqcn, Level.TRACE, EXIT_MARKER, this.exitMsg(format, result), null);
      }

      return result;
   }

   protected Message exitMsg(final String format, final Object result) {
      if (result == null) {
         return format == null ? this.messageFactory.newMessage("Exit") : this.messageFactory.newMessage("Exit: " + format);
      } else {
         return format == null ? this.messageFactory.newMessage("Exit with(" + result + ')') : this.messageFactory.newMessage("Exit: " + format, result);
      }
   }

   @Override
   public void fatal(final Marker marker, final Message message) {
      this.logIfEnabled(FQCN, Level.FATAL, marker, message, message != null ? message.getThrowable() : null);
   }

   @Override
   public void fatal(final Marker marker, final Message message, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.FATAL, marker, message, throwable);
   }

   @Override
   public void fatal(final Marker marker, final CharSequence message) {
      this.logIfEnabled(FQCN, Level.FATAL, marker, message, null);
   }

   @Override
   public void fatal(final Marker marker, final CharSequence message, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.FATAL, marker, message, throwable);
   }

   @Override
   public void fatal(final Marker marker, final Object message) {
      this.logIfEnabled(FQCN, Level.FATAL, marker, message, null);
   }

   @Override
   public void fatal(final Marker marker, final Object message, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.FATAL, marker, message, throwable);
   }

   @Override
   public void fatal(final Marker marker, final String message) {
      this.logIfEnabled(FQCN, Level.FATAL, marker, message, (Throwable)null);
   }

   @Override
   public void fatal(final Marker marker, final String message, final Object... params) {
      this.logIfEnabled(FQCN, Level.FATAL, marker, message, params);
   }

   @Override
   public void fatal(final Marker marker, final String message, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.FATAL, marker, message, throwable);
   }

   @Override
   public void fatal(final Message message) {
      this.logIfEnabled(FQCN, Level.FATAL, null, message, message != null ? message.getThrowable() : null);
   }

   @Override
   public void fatal(final Message message, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.FATAL, null, message, throwable);
   }

   @Override
   public void fatal(final CharSequence message) {
      this.logIfEnabled(FQCN, Level.FATAL, null, message, null);
   }

   @Override
   public void fatal(final CharSequence message, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.FATAL, null, message, throwable);
   }

   @Override
   public void fatal(final Object message) {
      this.logIfEnabled(FQCN, Level.FATAL, null, message, null);
   }

   @Override
   public void fatal(final Object message, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.FATAL, null, message, throwable);
   }

   @Override
   public void fatal(final String message) {
      this.logIfEnabled(FQCN, Level.FATAL, null, message, (Throwable)null);
   }

   @Override
   public void fatal(final String message, final Object... params) {
      this.logIfEnabled(FQCN, Level.FATAL, null, message, params);
   }

   @Override
   public void fatal(final String message, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.FATAL, null, message, throwable);
   }

   @Override
   public void fatal(final Supplier<?> messageSupplier) {
      this.logIfEnabled(FQCN, Level.FATAL, null, messageSupplier, (Throwable)null);
   }

   @Override
   public void fatal(final Supplier<?> messageSupplier, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.FATAL, null, messageSupplier, throwable);
   }

   @Override
   public void fatal(final Marker marker, final Supplier<?> messageSupplier) {
      this.logIfEnabled(FQCN, Level.FATAL, marker, messageSupplier, (Throwable)null);
   }

   @Override
   public void fatal(final Marker marker, final String message, final Supplier<?>... paramSuppliers) {
      this.logIfEnabled(FQCN, Level.FATAL, marker, message, paramSuppliers);
   }

   @Override
   public void fatal(final Marker marker, final Supplier<?> messageSupplier, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.FATAL, marker, messageSupplier, throwable);
   }

   @Override
   public void fatal(final String message, final Supplier<?>... paramSuppliers) {
      this.logIfEnabled(FQCN, Level.FATAL, null, message, paramSuppliers);
   }

   @Override
   public void fatal(final Marker marker, final MessageSupplier messageSupplier) {
      this.logIfEnabled(FQCN, Level.FATAL, marker, messageSupplier, (Throwable)null);
   }

   @Override
   public void fatal(final Marker marker, final MessageSupplier messageSupplier, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.FATAL, marker, messageSupplier, throwable);
   }

   @Override
   public void fatal(final MessageSupplier messageSupplier) {
      this.logIfEnabled(FQCN, Level.FATAL, null, messageSupplier, (Throwable)null);
   }

   @Override
   public void fatal(final MessageSupplier messageSupplier, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.FATAL, null, messageSupplier, throwable);
   }

   @Override
   public void fatal(final Marker marker, final String message, final Object p0) {
      this.logIfEnabled(FQCN, Level.FATAL, marker, message, p0);
   }

   @Override
   public void fatal(final Marker marker, final String message, final Object p0, final Object p1) {
      this.logIfEnabled(FQCN, Level.FATAL, marker, message, p0, p1);
   }

   @Override
   public void fatal(final Marker marker, final String message, final Object p0, final Object p1, final Object p2) {
      this.logIfEnabled(FQCN, Level.FATAL, marker, message, p0, p1, p2);
   }

   @Override
   public void fatal(final Marker marker, final String message, final Object p0, final Object p1, final Object p2, final Object p3) {
      this.logIfEnabled(FQCN, Level.FATAL, marker, message, p0, p1, p2, p3);
   }

   @Override
   public void fatal(final Marker marker, final String message, final Object p0, final Object p1, final Object p2, final Object p3, final Object p4) {
      this.logIfEnabled(FQCN, Level.FATAL, marker, message, p0, p1, p2, p3, p4);
   }

   @Override
   public void fatal(
      final Marker marker, final String message, final Object p0, final Object p1, final Object p2, final Object p3, final Object p4, final Object p5
   ) {
      this.logIfEnabled(FQCN, Level.FATAL, marker, message, p0, p1, p2, p3, p4, p5);
   }

   @Override
   public void fatal(
      final Marker marker,
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6
   ) {
      this.logIfEnabled(FQCN, Level.FATAL, marker, message, p0, p1, p2, p3, p4, p5, p6);
   }

   @Override
   public void fatal(
      final Marker marker,
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6,
      final Object p7
   ) {
      this.logIfEnabled(FQCN, Level.FATAL, marker, message, p0, p1, p2, p3, p4, p5, p6, p7);
   }

   @Override
   public void fatal(
      final Marker marker,
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6,
      final Object p7,
      final Object p8
   ) {
      this.logIfEnabled(FQCN, Level.FATAL, marker, message, p0, p1, p2, p3, p4, p5, p6, p7, p8);
   }

   @Override
   public void fatal(
      final Marker marker,
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6,
      final Object p7,
      final Object p8,
      final Object p9
   ) {
      this.logIfEnabled(FQCN, Level.FATAL, marker, message, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9);
   }

   @Override
   public void fatal(final String message, final Object p0) {
      this.logIfEnabled(FQCN, Level.FATAL, null, message, p0);
   }

   @Override
   public void fatal(final String message, final Object p0, final Object p1) {
      this.logIfEnabled(FQCN, Level.FATAL, null, message, p0, p1);
   }

   @Override
   public void fatal(final String message, final Object p0, final Object p1, final Object p2) {
      this.logIfEnabled(FQCN, Level.FATAL, null, message, p0, p1, p2);
   }

   @Override
   public void fatal(final String message, final Object p0, final Object p1, final Object p2, final Object p3) {
      this.logIfEnabled(FQCN, Level.FATAL, null, message, p0, p1, p2, p3);
   }

   @Override
   public void fatal(final String message, final Object p0, final Object p1, final Object p2, final Object p3, final Object p4) {
      this.logIfEnabled(FQCN, Level.FATAL, null, message, p0, p1, p2, p3, p4);
   }

   @Override
   public void fatal(final String message, final Object p0, final Object p1, final Object p2, final Object p3, final Object p4, final Object p5) {
      this.logIfEnabled(FQCN, Level.FATAL, null, message, p0, p1, p2, p3, p4, p5);
   }

   @Override
   public void fatal(
      final String message, final Object p0, final Object p1, final Object p2, final Object p3, final Object p4, final Object p5, final Object p6
   ) {
      this.logIfEnabled(FQCN, Level.FATAL, null, message, p0, p1, p2, p3, p4, p5, p6);
   }

   @Override
   public void fatal(
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6,
      final Object p7
   ) {
      this.logIfEnabled(FQCN, Level.FATAL, null, message, p0, p1, p2, p3, p4, p5, p6, p7);
   }

   @Override
   public void fatal(
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6,
      final Object p7,
      final Object p8
   ) {
      this.logIfEnabled(FQCN, Level.FATAL, null, message, p0, p1, p2, p3, p4, p5, p6, p7, p8);
   }

   @Override
   public void fatal(
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6,
      final Object p7,
      final Object p8,
      final Object p9
   ) {
      this.logIfEnabled(FQCN, Level.FATAL, null, message, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9);
   }

   @Override
   public <MF extends MessageFactory> MF getMessageFactory() {
      return (MF)this.messageFactory;
   }

   @Override
   public String getName() {
      return this.name;
   }

   @Override
   public void info(final Marker marker, final Message message) {
      this.logIfEnabled(FQCN, Level.INFO, marker, message, message != null ? message.getThrowable() : null);
   }

   @Override
   public void info(final Marker marker, final Message message, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.INFO, marker, message, throwable);
   }

   @Override
   public void info(final Marker marker, final CharSequence message) {
      this.logIfEnabled(FQCN, Level.INFO, marker, message, null);
   }

   @Override
   public void info(final Marker marker, final CharSequence message, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.INFO, marker, message, throwable);
   }

   @Override
   public void info(final Marker marker, final Object message) {
      this.logIfEnabled(FQCN, Level.INFO, marker, message, null);
   }

   @Override
   public void info(final Marker marker, final Object message, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.INFO, marker, message, throwable);
   }

   @Override
   public void info(final Marker marker, final String message) {
      this.logIfEnabled(FQCN, Level.INFO, marker, message, (Throwable)null);
   }

   @Override
   public void info(final Marker marker, final String message, final Object... params) {
      this.logIfEnabled(FQCN, Level.INFO, marker, message, params);
   }

   @Override
   public void info(final Marker marker, final String message, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.INFO, marker, message, throwable);
   }

   @Override
   public void info(final Message message) {
      this.logIfEnabled(FQCN, Level.INFO, null, message, message != null ? message.getThrowable() : null);
   }

   @Override
   public void info(final Message message, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.INFO, null, message, throwable);
   }

   @Override
   public void info(final CharSequence message) {
      this.logIfEnabled(FQCN, Level.INFO, null, message, null);
   }

   @Override
   public void info(final CharSequence message, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.INFO, null, message, throwable);
   }

   @Override
   public void info(final Object message) {
      this.logIfEnabled(FQCN, Level.INFO, null, message, null);
   }

   @Override
   public void info(final Object message, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.INFO, null, message, throwable);
   }

   @Override
   public void info(final String message) {
      this.logIfEnabled(FQCN, Level.INFO, null, message, (Throwable)null);
   }

   @Override
   public void info(final String message, final Object... params) {
      this.logIfEnabled(FQCN, Level.INFO, null, message, params);
   }

   @Override
   public void info(final String message, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.INFO, null, message, throwable);
   }

   @Override
   public void info(final Supplier<?> messageSupplier) {
      this.logIfEnabled(FQCN, Level.INFO, null, messageSupplier, (Throwable)null);
   }

   @Override
   public void info(final Supplier<?> messageSupplier, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.INFO, null, messageSupplier, throwable);
   }

   @Override
   public void info(final Marker marker, final Supplier<?> messageSupplier) {
      this.logIfEnabled(FQCN, Level.INFO, marker, messageSupplier, (Throwable)null);
   }

   @Override
   public void info(final Marker marker, final String message, final Supplier<?>... paramSuppliers) {
      this.logIfEnabled(FQCN, Level.INFO, marker, message, paramSuppliers);
   }

   @Override
   public void info(final Marker marker, final Supplier<?> messageSupplier, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.INFO, marker, messageSupplier, throwable);
   }

   @Override
   public void info(final String message, final Supplier<?>... paramSuppliers) {
      this.logIfEnabled(FQCN, Level.INFO, null, message, paramSuppliers);
   }

   @Override
   public void info(final Marker marker, final MessageSupplier messageSupplier) {
      this.logIfEnabled(FQCN, Level.INFO, marker, messageSupplier, (Throwable)null);
   }

   @Override
   public void info(final Marker marker, final MessageSupplier messageSupplier, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.INFO, marker, messageSupplier, throwable);
   }

   @Override
   public void info(final MessageSupplier messageSupplier) {
      this.logIfEnabled(FQCN, Level.INFO, null, messageSupplier, (Throwable)null);
   }

   @Override
   public void info(final MessageSupplier messageSupplier, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.INFO, null, messageSupplier, throwable);
   }

   @Override
   public void info(final Marker marker, final String message, final Object p0) {
      this.logIfEnabled(FQCN, Level.INFO, marker, message, p0);
   }

   @Override
   public void info(final Marker marker, final String message, final Object p0, final Object p1) {
      this.logIfEnabled(FQCN, Level.INFO, marker, message, p0, p1);
   }

   @Override
   public void info(final Marker marker, final String message, final Object p0, final Object p1, final Object p2) {
      this.logIfEnabled(FQCN, Level.INFO, marker, message, p0, p1, p2);
   }

   @Override
   public void info(final Marker marker, final String message, final Object p0, final Object p1, final Object p2, final Object p3) {
      this.logIfEnabled(FQCN, Level.INFO, marker, message, p0, p1, p2, p3);
   }

   @Override
   public void info(final Marker marker, final String message, final Object p0, final Object p1, final Object p2, final Object p3, final Object p4) {
      this.logIfEnabled(FQCN, Level.INFO, marker, message, p0, p1, p2, p3, p4);
   }

   @Override
   public void info(
      final Marker marker, final String message, final Object p0, final Object p1, final Object p2, final Object p3, final Object p4, final Object p5
   ) {
      this.logIfEnabled(FQCN, Level.INFO, marker, message, p0, p1, p2, p3, p4, p5);
   }

   @Override
   public void info(
      final Marker marker,
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6
   ) {
      this.logIfEnabled(FQCN, Level.INFO, marker, message, p0, p1, p2, p3, p4, p5, p6);
   }

   @Override
   public void info(
      final Marker marker,
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6,
      final Object p7
   ) {
      this.logIfEnabled(FQCN, Level.INFO, marker, message, p0, p1, p2, p3, p4, p5, p6, p7);
   }

   @Override
   public void info(
      final Marker marker,
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6,
      final Object p7,
      final Object p8
   ) {
      this.logIfEnabled(FQCN, Level.INFO, marker, message, p0, p1, p2, p3, p4, p5, p6, p7, p8);
   }

   @Override
   public void info(
      final Marker marker,
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6,
      final Object p7,
      final Object p8,
      final Object p9
   ) {
      this.logIfEnabled(FQCN, Level.INFO, marker, message, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9);
   }

   @Override
   public void info(final String message, final Object p0) {
      this.logIfEnabled(FQCN, Level.INFO, null, message, p0);
   }

   @Override
   public void info(final String message, final Object p0, final Object p1) {
      this.logIfEnabled(FQCN, Level.INFO, null, message, p0, p1);
   }

   @Override
   public void info(final String message, final Object p0, final Object p1, final Object p2) {
      this.logIfEnabled(FQCN, Level.INFO, null, message, p0, p1, p2);
   }

   @Override
   public void info(final String message, final Object p0, final Object p1, final Object p2, final Object p3) {
      this.logIfEnabled(FQCN, Level.INFO, null, message, p0, p1, p2, p3);
   }

   @Override
   public void info(final String message, final Object p0, final Object p1, final Object p2, final Object p3, final Object p4) {
      this.logIfEnabled(FQCN, Level.INFO, null, message, p0, p1, p2, p3, p4);
   }

   @Override
   public void info(final String message, final Object p0, final Object p1, final Object p2, final Object p3, final Object p4, final Object p5) {
      this.logIfEnabled(FQCN, Level.INFO, null, message, p0, p1, p2, p3, p4, p5);
   }

   @Override
   public void info(final String message, final Object p0, final Object p1, final Object p2, final Object p3, final Object p4, final Object p5, final Object p6) {
      this.logIfEnabled(FQCN, Level.INFO, null, message, p0, p1, p2, p3, p4, p5, p6);
   }

   @Override
   public void info(
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6,
      final Object p7
   ) {
      this.logIfEnabled(FQCN, Level.INFO, null, message, p0, p1, p2, p3, p4, p5, p6, p7);
   }

   @Override
   public void info(
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6,
      final Object p7,
      final Object p8
   ) {
      this.logIfEnabled(FQCN, Level.INFO, null, message, p0, p1, p2, p3, p4, p5, p6, p7, p8);
   }

   @Override
   public void info(
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6,
      final Object p7,
      final Object p8,
      final Object p9
   ) {
      this.logIfEnabled(FQCN, Level.INFO, null, message, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9);
   }

   @Override
   public boolean isDebugEnabled() {
      return this.isEnabled(Level.DEBUG, null, null);
   }

   @Override
   public boolean isDebugEnabled(final Marker marker) {
      return this.isEnabled(Level.DEBUG, marker, null, null);
   }

   @Override
   public boolean isEnabled(final Level level) {
      return this.isEnabled(level, null, null, null);
   }

   @Override
   public boolean isEnabled(final Level level, final Marker marker) {
      return this.isEnabled(level, marker, null, null);
   }

   @Override
   public boolean isErrorEnabled() {
      return this.isEnabled(Level.ERROR, null, null, null);
   }

   @Override
   public boolean isErrorEnabled(final Marker marker) {
      return this.isEnabled(Level.ERROR, marker, null, null);
   }

   @Override
   public boolean isFatalEnabled() {
      return this.isEnabled(Level.FATAL, null, null, null);
   }

   @Override
   public boolean isFatalEnabled(final Marker marker) {
      return this.isEnabled(Level.FATAL, marker, null, null);
   }

   @Override
   public boolean isInfoEnabled() {
      return this.isEnabled(Level.INFO, null, null, null);
   }

   @Override
   public boolean isInfoEnabled(final Marker marker) {
      return this.isEnabled(Level.INFO, marker, null, null);
   }

   @Override
   public boolean isTraceEnabled() {
      return this.isEnabled(Level.TRACE, null, null, null);
   }

   @Override
   public boolean isTraceEnabled(final Marker marker) {
      return this.isEnabled(Level.TRACE, marker, null, null);
   }

   @Override
   public boolean isWarnEnabled() {
      return this.isEnabled(Level.WARN, null, null, null);
   }

   @Override
   public boolean isWarnEnabled(final Marker marker) {
      return this.isEnabled(Level.WARN, marker, null, null);
   }

   @Override
   public void log(final Level level, final Marker marker, final Message message) {
      this.logIfEnabled(FQCN, level, marker, message, message != null ? message.getThrowable() : null);
   }

   @Override
   public void log(final Level level, final Marker marker, final Message message, final Throwable throwable) {
      this.logIfEnabled(FQCN, level, marker, message, throwable);
   }

   @Override
   public void log(final Level level, final Marker marker, final CharSequence message) {
      this.logIfEnabled(FQCN, level, marker, message, (Throwable)null);
   }

   @Override
   public void log(final Level level, final Marker marker, final CharSequence message, final Throwable throwable) {
      if (this.isEnabled(level, marker, message, throwable)) {
         this.logMessage(FQCN, level, marker, message, throwable);
      }
   }

   @Override
   public void log(final Level level, final Marker marker, final Object message) {
      this.logIfEnabled(FQCN, level, marker, message, (Throwable)null);
   }

   @Override
   public void log(final Level level, final Marker marker, final Object message, final Throwable throwable) {
      if (this.isEnabled(level, marker, message, throwable)) {
         this.logMessage(FQCN, level, marker, message, throwable);
      }
   }

   @Override
   public void log(final Level level, final Marker marker, final String message) {
      this.logIfEnabled(FQCN, level, marker, message, (Throwable)null);
   }

   @Override
   public void log(final Level level, final Marker marker, final String message, final Object... params) {
      this.logIfEnabled(FQCN, level, marker, message, params);
   }

   @Override
   public void log(final Level level, final Marker marker, final String message, final Throwable throwable) {
      this.logIfEnabled(FQCN, level, marker, message, throwable);
   }

   @Override
   public void log(final Level level, final Message message) {
      this.logIfEnabled(FQCN, level, null, message, message != null ? message.getThrowable() : null);
   }

   @Override
   public void log(final Level level, final Message message, final Throwable throwable) {
      this.logIfEnabled(FQCN, level, null, message, throwable);
   }

   @Override
   public void log(final Level level, final CharSequence message) {
      this.logIfEnabled(FQCN, level, null, message, null);
   }

   @Override
   public void log(final Level level, final CharSequence message, final Throwable throwable) {
      this.logIfEnabled(FQCN, level, null, message, throwable);
   }

   @Override
   public void log(final Level level, final Object message) {
      this.logIfEnabled(FQCN, level, null, message, null);
   }

   @Override
   public void log(final Level level, final Object message, final Throwable throwable) {
      this.logIfEnabled(FQCN, level, null, message, throwable);
   }

   @Override
   public void log(final Level level, final String message) {
      this.logIfEnabled(FQCN, level, null, message, (Throwable)null);
   }

   @Override
   public void log(final Level level, final String message, final Object... params) {
      this.logIfEnabled(FQCN, level, null, message, params);
   }

   @Override
   public void log(final Level level, final String message, final Throwable throwable) {
      this.logIfEnabled(FQCN, level, null, message, throwable);
   }

   @Override
   public void log(final Level level, final Supplier<?> messageSupplier) {
      this.logIfEnabled(FQCN, level, null, messageSupplier, (Throwable)null);
   }

   @Override
   public void log(final Level level, final Supplier<?> messageSupplier, final Throwable throwable) {
      this.logIfEnabled(FQCN, level, null, messageSupplier, throwable);
   }

   @Override
   public void log(final Level level, final Marker marker, final Supplier<?> messageSupplier) {
      this.logIfEnabled(FQCN, level, marker, messageSupplier, (Throwable)null);
   }

   @Override
   public void log(final Level level, final Marker marker, final String message, final Supplier<?>... paramSuppliers) {
      this.logIfEnabled(FQCN, level, marker, message, paramSuppliers);
   }

   @Override
   public void log(final Level level, final Marker marker, final Supplier<?> messageSupplier, final Throwable throwable) {
      this.logIfEnabled(FQCN, level, marker, messageSupplier, throwable);
   }

   @Override
   public void log(final Level level, final String message, final Supplier<?>... paramSuppliers) {
      this.logIfEnabled(FQCN, level, null, message, paramSuppliers);
   }

   @Override
   public void log(final Level level, final Marker marker, final MessageSupplier messageSupplier) {
      this.logIfEnabled(FQCN, level, marker, messageSupplier, (Throwable)null);
   }

   @Override
   public void log(final Level level, final Marker marker, final MessageSupplier messageSupplier, final Throwable throwable) {
      this.logIfEnabled(FQCN, level, marker, messageSupplier, throwable);
   }

   @Override
   public void log(final Level level, final MessageSupplier messageSupplier) {
      this.logIfEnabled(FQCN, level, null, messageSupplier, (Throwable)null);
   }

   @Override
   public void log(final Level level, final MessageSupplier messageSupplier, final Throwable throwable) {
      this.logIfEnabled(FQCN, level, null, messageSupplier, throwable);
   }

   @Override
   public void log(final Level level, final Marker marker, final String message, final Object p0) {
      this.logIfEnabled(FQCN, level, marker, message, p0);
   }

   @Override
   public void log(final Level level, final Marker marker, final String message, final Object p0, final Object p1) {
      this.logIfEnabled(FQCN, level, marker, message, p0, p1);
   }

   @Override
   public void log(final Level level, final Marker marker, final String message, final Object p0, final Object p1, final Object p2) {
      this.logIfEnabled(FQCN, level, marker, message, p0, p1, p2);
   }

   @Override
   public void log(final Level level, final Marker marker, final String message, final Object p0, final Object p1, final Object p2, final Object p3) {
      this.logIfEnabled(FQCN, level, marker, message, p0, p1, p2, p3);
   }

   @Override
   public void log(
      final Level level, final Marker marker, final String message, final Object p0, final Object p1, final Object p2, final Object p3, final Object p4
   ) {
      this.logIfEnabled(FQCN, level, marker, message, p0, p1, p2, p3, p4);
   }

   @Override
   public void log(
      final Level level,
      final Marker marker,
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5
   ) {
      this.logIfEnabled(FQCN, level, marker, message, p0, p1, p2, p3, p4, p5);
   }

   @Override
   public void log(
      final Level level,
      final Marker marker,
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6
   ) {
      this.logIfEnabled(FQCN, level, marker, message, p0, p1, p2, p3, p4, p5, p6);
   }

   @Override
   public void log(
      final Level level,
      final Marker marker,
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6,
      final Object p7
   ) {
      this.logIfEnabled(FQCN, level, marker, message, p0, p1, p2, p3, p4, p5, p6, p7);
   }

   @Override
   public void log(
      final Level level,
      final Marker marker,
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6,
      final Object p7,
      final Object p8
   ) {
      this.logIfEnabled(FQCN, level, marker, message, p0, p1, p2, p3, p4, p5, p6, p7, p8);
   }

   @Override
   public void log(
      final Level level,
      final Marker marker,
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6,
      final Object p7,
      final Object p8,
      final Object p9
   ) {
      this.logIfEnabled(FQCN, level, marker, message, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9);
   }

   @Override
   public void log(final Level level, final String message, final Object p0) {
      this.logIfEnabled(FQCN, level, null, message, p0);
   }

   @Override
   public void log(final Level level, final String message, final Object p0, final Object p1) {
      this.logIfEnabled(FQCN, level, null, message, p0, p1);
   }

   @Override
   public void log(final Level level, final String message, final Object p0, final Object p1, final Object p2) {
      this.logIfEnabled(FQCN, level, null, message, p0, p1, p2);
   }

   @Override
   public void log(final Level level, final String message, final Object p0, final Object p1, final Object p2, final Object p3) {
      this.logIfEnabled(FQCN, level, null, message, p0, p1, p2, p3);
   }

   @Override
   public void log(final Level level, final String message, final Object p0, final Object p1, final Object p2, final Object p3, final Object p4) {
      this.logIfEnabled(FQCN, level, null, message, p0, p1, p2, p3, p4);
   }

   @Override
   public void log(
      final Level level, final String message, final Object p0, final Object p1, final Object p2, final Object p3, final Object p4, final Object p5
   ) {
      this.logIfEnabled(FQCN, level, null, message, p0, p1, p2, p3, p4, p5);
   }

   @Override
   public void log(
      final Level level,
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6
   ) {
      this.logIfEnabled(FQCN, level, null, message, p0, p1, p2, p3, p4, p5, p6);
   }

   @Override
   public void log(
      final Level level,
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6,
      final Object p7
   ) {
      this.logIfEnabled(FQCN, level, null, message, p0, p1, p2, p3, p4, p5, p6, p7);
   }

   @Override
   public void log(
      final Level level,
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6,
      final Object p7,
      final Object p8
   ) {
      this.logIfEnabled(FQCN, level, null, message, p0, p1, p2, p3, p4, p5, p6, p7, p8);
   }

   @Override
   public void log(
      final Level level,
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6,
      final Object p7,
      final Object p8,
      final Object p9
   ) {
      this.logIfEnabled(FQCN, level, null, message, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9);
   }

   @Override
   public void logIfEnabled(final String fqcn, final Level level, final Marker marker, final Message message, final Throwable throwable) {
      if (this.isEnabled(level, marker, message, throwable)) {
         this.logMessageSafely(fqcn, level, marker, message, throwable);
      }
   }

   @Override
   public void logIfEnabled(final String fqcn, final Level level, final Marker marker, final MessageSupplier messageSupplier, final Throwable throwable) {
      if (this.isEnabled(level, marker, messageSupplier, throwable)) {
         this.logMessage(fqcn, level, marker, messageSupplier, throwable);
      }
   }

   @Override
   public void logIfEnabled(final String fqcn, final Level level, final Marker marker, final Object message, final Throwable throwable) {
      if (this.isEnabled(level, marker, message, throwable)) {
         this.logMessage(fqcn, level, marker, message, throwable);
      }
   }

   @Override
   public void logIfEnabled(final String fqcn, final Level level, final Marker marker, final CharSequence message, final Throwable throwable) {
      if (this.isEnabled(level, marker, message, throwable)) {
         this.logMessage(fqcn, level, marker, message, throwable);
      }
   }

   @Override
   public void logIfEnabled(final String fqcn, final Level level, final Marker marker, final Supplier<?> messageSupplier, final Throwable throwable) {
      if (this.isEnabled(level, marker, messageSupplier, throwable)) {
         this.logMessage(fqcn, level, marker, messageSupplier, throwable);
      }
   }

   @Override
   public void logIfEnabled(final String fqcn, final Level level, final Marker marker, final String message) {
      if (this.isEnabled(level, marker, message)) {
         this.logMessage(fqcn, level, marker, message);
      }
   }

   @Override
   public void logIfEnabled(final String fqcn, final Level level, final Marker marker, final String message, final Supplier<?>... paramSuppliers) {
      if (this.isEnabled(level, marker, message)) {
         this.logMessage(fqcn, level, marker, message, paramSuppliers);
      }
   }

   @Override
   public void logIfEnabled(final String fqcn, final Level level, final Marker marker, final String message, final Object... params) {
      if (this.isEnabled(level, marker, message, params)) {
         this.logMessage(fqcn, level, marker, message, params);
      }
   }

   @Override
   public void logIfEnabled(final String fqcn, final Level level, final Marker marker, final String message, final Object p0) {
      if (this.isEnabled(level, marker, message, p0)) {
         this.logMessage(fqcn, level, marker, message, p0);
      }
   }

   @Override
   public void logIfEnabled(final String fqcn, final Level level, final Marker marker, final String message, final Object p0, final Object p1) {
      if (this.isEnabled(level, marker, message, p0, p1)) {
         this.logMessage(fqcn, level, marker, message, p0, p1);
      }
   }

   @Override
   public void logIfEnabled(final String fqcn, final Level level, final Marker marker, final String message, final Object p0, final Object p1, final Object p2) {
      if (this.isEnabled(level, marker, message, p0, p1, p2)) {
         this.logMessage(fqcn, level, marker, message, p0, p1, p2);
      }
   }

   @Override
   public void logIfEnabled(
      final String fqcn, final Level level, final Marker marker, final String message, final Object p0, final Object p1, final Object p2, final Object p3
   ) {
      if (this.isEnabled(level, marker, message, p0, p1, p2, p3)) {
         this.logMessage(fqcn, level, marker, message, p0, p1, p2, p3);
      }
   }

   @Override
   public void logIfEnabled(
      final String fqcn,
      final Level level,
      final Marker marker,
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4
   ) {
      if (this.isEnabled(level, marker, message, p0, p1, p2, p3, p4)) {
         this.logMessage(fqcn, level, marker, message, p0, p1, p2, p3, p4);
      }
   }

   @Override
   public void logIfEnabled(
      final String fqcn,
      final Level level,
      final Marker marker,
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5
   ) {
      if (this.isEnabled(level, marker, message, p0, p1, p2, p3, p4, p5)) {
         this.logMessage(fqcn, level, marker, message, p0, p1, p2, p3, p4, p5);
      }
   }

   @Override
   public void logIfEnabled(
      final String fqcn,
      final Level level,
      final Marker marker,
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6
   ) {
      if (this.isEnabled(level, marker, message, p0, p1, p2, p3, p4, p5, p6)) {
         this.logMessage(fqcn, level, marker, message, p0, p1, p2, p3, p4, p5, p6);
      }
   }

   @Override
   public void logIfEnabled(
      final String fqcn,
      final Level level,
      final Marker marker,
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6,
      final Object p7
   ) {
      if (this.isEnabled(level, marker, message, p0, p1, p2, p3, p4, p5, p6, p7)) {
         this.logMessage(fqcn, level, marker, message, p0, p1, p2, p3, p4, p5, p6, p7);
      }
   }

   @Override
   public void logIfEnabled(
      final String fqcn,
      final Level level,
      final Marker marker,
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6,
      final Object p7,
      final Object p8
   ) {
      if (this.isEnabled(level, marker, message, p0, p1, p2, p3, p4, p5, p6, p7, p8)) {
         this.logMessage(fqcn, level, marker, message, p0, p1, p2, p3, p4, p5, p6, p7, p8);
      }
   }

   @Override
   public void logIfEnabled(
      final String fqcn,
      final Level level,
      final Marker marker,
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6,
      final Object p7,
      final Object p8,
      final Object p9
   ) {
      if (this.isEnabled(level, marker, message, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9)) {
         this.logMessage(fqcn, level, marker, message, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9);
      }
   }

   @Override
   public void logIfEnabled(final String fqcn, final Level level, final Marker marker, final String message, final Throwable throwable) {
      if (this.isEnabled(level, marker, message, throwable)) {
         this.logMessage(fqcn, level, marker, message, throwable);
      }
   }

   protected void logMessage(final String fqcn, final Level level, final Marker marker, final CharSequence message, final Throwable throwable) {
      this.logMessageSafely(fqcn, level, marker, this.messageFactory.newMessage(message), throwable);
   }

   protected void logMessage(final String fqcn, final Level level, final Marker marker, final Object message, final Throwable throwable) {
      this.logMessageSafely(fqcn, level, marker, this.messageFactory.newMessage(message), throwable);
   }

   protected void logMessage(final String fqcn, final Level level, final Marker marker, final MessageSupplier messageSupplier, final Throwable throwable) {
      Message message = LambdaUtil.get(messageSupplier);
      Throwable effectiveThrowable = throwable == null && message != null ? message.getThrowable() : throwable;
      this.logMessageSafely(fqcn, level, marker, message, effectiveThrowable);
   }

   protected void logMessage(final String fqcn, final Level level, final Marker marker, final Supplier<?> messageSupplier, final Throwable throwable) {
      Message message = LambdaUtil.getMessage(messageSupplier, this.messageFactory);
      Throwable effectiveThrowable = throwable == null && message != null ? message.getThrowable() : throwable;
      this.logMessageSafely(fqcn, level, marker, message, effectiveThrowable);
   }

   protected void logMessage(final String fqcn, final Level level, final Marker marker, final String message, final Throwable throwable) {
      this.logMessageSafely(fqcn, level, marker, this.messageFactory.newMessage(message), throwable);
   }

   protected void logMessage(final String fqcn, final Level level, final Marker marker, final String message) {
      Message msg = this.messageFactory.newMessage(message);
      this.logMessageSafely(fqcn, level, marker, msg, msg.getThrowable());
   }

   protected void logMessage(final String fqcn, final Level level, final Marker marker, final String message, final Object... params) {
      Message msg = this.messageFactory.newMessage(message, params);
      this.logMessageSafely(fqcn, level, marker, msg, msg.getThrowable());
   }

   protected void logMessage(final String fqcn, final Level level, final Marker marker, final String message, final Object p0) {
      Message msg = this.messageFactory.newMessage(message, p0);
      this.logMessageSafely(fqcn, level, marker, msg, msg.getThrowable());
   }

   protected void logMessage(final String fqcn, final Level level, final Marker marker, final String message, final Object p0, final Object p1) {
      Message msg = this.messageFactory.newMessage(message, p0, p1);
      this.logMessageSafely(fqcn, level, marker, msg, msg.getThrowable());
   }

   protected void logMessage(final String fqcn, final Level level, final Marker marker, final String message, final Object p0, final Object p1, final Object p2) {
      Message msg = this.messageFactory.newMessage(message, p0, p1, p2);
      this.logMessageSafely(fqcn, level, marker, msg, msg.getThrowable());
   }

   protected void logMessage(
      final String fqcn, final Level level, final Marker marker, final String message, final Object p0, final Object p1, final Object p2, final Object p3
   ) {
      Message msg = this.messageFactory.newMessage(message, p0, p1, p2, p3);
      this.logMessageSafely(fqcn, level, marker, msg, msg.getThrowable());
   }

   protected void logMessage(
      final String fqcn,
      final Level level,
      final Marker marker,
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4
   ) {
      Message msg = this.messageFactory.newMessage(message, p0, p1, p2, p3, p4);
      this.logMessageSafely(fqcn, level, marker, msg, msg.getThrowable());
   }

   protected void logMessage(
      final String fqcn,
      final Level level,
      final Marker marker,
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5
   ) {
      Message msg = this.messageFactory.newMessage(message, p0, p1, p2, p3, p4, p5);
      this.logMessageSafely(fqcn, level, marker, msg, msg.getThrowable());
   }

   protected void logMessage(
      final String fqcn,
      final Level level,
      final Marker marker,
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6
   ) {
      Message msg = this.messageFactory.newMessage(message, p0, p1, p2, p3, p4, p5, p6);
      this.logMessageSafely(fqcn, level, marker, msg, msg.getThrowable());
   }

   protected void logMessage(
      final String fqcn,
      final Level level,
      final Marker marker,
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6,
      final Object p7
   ) {
      Message msg = this.messageFactory.newMessage(message, p0, p1, p2, p3, p4, p5, p6, p7);
      this.logMessageSafely(fqcn, level, marker, msg, msg.getThrowable());
   }

   protected void logMessage(
      final String fqcn,
      final Level level,
      final Marker marker,
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6,
      final Object p7,
      final Object p8
   ) {
      Message msg = this.messageFactory.newMessage(message, p0, p1, p2, p3, p4, p5, p6, p7, p8);
      this.logMessageSafely(fqcn, level, marker, msg, msg.getThrowable());
   }

   protected void logMessage(
      final String fqcn,
      final Level level,
      final Marker marker,
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6,
      final Object p7,
      final Object p8,
      final Object p9
   ) {
      Message msg = this.messageFactory.newMessage(message, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9);
      this.logMessageSafely(fqcn, level, marker, msg, msg.getThrowable());
   }

   protected void logMessage(final String fqcn, final Level level, final Marker marker, final String message, final Supplier<?>... paramSuppliers) {
      Message msg = this.messageFactory.newMessage(message, LambdaUtil.getAll(paramSuppliers));
      this.logMessageSafely(fqcn, level, marker, msg, msg.getThrowable());
   }

   @Override
   public void logMessage(
      final Level level, final Marker marker, final String fqcn, final StackTraceElement location, final Message message, final Throwable throwable
   ) {
      try {
         incrementRecursionDepth();
         this.log(level, marker, fqcn, location, message, throwable);
      } catch (Throwable var11) {
         this.handleLogMessageException(var11, fqcn, message);
      } finally {
         decrementRecursionDepth();
         ReusableMessageFactory.release(message);
      }
   }

   protected void log(
      final Level level, final Marker marker, final String fqcn, final StackTraceElement location, final Message message, final Throwable throwable
   ) {
      this.logMessage(fqcn, level, marker, message, throwable);
   }

   @Override
   public void printf(final Level level, final Marker marker, final String format, final Object... params) {
      if (this.isEnabled(level, marker, format, params)) {
         Message message = new StringFormattedMessage(format, params);
         this.logMessageSafely(FQCN, level, marker, message, message.getThrowable());
      }
   }

   @Override
   public void printf(final Level level, final String format, final Object... params) {
      if (this.isEnabled(level, null, format, params)) {
         Message message = new StringFormattedMessage(format, params);
         this.logMessageSafely(FQCN, level, null, message, message.getThrowable());
      }
   }

   @PerformanceSensitive
   private void logMessageSafely(final String fqcn, final Level level, final Marker marker, final Message message, final Throwable throwable) {
      try {
         this.logMessageTrackRecursion(fqcn, level, marker, message, throwable);
      } finally {
         ReusableMessageFactory.release(message);
      }
   }

   @PerformanceSensitive
   private void logMessageTrackRecursion(final String fqcn, final Level level, final Marker marker, final Message message, final Throwable throwable) {
      try {
         incrementRecursionDepth();
         this.tryLogMessage(fqcn, this.getLocation(fqcn), level, marker, message, throwable);
      } finally {
         decrementRecursionDepth();
      }
   }

   private static int[] getRecursionDepthHolder() {
      int[] result = recursionDepthHolder.get();
      if (result == null) {
         result = new int[1];
         recursionDepthHolder.set(result);
      }

      return result;
   }

   private static void incrementRecursionDepth() {
      getRecursionDepthHolder()[0]++;
   }

   private static void decrementRecursionDepth() {
      int newDepth = --getRecursionDepthHolder()[0];
      if (newDepth < 0) {
         throw new IllegalStateException("Recursion depth became negative: " + newDepth);
      }
   }

   public static int getRecursionDepth() {
      return getRecursionDepthHolder()[0];
   }

   @PerformanceSensitive
   private void tryLogMessage(
      final String fqcn, final StackTraceElement location, final Level level, final Marker marker, final Message message, final Throwable throwable
   ) {
      try {
         this.log(level, marker, fqcn, location, message, throwable);
      } catch (Throwable var8) {
         this.handleLogMessageException(var8, fqcn, message);
      }
   }

   @PerformanceSensitive
   private StackTraceElement getLocation(String fqcn) {
      return this.requiresLocation() ? StackLocatorUtil.calcLocation(fqcn) : null;
   }

   private void handleLogMessageException(final Throwable throwable, final String fqcn, final Message message) {
      if (throwable instanceof LoggingException) {
         throw (LoggingException)throwable;
      } else {
         StatusLogger.getLogger()
            .warn("{} caught {} logging {}: {}", fqcn, throwable.getClass().getName(), message.getClass().getSimpleName(), message.getFormat(), throwable);
      }
   }

   @Override
   public <T extends Throwable> T throwing(final T throwable) {
      return this.throwing(FQCN, Level.ERROR, throwable);
   }

   @Override
   public <T extends Throwable> T throwing(final Level level, final T throwable) {
      return this.throwing(FQCN, level, throwable);
   }

   protected <T extends Throwable> T throwing(final String fqcn, final Level level, final T throwable) {
      if (this.isEnabled(level, THROWING_MARKER, null, null)) {
         this.logMessageSafely(fqcn, level, THROWING_MARKER, this.throwingMsg(throwable), throwable);
      }

      return throwable;
   }

   protected Message throwingMsg(final Throwable throwable) {
      return this.messageFactory.newMessage("Throwing");
   }

   @Override
   public void trace(final Marker marker, final Message message) {
      this.logIfEnabled(FQCN, Level.TRACE, marker, message, message != null ? message.getThrowable() : null);
   }

   @Override
   public void trace(final Marker marker, final Message message, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.TRACE, marker, message, throwable);
   }

   @Override
   public void trace(final Marker marker, final CharSequence message) {
      this.logIfEnabled(FQCN, Level.TRACE, marker, message, null);
   }

   @Override
   public void trace(final Marker marker, final CharSequence message, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.TRACE, marker, message, throwable);
   }

   @Override
   public void trace(final Marker marker, final Object message) {
      this.logIfEnabled(FQCN, Level.TRACE, marker, message, null);
   }

   @Override
   public void trace(final Marker marker, final Object message, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.TRACE, marker, message, throwable);
   }

   @Override
   public void trace(final Marker marker, final String message) {
      this.logIfEnabled(FQCN, Level.TRACE, marker, message, (Throwable)null);
   }

   @Override
   public void trace(final Marker marker, final String message, final Object... params) {
      this.logIfEnabled(FQCN, Level.TRACE, marker, message, params);
   }

   @Override
   public void trace(final Marker marker, final String message, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.TRACE, marker, message, throwable);
   }

   @Override
   public void trace(final Message message) {
      this.logIfEnabled(FQCN, Level.TRACE, null, message, message != null ? message.getThrowable() : null);
   }

   @Override
   public void trace(final Message message, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.TRACE, null, message, throwable);
   }

   @Override
   public void trace(final CharSequence message) {
      this.logIfEnabled(FQCN, Level.TRACE, null, message, null);
   }

   @Override
   public void trace(final CharSequence message, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.TRACE, null, message, throwable);
   }

   @Override
   public void trace(final Object message) {
      this.logIfEnabled(FQCN, Level.TRACE, null, message, null);
   }

   @Override
   public void trace(final Object message, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.TRACE, null, message, throwable);
   }

   @Override
   public void trace(final String message) {
      this.logIfEnabled(FQCN, Level.TRACE, null, message, (Throwable)null);
   }

   @Override
   public void trace(final String message, final Object... params) {
      this.logIfEnabled(FQCN, Level.TRACE, null, message, params);
   }

   @Override
   public void trace(final String message, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.TRACE, null, message, throwable);
   }

   @Override
   public void trace(final Supplier<?> messageSupplier) {
      this.logIfEnabled(FQCN, Level.TRACE, null, messageSupplier, (Throwable)null);
   }

   @Override
   public void trace(final Supplier<?> messageSupplier, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.TRACE, null, messageSupplier, throwable);
   }

   @Override
   public void trace(final Marker marker, final Supplier<?> messageSupplier) {
      this.logIfEnabled(FQCN, Level.TRACE, marker, messageSupplier, (Throwable)null);
   }

   @Override
   public void trace(final Marker marker, final String message, final Supplier<?>... paramSuppliers) {
      this.logIfEnabled(FQCN, Level.TRACE, marker, message, paramSuppliers);
   }

   @Override
   public void trace(final Marker marker, final Supplier<?> messageSupplier, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.TRACE, marker, messageSupplier, throwable);
   }

   @Override
   public void trace(final String message, final Supplier<?>... paramSuppliers) {
      this.logIfEnabled(FQCN, Level.TRACE, null, message, paramSuppliers);
   }

   @Override
   public void trace(final Marker marker, final MessageSupplier messageSupplier) {
      this.logIfEnabled(FQCN, Level.TRACE, marker, messageSupplier, (Throwable)null);
   }

   @Override
   public void trace(final Marker marker, final MessageSupplier messageSupplier, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.TRACE, marker, messageSupplier, throwable);
   }

   @Override
   public void trace(final MessageSupplier messageSupplier) {
      this.logIfEnabled(FQCN, Level.TRACE, null, messageSupplier, (Throwable)null);
   }

   @Override
   public void trace(final MessageSupplier messageSupplier, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.TRACE, null, messageSupplier, throwable);
   }

   @Override
   public void trace(final Marker marker, final String message, final Object p0) {
      this.logIfEnabled(FQCN, Level.TRACE, marker, message, p0);
   }

   @Override
   public void trace(final Marker marker, final String message, final Object p0, final Object p1) {
      this.logIfEnabled(FQCN, Level.TRACE, marker, message, p0, p1);
   }

   @Override
   public void trace(final Marker marker, final String message, final Object p0, final Object p1, final Object p2) {
      this.logIfEnabled(FQCN, Level.TRACE, marker, message, p0, p1, p2);
   }

   @Override
   public void trace(final Marker marker, final String message, final Object p0, final Object p1, final Object p2, final Object p3) {
      this.logIfEnabled(FQCN, Level.TRACE, marker, message, p0, p1, p2, p3);
   }

   @Override
   public void trace(final Marker marker, final String message, final Object p0, final Object p1, final Object p2, final Object p3, final Object p4) {
      this.logIfEnabled(FQCN, Level.TRACE, marker, message, p0, p1, p2, p3, p4);
   }

   @Override
   public void trace(
      final Marker marker, final String message, final Object p0, final Object p1, final Object p2, final Object p3, final Object p4, final Object p5
   ) {
      this.logIfEnabled(FQCN, Level.TRACE, marker, message, p0, p1, p2, p3, p4, p5);
   }

   @Override
   public void trace(
      final Marker marker,
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6
   ) {
      this.logIfEnabled(FQCN, Level.TRACE, marker, message, p0, p1, p2, p3, p4, p5, p6);
   }

   @Override
   public void trace(
      final Marker marker,
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6,
      final Object p7
   ) {
      this.logIfEnabled(FQCN, Level.TRACE, marker, message, p0, p1, p2, p3, p4, p5, p6, p7);
   }

   @Override
   public void trace(
      final Marker marker,
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6,
      final Object p7,
      final Object p8
   ) {
      this.logIfEnabled(FQCN, Level.TRACE, marker, message, p0, p1, p2, p3, p4, p5, p6, p7, p8);
   }

   @Override
   public void trace(
      final Marker marker,
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6,
      final Object p7,
      final Object p8,
      final Object p9
   ) {
      this.logIfEnabled(FQCN, Level.TRACE, marker, message, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9);
   }

   @Override
   public void trace(final String message, final Object p0) {
      this.logIfEnabled(FQCN, Level.TRACE, null, message, p0);
   }

   @Override
   public void trace(final String message, final Object p0, final Object p1) {
      this.logIfEnabled(FQCN, Level.TRACE, null, message, p0, p1);
   }

   @Override
   public void trace(final String message, final Object p0, final Object p1, final Object p2) {
      this.logIfEnabled(FQCN, Level.TRACE, null, message, p0, p1, p2);
   }

   @Override
   public void trace(final String message, final Object p0, final Object p1, final Object p2, final Object p3) {
      this.logIfEnabled(FQCN, Level.TRACE, null, message, p0, p1, p2, p3);
   }

   @Override
   public void trace(final String message, final Object p0, final Object p1, final Object p2, final Object p3, final Object p4) {
      this.logIfEnabled(FQCN, Level.TRACE, null, message, p0, p1, p2, p3, p4);
   }

   @Override
   public void trace(final String message, final Object p0, final Object p1, final Object p2, final Object p3, final Object p4, final Object p5) {
      this.logIfEnabled(FQCN, Level.TRACE, null, message, p0, p1, p2, p3, p4, p5);
   }

   @Override
   public void trace(
      final String message, final Object p0, final Object p1, final Object p2, final Object p3, final Object p4, final Object p5, final Object p6
   ) {
      this.logIfEnabled(FQCN, Level.TRACE, null, message, p0, p1, p2, p3, p4, p5, p6);
   }

   @Override
   public void trace(
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6,
      final Object p7
   ) {
      this.logIfEnabled(FQCN, Level.TRACE, null, message, p0, p1, p2, p3, p4, p5, p6, p7);
   }

   @Override
   public void trace(
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6,
      final Object p7,
      final Object p8
   ) {
      this.logIfEnabled(FQCN, Level.TRACE, null, message, p0, p1, p2, p3, p4, p5, p6, p7, p8);
   }

   @Override
   public void trace(
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6,
      final Object p7,
      final Object p8,
      final Object p9
   ) {
      this.logIfEnabled(FQCN, Level.TRACE, null, message, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9);
   }

   @Override
   public EntryMessage traceEntry() {
      return this.enter(FQCN, null, (Object[])null);
   }

   @Override
   public EntryMessage traceEntry(final String format, final Object... params) {
      return this.enter(FQCN, format, params);
   }

   @Override
   public EntryMessage traceEntry(final Supplier<?>... paramSuppliers) {
      return this.enter(FQCN, null, paramSuppliers);
   }

   @Override
   public EntryMessage traceEntry(final String format, final Supplier<?>... paramSuppliers) {
      return this.enter(FQCN, format, paramSuppliers);
   }

   @Override
   public EntryMessage traceEntry(final Message message) {
      return this.enter(FQCN, message);
   }

   @Override
   public void traceExit() {
      this.exit(FQCN, null, null);
   }

   @Override
   public <R> R traceExit(final R result) {
      return this.exit(FQCN, null, result);
   }

   @Override
   public <R> R traceExit(final String format, final R result) {
      return this.exit(FQCN, format, result);
   }

   @Override
   public void traceExit(final EntryMessage message) {
      if (message != null && this.isEnabled(Level.TRACE, EXIT_MARKER, message, null)) {
         this.logMessageSafely(FQCN, Level.TRACE, EXIT_MARKER, this.flowMessageFactory.newExitMessage(message), null);
      }
   }

   @Override
   public <R> R traceExit(final EntryMessage message, final R result) {
      if (message != null && this.isEnabled(Level.TRACE, EXIT_MARKER, message, null)) {
         this.logMessageSafely(FQCN, Level.TRACE, EXIT_MARKER, this.flowMessageFactory.newExitMessage(result, message), null);
      }

      return result;
   }

   @Override
   public <R> R traceExit(final Message message, final R result) {
      if (message != null && this.isEnabled(Level.TRACE, EXIT_MARKER, message, null)) {
         this.logMessageSafely(FQCN, Level.TRACE, EXIT_MARKER, this.flowMessageFactory.newExitMessage(result, message), null);
      }

      return result;
   }

   @Override
   public void warn(final Marker marker, final Message message) {
      this.logIfEnabled(FQCN, Level.WARN, marker, message, message != null ? message.getThrowable() : null);
   }

   @Override
   public void warn(final Marker marker, final Message message, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.WARN, marker, message, throwable);
   }

   @Override
   public void warn(final Marker marker, final CharSequence message) {
      this.logIfEnabled(FQCN, Level.WARN, marker, message, null);
   }

   @Override
   public void warn(final Marker marker, final CharSequence message, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.WARN, marker, message, throwable);
   }

   @Override
   public void warn(final Marker marker, final Object message) {
      this.logIfEnabled(FQCN, Level.WARN, marker, message, null);
   }

   @Override
   public void warn(final Marker marker, final Object message, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.WARN, marker, message, throwable);
   }

   @Override
   public void warn(final Marker marker, final String message) {
      this.logIfEnabled(FQCN, Level.WARN, marker, message, (Throwable)null);
   }

   @Override
   public void warn(final Marker marker, final String message, final Object... params) {
      this.logIfEnabled(FQCN, Level.WARN, marker, message, params);
   }

   @Override
   public void warn(final Marker marker, final String message, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.WARN, marker, message, throwable);
   }

   @Override
   public void warn(final Message message) {
      this.logIfEnabled(FQCN, Level.WARN, null, message, message != null ? message.getThrowable() : null);
   }

   @Override
   public void warn(final Message message, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.WARN, null, message, throwable);
   }

   @Override
   public void warn(final CharSequence message) {
      this.logIfEnabled(FQCN, Level.WARN, null, message, null);
   }

   @Override
   public void warn(final CharSequence message, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.WARN, null, message, throwable);
   }

   @Override
   public void warn(final Object message) {
      this.logIfEnabled(FQCN, Level.WARN, null, message, null);
   }

   @Override
   public void warn(final Object message, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.WARN, null, message, throwable);
   }

   @Override
   public void warn(final String message) {
      this.logIfEnabled(FQCN, Level.WARN, null, message, (Throwable)null);
   }

   @Override
   public void warn(final String message, final Object... params) {
      this.logIfEnabled(FQCN, Level.WARN, null, message, params);
   }

   @Override
   public void warn(final String message, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.WARN, null, message, throwable);
   }

   @Override
   public void warn(final Supplier<?> messageSupplier) {
      this.logIfEnabled(FQCN, Level.WARN, null, messageSupplier, (Throwable)null);
   }

   @Override
   public void warn(final Supplier<?> messageSupplier, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.WARN, null, messageSupplier, throwable);
   }

   @Override
   public void warn(final Marker marker, final Supplier<?> messageSupplier) {
      this.logIfEnabled(FQCN, Level.WARN, marker, messageSupplier, (Throwable)null);
   }

   @Override
   public void warn(final Marker marker, final String message, final Supplier<?>... paramSuppliers) {
      this.logIfEnabled(FQCN, Level.WARN, marker, message, paramSuppliers);
   }

   @Override
   public void warn(final Marker marker, final Supplier<?> messageSupplier, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.WARN, marker, messageSupplier, throwable);
   }

   @Override
   public void warn(final String message, final Supplier<?>... paramSuppliers) {
      this.logIfEnabled(FQCN, Level.WARN, null, message, paramSuppliers);
   }

   @Override
   public void warn(final Marker marker, final MessageSupplier messageSupplier) {
      this.logIfEnabled(FQCN, Level.WARN, marker, messageSupplier, (Throwable)null);
   }

   @Override
   public void warn(final Marker marker, final MessageSupplier messageSupplier, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.WARN, marker, messageSupplier, throwable);
   }

   @Override
   public void warn(final MessageSupplier messageSupplier) {
      this.logIfEnabled(FQCN, Level.WARN, null, messageSupplier, (Throwable)null);
   }

   @Override
   public void warn(final MessageSupplier messageSupplier, final Throwable throwable) {
      this.logIfEnabled(FQCN, Level.WARN, null, messageSupplier, throwable);
   }

   @Override
   public void warn(final Marker marker, final String message, final Object p0) {
      this.logIfEnabled(FQCN, Level.WARN, marker, message, p0);
   }

   @Override
   public void warn(final Marker marker, final String message, final Object p0, final Object p1) {
      this.logIfEnabled(FQCN, Level.WARN, marker, message, p0, p1);
   }

   @Override
   public void warn(final Marker marker, final String message, final Object p0, final Object p1, final Object p2) {
      this.logIfEnabled(FQCN, Level.WARN, marker, message, p0, p1, p2);
   }

   @Override
   public void warn(final Marker marker, final String message, final Object p0, final Object p1, final Object p2, final Object p3) {
      this.logIfEnabled(FQCN, Level.WARN, marker, message, p0, p1, p2, p3);
   }

   @Override
   public void warn(final Marker marker, final String message, final Object p0, final Object p1, final Object p2, final Object p3, final Object p4) {
      this.logIfEnabled(FQCN, Level.WARN, marker, message, p0, p1, p2, p3, p4);
   }

   @Override
   public void warn(
      final Marker marker, final String message, final Object p0, final Object p1, final Object p2, final Object p3, final Object p4, final Object p5
   ) {
      this.logIfEnabled(FQCN, Level.WARN, marker, message, p0, p1, p2, p3, p4, p5);
   }

   @Override
   public void warn(
      final Marker marker,
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6
   ) {
      this.logIfEnabled(FQCN, Level.WARN, marker, message, p0, p1, p2, p3, p4, p5, p6);
   }

   @Override
   public void warn(
      final Marker marker,
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6,
      final Object p7
   ) {
      this.logIfEnabled(FQCN, Level.WARN, marker, message, p0, p1, p2, p3, p4, p5, p6, p7);
   }

   @Override
   public void warn(
      final Marker marker,
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6,
      final Object p7,
      final Object p8
   ) {
      this.logIfEnabled(FQCN, Level.WARN, marker, message, p0, p1, p2, p3, p4, p5, p6, p7, p8);
   }

   @Override
   public void warn(
      final Marker marker,
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6,
      final Object p7,
      final Object p8,
      final Object p9
   ) {
      this.logIfEnabled(FQCN, Level.WARN, marker, message, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9);
   }

   @Override
   public void warn(final String message, final Object p0) {
      this.logIfEnabled(FQCN, Level.WARN, null, message, p0);
   }

   @Override
   public void warn(final String message, final Object p0, final Object p1) {
      this.logIfEnabled(FQCN, Level.WARN, null, message, p0, p1);
   }

   @Override
   public void warn(final String message, final Object p0, final Object p1, final Object p2) {
      this.logIfEnabled(FQCN, Level.WARN, null, message, p0, p1, p2);
   }

   @Override
   public void warn(final String message, final Object p0, final Object p1, final Object p2, final Object p3) {
      this.logIfEnabled(FQCN, Level.WARN, null, message, p0, p1, p2, p3);
   }

   @Override
   public void warn(final String message, final Object p0, final Object p1, final Object p2, final Object p3, final Object p4) {
      this.logIfEnabled(FQCN, Level.WARN, null, message, p0, p1, p2, p3, p4);
   }

   @Override
   public void warn(final String message, final Object p0, final Object p1, final Object p2, final Object p3, final Object p4, final Object p5) {
      this.logIfEnabled(FQCN, Level.WARN, null, message, p0, p1, p2, p3, p4, p5);
   }

   @Override
   public void warn(final String message, final Object p0, final Object p1, final Object p2, final Object p3, final Object p4, final Object p5, final Object p6) {
      this.logIfEnabled(FQCN, Level.WARN, null, message, p0, p1, p2, p3, p4, p5, p6);
   }

   @Override
   public void warn(
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6,
      final Object p7
   ) {
      this.logIfEnabled(FQCN, Level.WARN, null, message, p0, p1, p2, p3, p4, p5, p6, p7);
   }

   @Override
   public void warn(
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6,
      final Object p7,
      final Object p8
   ) {
      this.logIfEnabled(FQCN, Level.WARN, null, message, p0, p1, p2, p3, p4, p5, p6, p7, p8);
   }

   @Override
   public void warn(
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6,
      final Object p7,
      final Object p8,
      final Object p9
   ) {
      this.logIfEnabled(FQCN, Level.WARN, null, message, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9);
   }

   protected boolean requiresLocation() {
      return false;
   }

   @Override
   public LogBuilder atTrace() {
      return this.atLevel(Level.TRACE);
   }

   @Override
   public LogBuilder atDebug() {
      return this.atLevel(Level.DEBUG);
   }

   @Override
   public LogBuilder atInfo() {
      return this.atLevel(Level.INFO);
   }

   @Override
   public LogBuilder atWarn() {
      return this.atLevel(Level.WARN);
   }

   @Override
   public LogBuilder atError() {
      return this.atLevel(Level.ERROR);
   }

   @Override
   public LogBuilder atFatal() {
      return this.atLevel(Level.FATAL);
   }

   @Override
   public LogBuilder always() {
      DefaultLogBuilder builder = this.logBuilder.get();
      return (LogBuilder)(builder.isInUse() ? new DefaultLogBuilder(this) : builder.reset(Level.OFF));
   }

   @Override
   public LogBuilder atLevel(Level level) {
      return this.isEnabled(level) ? this.getLogBuilder(level).reset(level) : LogBuilder.NOOP;
   }

   private DefaultLogBuilder getLogBuilder(Level level) {
      DefaultLogBuilder builder = this.logBuilder.get();
      return Constants.ENABLE_THREADLOCALS && !builder.isInUse() ? builder : new DefaultLogBuilder(this, level);
   }

   private void readObject(final ObjectInputStream s) throws ClassNotFoundException, IOException {
      s.defaultReadObject();

      try {
         Field f = this.getClass().getDeclaredField("logBuilder");
         f.setAccessible(true);
         f.set(this, new AbstractLogger.LocalLogBuilder(this));
      } catch (IllegalAccessException | NoSuchFieldException var3) {
         StatusLogger.getLogger().warn("Unable to initialize LogBuilder");
      }
   }

   private class LocalLogBuilder extends ThreadLocal<DefaultLogBuilder> {
      private AbstractLogger logger;

      LocalLogBuilder(AbstractLogger logger) {
         this.logger = logger;
      }

      protected DefaultLogBuilder initialValue() {
         return new DefaultLogBuilder(this.logger);
      }
   }
}
