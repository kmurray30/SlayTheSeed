package org.apache.logging.log4j.util;

import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.spi.Provider;
import org.apache.logging.log4j.status.StatusLogger;

public final class ProviderUtil {
   protected static final String PROVIDER_RESOURCE = "META-INF/log4j-provider.properties";
   protected static final Collection<Provider> PROVIDERS = new HashSet<>();
   protected static final Lock STARTUP_LOCK = new ReentrantLock();
   private static final String API_VERSION = "Log4jAPIVersion";
   private static final String[] COMPATIBLE_API_VERSIONS = new String[]{"2.6.0"};
   private static final Logger LOGGER = StatusLogger.getLogger();
   private static volatile ProviderUtil instance;

   private ProviderUtil() {
      for (ClassLoader classLoader : LoaderUtil.getClassLoaders()) {
         try {
            loadProviders(classLoader);
         } catch (Throwable var6) {
            LOGGER.debug("Unable to retrieve provider from ClassLoader {}", classLoader, var6);
         }
      }

      for (LoaderUtil.UrlResource resource : LoaderUtil.findUrlResources("META-INF/log4j-provider.properties")) {
         loadProvider(resource.getUrl(), resource.getClassLoader());
      }
   }

   protected static void addProvider(final Provider provider) {
      PROVIDERS.add(provider);
      LOGGER.debug("Loaded Provider {}", provider);
   }

   protected static void loadProvider(final URL url, final ClassLoader cl) {
      try {
         Properties props = PropertiesUtil.loadClose(url.openStream(), url);
         if (validVersion(props.getProperty("Log4jAPIVersion"))) {
            Provider provider = new Provider(props, url, cl);
            PROVIDERS.add(provider);
            LOGGER.debug("Loaded Provider {}", provider);
         }
      } catch (IOException var4) {
         LOGGER.error("Unable to open {}", url, var4);
      }
   }

   protected static void loadProviders(final ClassLoader classLoader) {
      for (Provider provider : ServiceLoader.load(Provider.class, classLoader)) {
         if (validVersion(provider.getVersions()) && !PROVIDERS.contains(provider)) {
            PROVIDERS.add(provider);
         }
      }
   }

   @Deprecated
   protected static void loadProviders(final Enumeration<URL> urls, final ClassLoader cl) {
      if (urls != null) {
         while (urls.hasMoreElements()) {
            loadProvider(urls.nextElement(), cl);
         }
      }
   }

   public static Iterable<Provider> getProviders() {
      lazyInit();
      return PROVIDERS;
   }

   public static boolean hasProviders() {
      lazyInit();
      return !PROVIDERS.isEmpty();
   }

   protected static void lazyInit() {
      if (instance == null) {
         try {
            STARTUP_LOCK.lockInterruptibly();

            try {
               if (instance == null) {
                  instance = new ProviderUtil();
               }
            } finally {
               STARTUP_LOCK.unlock();
            }
         } catch (InterruptedException var4) {
            LOGGER.fatal("Interrupted before Log4j Providers could be loaded.", (Throwable)var4);
            Thread.currentThread().interrupt();
         }
      }
   }

   public static ClassLoader findClassLoader() {
      return LoaderUtil.getThreadContextClassLoader();
   }

   private static boolean validVersion(final String version) {
      for (String v : COMPATIBLE_API_VERSIONS) {
         if (version.startsWith(v)) {
            return true;
         }
      }

      return false;
   }
}
