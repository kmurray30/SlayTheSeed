package org.apache.logging.log4j.message;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.IllegalFormatException;
import java.util.Locale;
import java.util.Locale.Category;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.status.StatusLogger;

public class StringFormattedMessage implements Message {
   private static final Logger LOGGER = StatusLogger.getLogger();
   private static final long serialVersionUID = -665975803997290697L;
   private static final int HASHVAL = 31;
   private String messagePattern;
   private transient Object[] argArray;
   private String[] stringArgs;
   private transient String formattedMessage;
   private transient Throwable throwable;
   private final Locale locale;

   public StringFormattedMessage(final Locale locale, final String messagePattern, final Object... arguments) {
      this.locale = locale;
      this.messagePattern = messagePattern;
      this.argArray = arguments;
      if (arguments != null && arguments.length > 0 && arguments[arguments.length - 1] instanceof Throwable) {
         this.throwable = (Throwable)arguments[arguments.length - 1];
      }
   }

   public StringFormattedMessage(final String messagePattern, final Object... arguments) {
      this(Locale.getDefault(Category.FORMAT), messagePattern, arguments);
   }

   @Override
   public String getFormattedMessage() {
      if (this.formattedMessage == null) {
         this.formattedMessage = this.formatMessage(this.messagePattern, this.argArray);
      }

      return this.formattedMessage;
   }

   @Override
   public String getFormat() {
      return this.messagePattern;
   }

   @Override
   public Object[] getParameters() {
      return (Object[])(this.argArray != null ? this.argArray : this.stringArgs);
   }

   protected String formatMessage(final String msgPattern, final Object... args) {
      try {
         return String.format(this.locale, msgPattern, args);
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
         StringFormattedMessage that = (StringFormattedMessage)o;
         return (this.messagePattern != null ? this.messagePattern.equals(that.messagePattern) : that.messagePattern == null)
            ? Arrays.equals((Object[])this.stringArgs, (Object[])that.stringArgs)
            : false;
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      int result = this.messagePattern != null ? this.messagePattern.hashCode() : 0;
      return 31 * result + (this.stringArgs != null ? Arrays.hashCode((Object[])this.stringArgs) : 0);
   }

   @Override
   public String toString() {
      return this.getFormattedMessage();
   }

   private void writeObject(final ObjectOutputStream out) throws IOException {
      out.defaultWriteObject();
      this.getFormattedMessage();
      out.writeUTF(this.formattedMessage);
      out.writeUTF(this.messagePattern);
      out.writeInt(this.argArray.length);
      this.stringArgs = new String[this.argArray.length];
      int i = 0;

      for (Object obj : this.argArray) {
         String string = String.valueOf(obj);
         this.stringArgs[i] = string;
         out.writeUTF(string);
         i++;
      }
   }

   private void readObject(final ObjectInputStream in) throws IOException, ClassNotFoundException {
      in.defaultReadObject();
      this.formattedMessage = in.readUTF();
      this.messagePattern = in.readUTF();
      int length = in.readInt();
      this.stringArgs = new String[length];

      for (int i = 0; i < length; i++) {
         this.stringArgs[i] = in.readUTF();
      }
   }

   @Override
   public Throwable getThrowable() {
      return this.throwable;
   }
}
