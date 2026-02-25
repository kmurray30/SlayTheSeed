package org.apache.logging.log4j.core.jackson;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.annotation.JsonCreator.Mode;
import org.apache.logging.log4j.Level;

@JsonIgnoreProperties({"name", "declaringClass", "standardLevel"})
abstract class LevelMixIn {
   @JsonCreator(mode = Mode.DELEGATING)
   public static Level getLevel(final String name) {
      return null;
   }

   @JsonValue
   public abstract String name();
}
