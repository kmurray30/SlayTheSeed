package org.apache.logging.log4j.core.layout;

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.Objects;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;

@Plugin(name = "PatternMatch", category = "Core", printObject = true)
public final class PatternMatch {
   private final String key;
   private final String pattern;

   public PatternMatch(final String key, final String pattern) {
      this.key = key;
      this.pattern = pattern;
   }

   public String getKey() {
      return this.key;
   }

   public String getPattern() {
      return this.pattern;
   }

   @Override
   public String toString() {
      return this.key + '=' + this.pattern;
   }

   @PluginBuilderFactory
   public static PatternMatch.Builder newBuilder() {
      return new PatternMatch.Builder();
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.key, this.pattern);
   }

   @Override
   public boolean equals(final Object obj) {
      if (this == obj) {
         return true;
      } else if (obj == null) {
         return false;
      } else if (this.getClass() != obj.getClass()) {
         return false;
      } else {
         PatternMatch other = (PatternMatch)obj;
         return !Objects.equals(this.key, other.key) ? false : Objects.equals(this.pattern, other.pattern);
      }
   }

   public static class Builder implements org.apache.logging.log4j.core.util.Builder<PatternMatch>, Serializable {
      private static final long serialVersionUID = 1L;
      @PluginBuilderAttribute
      private String key;
      @PluginBuilderAttribute
      private String pattern;

      public PatternMatch.Builder setKey(final String key) {
         this.key = key;
         return this;
      }

      public PatternMatch.Builder setPattern(final String pattern) {
         this.pattern = pattern;
         return this;
      }

      public PatternMatch build() {
         return new PatternMatch(this.key, this.pattern);
      }

      protected Object readResolve() throws ObjectStreamException {
         return new PatternMatch(this.key, this.pattern);
      }
   }
}
