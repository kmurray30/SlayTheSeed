package org.apache.logging.log4j.core.util;

import java.io.InterruptedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Properties;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.status.StatusLogger;
import org.apache.logging.log4j.util.PropertiesUtil;
import org.apache.logging.log4j.util.Strings;

public final class OptionConverter {
   private static final Logger LOGGER = StatusLogger.getLogger();
   private static final String DELIM_START = "${";
   private static final char DELIM_STOP = '}';
   private static final int DELIM_START_LEN = 2;
   private static final int DELIM_STOP_LEN = 1;
   private static final int ONE_K = 1024;

   private OptionConverter() {
   }

   public static String[] concatenateArrays(final String[] l, final String[] r) {
      int len = l.length + r.length;
      String[] a = new String[len];
      System.arraycopy(l, 0, a, 0, l.length);
      System.arraycopy(r, 0, a, l.length, r.length);
      return a;
   }

   public static String convertSpecialChars(final String s) {
      int len = s.length();
      StringBuilder sbuf = new StringBuilder(len);
      int i = 0;

      while (i < len) {
         char c = s.charAt(i++);
         if (c == '\\') {
            c = s.charAt(i++);
            switch (c) {
               case '"':
                  c = '"';
                  break;
               case '\'':
                  c = '\'';
                  break;
               case '\\':
                  c = '\\';
                  break;
               case 'b':
                  c = '\b';
                  break;
               case 'f':
                  c = '\f';
                  break;
               case 'n':
                  c = '\n';
                  break;
               case 'r':
                  c = '\r';
                  break;
               case 't':
                  c = '\t';
            }
         }

         sbuf.append(c);
      }

      return sbuf.toString();
   }

   public static Object instantiateByKey(final Properties props, final String key, final Class<?> superClass, final Object defaultValue) {
      String className = findAndSubst(key, props);
      if (className == null) {
         LOGGER.error("Could not find value for key {}", key);
         return defaultValue;
      } else {
         return instantiateByClassName(className.trim(), superClass, defaultValue);
      }
   }

   public static boolean toBoolean(final String value, final boolean defaultValue) {
      if (value == null) {
         return defaultValue;
      } else {
         String trimmedVal = value.trim();
         if ("true".equalsIgnoreCase(trimmedVal)) {
            return true;
         } else {
            return "false".equalsIgnoreCase(trimmedVal) ? false : defaultValue;
         }
      }
   }

   public static int toInt(final String value, final int defaultValue) {
      if (value != null) {
         String s = value.trim();

         try {
            return Integer.parseInt(s);
         } catch (NumberFormatException var4) {
            LOGGER.error("[{}] is not in proper int form.", s, var4);
         }
      }

      return defaultValue;
   }

   public static Level toLevel(String value, Level defaultValue) {
      if (value == null) {
         return defaultValue;
      } else {
         value = value.trim();
         int hashIndex = value.indexOf(35);
         if (hashIndex == -1) {
            return "NULL".equalsIgnoreCase(value) ? null : Level.toLevel(value, defaultValue);
         } else {
            Level result = defaultValue;
            String clazz = value.substring(hashIndex + 1);
            String levelName = value.substring(0, hashIndex);
            if ("NULL".equalsIgnoreCase(levelName)) {
               return null;
            } else {
               LOGGER.debug("toLevel:class=[" + clazz + "]:pri=[" + levelName + "]");

               try {
                  Class<?> customLevel = Loader.loadClass(clazz);
                  Class<?>[] paramTypes = new Class[]{String.class, Level.class};
                  Method toLevelMethod = customLevel.getMethod("toLevel", paramTypes);
                  Object[] params = new Object[]{levelName, defaultValue};
                  Object o = toLevelMethod.invoke(null, params);
                  result = (Level)o;
               } catch (ClassNotFoundException var11) {
                  LOGGER.warn("custom level class [" + clazz + "] not found.");
               } catch (NoSuchMethodException var12) {
                  LOGGER.warn("custom level class [" + clazz + "] does not have a class function toLevel(String, Level)", (Throwable)var12);
               } catch (InvocationTargetException var13) {
                  if (var13.getTargetException() instanceof InterruptedException || var13.getTargetException() instanceof InterruptedIOException) {
                     Thread.currentThread().interrupt();
                  }

                  LOGGER.warn("custom level class [" + clazz + "] could not be instantiated", (Throwable)var13);
               } catch (ClassCastException var14) {
                  LOGGER.warn("class [" + clazz + "] is not a subclass of org.apache.log4j.Level", (Throwable)var14);
               } catch (IllegalAccessException var15) {
                  LOGGER.warn("class [" + clazz + "] cannot be instantiated due to access restrictions", (Throwable)var15);
               } catch (RuntimeException var16) {
                  LOGGER.warn("class [" + clazz + "], level [" + levelName + "] conversion failed.", (Throwable)var16);
               }

               return result;
            }
         }
      }
   }

   public static long toFileSize(final String value, final long defaultValue) {
      if (value == null) {
         return defaultValue;
      } else {
         String str = value.trim().toUpperCase(Locale.ENGLISH);
         long multiplier = 1L;
         int index;
         if ((index = str.indexOf("KB")) != -1) {
            multiplier = 1024L;
            str = str.substring(0, index);
         } else if ((index = str.indexOf("MB")) != -1) {
            multiplier = 1048576L;
            str = str.substring(0, index);
         } else if ((index = str.indexOf("GB")) != -1) {
            multiplier = 1073741824L;
            str = str.substring(0, index);
         }

         try {
            return Long.parseLong(str) * multiplier;
         } catch (NumberFormatException var8) {
            LOGGER.error("[{}] is not in proper int form.", str);
            LOGGER.error("[{}] not in expected format.", value, var8);
            return defaultValue;
         }
      }
   }

   public static String findAndSubst(final String key, final Properties props) {
      String value = props.getProperty(key);
      if (value == null) {
         return null;
      } else {
         try {
            return substVars(value, props);
         } catch (IllegalArgumentException var4) {
            LOGGER.error("Bad option value [{}].", value, var4);
            return value;
         }
      }
   }

   public static Object instantiateByClassName(final String className, final Class<?> superClass, final Object defaultValue) {
      if (className != null) {
         try {
            Class<?> classObj = Loader.loadClass(className);
            if (!superClass.isAssignableFrom(classObj)) {
               LOGGER.error("A \"{}\" object is not assignable to a \"{}\" variable.", className, superClass.getName());
               LOGGER.error(
                  "The class \"{}\" was loaded by [{}] whereas object of type [{}] was loaded by [{}].",
                  superClass.getName(),
                  superClass.getClassLoader(),
                  classObj.getTypeName(),
                  classObj.getName()
               );
               return defaultValue;
            }

            return classObj.newInstance();
         } catch (Exception var4) {
            LOGGER.error("Could not instantiate class [{}].", className, var4);
         }
      }

      return defaultValue;
   }

   public static String substVars(final String val, final Properties props) throws IllegalArgumentException {
      StringBuilder sbuf = new StringBuilder();
      int i = 0;

      while (true) {
         int j = val.indexOf("${", i);
         if (j == -1) {
            if (i == 0) {
               return val;
            } else {
               sbuf.append(val.substring(i, val.length()));
               return sbuf.toString();
            }
         }

         sbuf.append(val.substring(i, j));
         int k = val.indexOf(125, j);
         if (k == -1) {
            throw new IllegalArgumentException(Strings.dquote(val) + " has no closing brace. Opening brace at position " + j + '.');
         }

         j += 2;
         String key = val.substring(j, k);
         String replacement = PropertiesUtil.getProperties().getStringProperty(key, null);
         if (replacement == null && props != null) {
            replacement = props.getProperty(key);
         }

         if (replacement != null) {
            String recursiveReplacement = substVars(replacement, props);
            sbuf.append(recursiveReplacement);
         }

         i = k + 1;
      }
   }
}
