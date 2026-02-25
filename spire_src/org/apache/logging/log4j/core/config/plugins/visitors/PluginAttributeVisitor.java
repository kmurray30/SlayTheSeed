package org.apache.logging.log4j.core.config.plugins.visitors;

import java.util.Map;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.util.StringBuilders;

public class PluginAttributeVisitor extends AbstractPluginVisitor<PluginAttribute> {
   public PluginAttributeVisitor() {
      super(PluginAttribute.class);
   }

   @Override
   public Object visit(final Configuration configuration, final Node node, final LogEvent event, final StringBuilder log) {
      String name = this.annotation.value();
      Map<String, String> attributes = node.getAttributes();
      String rawValue = removeAttributeValue(attributes, name, this.aliases);
      String replacedValue = this.substitutor.replace(event, rawValue);
      Object defaultValue = this.findDefaultValue(event);
      Object value = this.convert(replacedValue, defaultValue);
      Object debugValue = this.annotation.sensitive() ? "*****" : value;
      StringBuilders.appendKeyDqValue(log, name, debugValue);
      return value;
   }

   private Object findDefaultValue(final LogEvent event) {
      if (this.conversionType == int.class || this.conversionType == Integer.class) {
         return this.annotation.defaultInt();
      } else if (this.conversionType == long.class || this.conversionType == Long.class) {
         return this.annotation.defaultLong();
      } else if (this.conversionType == boolean.class || this.conversionType == Boolean.class) {
         return this.annotation.defaultBoolean();
      } else if (this.conversionType == float.class || this.conversionType == Float.class) {
         return this.annotation.defaultFloat();
      } else if (this.conversionType == double.class || this.conversionType == Double.class) {
         return this.annotation.defaultDouble();
      } else if (this.conversionType == byte.class || this.conversionType == Byte.class) {
         return this.annotation.defaultByte();
      } else if (this.conversionType == char.class || this.conversionType == Character.class) {
         return this.annotation.defaultChar();
      } else if (this.conversionType == short.class || this.conversionType == Short.class) {
         return this.annotation.defaultShort();
      } else {
         return this.conversionType == Class.class ? this.annotation.defaultClass() : this.substitutor.replace(event, this.annotation.defaultString());
      }
   }
}
