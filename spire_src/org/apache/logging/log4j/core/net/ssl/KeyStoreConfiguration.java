package org.apache.logging.log4j.core.net.ssl;

import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.util.Arrays;
import java.util.Objects;
import javax.net.ssl.KeyManagerFactory;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

@Plugin(name = "KeyStore", category = "Core", printObject = true)
public class KeyStoreConfiguration extends AbstractKeyStoreConfiguration {
   private final String keyManagerFactoryAlgorithm;

   public KeyStoreConfiguration(
      final String location, final PasswordProvider passwordProvider, final String keyStoreType, final String keyManagerFactoryAlgorithm
   ) throws StoreConfigurationException {
      super(location, passwordProvider, keyStoreType);
      this.keyManagerFactoryAlgorithm = keyManagerFactoryAlgorithm == null ? KeyManagerFactory.getDefaultAlgorithm() : keyManagerFactoryAlgorithm;
   }

   @Deprecated
   public KeyStoreConfiguration(final String location, final char[] password, final String keyStoreType, final String keyManagerFactoryAlgorithm) throws StoreConfigurationException {
      this(location, new MemoryPasswordProvider(password), keyStoreType, keyManagerFactoryAlgorithm);
      if (password != null) {
         Arrays.fill(password, '\u0000');
      }
   }

   @Deprecated
   public KeyStoreConfiguration(final String location, final String password, final String keyStoreType, final String keyManagerFactoryAlgorithm) throws StoreConfigurationException {
      this(location, new MemoryPasswordProvider(password == null ? null : password.toCharArray()), keyStoreType, keyManagerFactoryAlgorithm);
   }

   @PluginFactory
   public static KeyStoreConfiguration createKeyStoreConfiguration(
      @PluginAttribute("location") final String location,
      @PluginAttribute(value = "password", sensitive = true) final char[] password,
      @PluginAttribute("passwordEnvironmentVariable") final String passwordEnvironmentVariable,
      @PluginAttribute("passwordFile") final String passwordFile,
      @PluginAttribute("type") final String keyStoreType,
      @PluginAttribute("keyManagerFactoryAlgorithm") final String keyManagerFactoryAlgorithm
   ) throws StoreConfigurationException {
      if (password != null && passwordEnvironmentVariable != null && passwordFile != null) {
         throw new StoreConfigurationException("You MUST set only one of 'password', 'passwordEnvironmentVariable' or 'passwordFile'.");
      } else {
         try {
            PasswordProvider provider = (PasswordProvider)(passwordFile != null
               ? new FilePasswordProvider(passwordFile)
               : (passwordEnvironmentVariable != null ? new EnvironmentPasswordProvider(passwordEnvironmentVariable) : new MemoryPasswordProvider(password)));
            if (password != null) {
               Arrays.fill(password, '\u0000');
            }

            return new KeyStoreConfiguration(location, provider, keyStoreType, keyManagerFactoryAlgorithm);
         } catch (Exception var7) {
            throw new StoreConfigurationException("Could not configure KeyStore", var7);
         }
      }
   }

   @Deprecated
   public static KeyStoreConfiguration createKeyStoreConfiguration(
      final String location, final char[] password, final String keyStoreType, final String keyManagerFactoryAlgorithm
   ) throws StoreConfigurationException {
      return createKeyStoreConfiguration(location, password, null, null, keyStoreType, keyManagerFactoryAlgorithm);
   }

   @Deprecated
   public static KeyStoreConfiguration createKeyStoreConfiguration(
      final String location, final String password, final String keyStoreType, final String keyManagerFactoryAlgorithm
   ) throws StoreConfigurationException {
      return createKeyStoreConfiguration(location, password == null ? null : password.toCharArray(), keyStoreType, keyManagerFactoryAlgorithm);
   }

   public KeyManagerFactory initKeyManagerFactory() throws NoSuchAlgorithmException, UnrecoverableKeyException, KeyStoreException {
      KeyManagerFactory kmFactory = KeyManagerFactory.getInstance(this.keyManagerFactoryAlgorithm);
      char[] password = this.getPasswordAsCharArray();

      try {
         kmFactory.init(this.getKeyStore(), password);
      } finally {
         if (password != null) {
            Arrays.fill(password, '\u0000');
         }
      }

      return kmFactory;
   }

   @Override
   public int hashCode() {
      int prime = 31;
      int result = super.hashCode();
      return 31 * result + (this.keyManagerFactoryAlgorithm == null ? 0 : this.keyManagerFactoryAlgorithm.hashCode());
   }

   @Override
   public boolean equals(final Object obj) {
      if (this == obj) {
         return true;
      } else if (!super.equals(obj)) {
         return false;
      } else if (this.getClass() != obj.getClass()) {
         return false;
      } else {
         KeyStoreConfiguration other = (KeyStoreConfiguration)obj;
         return Objects.equals(this.keyManagerFactoryAlgorithm, other.keyManagerFactoryAlgorithm);
      }
   }

   public String getKeyManagerFactoryAlgorithm() {
      return this.keyManagerFactoryAlgorithm;
   }
}
