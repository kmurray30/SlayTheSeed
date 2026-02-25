package org.apache.logging.log4j.core.pattern;

import java.util.List;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.util.EnglishEnums;
import org.apache.logging.log4j.util.PerformanceSensitive;
import org.apache.logging.log4j.util.StringBuilders;

@Plugin(name = "encode", category = "Converter")
@ConverterKeys({"enc", "encode"})
@PerformanceSensitive("allocation")
public final class EncodingPatternConverter extends LogEventPatternConverter {
   private final List<PatternFormatter> formatters;
   private final EncodingPatternConverter.EscapeFormat escapeFormat;

   private EncodingPatternConverter(final List<PatternFormatter> formatters, final EncodingPatternConverter.EscapeFormat escapeFormat) {
      super("encode", "encode");
      this.formatters = formatters;
      this.escapeFormat = escapeFormat;
   }

   @Override
   public boolean handlesThrowable() {
      return this.formatters != null && this.formatters.stream().map(PatternFormatter::getConverter).anyMatch(LogEventPatternConverter::handlesThrowable);
   }

   public static EncodingPatternConverter newInstance(final Configuration config, final String[] options) {
      if (options.length > 2 || options.length == 0) {
         LOGGER.error("Incorrect number of options on escape. Expected 1 or 2, but received {}", options.length);
         return null;
      } else if (options[0] == null) {
         LOGGER.error("No pattern supplied on escape");
         return null;
      } else {
         EncodingPatternConverter.EscapeFormat escapeFormat = options.length < 2
            ? EncodingPatternConverter.EscapeFormat.HTML
            : EnglishEnums.valueOf(EncodingPatternConverter.EscapeFormat.class, options[1], EncodingPatternConverter.EscapeFormat.HTML);
         PatternParser parser = PatternLayout.createPatternParser(config);
         List<PatternFormatter> formatters = parser.parse(options[0]);
         return new EncodingPatternConverter(formatters, escapeFormat);
      }
   }

   @Override
   public void format(final LogEvent event, final StringBuilder toAppendTo) {
      int start = toAppendTo.length();

      for (int i = 0; i < this.formatters.size(); i++) {
         this.formatters.get(i).format(event, toAppendTo);
      }

      this.escapeFormat.escape(toAppendTo, start);
   }

   private static enum EscapeFormat {
      HTML {
         @Override
         void escape(final StringBuilder toAppendTo, final int start) {
            int origLength = toAppendTo.length();
            int firstSpecialChar = origLength;

            for (int i = origLength - 1; i >= start; i--) {
               char c = toAppendTo.charAt(i);
               String escaped = this.escapeChar(c);
               if (escaped != null) {
                  firstSpecialChar = i;

                  for (int j = 0; j < escaped.length() - 1; j++) {
                     toAppendTo.append(' ');
                  }
               }
            }

            int ix = origLength - 1;

            for (int j = toAppendTo.length(); ix >= firstSpecialChar; ix--) {
               char c = toAppendTo.charAt(ix);
               String escaped = this.escapeChar(c);
               if (escaped == null) {
                  toAppendTo.setCharAt(--j, c);
               } else {
                  toAppendTo.replace(j - escaped.length(), j, escaped);
                  j -= escaped.length();
               }
            }
         }

         private String escapeChar(char c) {
            switch (c) {
               case '\n':
                  return "\\n";
               case '\r':
                  return "\\r";
               case '"':
                  return "&quot;";
               case '&':
                  return "&amp;";
               case '\'':
                  return "&apos;";
               case '/':
                  return "&#x2F;";
               case '<':
                  return "&lt;";
               case '>':
                  return "&gt;";
               default:
                  return null;
            }
         }
      },
      JSON {
         @Override
         void escape(final StringBuilder toAppendTo, final int start) {
            StringBuilders.escapeJson(toAppendTo, start);
         }
      },
      CRLF {
         @Override
         void escape(final StringBuilder toAppendTo, final int start) {
            int origLength = toAppendTo.length();
            int firstSpecialChar = origLength;

            for (int i = origLength - 1; i >= start; i--) {
               char c = toAppendTo.charAt(i);
               if (c == '\r' || c == '\n') {
                  firstSpecialChar = i;
                  toAppendTo.append(' ');
               }
            }

            int ix = origLength - 1;

            for (int j = toAppendTo.length(); ix >= firstSpecialChar; ix--) {
               char c = toAppendTo.charAt(ix);
               switch (c) {
                  case '\n':
                     toAppendTo.setCharAt(--j, 'n');
                     toAppendTo.setCharAt(--j, '\\');
                     break;
                  case '\r':
                     toAppendTo.setCharAt(--j, 'r');
                     toAppendTo.setCharAt(--j, '\\');
                     break;
                  default:
                     toAppendTo.setCharAt(--j, c);
               }
            }
         }
      },
      XML {
         @Override
         void escape(final StringBuilder toAppendTo, final int start) {
            StringBuilders.escapeXml(toAppendTo, start);
         }
      };

      private EscapeFormat() {
      }

      abstract void escape(final StringBuilder toAppendTo, final int start);
   }
}
