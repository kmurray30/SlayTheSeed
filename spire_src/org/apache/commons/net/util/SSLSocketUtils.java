package org.apache.commons.net.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import javax.net.ssl.SSLSocket;

public class SSLSocketUtils {
   private SSLSocketUtils() {
   }

   public static boolean enableEndpointNameVerification(SSLSocket socket) {
      try {
         Class<?> cls = Class.forName("javax.net.ssl.SSLParameters");
         Method setEndpointIdentificationAlgorithm = cls.getDeclaredMethod("setEndpointIdentificationAlgorithm", String.class);
         Method getSSLParameters = SSLSocket.class.getDeclaredMethod("getSSLParameters");
         Method setSSLParameters = SSLSocket.class.getDeclaredMethod("setSSLParameters", cls);
         if (setEndpointIdentificationAlgorithm != null && getSSLParameters != null && setSSLParameters != null) {
            Object sslParams = getSSLParameters.invoke(socket);
            if (sslParams != null) {
               setEndpointIdentificationAlgorithm.invoke(sslParams, "HTTPS");
               setSSLParameters.invoke(socket, sslParams);
               return true;
            }
         }
      } catch (SecurityException var6) {
      } catch (ClassNotFoundException var7) {
      } catch (NoSuchMethodException var8) {
      } catch (IllegalArgumentException var9) {
      } catch (IllegalAccessException var10) {
      } catch (InvocationTargetException var11) {
      }

      return false;
   }
}
