package org.apache.logging.log4j.spi;

import java.util.List;

public interface LoggerContextShutdownEnabled {
   void addShutdownListener(LoggerContextShutdownAware listener);

   List<LoggerContextShutdownAware> getListeners();
}
