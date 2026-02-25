package org.apache.logging.log4j.message;

public interface MessageFactory2 extends MessageFactory {
   Message newMessage(CharSequence charSequence);

   Message newMessage(String message, Object p0);

   Message newMessage(String message, Object p0, Object p1);

   Message newMessage(String message, Object p0, Object p1, Object p2);

   Message newMessage(String message, Object p0, Object p1, Object p2, Object p3);

   Message newMessage(String message, Object p0, Object p1, Object p2, Object p3, Object p4);

   Message newMessage(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5);

   Message newMessage(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6);

   Message newMessage(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7);

   Message newMessage(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8);

   Message newMessage(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8, Object p9);
}
