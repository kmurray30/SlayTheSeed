package org.apache.logging.log4j.core.net.ssl;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.util.Objects;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.status.StatusLogger;

@Plugin(name = "Ssl", category = "Core", printObject = true)
public class SslConfiguration {
   private static final StatusLogger LOGGER = StatusLogger.getLogger();
   private final KeyStoreConfiguration keyStoreConfig;
   private final TrustStoreConfiguration trustStoreConfig;
   private final SSLContext sslContext;
   private final String protocol;
   private final boolean verifyHostName;

   private SslConfiguration(
      final String protocol, final KeyStoreConfiguration keyStoreConfig, final TrustStoreConfiguration trustStoreConfig, boolean verifyHostName
   ) {
      this.keyStoreConfig = keyStoreConfig;
      this.trustStoreConfig = trustStoreConfig;
      this.protocol = protocol == null ? "SSL" : protocol;
      this.sslContext = this.createSslContext();
      this.verifyHostName = verifyHostName;
   }

   public void clearSecrets() {
      if (this.keyStoreConfig != null) {
         this.keyStoreConfig.clearSecrets();
      }

      if (this.trustStoreConfig != null) {
         this.trustStoreConfig.clearSecrets();
      }
   }

   public SSLSocketFactory getSslSocketFactory() {
      return this.sslContext.getSocketFactory();
   }

   public SSLServerSocketFactory getSslServerSocketFactory() {
      return this.sslContext.getServerSocketFactory();
   }

   private SSLContext createSslContext() {
      SSLContext context = null;

      try {
         context = this.createSslContextBasedOnConfiguration();
         LOGGER.debug("Creating SSLContext with the given parameters");
      } catch (TrustStoreConfigurationException var3) {
         context = this.createSslContextWithTrustStoreFailure();
      } catch (KeyStoreConfigurationException var4) {
         context = this.createSslContextWithKeyStoreFailure();
      }

      return context;
   }

   private SSLContext createSslContextWithTrustStoreFailure() {
      SSLContext context;
      try {
         context = this.createSslContextWithDefaultTrustManagerFactory();
         LOGGER.debug("Creating SSLContext with default truststore");
      } catch (KeyStoreConfigurationException var3) {
         context = this.createDefaultSslContext();
         LOGGER.debug("Creating SSLContext with default configuration");
      }

      return context;
   }

   private SSLContext createSslContextWithKeyStoreFailure() {
      SSLContext context;
      try {
         context = this.createSslContextWithDefaultKeyManagerFactory();
         LOGGER.debug("Creating SSLContext with default keystore");
      } catch (TrustStoreConfigurationException var3) {
         context = this.createDefaultSslContext();
         LOGGER.debug("Creating SSLContext with default configuration");
      }

      return context;
   }

   private SSLContext createSslContextBasedOnConfiguration() throws KeyStoreConfigurationException, TrustStoreConfigurationException {
      return this.createSslContext(false, false);
   }

   private SSLContext createSslContextWithDefaultKeyManagerFactory() throws TrustStoreConfigurationException {
      try {
         return this.createSslContext(true, false);
      } catch (KeyStoreConfigurationException var2) {
         LOGGER.debug("Exception occurred while using default keystore. This should be a BUG");
         return null;
      }
   }

   private SSLContext createSslContextWithDefaultTrustManagerFactory() throws KeyStoreConfigurationException {
      try {
         return this.createSslContext(false, true);
      } catch (TrustStoreConfigurationException var2) {
         LOGGER.debug("Exception occurred while using default truststore. This should be a BUG");
         return null;
      }
   }

   private SSLContext createDefaultSslContext() {
      try {
         return SSLContext.getDefault();
      } catch (NoSuchAlgorithmException var2) {
         LOGGER.error("Failed to create an SSLContext with default configuration", var2);
         return null;
      }
   }

   private SSLContext createSslContext(final boolean loadDefaultKeyManagerFactory, final boolean loadDefaultTrustManagerFactory) throws KeyStoreConfigurationException, TrustStoreConfigurationException {
      try {
         KeyManager[] kManagers = null;
         TrustManager[] tManagers = null;
         SSLContext newSslContext = SSLContext.getInstance(this.protocol);
         if (!loadDefaultKeyManagerFactory) {
            KeyManagerFactory kmFactory = this.loadKeyManagerFactory();
            kManagers = kmFactory.getKeyManagers();
         }

         if (!loadDefaultTrustManagerFactory) {
            TrustManagerFactory tmFactory = this.loadTrustManagerFactory();
            tManagers = tmFactory.getTrustManagers();
         }

         newSslContext.init(kManagers, tManagers, null);
         return newSslContext;
      } catch (NoSuchAlgorithmException var7) {
         LOGGER.error("No Provider supports a TrustManagerFactorySpi implementation for the specified protocol", var7);
         throw new TrustStoreConfigurationException(var7);
      } catch (KeyManagementException var8) {
         LOGGER.error("Failed to initialize the SSLContext", var8);
         throw new KeyStoreConfigurationException(var8);
      }
   }

   private TrustManagerFactory loadTrustManagerFactory() throws TrustStoreConfigurationException {
      if (this.trustStoreConfig == null) {
         throw new TrustStoreConfigurationException(new Exception("The trustStoreConfiguration is null"));
      } else {
         try {
            return this.trustStoreConfig.initTrustManagerFactory();
         } catch (NoSuchAlgorithmException var2) {
            LOGGER.error("The specified algorithm is not available from the specified provider", var2);
            throw new TrustStoreConfigurationException(var2);
         } catch (KeyStoreException var3) {
            LOGGER.error("Failed to initialize the TrustManagerFactory", var3);
            throw new TrustStoreConfigurationException(var3);
         }
      }
   }

   private KeyManagerFactory loadKeyManagerFactory() throws KeyStoreConfigurationException {
      if (this.keyStoreConfig == null) {
         throw new KeyStoreConfigurationException(new Exception("The keyStoreConfiguration is null"));
      } else {
         try {
            return this.keyStoreConfig.initKeyManagerFactory();
         } catch (NoSuchAlgorithmException var2) {
            LOGGER.error("The specified algorithm is not available from the specified provider", var2);
            throw new KeyStoreConfigurationException(var2);
         } catch (KeyStoreException var3) {
            LOGGER.error("Failed to initialize the TrustManagerFactory", var3);
            throw new KeyStoreConfigurationException(var3);
         } catch (UnrecoverableKeyException var4) {
            LOGGER.error("The key cannot be recovered (e.g. the given password is wrong)", var4);
            throw new KeyStoreConfigurationException(var4);
         }
      }
   }

   @PluginFactory
   public static SslConfiguration createSSLConfiguration(
      @PluginAttribute("protocol") final String protocol,
      @PluginElement("KeyStore") final KeyStoreConfiguration keyStoreConfig,
      @PluginElement("TrustStore") final TrustStoreConfiguration trustStoreConfig
   ) {
      return new SslConfiguration(protocol, keyStoreConfig, trustStoreConfig, false);
   }

   public static SslConfiguration createSSLConfiguration(
      @PluginAttribute("protocol") final String protocol,
      @PluginElement("KeyStore") final KeyStoreConfiguration keyStoreConfig,
      @PluginElement("TrustStore") final TrustStoreConfiguration trustStoreConfig,
      @PluginAttribute("verifyHostName") final boolean verifyHostName
   ) {
      return new SslConfiguration(protocol, keyStoreConfig, trustStoreConfig, verifyHostName);
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.keyStoreConfig, this.protocol, this.sslContext, this.trustStoreConfig);
   }

   @Override
   public boolean equals(final Object obj) {
      if (this == obj) {
         return true;
      } else if (obj == null) {
         return false;
      } else if (this.getClass() != obj.getClass()) {
         return false;
      } else {
         SslConfiguration other = (SslConfiguration)obj;
         if (!Objects.equals(this.keyStoreConfig, other.keyStoreConfig)) {
            return false;
         } else if (!Objects.equals(this.protocol, other.protocol)) {
            return false;
         } else {
            return !Objects.equals(this.sslContext, other.sslContext) ? false : Objects.equals(this.trustStoreConfig, other.trustStoreConfig);
         }
      }
   }

   public KeyStoreConfiguration getKeyStoreConfig() {
      return this.keyStoreConfig;
   }

   public TrustStoreConfiguration getTrustStoreConfig() {
      return this.trustStoreConfig;
   }

   public SSLContext getSslContext() {
      return this.sslContext;
   }

   public String getProtocol() {
      return this.protocol;
   }

   public boolean isVerifyHostName() {
      return this.verifyHostName;
   }
}
