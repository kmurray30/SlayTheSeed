package org.apache.logging.log4j.core.jackson;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Log4jJsonObjectMapper extends ObjectMapper {
   private static final long serialVersionUID = 1L;

   public Log4jJsonObjectMapper() {
      this(false, true, false, false);
   }

   public Log4jJsonObjectMapper(
      final boolean encodeThreadContextAsList, final boolean includeStacktrace, final boolean stacktraceAsString, final boolean objectMessageAsJsonObject
   ) {
      this.registerModule(new Log4jJsonModule(encodeThreadContextAsList, includeStacktrace, stacktraceAsString, objectMessageAsJsonObject));
      this.setSerializationInclusion(Include.NON_EMPTY);
   }
}
