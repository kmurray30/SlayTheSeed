package org.apache.logging.log4j.message;

public interface ThreadInformation {
   void printThreadInfo(StringBuilder sb);

   void printStack(StringBuilder sb, StackTraceElement[] trace);
}
