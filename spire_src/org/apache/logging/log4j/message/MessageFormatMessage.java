package org.apache.logging.log4j.message;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.IllegalFormatException;
import java.util.Locale;
import java.util.Locale.Category;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.status.StatusLogger;

public class MessageFormatMessage implements Message {
   private static final Logger LOGGER = StatusLogger.getLogger();
   private static final long serialVersionUID = 1L;
   private static final int HASHVAL = 31;
   private String messagePattern;
   private transient Object[] parameters;
   private String[] serializedParameters;
   private transient String formattedMessage;
   private transient Throwable throwable;
   private final Locale locale;

   public MessageFormatMessage(final Locale locale, final String messagePattern, final Object... parameters) {
      this.locale = locale;
      this.messagePattern = messagePattern;
      this.parameters = parameters;
      int length = parameters == null ? 0 : parameters.length;
      if (length > 0 && parameters[length - 1] instanceof Throwable) {
         this.throwable = (Throwable)parameters[length - 1];
      }
   }

   public MessageFormatMessage(final String messagePattern, final Object... parameters) {
      this(Locale.getDefault(Category.FORMAT), messagePattern, parameters);
   }

   @Override
   public String getFormattedMessage() {
      if (this.formattedMessage == null) {
         this.formattedMessage = this.formatMessage(this.messagePattern, this.parameters);
      }

      return this.formattedMessage;
   }

   @Override
   public String getFormat() {
      return this.messagePattern;
   }

   @Override
   public Object[] getParameters() {
      return (Object[])(this.parameters != null ? this.parameters : this.serializedParameters);
   }

   protected String formatMessage(final String msgPattern, final Object... args) {
      try {
         MessageFormat temp = new MessageFormat(msgPattern, this.locale);
         return temp.format(args);
      } catch (IllegalFormatException var4) {
         LOGGER.error("Unable to format msg: " + msgPattern, (Throwable)var4);
         return msgPattern;
      }
   }

   @Override
   public boolean equals(final Object o) {
      if (this == o) {
         return true;
      } else if (o != null && this.getClass() == o.getClass()) {
         MessageFormatMessage that = (MessageFormatMessage)o;
         return (this.messagePattern != null ? this.messagePattern.equals(that.messagePattern) : that.messagePattern == null)
            ? Arrays.equals((Object[])this.serializedParameters, (Object[])that.serializedParameters)
            : false;
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      int result = this.messagePattern != null ? this.messagePattern.hashCode() : 0;
      return 31 * result + (this.serializedParameters != null ? Arrays.hashCode((Object[])this.serializedParameters) : 0);
   }

   @Override
   public String toString() {
      return this.getFormattedMessage();
   }

   private void writeObject(final ObjectOutputStream out) throws IOException {
      this.getFormattedMessage();
      out.writeUTF(this.formattedMessage);
      out.writeUTF(this.messagePattern);
      int length = this.parameters == null ? 0 : this.parameters.length;
      out.writeInt(length);
      this.serializedParameters = new String[length];
      if (length > 0) {
         for (int i = 0; i < length; i++) {
            this.serializedParameters[i] = String.valueOf(this.parameters[i]);
            out.writeUTF(this.serializedParameters[i]);
         }
      }
   }

   private void readObject(final ObjectInputStream in) throws IOException {
      this.parameters = null;
      this.throwable = null;
      this.formattedMessage = in.readUTF();
      this.messagePattern = in.readUTF();
      int length = in.readInt();
      this.serializedParameters = new String[length];

      for (int i = 0; i < length; i++) {
         this.serializedParameters[i] = in.readUTF();
      }
   }

   @Override
   public Throwable getThrowable() {
      return this.throwable;
   }
}
