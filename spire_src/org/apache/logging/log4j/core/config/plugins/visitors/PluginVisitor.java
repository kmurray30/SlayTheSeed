package org.apache.logging.log4j.core.config.plugins.visitors;

import java.lang.annotation.Annotation;
import java.lang.reflect.Member;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.lookup.StrSubstitutor;

public interface PluginVisitor<A extends Annotation> {
   PluginVisitor<A> setAnnotation(Annotation annotation);

   PluginVisitor<A> setAliases(String... aliases);

   PluginVisitor<A> setConversionType(Class<?> conversionType);

   PluginVisitor<A> setStrSubstitutor(StrSubstitutor substitutor);

   PluginVisitor<A> setMember(Member member);

   Object visit(Configuration configuration, Node node, LogEvent event, StringBuilder log);
}
