package org.apache.logging.log4j.core.layout;

import java.io.IOException;
import java.nio.charset.Charset;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.QuoteMode;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginConfiguration;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.status.StatusLogger;

@Plugin(name = "CsvLogEventLayout", category = "Core", elementType = "layout", printObject = true)
public class CsvLogEventLayout extends AbstractCsvLayout {
   public static CsvLogEventLayout createDefaultLayout() {
      return new CsvLogEventLayout(null, Charset.forName("UTF-8"), CSVFormat.valueOf("Default"), null, null);
   }

   public static CsvLogEventLayout createLayout(final CSVFormat format) {
      return new CsvLogEventLayout(null, Charset.forName("UTF-8"), format, null, null);
   }

   @PluginFactory
   public static CsvLogEventLayout createLayout(
      @PluginConfiguration final Configuration config,
      @PluginAttribute(value = "format", defaultString = "Default") final String format,
      @PluginAttribute("delimiter") final Character delimiter,
      @PluginAttribute("escape") final Character escape,
      @PluginAttribute("quote") final Character quote,
      @PluginAttribute("quoteMode") final QuoteMode quoteMode,
      @PluginAttribute("nullString") final String nullString,
      @PluginAttribute("recordSeparator") final String recordSeparator,
      @PluginAttribute(value = "charset", defaultString = "UTF-8") final Charset charset,
      @PluginAttribute("header") final String header,
      @PluginAttribute("footer") final String footer
   ) {
      CSVFormat csvFormat = createFormat(format, delimiter, escape, quote, quoteMode, nullString, recordSeparator);
      return new CsvLogEventLayout(config, charset, csvFormat, header, footer);
   }

   protected CsvLogEventLayout(final Configuration config, final Charset charset, final CSVFormat csvFormat, final String header, final String footer) {
      super(config, charset, csvFormat, header, footer);
   }

   public String toSerializable(final LogEvent event) {
      StringBuilder buffer = getStringBuilder();
      CSVFormat format = this.getFormat();

      try {
         format.print(event.getNanoTime(), buffer, true);
         format.print(event.getTimeMillis(), buffer, false);
         format.print(event.getLevel(), buffer, false);
         format.print(event.getThreadId(), buffer, false);
         format.print(event.getThreadName(), buffer, false);
         format.print(event.getThreadPriority(), buffer, false);
         format.print(event.getMessage().getFormattedMessage(), buffer, false);
         format.print(event.getLoggerFqcn(), buffer, false);
         format.print(event.getLoggerName(), buffer, false);
         format.print(event.getMarker(), buffer, false);
         format.print(event.getThrownProxy(), buffer, false);
         format.print(event.getSource(), buffer, false);
         format.print(event.getContextData(), buffer, false);
         format.print(event.getContextStack(), buffer, false);
         format.println(buffer);
         return buffer.toString();
      } catch (IOException var5) {
         StatusLogger.getLogger().error(event.toString(), var5);
         return format.getCommentMarker() + " " + var5;
      }
   }
}
