package org.apache.logging.log4j.core.config.plugins.visitors;

import java.lang.annotation.Annotation;
import java.lang.reflect.Member;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.plugins.convert.TypeConverters;
import org.apache.logging.log4j.core.lookup.StrSubstitutor;
import org.apache.logging.log4j.status.StatusLogger;
import org.apache.logging.log4j.util.Strings;

public abstract class AbstractPluginVisitor<A extends Annotation> implements PluginVisitor<A> {
   protected static final Logger LOGGER = StatusLogger.getLogger();
   protected final Class<A> clazz;
   protected A annotation;
   protected String[] aliases;
   protected Class<?> conversionType;
   protected StrSubstitutor substitutor;
   protected Member member;

   protected AbstractPluginVisitor(final Class<A> clazz) {
      this.clazz = clazz;
   }

   @Override
   public PluginVisitor<A> setAnnotation(final Annotation anAnnotation) {
      Annotation a = Objects.requireNonNull(anAnnotation, "No annotation was provided");
      if (this.clazz.isInstance(a)) {
         this.annotation = (A)a;
      }

      return this;
   }

   @Override
   public PluginVisitor<A> setAliases(final String... someAliases) {
      this.aliases = someAliases;
      return this;
   }

   @Override
   public PluginVisitor<A> setConversionType(final Class<?> aConversionType) {
      this.conversionType = Objects.requireNonNull(aConversionType, "No conversion type class was provided");
      return this;
   }

   @Override
   public PluginVisitor<A> setStrSubstitutor(final StrSubstitutor aSubstitutor) {
      this.substitutor = Objects.requireNonNull(aSubstitutor, "No StrSubstitutor was provided");
      return this;
   }

   @Override
   public PluginVisitor<A> setMember(final Member aMember) {
      this.member = aMember;
      return this;
   }

   protected static String removeAttributeValue(final Map<String, String> attributes, final String name, final String... aliases) {
      for (Entry<String, String> entry : attributes.entrySet()) {
         String key = entry.getKey();
         String value = entry.getValue();
         if (key.equalsIgnoreCase(name)) {
            attributes.remove(key);
            return value;
         }

         if (aliases != null) {
            for (String alias : aliases) {
               if (key.equalsIgnoreCase(alias)) {
                  attributes.remove(key);
                  return value;
               }
            }
         }
      }

      return null;
   }

   protected Object convert(final String value, final Object defaultValue) {
      return defaultValue instanceof String
         ? TypeConverters.convert(value, this.conversionType, Strings.trimToNull((String)defaultValue))
         : TypeConverters.convert(value, this.conversionType, defaultValue);
   }
}
