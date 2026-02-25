package org.apache.logging.log4j.core.pattern;

import java.util.List;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.util.PerformanceSensitive;

@Plugin(name = "notEmpty", category = "Converter")
@ConverterKeys({"notEmpty", "varsNotEmpty", "variablesNotEmpty"})
@PerformanceSensitive("allocation")
public final class VariablesNotEmptyReplacementConverter extends LogEventPatternConverter {
   private final List<PatternFormatter> formatters;

   private VariablesNotEmptyReplacementConverter(final List<PatternFormatter> formatters) {
      super("notEmpty", "notEmpty");
      this.formatters = formatters;
   }

   public static VariablesNotEmptyReplacementConverter newInstance(final Configuration config, final String[] options) {
      if (options.length != 1) {
         LOGGER.error("Incorrect number of options on varsNotEmpty. Expected 1 received " + options.length);
         return null;
      } else if (options[0] == null) {
         LOGGER.error("No pattern supplied on varsNotEmpty");
         return null;
      } else {
         PatternParser parser = PatternLayout.createPatternParser(config);
         List<PatternFormatter> formatters = parser.parse(options[0]);
         return new VariablesNotEmptyReplacementConverter(formatters);
      }
   }

   @Override
   public void format(final LogEvent event, final StringBuilder toAppendTo) {
      int start = toAppendTo.length();
      boolean allVarsEmpty = true;
      boolean hasVars = false;

      for (int i = 0; i < this.formatters.size(); i++) {
         PatternFormatter formatter = this.formatters.get(i);
         int formatterStart = toAppendTo.length();
         formatter.format(event, toAppendTo);
         if (formatter.getConverter().isVariable()) {
            hasVars = true;
            allVarsEmpty = allVarsEmpty && toAppendTo.length() == formatterStart;
         }
      }

      if (!hasVars || allVarsEmpty) {
         toAppendTo.setLength(start);
      }
   }
}
