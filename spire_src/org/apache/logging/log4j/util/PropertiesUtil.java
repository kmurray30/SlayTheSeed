package org.apache.logging.log4j.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

public final class PropertiesUtil {
   private static final String LOG4J_PROPERTIES_FILE_NAME = "log4j2.component.properties";
   private static final String LOG4J_SYSTEM_PROPERTIES_FILE_NAME = "log4j2.system.properties";
   private static final String SYSTEM = "system:";
   private static final PropertiesUtil LOG4J_PROPERTIES = new PropertiesUtil("log4j2.component.properties");
   private final PropertiesUtil.Environment environment;

   public PropertiesUtil(final Properties props) {
      this.environment = new PropertiesUtil.Environment(new PropertiesPropertySource(props));
   }

   public PropertiesUtil(final String propertiesFileName) {
      this.environment = new PropertiesUtil.Environment(new PropertyFilePropertySource(propertiesFileName));
   }

   static Properties loadClose(final InputStream in, final Object source) {
      Properties props = new Properties();
      if (null != in) {
         try {
            props.load(in);
         } catch (IOException var12) {
            LowLevelLogUtil.logException("Unable to read " + source, var12);
         } finally {
            try {
               in.close();
            } catch (IOException var11) {
               LowLevelLogUtil.logException("Unable to close " + source, var11);
            }
         }
      }

      return props;
   }

   public static PropertiesUtil getProperties() {
      return LOG4J_PROPERTIES;
   }

   public boolean hasProperty(final String name) {
      return this.environment.containsKey(name);
   }

   public boolean getBooleanProperty(final String name) {
      return this.getBooleanProperty(name, false);
   }

   public boolean getBooleanProperty(final String name, final boolean defaultValue) {
      String prop = this.getStringProperty(name);
      return prop == null ? defaultValue : "true".equalsIgnoreCase(prop);
   }

   public boolean getBooleanProperty(final String name, final boolean defaultValueIfAbsent, final boolean defaultValueIfPresent) {
      String prop = this.getStringProperty(name);
      return prop == null ? defaultValueIfAbsent : (prop.isEmpty() ? defaultValueIfPresent : "true".equalsIgnoreCase(prop));
   }

   public Boolean getBooleanProperty(final String[] prefixes, String key, Supplier<Boolean> supplier) {
      for (String prefix : prefixes) {
         if (this.hasProperty(prefix + key)) {
            return this.getBooleanProperty(prefix + key);
         }
      }

      return supplier != null ? supplier.get() : null;
   }

   public Charset getCharsetProperty(final String name) {
      return this.getCharsetProperty(name, Charset.defaultCharset());
   }

   public Charset getCharsetProperty(final String name, final Charset defaultValue) {
      String charsetName = this.getStringProperty(name);
      if (charsetName == null) {
         return defaultValue;
      } else if (Charset.isSupported(charsetName)) {
         return Charset.forName(charsetName);
      } else {
         ResourceBundle bundle = getCharsetsResourceBundle();
         if (bundle.containsKey(name)) {
            String mapped = bundle.getString(name);
            if (Charset.isSupported(mapped)) {
               return Charset.forName(mapped);
            }
         }

         LowLevelLogUtil.log("Unable to get Charset '" + charsetName + "' for property '" + name + "', using default " + defaultValue + " and continuing.");
         return defaultValue;
      }
   }

   public double getDoubleProperty(final String name, final double defaultValue) {
      String prop = this.getStringProperty(name);
      if (prop != null) {
         try {
            return Double.parseDouble(prop);
         } catch (Exception var6) {
         }
      }

      return defaultValue;
   }

   public int getIntegerProperty(final String name, final int defaultValue) {
      String prop = this.getStringProperty(name);
      if (prop != null) {
         try {
            return Integer.parseInt(prop);
         } catch (Exception var5) {
         }
      }

      return defaultValue;
   }

   public Integer getIntegerProperty(final String[] prefixes, String key, Supplier<Integer> supplier) {
      for (String prefix : prefixes) {
         if (this.hasProperty(prefix + key)) {
            return this.getIntegerProperty(prefix + key, 0);
         }
      }

      return supplier != null ? supplier.get() : null;
   }

   public long getLongProperty(final String name, final long defaultValue) {
      String prop = this.getStringProperty(name);
      if (prop != null) {
         try {
            return Long.parseLong(prop);
         } catch (Exception var6) {
         }
      }

      return defaultValue;
   }

   public Long getLongProperty(final String[] prefixes, String key, Supplier<Long> supplier) {
      for (String prefix : prefixes) {
         if (this.hasProperty(prefix + key)) {
            return this.getLongProperty(prefix + key, 0L);
         }
      }

      return supplier != null ? supplier.get() : null;
   }

   public Duration getDurationProperty(final String name, Duration defaultValue) {
      String prop = this.getStringProperty(name);
      return prop != null ? PropertiesUtil.TimeUnit.getDuration(prop) : defaultValue;
   }

   public Duration getDurationProperty(final String[] prefixes, String key, Supplier<Duration> supplier) {
      for (String prefix : prefixes) {
         if (this.hasProperty(prefix + key)) {
            return this.getDurationProperty(prefix + key, null);
         }
      }

      return supplier != null ? supplier.get() : null;
   }

   public String getStringProperty(final String[] prefixes, String key, Supplier<String> supplier) {
      for (String prefix : prefixes) {
         String result = this.getStringProperty(prefix + key);
         if (result != null) {
            return result;
         }
      }

      return supplier != null ? supplier.get() : null;
   }

   public String getStringProperty(final String name) {
      return this.environment.get(name);
   }

   public String getStringProperty(final String name, final String defaultValue) {
      String prop = this.getStringProperty(name);
      return prop == null ? defaultValue : prop;
   }

   public static Properties getSystemProperties() {
      try {
         return new Properties(System.getProperties());
      } catch (SecurityException var1) {
         LowLevelLogUtil.logException("Unable to access system properties.", var1);
         return new Properties();
      }
   }

   public void reload() {
      this.environment.reload();
   }

   public static Properties extractSubset(final Properties properties, final String prefix) {
      Properties subset = new Properties();
      if (prefix != null && prefix.length() != 0) {
         String prefixToMatch = prefix.charAt(prefix.length() - 1) != '.' ? prefix + '.' : prefix;
         List<String> keys = new ArrayList<>();

         for (String key : properties.stringPropertyNames()) {
            if (key.startsWith(prefixToMatch)) {
               subset.setProperty(key.substring(prefixToMatch.length()), properties.getProperty(key));
               keys.add(key);
            }
         }

         for (String keyx : keys) {
            properties.remove(keyx);
         }

         return subset;
      } else {
         return subset;
      }
   }

   static ResourceBundle getCharsetsResourceBundle() {
      return ResourceBundle.getBundle("Log4j-charsets");
   }

   public static Map<String, Properties> partitionOnCommonPrefixes(final Properties properties) {
      Map<String, Properties> parts = new ConcurrentHashMap<>();

      for (String key : properties.stringPropertyNames()) {
         String prefix = key.substring(0, key.indexOf(46));
         if (!parts.containsKey(prefix)) {
            parts.put(prefix, new Properties());
         }

         parts.get(prefix).setProperty(key.substring(key.indexOf(46) + 1), properties.getProperty(key));
      }

      return parts;
   }

   public boolean isOsWindows() {
      return this.getStringProperty("os.name", "").startsWith("Windows");
   }

   private static class Environment {
      private final Set<PropertySource> sources = new TreeSet<>(new PropertySource.Comparator());
      private final Map<CharSequence, String> literal = new ConcurrentHashMap<>();
      private final Map<CharSequence, String> normalized = new ConcurrentHashMap<>();
      private final Map<List<CharSequence>, String> tokenized = new ConcurrentHashMap<>();

      private Environment(final PropertySource propertySource) {
         PropertyFilePropertySource sysProps = new PropertyFilePropertySource("log4j2.system.properties");

         try {
            sysProps.forEach((key, value) -> {
               if (System.getProperty(key) == null) {
                  System.setProperty(key, value);
               }
            });
         } catch (SecurityException var9) {
         }

         this.sources.add(propertySource);

         for (ClassLoader classLoader : LoaderUtil.getClassLoaders()) {
            try {
               for (PropertySource source : ServiceLoader.load(PropertySource.class, classLoader)) {
                  this.sources.add(source);
               }
            } catch (Throwable var10) {
            }
         }

         this.reload();
      }

      private synchronized void reload() {
         this.literal.clear();
         this.normalized.clear();
         this.tokenized.clear();

         for (PropertySource source : this.sources) {
            source.forEach((key, value) -> {
               if (key != null && value != null) {
                  this.literal.put(key, value);
                  List<CharSequence> tokens = PropertySource.Util.tokenize(key);
                  if (tokens.isEmpty()) {
                     this.normalized.put(source.getNormalForm(Collections.singleton(key)), value);
                  } else {
                     this.normalized.put(source.getNormalForm(tokens), value);
                     this.tokenized.put(tokens, value);
                  }
               }
            });
         }
      }

      private static boolean hasSystemProperty(final String key) {
         try {
            return System.getProperties().containsKey(key);
         } catch (SecurityException var2) {
            return false;
         }
      }

      private String get(final String key) {
         if (this.normalized.containsKey(key)) {
            return this.normalized.get(key);
         } else if (this.literal.containsKey(key)) {
            return this.literal.get(key);
         } else if (hasSystemProperty(key)) {
            return System.getProperty(key);
         } else {
            for (PropertySource source : this.sources) {
               if (source.containsProperty(key)) {
                  return source.getProperty(key);
               }
            }

            return this.tokenized.get(PropertySource.Util.tokenize(key));
         }
      }

      private boolean containsKey(final String key) {
         return this.normalized.containsKey(key)
            || this.literal.containsKey(key)
            || hasSystemProperty(key)
            || this.tokenized.containsKey(PropertySource.Util.tokenize(key));
      }
   }

   private static enum TimeUnit {
      NANOS("ns,nano,nanos,nanosecond,nanoseconds", ChronoUnit.NANOS),
      MICROS("us,micro,micros,microsecond,microseconds", ChronoUnit.MICROS),
      MILLIS("ms,milli,millis,millsecond,milliseconds", ChronoUnit.MILLIS),
      SECONDS("s,second,seconds", ChronoUnit.SECONDS),
      MINUTES("m,minute,minutes", ChronoUnit.MINUTES),
      HOURS("h,hour,hours", ChronoUnit.HOURS),
      DAYS("d,day,days", ChronoUnit.DAYS);

      private final String[] descriptions;
      private final ChronoUnit timeUnit;

      private TimeUnit(String descriptions, ChronoUnit timeUnit) {
         this.descriptions = descriptions.split(",");
         this.timeUnit = timeUnit;
      }

      ChronoUnit getTimeUnit() {
         return this.timeUnit;
      }

      static Duration getDuration(String time) {
         String value = time.trim();
         TemporalUnit temporalUnit = ChronoUnit.MILLIS;
         long timeVal = 0L;

         for (PropertiesUtil.TimeUnit timeUnit : values()) {
            for (String suffix : timeUnit.descriptions) {
               if (value.endsWith(suffix)) {
                  temporalUnit = timeUnit.timeUnit;
                  timeVal = Long.parseLong(value.substring(0, value.length() - suffix.length()));
               }
            }
         }

         return Duration.of(timeVal, temporalUnit);
      }
   }
}
