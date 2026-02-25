package org.apache.logging.log4j.message;

import org.apache.logging.log4j.util.PerformanceSensitive;
import org.apache.logging.log4j.util.StringBuilderFormattable;

@PerformanceSensitive("allocation")
public interface ReusableMessage extends Message, StringBuilderFormattable {
   Object[] swapParameters(Object[] emptyReplacement);

   short getParameterCount();

   Message memento();
}
