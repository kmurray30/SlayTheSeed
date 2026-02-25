package org.apache.logging.log4j.core.net.ssl;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.status.StatusLogger;
import org.apache.logging.log4j.util.PropertiesUtil;

public class SslConfigurationFactory {
   private static final Logger LOGGER = StatusLogger.getLogger();
   private static SslConfiguration sslConfiguration = null;
   private static final String trustStorelocation = "log4j2.trustStoreLocation";
   private static final String trustStorePassword = "log4j2.trustStorePassword";
   private static final String trustStorePasswordFile = "log4j2.trustStorePasswordFile";
   private static final String trustStorePasswordEnvVar = "log4j2.trustStorePasswordEnvironmentVariable";
   private static final String trustStoreKeyStoreType = "log4j2.trustStoreKeyStoreType";
   private static final String trustStoreKeyManagerFactoryAlgorithm = "log4j2.trustStoreKeyManagerFactoryAlgorithm";
   private static final String keyStoreLocation = "log4j2.keyStoreLocation";
   private static final String keyStorePassword = "log4j2.keyStorePassword";
   private static final String keyStorePasswordFile = "log4j2.keyStorePasswordFile";
   private static final String keyStorePasswordEnvVar = "log4j2.keyStorePasswordEnvironmentVariable";
   private static final String keyStoreType = "log4j2.keyStoreType";
   private static final String keyStoreKeyManagerFactoryAlgorithm = "log4j2.keyStoreKeyManagerFactoryAlgorithm";
   private static final String verifyHostName = "log4j2.sslVerifyHostName";

   public static SslConfiguration getSslConfiguration() {
      return sslConfiguration;
   }

   static {
      PropertiesUtil props = PropertiesUtil.getProperties();
      KeyStoreConfiguration keyStoreConfiguration = null;
      TrustStoreConfiguration trustStoreConfiguration = null;
      String location = props.getStringProperty("log4j2.trustStoreLocation");
      if (location != null) {
         String password = props.getStringProperty("log4j2.trustStorePassword");
         char[] passwordChars = null;
         if (password != null) {
            passwordChars = password.toCharArray();
         }

         try {
            trustStoreConfiguration = TrustStoreConfiguration.createKeyStoreConfiguration(
               location,
               passwordChars,
               props.getStringProperty("log4j2.trustStorePasswordEnvironmentVariable"),
               props.getStringProperty("log4j2.trustStorePasswordFile"),
               props.getStringProperty("log4j2.trustStoreKeyStoreType"),
               props.getStringProperty("log4j2.trustStoreKeyManagerFactoryAlgorithm")
            );
         } catch (Exception var8) {
            LOGGER.warn("Unable to create trust store configuration due to: {} {}", var8.getClass().getName(), var8.getMessage());
         }
      }

      location = props.getStringProperty("log4j2.keyStoreLocation");
      if (location != null) {
         String password = props.getStringProperty("log4j2.keyStorePassword");
         char[] passwordChars = null;
         if (password != null) {
            passwordChars = password.toCharArray();
         }

         try {
            keyStoreConfiguration = KeyStoreConfiguration.createKeyStoreConfiguration(
               location,
               passwordChars,
               props.getStringProperty("log4j2.keyStorePasswordEnvironmentVariable"),
               props.getStringProperty("log4j2.keyStorePasswordFile"),
               props.getStringProperty("log4j2.keyStoreType"),
               props.getStringProperty("log4j2.keyStoreKeyManagerFactoryAlgorithm")
            );
         } catch (Exception var7) {
            LOGGER.warn("Unable to create key store configuration due to: {} {}", var7.getClass().getName(), var7.getMessage());
         }
      }

      if (trustStoreConfiguration != null || keyStoreConfiguration != null) {
         boolean isVerifyHostName = props.getBooleanProperty("log4j2.sslVerifyHostName", false);
         sslConfiguration = SslConfiguration.createSSLConfiguration("https", keyStoreConfiguration, trustStoreConfiguration, isVerifyHostName);
      }
   }
}
