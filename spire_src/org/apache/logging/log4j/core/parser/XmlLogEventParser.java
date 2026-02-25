package org.apache.logging.log4j.core.parser;

import org.apache.logging.log4j.core.jackson.Log4jXmlObjectMapper;

public class XmlLogEventParser extends AbstractJacksonLogEventParser {
   public XmlLogEventParser() {
      super(new Log4jXmlObjectMapper());
   }
}
