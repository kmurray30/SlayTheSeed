package org.apache.logging.log4j.core.net.ssl;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.Objects;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.util.NetUtils;

public class AbstractKeyStoreConfiguration extends StoreConfiguration<KeyStore> {
   private final KeyStore keyStore;
   private final String keyStoreType;

   public AbstractKeyStoreConfiguration(final String location, final PasswordProvider passwordProvider, final String keyStoreType) throws StoreConfigurationException {
      super(location, passwordProvider);
      this.keyStoreType = keyStoreType == null ? "JKS" : keyStoreType;
      this.keyStore = this.load();
   }

   @Deprecated
   public AbstractKeyStoreConfiguration(final String location, final char[] password, final String keyStoreType) throws StoreConfigurationException {
      this(location, new MemoryPasswordProvider(password), keyStoreType);
   }

   @Deprecated
   public AbstractKeyStoreConfiguration(final String location, final String password, final String keyStoreType) throws StoreConfigurationException {
      this(location, new MemoryPasswordProvider(password == null ? null : password.toCharArray()), keyStoreType);
   }

   protected KeyStore load() throws StoreConfigurationException {
      String loadLocation = this.getLocation();
      LOGGER.debug("Loading keystore from location {}", loadLocation);

      try {
         if (loadLocation == null) {
            throw new IOException("The location is null");
         } else {
            KeyStore var6;
            try (InputStream fin = this.openInputStream(loadLocation)) {
               KeyStore ks = KeyStore.getInstance(this.keyStoreType);
               char[] password = this.getPasswordAsCharArray();

               try {
                  ks.load(fin, password);
               } finally {
                  if (password != null) {
                     Arrays.fill(password, '\u0000');
                  }
               }

               LOGGER.debug("KeyStore successfully loaded from location {}", loadLocation);
               var6 = ks;
            }

            return var6;
         }
      } catch (CertificateException var33) {
         LOGGER.error("No Provider supports a KeyStoreSpi implementation for the specified type {} for location {}", this.keyStoreType, loadLocation, var33);
         throw new StoreConfigurationException(loadLocation, var33);
      } catch (NoSuchAlgorithmException var34) {
         LOGGER.error("The algorithm used to check the integrity of the keystore cannot be found for location {}", loadLocation, var34);
         throw new StoreConfigurationException(loadLocation, var34);
      } catch (KeyStoreException var35) {
         LOGGER.error("KeyStoreException for location {}", loadLocation, var35);
         throw new StoreConfigurationException(loadLocation, var35);
      } catch (FileNotFoundException var36) {
         LOGGER.error("The keystore file {} is not found", loadLocation, var36);
         throw new StoreConfigurationException(loadLocation, var36);
      } catch (IOException var37) {
         LOGGER.error("Something is wrong with the format of the keystore or the given password for location {}", loadLocation, var37);
         throw new StoreConfigurationException(loadLocation, var37);
      }
   }

   private InputStream openInputStream(final String filePathOrUri) {
      return ConfigurationSource.fromUri(NetUtils.toURI(filePathOrUri)).getInputStream();
   }

   public KeyStore getKeyStore() {
      return this.keyStore;
   }

   @Override
   public int hashCode() {
      int prime = 31;
      int result = super.hashCode();
      result = 31 * result + (this.keyStore == null ? 0 : this.keyStore.hashCode());
      return 31 * result + (this.keyStoreType == null ? 0 : this.keyStoreType.hashCode());
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
         AbstractKeyStoreConfiguration other = (AbstractKeyStoreConfiguration)obj;
         return !Objects.equals(this.keyStore, other.keyStore) ? false : Objects.equals(this.keyStoreType, other.keyStoreType);
      }
   }

   public String getKeyStoreType() {
      return this.keyStoreType;
   }
}
