package org.apache.logging.log4j.core.config.plugins.validation.validators;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.plugins.convert.TypeConverters;
import org.apache.logging.log4j.core.config.plugins.validation.ConstraintValidator;
import org.apache.logging.log4j.core.config.plugins.validation.constraints.ValidPort;
import org.apache.logging.log4j.status.StatusLogger;

public class ValidPortValidator implements ConstraintValidator<ValidPort> {
   private static final Logger LOGGER = StatusLogger.getLogger();
   private ValidPort annotation;

   public void initialize(final ValidPort annotation) {
      this.annotation = annotation;
   }

   @Override
   public boolean isValid(final String name, final Object value) {
      if (value instanceof CharSequence) {
         return this.isValid(name, TypeConverters.convert(value.toString(), Integer.class, -1));
      } else if (!Integer.class.isInstance(value)) {
         LOGGER.error(this.annotation.message());
         return false;
      } else {
         int port = (Integer)value;
         if (port >= 0 && port <= 65535) {
            return true;
         } else {
            LOGGER.error(this.annotation.message());
            return false;
         }
      }
   }
}
