package org.apache.logging.log4j.core.jackson;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

public class Log4jYamlObjectMapper extends YAMLMapper {
   private static final long serialVersionUID = 1L;

   public Log4jYamlObjectMapper() {
      this(false, true, false);
   }

   public Log4jYamlObjectMapper(final boolean encodeThreadContextAsList, final boolean includeStacktrace, final boolean stacktraceAsString) {
      this.registerModule(new Log4jYamlModule(encodeThreadContextAsList, includeStacktrace, stacktraceAsString));
      this.setSerializationInclusion(Include.NON_EMPTY);
   }
}
