package org.apache.logging.log4j.core.layout;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.core.util.KeyValuePair;

@Plugin(name = "XmlLayout", category = "Core", elementType = "layout", printObject = true)
public final class XmlLayout extends AbstractJacksonLayout {
   private static final String ROOT_TAG = "Events";

   @Deprecated
   protected XmlLayout(
      final boolean locationInfo,
      final boolean properties,
      final boolean complete,
      final boolean compact,
      final Charset charset,
      final boolean includeStacktrace
   ) {
      this(null, locationInfo, properties, complete, compact, null, charset, includeStacktrace, false, false, false, null);
   }

   private XmlLayout(
      final Configuration config,
      final boolean locationInfo,
      final boolean properties,
      final boolean complete,
      final boolean compact,
      final String endOfLine,
      final Charset charset,
      final boolean includeStacktrace,
      final boolean stacktraceAsString,
      final boolean includeNullDelimiter,
      final boolean includeTimeMillis,
      final KeyValuePair[] additionalFields
   ) {
      super(
         config,
         new JacksonFactory.XML(includeStacktrace, stacktraceAsString).newWriter(locationInfo, properties, compact, includeTimeMillis),
         charset,
         compact,
         complete,
         false,
         endOfLine,
         null,
         null,
         includeNullDelimiter,
         additionalFields
      );
   }

   @Override
   public byte[] getHeader() {
      if (!this.complete) {
         return null;
      } else {
         StringBuilder buf = new StringBuilder();
         buf.append("<?xml version=\"1.0\" encoding=\"");
         buf.append(this.getCharset().name());
         buf.append("\"?>");
         buf.append(this.eol);
         buf.append('<');
         buf.append("Events");
         buf.append(" xmlns=\"http://logging.apache.org/log4j/2.0/events\">");
         buf.append(this.eol);
         return buf.toString().getBytes(this.getCharset());
      }
   }

   @Override
   public byte[] getFooter() {
      return !this.complete ? null : this.getBytes("</Events>" + this.eol);
   }

   @Override
   public Map<String, String> getContentFormat() {
      Map<String, String> result = new HashMap<>();
      result.put("xsd", "log4j-events.xsd");
      result.put("version", "2.0");
      return result;
   }

   @Override
   public String getContentType() {
      return "text/xml; charset=" + this.getCharset();
   }

   @Deprecated
   public static XmlLayout createLayout(
      final boolean locationInfo,
      final boolean properties,
      final boolean complete,
      final boolean compact,
      final Charset charset,
      final boolean includeStacktrace
   ) {
      return new XmlLayout(null, locationInfo, properties, complete, compact, null, charset, includeStacktrace, false, false, false, null);
   }

   @PluginBuilderFactory
   public static <B extends XmlLayout.Builder<B>> B newBuilder() {
      return new XmlLayout.Builder<B>().asBuilder();
   }

   public static XmlLayout createDefaultLayout() {
      return new XmlLayout(null, false, false, false, false, null, StandardCharsets.UTF_8, true, false, false, false, null);
   }

   public static class Builder<B extends XmlLayout.Builder<B>>
      extends AbstractJacksonLayout.Builder<B>
      implements org.apache.logging.log4j.core.util.Builder<XmlLayout> {
      public Builder() {
         this.setCharset(StandardCharsets.UTF_8);
      }

      public XmlLayout build() {
         return new XmlLayout(
            this.getConfiguration(),
            this.isLocationInfo(),
            this.isProperties(),
            this.isComplete(),
            this.isCompact(),
            this.getEndOfLine(),
            this.getCharset(),
            this.isIncludeStacktrace(),
            this.isStacktraceAsString(),
            this.isIncludeNullDelimiter(),
            this.isIncludeTimeMillis(),
            this.getAdditionalFields()
         );
      }
   }
}
