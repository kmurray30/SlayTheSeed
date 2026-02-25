package org.apache.logging.log4j.core.net;

import java.nio.charset.StandardCharsets;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Message.RecipientType;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import org.apache.logging.log4j.core.util.Builder;

public class MimeMessageBuilder implements Builder<MimeMessage> {
   private final MimeMessage message;

   public MimeMessageBuilder(final Session session) {
      this.message = new MimeMessage(session);
   }

   public MimeMessageBuilder setFrom(final String from) throws MessagingException {
      InternetAddress address = parseAddress(from);
      if (null != address) {
         this.message.setFrom(address);
      } else {
         try {
            this.message.setFrom();
         } catch (Exception var4) {
            this.message.setFrom((InternetAddress)null);
         }
      }

      return this;
   }

   public MimeMessageBuilder setReplyTo(final String replyTo) throws MessagingException {
      InternetAddress[] addresses = parseAddresses(replyTo);
      if (null != addresses) {
         this.message.setReplyTo(addresses);
      }

      return this;
   }

   public MimeMessageBuilder setRecipients(final RecipientType recipientType, final String recipients) throws MessagingException {
      InternetAddress[] addresses = parseAddresses(recipients);
      if (null != addresses) {
         this.message.setRecipients(recipientType, addresses);
      }

      return this;
   }

   public MimeMessageBuilder setSubject(final String subject) throws MessagingException {
      if (subject != null) {
         this.message.setSubject(subject, StandardCharsets.UTF_8.name());
      }

      return this;
   }

   @Deprecated
   public MimeMessage getMimeMessage() {
      return this.build();
   }

   public MimeMessage build() {
      return this.message;
   }

   private static InternetAddress parseAddress(final String address) throws AddressException {
      return address == null ? null : new InternetAddress(address);
   }

   private static InternetAddress[] parseAddresses(final String addresses) throws AddressException {
      return addresses == null ? null : InternetAddress.parse(addresses, true);
   }
}
