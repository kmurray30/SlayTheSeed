package org.apache.logging.log4j.message;

public interface FlowMessageFactory {
   EntryMessage newEntryMessage(Message message);

   ExitMessage newExitMessage(Object result, Message message);

   ExitMessage newExitMessage(EntryMessage message);

   ExitMessage newExitMessage(Object result, EntryMessage message);
}
