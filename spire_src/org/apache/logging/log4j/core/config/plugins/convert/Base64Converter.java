package org.apache.logging.log4j.core.config.plugins.convert;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.status.StatusLogger;
import org.apache.logging.log4j.util.Constants;
import org.apache.logging.log4j.util.LoaderUtil;

public class Base64Converter {
   private static final Logger LOGGER = StatusLogger.getLogger();
   private static Method method = null;
   private static Object decoder = null;

   public static byte[] parseBase64Binary(final String encoded) {
      if (method == null) {
         LOGGER.error("No base64 converter");
      } else {
         try {
            return (byte[])method.invoke(decoder, encoded);
         } catch (InvocationTargetException | IllegalAccessException var2) {
            LOGGER.error("Error decoding string - " + var2.getMessage());
         }
      }

      return Constants.EMPTY_BYTE_ARRAY;
   }

   static {
      try {
         Class<?> clazz = LoaderUtil.loadClass("java.util.Base64");
         Method getDecoder = clazz.getMethod("getDecoder", (Class<?>[])null);
         decoder = getDecoder.invoke(null, (Object[])null);
         clazz = decoder.getClass();
         method = clazz.getMethod("decode", String.class);
      } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | ClassNotFoundException var4) {
      }

      if (method == null) {
         try {
            Class<?> clazz = LoaderUtil.loadClass("javax.xml.bind.DatatypeConverter");
            method = clazz.getMethod("parseBase64Binary", String.class);
         } catch (ClassNotFoundException var2) {
            LOGGER.error("No Base64 Converter is available");
         } catch (NoSuchMethodException var3) {
         }
      }
   }
}
