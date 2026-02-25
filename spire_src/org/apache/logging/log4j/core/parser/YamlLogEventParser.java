package org.apache.logging.log4j.core.parser;

import org.apache.logging.log4j.core.jackson.Log4jYamlObjectMapper;

public class YamlLogEventParser extends AbstractJacksonLogEventParser {
   public YamlLogEventParser() {
      super(new Log4jYamlObjectMapper());
   }
}
