package org.apache.logging.log4j.core.pattern;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.util.Loader;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.MultiformatMessage;
import org.apache.logging.log4j.status.StatusLogger;
import org.apache.logging.log4j.util.MultiFormatStringBuilderFormattable;
import org.apache.logging.log4j.util.PerformanceSensitive;
import org.apache.logging.log4j.util.StringBuilderFormattable;

@Plugin(name = "MessagePatternConverter", category = "Converter")
@ConverterKeys({"m", "msg", "message"})
@PerformanceSensitive("allocation")
public class MessagePatternConverter extends LogEventPatternConverter {
   private static final String LOOKUPS = "lookups";
   private static final String NOLOOKUPS = "nolookups";

   private MessagePatternConverter() {
      super("Message", "message");
   }

   private static TextRenderer loadMessageRenderer(final String[] options) {
      if (options != null) {
         String[] var1 = options;
         int var2 = options.length;
         int var3 = 0;

         while (var3 < var2) {
            String option = var1[var3];
            String var5 = option.toUpperCase(Locale.ROOT);
            switch (var5) {
               case "ANSI":
                  if (Loader.isJansiAvailable()) {
                     return new JAnsiTextRenderer(options, JAnsiTextRenderer.DefaultMessageStyleMap);
                  }

                  StatusLogger.getLogger().warn("You requested ANSI message rendering but JANSI is not on the classpath.");
                  return null;
               case "HTML":
                  return new HtmlTextRenderer(options);
               default:
                  var3++;
            }
         }
      }

      return null;
   }

   public static MessagePatternConverter newInstance(final Configuration config, final String[] options) {
      String[] formats = withoutLookupOptions(options);
      TextRenderer textRenderer = loadMessageRenderer(formats);
      MessagePatternConverter result = (MessagePatternConverter)(formats != null && formats.length != 0
         ? new MessagePatternConverter.FormattedMessagePatternConverter(formats)
         : MessagePatternConverter.SimpleMessagePatternConverter.INSTANCE);
      if (textRenderer != null) {
         result = new MessagePatternConverter.RenderingPatternConverter(result, textRenderer);
      }

      return result;
   }

   private static String[] withoutLookupOptions(final String[] options) {
      if (options != null && options.length != 0) {
         List<String> results = new ArrayList<>(options.length);

         for (String option : options) {
            if (!"lookups".equalsIgnoreCase(option) && !"nolookups".equalsIgnoreCase(option)) {
               results.add(option);
            } else {
               LOGGER.info("The {} option will be ignored. Message Lookups are no longer supported.", option);
            }
         }

         return results.toArray(new String[0]);
      } else {
         return options;
      }
   }

   @Override
   public void format(final LogEvent event, final StringBuilder toAppendTo) {
      throw new UnsupportedOperationException();
   }

   private static final class FormattedMessagePatternConverter extends MessagePatternConverter {
      private final String[] formats;

      FormattedMessagePatternConverter(final String[] formats) {
         this.formats = formats;
      }

      @Override
      public void format(final LogEvent event, final StringBuilder toAppendTo) {
         Message msg = event.getMessage();
         if (msg instanceof StringBuilderFormattable) {
            if (msg instanceof MultiFormatStringBuilderFormattable) {
               ((MultiFormatStringBuilderFormattable)msg).formatTo(this.formats, toAppendTo);
            } else {
               ((StringBuilderFormattable)msg).formatTo(toAppendTo);
            }
         } else if (msg != null) {
            toAppendTo.append(msg instanceof MultiformatMessage ? ((MultiformatMessage)msg).getFormattedMessage(this.formats) : msg.getFormattedMessage());
         }
      }
   }

   private static final class RenderingPatternConverter extends MessagePatternConverter {
      private final MessagePatternConverter delegate;
      private final TextRenderer textRenderer;

      RenderingPatternConverter(final MessagePatternConverter delegate, final TextRenderer textRenderer) {
         this.delegate = delegate;
         this.textRenderer = textRenderer;
      }

      @Override
      public void format(final LogEvent event, final StringBuilder toAppendTo) {
         StringBuilder workingBuilder = new StringBuilder(80);
         this.delegate.format(event, workingBuilder);
         this.textRenderer.render(workingBuilder, toAppendTo);
      }
   }

   private static final class SimpleMessagePatternConverter extends MessagePatternConverter {
      private static final MessagePatternConverter INSTANCE = new MessagePatternConverter.SimpleMessagePatternConverter();

      @Override
      public void format(final LogEvent event, final StringBuilder toAppendTo) {
         Message msg = event.getMessage();
         if (msg instanceof StringBuilderFormattable) {
            ((StringBuilderFormattable)msg).formatTo(toAppendTo);
         } else if (msg != null) {
            toAppendTo.append(msg.getFormattedMessage());
         }
      }
   }
}
