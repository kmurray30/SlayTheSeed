package org.apache.logging.log4j.core.jackson;

import com.fasterxml.jackson.databind.Module.SetupContext;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.ExtendedStackTraceElement;
import org.apache.logging.log4j.core.impl.ThrowableProxy;
import org.apache.logging.log4j.core.time.Instant;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.ObjectMessage;

class Initializers {
   static class SetupContextInitializer {
      void setupModule(final SetupContext context, final boolean includeStacktrace, final boolean stacktraceAsString) {
         context.setMixInAnnotations(StackTraceElement.class, StackTraceElementMixIn.class);
         context.setMixInAnnotations(Marker.class, MarkerMixIn.class);
         context.setMixInAnnotations(Level.class, LevelMixIn.class);
         context.setMixInAnnotations(Instant.class, InstantMixIn.class);
         context.setMixInAnnotations(LogEvent.class, LogEventWithContextListMixIn.class);
         context.setMixInAnnotations(ExtendedStackTraceElement.class, ExtendedStackTraceElementMixIn.class);
         context.setMixInAnnotations(
            ThrowableProxy.class,
            includeStacktrace
               ? (stacktraceAsString ? ThrowableProxyWithStacktraceAsStringMixIn.class : ThrowableProxyMixIn.class)
               : ThrowableProxyWithoutStacktraceMixIn.class
         );
      }
   }

   static class SetupContextJsonInitializer {
      void setupModule(final SetupContext context, final boolean includeStacktrace, final boolean stacktraceAsString) {
         context.setMixInAnnotations(StackTraceElement.class, StackTraceElementMixIn.class);
         context.setMixInAnnotations(Marker.class, MarkerMixIn.class);
         context.setMixInAnnotations(Level.class, LevelMixIn.class);
         context.setMixInAnnotations(Instant.class, InstantMixIn.class);
         context.setMixInAnnotations(LogEvent.class, LogEventJsonMixIn.class);
         context.setMixInAnnotations(ExtendedStackTraceElement.class, ExtendedStackTraceElementMixIn.class);
         context.setMixInAnnotations(
            ThrowableProxy.class,
            includeStacktrace
               ? (stacktraceAsString ? ThrowableProxyWithStacktraceAsStringMixIn.class : ThrowableProxyMixIn.class)
               : ThrowableProxyWithoutStacktraceMixIn.class
         );
      }
   }

   static class SimpleModuleInitializer {
      void initialize(final SimpleModule simpleModule, final boolean objectMessageAsJsonObject) {
         simpleModule.addDeserializer(StackTraceElement.class, new Log4jStackTraceElementDeserializer());
         simpleModule.addDeserializer(ThreadContext.ContextStack.class, new MutableThreadContextStackDeserializer());
         if (objectMessageAsJsonObject) {
            simpleModule.addSerializer(ObjectMessage.class, new ObjectMessageSerializer());
         }

         simpleModule.addSerializer(Message.class, new MessageSerializer());
      }
   }
}
