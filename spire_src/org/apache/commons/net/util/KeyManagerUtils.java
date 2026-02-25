package org.apache.commons.net.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import javax.net.ssl.KeyManager;
import javax.net.ssl.X509ExtendedKeyManager;
import org.apache.commons.net.io.Util;

public final class KeyManagerUtils {
   private static final String DEFAULT_STORE_TYPE = KeyStore.getDefaultType();

   private KeyManagerUtils() {
   }

   public static KeyManager createClientKeyManager(KeyStore ks, String keyAlias, String keyPass) throws GeneralSecurityException {
      KeyManagerUtils.ClientKeyStore cks = new KeyManagerUtils.ClientKeyStore(ks, keyAlias != null ? keyAlias : findAlias(ks), keyPass);
      return new KeyManagerUtils.X509KeyManager(cks);
   }

   public static KeyManager createClientKeyManager(String storeType, File storePath, String storePass, String keyAlias, String keyPass) throws IOException, GeneralSecurityException {
      KeyStore ks = loadStore(storeType, storePath, storePass);
      return createClientKeyManager(ks, keyAlias, keyPass);
   }

   public static KeyManager createClientKeyManager(File storePath, String storePass, String keyAlias) throws IOException, GeneralSecurityException {
      return createClientKeyManager(DEFAULT_STORE_TYPE, storePath, storePass, keyAlias, storePass);
   }

   public static KeyManager createClientKeyManager(File storePath, String storePass) throws IOException, GeneralSecurityException {
      return createClientKeyManager(DEFAULT_STORE_TYPE, storePath, storePass, null, storePass);
   }

   private static KeyStore loadStore(String storeType, File storePath, String storePass) throws KeyStoreException, IOException, GeneralSecurityException {
      KeyStore ks = KeyStore.getInstance(storeType);
      FileInputStream stream = null;

      try {
         stream = new FileInputStream(storePath);
         ks.load(stream, storePass.toCharArray());
      } finally {
         Util.closeQuietly(stream);
      }

      return ks;
   }

   private static String findAlias(KeyStore ks) throws KeyStoreException {
      Enumeration<String> e = ks.aliases();

      while (e.hasMoreElements()) {
         String entry = e.nextElement();
         if (ks.isKeyEntry(entry)) {
            return entry;
         }
      }

      throw new KeyStoreException("Cannot find a private key entry");
   }

   private static class ClientKeyStore {
      private final X509Certificate[] certChain;
      private final PrivateKey key;
      private final String keyAlias;

      ClientKeyStore(KeyStore ks, String keyAlias, String keyPass) throws GeneralSecurityException {
         this.keyAlias = keyAlias;
         this.key = (PrivateKey)ks.getKey(this.keyAlias, keyPass.toCharArray());
         Certificate[] certs = ks.getCertificateChain(this.keyAlias);
         X509Certificate[] X509certs = new X509Certificate[certs.length];

         for (int i = 0; i < certs.length; i++) {
            X509certs[i] = (X509Certificate)certs[i];
         }

         this.certChain = X509certs;
      }

      final X509Certificate[] getCertificateChain() {
         return this.certChain;
      }

      final PrivateKey getPrivateKey() {
         return this.key;
      }

      final String getAlias() {
         return this.keyAlias;
      }
   }

   private static class X509KeyManager extends X509ExtendedKeyManager {
      private final KeyManagerUtils.ClientKeyStore keyStore;

      X509KeyManager(KeyManagerUtils.ClientKeyStore keyStore) {
         this.keyStore = keyStore;
      }

      @Override
      public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
         return this.keyStore.getAlias();
      }

      @Override
      public X509Certificate[] getCertificateChain(String alias) {
         return this.keyStore.getCertificateChain();
      }

      @Override
      public String[] getClientAliases(String keyType, Principal[] issuers) {
         return new String[]{this.keyStore.getAlias()};
      }

      @Override
      public PrivateKey getPrivateKey(String alias) {
         return this.keyStore.getPrivateKey();
      }

      @Override
      public String[] getServerAliases(String keyType, Principal[] issuers) {
         return null;
      }

      @Override
      public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
         return null;
      }
   }
}
