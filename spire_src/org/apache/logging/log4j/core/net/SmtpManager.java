package org.apache.logging.log4j.core.net;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import java.util.Properties;
import javax.activation.DataSource;
import javax.mail.Authenticator;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.Message.RecipientType;
import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimeUtility;
import javax.mail.util.ByteArrayDataSource;
import javax.net.ssl.SSLSocketFactory;
import org.apache.logging.log4j.LoggingException;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractManager;
import org.apache.logging.log4j.core.appender.ManagerFactory;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.layout.AbstractStringLayout;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.core.net.ssl.SslConfiguration;
import org.apache.logging.log4j.core.util.CyclicBuffer;
import org.apache.logging.log4j.core.util.NetUtils;
import org.apache.logging.log4j.util.PropertiesUtil;
import org.apache.logging.log4j.util.Strings;

public class SmtpManager extends AbstractManager {
   private static final SmtpManager.SMTPManagerFactory FACTORY = new SmtpManager.SMTPManagerFactory();
   private final Session session;
   private final CyclicBuffer<LogEvent> buffer;
   private volatile MimeMessage message;
   private final SmtpManager.FactoryData data;

   private static MimeMessage createMimeMessage(final SmtpManager.FactoryData data, final Session session, final LogEvent appendEvent) throws MessagingException {
      return new MimeMessageBuilder(session)
         .setFrom(data.from)
         .setReplyTo(data.replyto)
         .setRecipients(RecipientType.TO, data.to)
         .setRecipients(RecipientType.CC, data.cc)
         .setRecipients(RecipientType.BCC, data.bcc)
         .setSubject(data.subject.toSerializable(appendEvent))
         .build();
   }

   protected SmtpManager(final String name, final Session session, final MimeMessage message, final SmtpManager.FactoryData data) {
      super(null, name);
      this.session = session;
      this.message = message;
      this.data = data;
      this.buffer = new CyclicBuffer<>(LogEvent.class, data.numElements);
   }

   public void add(LogEvent event) {
      this.buffer.add(event.toImmutable());
   }

   public static SmtpManager getSmtpManager(
      final Configuration config,
      final String to,
      final String cc,
      final String bcc,
      final String from,
      final String replyTo,
      final String subject,
      String protocol,
      final String host,
      final int port,
      final String username,
      final String password,
      final boolean isDebug,
      final String filterName,
      final int numElements,
      final SslConfiguration sslConfiguration
   ) {
      if (Strings.isEmpty(protocol)) {
         protocol = "smtp";
      }

      String name = createManagerName(to, cc, bcc, from, replyTo, subject, protocol, host, port, username, isDebug, filterName);
      AbstractStringLayout.Serializer subjectSerializer = PatternLayout.newSerializerBuilder().setConfiguration(config).setPattern(subject).build();
      return getManager(
         name,
         FACTORY,
         new SmtpManager.FactoryData(
            to, cc, bcc, from, replyTo, subjectSerializer, protocol, host, port, username, password, isDebug, numElements, sslConfiguration
         )
      );
   }

   static String createManagerName(
      final String to,
      final String cc,
      final String bcc,
      final String from,
      final String replyTo,
      final String subject,
      final String protocol,
      final String host,
      final int port,
      final String username,
      final boolean isDebug,
      final String filterName
   ) {
      StringBuilder sb = new StringBuilder();
      if (to != null) {
         sb.append(to);
      }

      sb.append(':');
      if (cc != null) {
         sb.append(cc);
      }

      sb.append(':');
      if (bcc != null) {
         sb.append(bcc);
      }

      sb.append(':');
      if (from != null) {
         sb.append(from);
      }

      sb.append(':');
      if (replyTo != null) {
         sb.append(replyTo);
      }

      sb.append(':');
      if (subject != null) {
         sb.append(subject);
      }

      sb.append(':');
      sb.append(protocol).append(':').append(host).append(':').append(port).append(':');
      if (username != null) {
         sb.append(username);
      }

      sb.append(isDebug ? ":debug:" : "::");
      sb.append(filterName);
      return "SMTP:" + sb.toString();
   }

   public void sendEvents(final Layout<?> layout, final LogEvent appendEvent) {
      if (this.message == null) {
         this.connect(appendEvent);
      }

      try {
         LogEvent[] priorEvents = this.removeAllBufferedEvents();
         byte[] rawBytes = this.formatContentToBytes(priorEvents, appendEvent, layout);
         String contentType = layout.getContentType();
         String encoding = this.getEncoding(rawBytes, contentType);
         byte[] encodedBytes = this.encodeContentToBytes(rawBytes, encoding);
         InternetHeaders headers = this.getHeaders(contentType, encoding);
         MimeMultipart mp = this.getMimeMultipart(encodedBytes, headers);
         String subject = this.data.subject.toSerializable(appendEvent);
         this.sendMultipartMessage(this.message, mp, subject);
      } catch (IOException | RuntimeException | MessagingException var11) {
         this.logError("Caught exception while sending e-mail notification.", var11);
         throw new LoggingException("Error occurred while sending email", var11);
      }
   }

   LogEvent[] removeAllBufferedEvents() {
      return this.buffer.removeAll();
   }

   protected byte[] formatContentToBytes(final LogEvent[] priorEvents, final LogEvent appendEvent, final Layout<?> layout) throws IOException {
      ByteArrayOutputStream raw = new ByteArrayOutputStream();
      this.writeContent(priorEvents, appendEvent, layout, raw);
      return raw.toByteArray();
   }

   private void writeContent(final LogEvent[] priorEvents, final LogEvent appendEvent, final Layout<?> layout, final ByteArrayOutputStream out) throws IOException {
      this.writeHeader(layout, out);
      this.writeBuffer(priorEvents, appendEvent, layout, out);
      this.writeFooter(layout, out);
   }

   protected void writeHeader(final Layout<?> layout, final OutputStream out) throws IOException {
      byte[] header = layout.getHeader();
      if (header != null) {
         out.write(header);
      }
   }

   protected void writeBuffer(final LogEvent[] priorEvents, final LogEvent appendEvent, final Layout<?> layout, final OutputStream out) throws IOException {
      for (LogEvent priorEvent : priorEvents) {
         byte[] bytes = layout.toByteArray(priorEvent);
         out.write(bytes);
      }

      byte[] bytes = layout.toByteArray(appendEvent);
      out.write(bytes);
   }

   protected void writeFooter(final Layout<?> layout, final OutputStream out) throws IOException {
      byte[] footer = layout.getFooter();
      if (footer != null) {
         out.write(footer);
      }
   }

   protected String getEncoding(final byte[] rawBytes, final String contentType) {
      DataSource dataSource = new ByteArrayDataSource(rawBytes, contentType);
      return MimeUtility.getEncoding(dataSource);
   }

   protected byte[] encodeContentToBytes(final byte[] rawBytes, final String encoding) throws MessagingException, IOException {
      ByteArrayOutputStream encoded = new ByteArrayOutputStream();
      this.encodeContent(rawBytes, encoding, encoded);
      return encoded.toByteArray();
   }

   protected void encodeContent(final byte[] bytes, final String encoding, final ByteArrayOutputStream out) throws MessagingException, IOException {
      try (OutputStream encoder = MimeUtility.encode(out, encoding)) {
         encoder.write(bytes);
      }
   }

   protected InternetHeaders getHeaders(final String contentType, final String encoding) {
      InternetHeaders headers = new InternetHeaders();
      headers.setHeader("Content-Type", contentType + "; charset=UTF-8");
      headers.setHeader("Content-Transfer-Encoding", encoding);
      return headers;
   }

   protected MimeMultipart getMimeMultipart(final byte[] encodedBytes, final InternetHeaders headers) throws MessagingException {
      MimeMultipart mp = new MimeMultipart();
      MimeBodyPart part = new MimeBodyPart(headers, encodedBytes);
      mp.addBodyPart(part);
      return mp;
   }

   @Deprecated
   protected void sendMultipartMessage(final MimeMessage msg, final MimeMultipart mp) throws MessagingException {
      synchronized (msg) {
         msg.setContent(mp);
         msg.setSentDate(new Date());
         Transport.send(msg);
      }
   }

   protected void sendMultipartMessage(final MimeMessage msg, final MimeMultipart mp, final String subject) throws MessagingException {
      synchronized (msg) {
         msg.setContent(mp);
         msg.setSentDate(new Date());
         msg.setSubject(subject);
         Transport.send(msg);
      }
   }

   private synchronized void connect(final LogEvent appendEvent) {
      if (this.message == null) {
         try {
            this.message = createMimeMessage(this.data, this.session, appendEvent);
         } catch (MessagingException var3) {
            this.logError("Could not set SmtpAppender message options", var3);
            this.message = null;
         }
      }
   }

   private static class FactoryData {
      private final String to;
      private final String cc;
      private final String bcc;
      private final String from;
      private final String replyto;
      private final AbstractStringLayout.Serializer subject;
      private final String protocol;
      private final String host;
      private final int port;
      private final String username;
      private final String password;
      private final boolean isDebug;
      private final int numElements;
      private final SslConfiguration sslConfiguration;

      public FactoryData(
         final String to,
         final String cc,
         final String bcc,
         final String from,
         final String replyTo,
         final AbstractStringLayout.Serializer subjectSerializer,
         final String protocol,
         final String host,
         final int port,
         final String username,
         final String password,
         final boolean isDebug,
         final int numElements,
         final SslConfiguration sslConfiguration
      ) {
         this.to = to;
         this.cc = cc;
         this.bcc = bcc;
         this.from = from;
         this.replyto = replyTo;
         this.subject = subjectSerializer;
         this.protocol = protocol;
         this.host = host;
         this.port = port;
         this.username = username;
         this.password = password;
         this.isDebug = isDebug;
         this.numElements = numElements;
         this.sslConfiguration = sslConfiguration;
      }
   }

   private static class SMTPManagerFactory implements ManagerFactory<SmtpManager, SmtpManager.FactoryData> {
      private SMTPManagerFactory() {
      }

      public SmtpManager createManager(final String name, final SmtpManager.FactoryData data) {
         String prefix = "mail." + data.protocol;
         Properties properties = PropertiesUtil.getSystemProperties();
         properties.setProperty("mail.transport.protocol", data.protocol);
         if (properties.getProperty("mail.host") == null) {
            properties.setProperty("mail.host", NetUtils.getLocalHostname());
         }

         if (null != data.host) {
            properties.setProperty(prefix + ".host", data.host);
         }

         if (data.port > 0) {
            properties.setProperty(prefix + ".port", String.valueOf(data.port));
         }

         Authenticator authenticator = this.buildAuthenticator(data.username, data.password);
         if (null != authenticator) {
            properties.setProperty(prefix + ".auth", "true");
         }

         if (data.protocol.equals("smtps")) {
            SslConfiguration sslConfiguration = data.sslConfiguration;
            if (sslConfiguration != null) {
               SSLSocketFactory sslSocketFactory = sslConfiguration.getSslSocketFactory();
               properties.put(prefix + ".ssl.socketFactory", sslSocketFactory);
               properties.setProperty(prefix + ".ssl.checkserveridentity", Boolean.toString(sslConfiguration.isVerifyHostName()));
            }
         }

         Session session = Session.getInstance(properties, authenticator);
         session.setProtocolForAddress("rfc822", data.protocol);
         session.setDebug(data.isDebug);
         return new SmtpManager(name, session, null, data);
      }

      private Authenticator buildAuthenticator(final String username, final String password) {
         return null != password && null != username ? new Authenticator() {
            private final PasswordAuthentication passwordAuthentication = new PasswordAuthentication(username, password);

            protected PasswordAuthentication getPasswordAuthentication() {
               return this.passwordAuthentication;
            }
         } : null;
      }
   }
}
