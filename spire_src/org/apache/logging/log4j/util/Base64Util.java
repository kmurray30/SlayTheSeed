package org.apache.logging.log4j.util;

import java.lang.reflect.Method;
import org.apache.logging.log4j.LoggingException;

public final class Base64Util {
   private static Method encodeMethod = null;
   private static Object encoder = null;

   private Base64Util() {
   }

   public static String encode(String str) {
      if (str == null) {
         return null;
      } else {
         byte[] data = str.getBytes();
         if (encodeMethod != null) {
            try {
               return (String)encodeMethod.invoke(encoder, data);
            } catch (Exception var3) {
               throw new LoggingException("Unable to encode String", var3);
            }
         } else {
            throw new LoggingException("No Encoder, unable to encode string");
         }
      }
   }

   static {
      try {
         Class<?> clazz = LoaderUtil.loadClass("java.util.Base64");
         Class<?> encoderClazz = LoaderUtil.loadClass("java.util.Base64$Encoder");
         Method method = clazz.getMethod("getEncoder");
         encoder = method.invoke(null);
         encodeMethod = encoderClazz.getMethod("encodeToString", byte[].class);
      } catch (Exception var4) {
         try {
            Class<?> clazzx = LoaderUtil.loadClass("javax.xml.bind.DataTypeConverter");
            encodeMethod = clazzx.getMethod("printBase64Binary");
         } catch (Exception var3) {
            LowLevelLogUtil.logException("Unable to create a Base64 Encoder", var3);
         }
      }
   }
}
