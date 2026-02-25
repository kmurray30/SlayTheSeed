package org.apache.logging.log4j.message;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;
import org.apache.logging.log4j.util.BiConsumer;
import org.apache.logging.log4j.util.EnglishEnums;
import org.apache.logging.log4j.util.IndexedReadOnlyStringMap;
import org.apache.logging.log4j.util.IndexedStringMap;
import org.apache.logging.log4j.util.MultiFormatStringBuilderFormattable;
import org.apache.logging.log4j.util.PerformanceSensitive;
import org.apache.logging.log4j.util.SortedArrayStringMap;
import org.apache.logging.log4j.util.StringBuilders;
import org.apache.logging.log4j.util.TriConsumer;

@AsynchronouslyFormattable
@PerformanceSensitive("allocation")
public class MapMessage<M extends MapMessage<M, V>, V> implements MultiFormatStringBuilderFormattable {
   private static final long serialVersionUID = -5031471831131487120L;
   private final IndexedStringMap data;

   public MapMessage() {
      this.data = new SortedArrayStringMap();
   }

   public MapMessage(final int initialCapacity) {
      this.data = new SortedArrayStringMap(initialCapacity);
   }

   public MapMessage(final Map<String, V> map) {
      this.data = new SortedArrayStringMap(map);
   }

   @Override
   public String[] getFormats() {
      return MapMessage.MapFormat.names();
   }

   @Override
   public Object[] getParameters() {
      Object[] result = new Object[this.data.size()];

      for (int i = 0; i < this.data.size(); i++) {
         result[i] = this.data.getValueAt(i);
      }

      return result;
   }

   @Override
   public String getFormat() {
      return "";
   }

   public Map<String, V> getData() {
      TreeMap<String, V> result = new TreeMap<>();

      for (int i = 0; i < this.data.size(); i++) {
         result.put(this.data.getKeyAt(i), this.data.getValueAt(i));
      }

      return Collections.unmodifiableMap(result);
   }

   public IndexedReadOnlyStringMap getIndexedReadOnlyStringMap() {
      return this.data;
   }

   public void clear() {
      this.data.clear();
   }

   public boolean containsKey(final String key) {
      return this.data.containsKey(key);
   }

   public void put(final String candidateKey, final String value) {
      if (value == null) {
         throw new IllegalArgumentException("No value provided for key " + candidateKey);
      } else {
         String key = this.toKey(candidateKey);
         this.validate(key, value);
         this.data.putValue(key, value);
      }
   }

   public void putAll(final Map<String, String> map) {
      for (Entry<String, String> entry : map.entrySet()) {
         this.data.putValue(entry.getKey(), entry.getValue());
      }
   }

   public String get(final String key) {
      Object result = this.data.getValue(key);
      return ParameterFormatter.deepToString(result);
   }

   public String remove(final String key) {
      String result = this.get(key);
      this.data.remove(key);
      return result;
   }

   public String asString() {
      return this.format((MapMessage.MapFormat)null, new StringBuilder()).toString();
   }

   public String asString(final String format) {
      try {
         return this.format(EnglishEnums.valueOf(MapMessage.MapFormat.class, format), new StringBuilder()).toString();
      } catch (IllegalArgumentException var3) {
         return this.asString();
      }
   }

   public <CV> void forEach(final BiConsumer<String, ? super CV> action) {
      this.data.forEach(action);
   }

   public <CV, S> void forEach(final TriConsumer<String, ? super CV, S> action, final S state) {
      this.data.forEach(action, state);
   }

   private StringBuilder format(final MapMessage.MapFormat format, final StringBuilder sb) {
      if (format == null) {
         this.appendMap(sb);
      } else {
         switch (format) {
            case XML:
               this.asXml(sb);
               break;
            case JSON:
               this.asJson(sb);
               break;
            case JAVA:
               this.asJava(sb);
               break;
            case JAVA_UNQUOTED:
               this.asJavaUnquoted(sb);
               break;
            default:
               this.appendMap(sb);
         }
      }

      return sb;
   }

   public void asXml(final StringBuilder sb) {
      sb.append("<Map>\n");

      for (int i = 0; i < this.data.size(); i++) {
         sb.append("  <Entry key=\"").append(this.data.getKeyAt(i)).append("\">");
         int size = sb.length();
         ParameterFormatter.recursiveDeepToString(this.data.getValueAt(i), sb);
         StringBuilders.escapeXml(sb, size);
         sb.append("</Entry>\n");
      }

      sb.append("</Map>");
   }

   @Override
   public String getFormattedMessage() {
      return this.asString();
   }

   @Override
   public String getFormattedMessage(final String[] formats) {
      return this.format(this.getFormat(formats), new StringBuilder()).toString();
   }

   private MapMessage.MapFormat getFormat(final String[] formats) {
      if (formats != null && formats.length != 0) {
         for (int i = 0; i < formats.length; i++) {
            MapMessage.MapFormat mapFormat = MapMessage.MapFormat.lookupIgnoreCase(formats[i]);
            if (mapFormat != null) {
               return mapFormat;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   protected void appendMap(final StringBuilder sb) {
      for (int i = 0; i < this.data.size(); i++) {
         if (i > 0) {
            sb.append(' ');
         }

         sb.append(this.data.getKeyAt(i)).append('=').append('"');
         ParameterFormatter.recursiveDeepToString(this.data.getValueAt(i), sb);
         sb.append('"');
      }
   }

   protected void asJson(final StringBuilder sb) {
      MapMessageJsonFormatter.format(sb, this.data);
   }

   protected void asJavaUnquoted(final StringBuilder sb) {
      this.asJava(sb, false);
   }

   protected void asJava(final StringBuilder sb) {
      this.asJava(sb, true);
   }

   private void asJava(final StringBuilder sb, boolean quoted) {
      sb.append('{');

      for (int i = 0; i < this.data.size(); i++) {
         if (i > 0) {
            sb.append(", ");
         }

         sb.append(this.data.getKeyAt(i)).append('=');
         if (quoted) {
            sb.append('"');
         }

         ParameterFormatter.recursiveDeepToString(this.data.getValueAt(i), sb);
         if (quoted) {
            sb.append('"');
         }
      }

      sb.append('}');
   }

   public M newInstance(final Map<String, V> map) {
      return (M)(new MapMessage(map));
   }

   @Override
   public String toString() {
      return this.asString();
   }

   @Override
   public void formatTo(final StringBuilder buffer) {
      this.format((MapMessage.MapFormat)null, buffer);
   }

   @Override
   public void formatTo(final String[] formats, final StringBuilder buffer) {
      this.format(this.getFormat(formats), buffer);
   }

   @Override
   public boolean equals(final Object o) {
      if (this == o) {
         return true;
      } else if (o != null && this.getClass() == o.getClass()) {
         MapMessage<?, ?> that = (MapMessage<?, ?>)o;
         return this.data.equals(that.data);
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      return this.data.hashCode();
   }

   @Override
   public Throwable getThrowable() {
      return null;
   }

   protected void validate(final String key, final boolean value) {
   }

   protected void validate(final String key, final byte value) {
   }

   protected void validate(final String key, final char value) {
   }

   protected void validate(final String key, final double value) {
   }

   protected void validate(final String key, final float value) {
   }

   protected void validate(final String key, final int value) {
   }

   protected void validate(final String key, final long value) {
   }

   protected void validate(final String key, final Object value) {
   }

   protected void validate(final String key, final short value) {
   }

   protected void validate(final String key, final String value) {
   }

   protected String toKey(final String candidateKey) {
      return candidateKey;
   }

   public M with(final String candidateKey, final boolean value) {
      String key = this.toKey(candidateKey);
      this.validate(key, value);
      this.data.putValue(key, value);
      return (M)this;
   }

   public M with(final String candidateKey, final byte value) {
      String key = this.toKey(candidateKey);
      this.validate(key, value);
      this.data.putValue(key, value);
      return (M)this;
   }

   public M with(final String candidateKey, final char value) {
      String key = this.toKey(candidateKey);
      this.validate(key, value);
      this.data.putValue(key, value);
      return (M)this;
   }

   public M with(final String candidateKey, final double value) {
      String key = this.toKey(candidateKey);
      this.validate(key, value);
      this.data.putValue(key, value);
      return (M)this;
   }

   public M with(final String candidateKey, final float value) {
      String key = this.toKey(candidateKey);
      this.validate(key, value);
      this.data.putValue(key, value);
      return (M)this;
   }

   public M with(final String candidateKey, final int value) {
      String key = this.toKey(candidateKey);
      this.validate(key, value);
      this.data.putValue(key, value);
      return (M)this;
   }

   public M with(final String candidateKey, final long value) {
      String key = this.toKey(candidateKey);
      this.validate(key, value);
      this.data.putValue(key, value);
      return (M)this;
   }

   public M with(final String candidateKey, final Object value) {
      String key = this.toKey(candidateKey);
      this.validate(key, value);
      this.data.putValue(key, value);
      return (M)this;
   }

   public M with(final String candidateKey, final short value) {
      String key = this.toKey(candidateKey);
      this.validate(key, value);
      this.data.putValue(key, value);
      return (M)this;
   }

   public M with(final String candidateKey, final String value) {
      String key = this.toKey(candidateKey);
      this.put(key, value);
      return (M)this;
   }

   public static enum MapFormat {
      XML,
      JSON,
      JAVA,
      JAVA_UNQUOTED;

      public static MapMessage.MapFormat lookupIgnoreCase(final String format) {
         return XML.name().equalsIgnoreCase(format)
            ? XML
            : (
               JSON.name().equalsIgnoreCase(format)
                  ? JSON
                  : (JAVA.name().equalsIgnoreCase(format) ? JAVA : (JAVA_UNQUOTED.name().equalsIgnoreCase(format) ? JAVA_UNQUOTED : null))
            );
      }

      public static String[] names() {
         return new String[]{XML.name(), JSON.name(), JAVA.name(), JAVA_UNQUOTED.name()};
      }
   }
}
