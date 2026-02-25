package org.apache.logging.log4j.core.lookup;

import org.apache.logging.log4j.core.LogEvent;

public interface StrLookup {
   String CATEGORY = "Lookup";

   String lookup(String key);

   String lookup(LogEvent event, String key);
}
