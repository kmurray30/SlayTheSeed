package org.apache.commons.net.nntp;

public interface Threadable {
   boolean isDummy();

   String messageThreadId();

   String[] messageThreadReferences();

   String simplifiedSubject();

   boolean subjectIsReply();

   void setChild(Threadable var1);

   void setNext(Threadable var1);

   Threadable makeDummy();
}
