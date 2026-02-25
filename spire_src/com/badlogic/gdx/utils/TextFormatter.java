package com.badlogic.gdx.utils;

import java.text.MessageFormat;
import java.util.Locale;

class TextFormatter {
   private MessageFormat messageFormat;
   private StringBuilder buffer = new StringBuilder();

   public TextFormatter(Locale locale, boolean useMessageFormat) {
      if (useMessageFormat) {
         this.messageFormat = new MessageFormat("", locale);
      }
   }

   public String format(String pattern, Object... args) {
      if (this.messageFormat != null) {
         this.messageFormat.applyPattern(this.replaceEscapeChars(pattern));
         return this.messageFormat.format(args);
      } else {
         return this.simpleFormat(pattern, args);
      }
   }

   private String replaceEscapeChars(String pattern) {
      this.buffer.setLength(0);
      boolean changed = false;
      int len = pattern.length();

      for (int i = 0; i < len; i++) {
         char ch = pattern.charAt(i);
         if (ch == '\'') {
            changed = true;
            this.buffer.append("''");
         } else if (ch != '{') {
            this.buffer.append(ch);
         } else {
            int j = i + 1;

            while (j < len && pattern.charAt(j) == '{') {
               j++;
            }

            int escaped = (j - i) / 2;
            if (escaped > 0) {
               changed = true;
               this.buffer.append('\'');

               do {
                  this.buffer.append('{');
               } while (--escaped > 0);

               this.buffer.append('\'');
            }

            if ((j - i) % 2 != 0) {
               this.buffer.append('{');
            }

            i = j - 1;
         }
      }

      return changed ? this.buffer.toString() : pattern;
   }

   private String simpleFormat(String pattern, Object... args) {
      this.buffer.setLength(0);
      boolean changed = false;
      int placeholder = -1;
      int patternLength = pattern.length();

      for (int i = 0; i < patternLength; i++) {
         char ch = pattern.charAt(i);
         if (placeholder < 0) {
            if (ch == '{') {
               changed = true;
               if (i + 1 < patternLength && pattern.charAt(i + 1) == '{') {
                  this.buffer.append(ch);
                  i++;
               } else {
                  placeholder = 0;
               }
            } else {
               this.buffer.append(ch);
            }
         } else if (ch == '}') {
            if (placeholder >= args.length) {
               throw new IllegalArgumentException("Argument index out of bounds: " + placeholder);
            }

            if (pattern.charAt(i - 1) == '{') {
               throw new IllegalArgumentException("Missing argument index after a left curly brace");
            }

            if (args[placeholder] == null) {
               this.buffer.append("null");
            } else {
               this.buffer.append(args[placeholder].toString());
            }

            placeholder = -1;
         } else {
            if (ch < '0' || ch > '9') {
               throw new IllegalArgumentException("Unexpected '" + ch + "' while parsing argument index");
            }

            placeholder = placeholder * 10 + (ch - '0');
         }
      }

      if (placeholder >= 0) {
         throw new IllegalArgumentException("Unmatched braces in the pattern.");
      } else {
         return changed ? this.buffer.toString() : pattern;
      }
   }
}
