package org.apache.logging.log4j.core.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;
import org.apache.logging.log4j.core.config.composite.CompositeConfiguration;
import org.apache.logging.log4j.core.config.plugins.util.PluginManager;
import org.apache.logging.log4j.core.config.plugins.util.PluginType;
import org.apache.logging.log4j.core.lookup.ConfigurationStrSubstitutor;
import org.apache.logging.log4j.core.lookup.Interpolator;
import org.apache.logging.log4j.core.lookup.StrSubstitutor;
import org.apache.logging.log4j.core.net.UrlConnectionFactory;
import org.apache.logging.log4j.core.util.AuthorizationProvider;
import org.apache.logging.log4j.core.util.BasicAuthorizationProvider;
import org.apache.logging.log4j.core.util.FileUtils;
import org.apache.logging.log4j.core.util.Loader;
import org.apache.logging.log4j.core.util.NetUtils;
import org.apache.logging.log4j.core.util.ReflectionUtil;
import org.apache.logging.log4j.status.StatusLogger;
import org.apache.logging.log4j.util.LoaderUtil;
import org.apache.logging.log4j.util.PropertiesUtil;
import org.apache.logging.log4j.util.Strings;

public abstract class ConfigurationFactory extends ConfigurationBuilderFactory {
   public static final String CONFIGURATION_FACTORY_PROPERTY = "log4j.configurationFactory";
   public static final String CONFIGURATION_FILE_PROPERTY = "log4j.configurationFile";
   public static final String LOG4J1_CONFIGURATION_FILE_PROPERTY = "log4j.configuration";
   public static final String LOG4J1_EXPERIMENTAL = "log4j1.compatibility";
   public static final String AUTHORIZATION_PROVIDER = "log4j2.authorizationProvider";
   public static final String CATEGORY = "ConfigurationFactory";
   protected static final Logger LOGGER = StatusLogger.getLogger();
   protected static final String TEST_PREFIX = "log4j2-test";
   protected static final String DEFAULT_PREFIX = "log4j2";
   protected static final String LOG4J1_VERSION = "1";
   protected static final String LOG4J2_VERSION = "2";
   private static final String CLASS_LOADER_SCHEME = "classloader";
   private static final String CLASS_PATH_SCHEME = "classpath";
   private static final String OVERRIDE_PARAM = "override";
   private static volatile List<ConfigurationFactory> factories;
   private static ConfigurationFactory configFactory = new ConfigurationFactory.Factory();
   protected final StrSubstitutor substitutor = new ConfigurationStrSubstitutor(new Interpolator());
   private static final Lock LOCK = new ReentrantLock();
   private static final String HTTPS = "https";
   private static final String HTTP = "http";
   private static volatile AuthorizationProvider authorizationProvider;

   public static ConfigurationFactory getInstance() {
      if (factories == null) {
         LOCK.lock();

         try {
            if (factories == null) {
               List<ConfigurationFactory> list = new ArrayList<>();
               PropertiesUtil props = PropertiesUtil.getProperties();
               String factoryClass = props.getStringProperty("log4j.configurationFactory");
               if (factoryClass != null) {
                  addFactory(list, factoryClass);
               }

               PluginManager manager = new PluginManager("ConfigurationFactory");
               manager.collectPlugins();
               Map<String, PluginType<?>> plugins = manager.getPlugins();
               List<Class<? extends ConfigurationFactory>> ordered = new ArrayList<>(plugins.size());

               for (PluginType<?> type : plugins.values()) {
                  try {
                     ordered.add(type.getPluginClass().asSubclass(ConfigurationFactory.class));
                  } catch (Exception var12) {
                     LOGGER.warn("Unable to add class {}", type.getPluginClass(), var12);
                  }
               }

               Collections.sort(ordered, OrderComparator.getInstance());

               for (Class<? extends ConfigurationFactory> clazz : ordered) {
                  addFactory(list, clazz);
               }

               factories = Collections.unmodifiableList(list);
               authorizationProvider = authorizationProvider(props);
            }
         } finally {
            LOCK.unlock();
         }
      }

      LOGGER.debug("Using configurationFactory {}", configFactory);
      return configFactory;
   }

   public static AuthorizationProvider authorizationProvider(PropertiesUtil props) {
      String authClass = props.getStringProperty("log4j2.authorizationProvider");
      AuthorizationProvider provider = null;
      if (authClass != null) {
         try {
            Object obj = LoaderUtil.newInstanceOf(authClass);
            if (obj instanceof AuthorizationProvider) {
               provider = (AuthorizationProvider)obj;
            } else {
               LOGGER.warn("{} is not an AuthorizationProvider, using default", obj.getClass().getName());
            }
         } catch (Exception var4) {
            LOGGER.warn("Unable to create {}, using default: {}", authClass, var4.getMessage());
         }
      }

      if (provider == null) {
         provider = new BasicAuthorizationProvider(props);
      }

      return provider;
   }

   public static AuthorizationProvider getAuthorizationProvider() {
      return authorizationProvider;
   }

   private static void addFactory(final Collection<ConfigurationFactory> list, final String factoryClass) {
      try {
         addFactory(list, Loader.loadClass(factoryClass).asSubclass(ConfigurationFactory.class));
      } catch (Exception var3) {
         LOGGER.error("Unable to load class {}", factoryClass, var3);
      }
   }

   private static void addFactory(final Collection<ConfigurationFactory> list, final Class<? extends ConfigurationFactory> factoryClass) {
      try {
         list.add(ReflectionUtil.instantiate(factoryClass));
      } catch (Exception var3) {
         LOGGER.error("Unable to create instance of {}", factoryClass.getName(), var3);
      }
   }

   public static void setConfigurationFactory(final ConfigurationFactory factory) {
      configFactory = factory;
   }

   public static void resetConfigurationFactory() {
      configFactory = new ConfigurationFactory.Factory();
   }

   public static void removeConfigurationFactory(final ConfigurationFactory factory) {
      if (configFactory == factory) {
         configFactory = new ConfigurationFactory.Factory();
      }
   }

   protected abstract String[] getSupportedTypes();

   protected String getTestPrefix() {
      return "log4j2-test";
   }

   protected String getDefaultPrefix() {
      return "log4j2";
   }

   protected String getVersion() {
      return "2";
   }

   protected boolean isActive() {
      return true;
   }

   public abstract Configuration getConfiguration(final LoggerContext loggerContext, ConfigurationSource source);

   public Configuration getConfiguration(final LoggerContext loggerContext, final String name, final URI configLocation) {
      if (!this.isActive()) {
         return null;
      } else {
         if (configLocation != null) {
            ConfigurationSource source = ConfigurationSource.fromUri(configLocation);
            if (source != null) {
               return this.getConfiguration(loggerContext, source);
            }
         }

         return null;
      }
   }

   public Configuration getConfiguration(final LoggerContext loggerContext, final String name, final URI configLocation, final ClassLoader loader) {
      if (!this.isActive()) {
         return null;
      } else if (loader == null) {
         return this.getConfiguration(loggerContext, name, configLocation);
      } else {
         if (isClassLoaderUri(configLocation)) {
            String path = extractClassLoaderUriPath(configLocation);
            ConfigurationSource source = ConfigurationSource.fromResource(path, loader);
            if (source != null) {
               Configuration configuration = this.getConfiguration(loggerContext, source);
               if (configuration != null) {
                  return configuration;
               }
            }
         }

         return this.getConfiguration(loggerContext, name, configLocation);
      }
   }

   static boolean isClassLoaderUri(final URI uri) {
      if (uri == null) {
         return false;
      } else {
         String scheme = uri.getScheme();
         return scheme == null || scheme.equals("classloader") || scheme.equals("classpath");
      }
   }

   static String extractClassLoaderUriPath(final URI uri) {
      return uri.getScheme() == null ? uri.getPath() : uri.getSchemeSpecificPart();
   }

   protected ConfigurationSource getInputFromString(final String config, final ClassLoader loader) {
      try {
         URL url = new URL(config);
         URLConnection urlConnection = UrlConnectionFactory.createConnection(url);
         File file = FileUtils.fileFromUri(url.toURI());
         return file != null
            ? new ConfigurationSource(urlConnection.getInputStream(), FileUtils.fileFromUri(url.toURI()))
            : new ConfigurationSource(urlConnection.getInputStream(), url, urlConnection.getLastModified());
      } catch (Exception var7) {
         ConfigurationSource source = ConfigurationSource.fromResource(config, loader);
         if (source == null) {
            try {
               File filex = new File(config);
               return new ConfigurationSource(new FileInputStream(filex), filex);
            } catch (FileNotFoundException var6) {
               LOGGER.catching(Level.DEBUG, var6);
            }
         }

         return source;
      }
   }

   static List<ConfigurationFactory> getFactories() {
      return factories;
   }

   private static class Factory extends ConfigurationFactory {
      private static final String ALL_TYPES = "*";

      private Factory() {
      }

      @Override
      public Configuration getConfiguration(final LoggerContext loggerContext, final String name, final URI configLocation) {
         if (configLocation == null) {
            String configLocationStr = this.substitutor.replace(PropertiesUtil.getProperties().getStringProperty("log4j.configurationFile"));
            if (configLocationStr != null) {
               String[] sources = this.parseConfigLocations(configLocationStr);
               if (sources.length > 1) {
                  List<AbstractConfiguration> configs = new ArrayList<>();

                  for (String sourceLocation : sources) {
                     Configuration config = this.getConfiguration(loggerContext, sourceLocation.trim());
                     if (config != null) {
                        if (!(config instanceof AbstractConfiguration)) {
                           LOGGER.error("Failed to created configuration at {}", sourceLocation);
                           return null;
                        }

                        configs.add((AbstractConfiguration)config);
                     } else {
                        LOGGER.warn("Unable to create configuration for {}, ignoring", sourceLocation);
                     }
                  }

                  if (configs.size() > 1) {
                     return new CompositeConfiguration(configs);
                  }

                  if (configs.size() == 1) {
                     return configs.get(0);
                  }
               }

               return this.getConfiguration(loggerContext, configLocationStr);
            }

            String log4j1ConfigStr = this.substitutor.replace(PropertiesUtil.getProperties().getStringProperty("log4j.configuration"));
            if (log4j1ConfigStr != null) {
               System.setProperty("log4j1.compatibility", "true");
               return this.getConfiguration("1", loggerContext, log4j1ConfigStr);
            }

            for (ConfigurationFactory factory : getFactories()) {
               String[] types = factory.getSupportedTypes();
               if (types != null) {
                  for (String type : types) {
                     if (type.equals("*")) {
                        Configuration config = factory.getConfiguration(loggerContext, name, configLocation);
                        if (config != null) {
                           return config;
                        }
                     }
                  }
               }
            }
         } else {
            String[] sources = this.parseConfigLocations(configLocation);
            if (sources.length > 1) {
               List<AbstractConfiguration> configs = new ArrayList<>();

               for (String sourceLocationx : sources) {
                  Configuration config = this.getConfiguration(loggerContext, sourceLocationx.trim());
                  if (!(config instanceof AbstractConfiguration)) {
                     LOGGER.error("Failed to created configuration at {}", sourceLocationx);
                     return null;
                  }

                  configs.add((AbstractConfiguration)config);
               }

               return new CompositeConfiguration(configs);
            }

            String configLocationStrx = configLocation.toString();

            for (ConfigurationFactory factoryx : getFactories()) {
               String[] types = factoryx.getSupportedTypes();
               if (types != null) {
                  for (String typex : types) {
                     if (typex.equals("*") || configLocationStrx.endsWith(typex)) {
                        Configuration config = factoryx.getConfiguration(loggerContext, name, configLocation);
                        if (config != null) {
                           return config;
                        }
                     }
                  }
               }
            }
         }

         Configuration config = this.getConfiguration(loggerContext, true, name);
         if (config == null) {
            config = this.getConfiguration(loggerContext, true, null);
            if (config == null) {
               config = this.getConfiguration(loggerContext, false, name);
               if (config == null) {
                  config = this.getConfiguration(loggerContext, false, null);
               }
            }
         }

         if (config != null) {
            return config;
         } else {
            LOGGER.warn(
               "No Log4j 2 configuration file found. Using default configuration (logging only errors to the console), or user programmatically provided configurations. Set system property 'log4j2.debug' to show Log4j 2 internal initialization logging. See https://logging.apache.org/log4j/2.x/manual/configuration.html for instructions on how to configure Log4j 2"
            );
            return new DefaultConfiguration();
         }
      }

      private Configuration getConfiguration(final LoggerContext loggerContext, final String configLocationStr) {
         return this.getConfiguration(null, loggerContext, configLocationStr);
      }

      private Configuration getConfiguration(String requiredVersion, final LoggerContext loggerContext, final String configLocationStr) {
         ConfigurationSource source = null;

         try {
            source = ConfigurationSource.fromUri(NetUtils.toURI(configLocationStr));
         } catch (Exception var13) {
            LOGGER.catching(Level.DEBUG, var13);
         }

         if (source == null) {
            ClassLoader loader = LoaderUtil.getThreadContextClassLoader();
            source = this.getInputFromString(configLocationStr, loader);
         }

         if (source != null) {
            for (ConfigurationFactory factory : getFactories()) {
               if (requiredVersion == null || factory.getVersion().equals(requiredVersion)) {
                  String[] types = factory.getSupportedTypes();
                  if (types != null) {
                     for (String type : types) {
                        if (type.equals("*") || configLocationStr.endsWith(type)) {
                           Configuration config = factory.getConfiguration(loggerContext, source);
                           if (config != null) {
                              return config;
                           }
                        }
                     }
                  }
               }
            }
         }

         return null;
      }

      private Configuration getConfiguration(final LoggerContext loggerContext, final boolean isTest, final String name) {
         boolean named = Strings.isNotEmpty(name);
         ClassLoader loader = LoaderUtil.getThreadContextClassLoader();

         for (ConfigurationFactory factory : getFactories()) {
            String prefix = isTest ? factory.getTestPrefix() : factory.getDefaultPrefix();
            String[] types = factory.getSupportedTypes();
            if (types != null) {
               for (String suffix : types) {
                  if (!suffix.equals("*")) {
                     String configName = named ? prefix + name + suffix : prefix + suffix;
                     ConfigurationSource source = ConfigurationSource.fromResource(configName, loader);
                     if (source != null) {
                        if (!factory.isActive()) {
                           LOGGER.warn("Found configuration file {} for inactive ConfigurationFactory {}", configName, factory.getClass().getName());
                        }

                        return factory.getConfiguration(loggerContext, source);
                     }
                  }
               }
            }
         }

         return null;
      }

      @Override
      public String[] getSupportedTypes() {
         return null;
      }

      @Override
      public Configuration getConfiguration(final LoggerContext loggerContext, final ConfigurationSource source) {
         if (source != null) {
            String config = source.getLocation();

            for (ConfigurationFactory factory : getFactories()) {
               String[] types = factory.getSupportedTypes();
               if (types != null) {
                  for (String type : types) {
                     if (type.equals("*") || config != null && config.endsWith(type)) {
                        Configuration c = factory.getConfiguration(loggerContext, source);
                        if (c != null) {
                           LOGGER.debug("Loaded configuration from {}", source);
                           return c;
                        }

                        LOGGER.error("Cannot determine the ConfigurationFactory to use for {}", config);
                        return null;
                     }
                  }
               }
            }
         }

         LOGGER.error("Cannot process configuration, input source is null");
         return null;
      }

      private String[] parseConfigLocations(URI configLocations) {
         String[] uris = configLocations.toString().split("\\?");
         List<String> locations = new ArrayList<>();
         if (uris.length <= 1) {
            return new String[]{uris[0]};
         } else {
            locations.add(uris[0]);
            String[] pairs = configLocations.getQuery().split("&");

            for (String pair : pairs) {
               int idx = pair.indexOf("=");

               try {
                  String key = idx > 0 ? URLDecoder.decode(pair.substring(0, idx), "UTF-8") : pair;
                  if (key.equalsIgnoreCase("override")) {
                     locations.add(URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
                  }
               } catch (UnsupportedEncodingException var11) {
                  LOGGER.warn("Invalid query parameter in {}", configLocations);
               }
            }

            return locations.toArray(Strings.EMPTY_ARRAY);
         }
      }

      private String[] parseConfigLocations(String configLocations) {
         String[] uris = configLocations.split(",");
         if (uris.length > 1) {
            return uris;
         } else {
            try {
               return this.parseConfigLocations(new URI(configLocations));
            } catch (URISyntaxException var4) {
               LOGGER.warn("Error parsing URI {}", configLocations);
               return new String[]{configLocations};
            }
         }
      }
   }
}
