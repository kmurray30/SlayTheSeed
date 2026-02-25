package org.apache.logging.log4j.message;

public interface MultiformatMessage extends Message {
   String getFormattedMessage(String[] formats);

   String[] getFormats();
}
