package org.apache.logging.log4j.core.config;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAliases;
import org.apache.logging.log4j.core.net.UrlConnectionFactory;
import org.apache.logging.log4j.core.net.ssl.SslConfiguration;
import org.apache.logging.log4j.core.net.ssl.SslConfigurationFactory;
import org.apache.logging.log4j.core.util.AbstractWatcher;
import org.apache.logging.log4j.core.util.Source;
import org.apache.logging.log4j.core.util.Watcher;
import org.apache.logging.log4j.status.StatusLogger;

@Plugin(name = "http", category = "Watcher", elementType = "watcher", printObject = true)
@PluginAliases("https")
public class HttpWatcher extends AbstractWatcher {
   private Logger LOGGER = StatusLogger.getLogger();
   private SslConfiguration sslConfiguration = SslConfigurationFactory.getSslConfiguration();
   private URL url;
   private volatile long lastModifiedMillis;
   private static final int NOT_MODIFIED = 304;
   private static final int OK = 200;
   private static final int BUF_SIZE = 1024;
   private static final String HTTP = "http";
   private static final String HTTPS = "https";

   public HttpWatcher(
      final Configuration configuration, final Reconfigurable reconfigurable, final List<ConfigurationListener> configurationListeners, long lastModifiedMillis
   ) {
      super(configuration, reconfigurable, configurationListeners);
      this.lastModifiedMillis = lastModifiedMillis;
   }

   @Override
   public long getLastModified() {
      return this.lastModifiedMillis;
   }

   @Override
   public boolean isModified() {
      return this.refreshConfiguration();
   }

   @Override
   public void watching(Source source) {
      if (!source.getURI().getScheme().equals("http") && !source.getURI().getScheme().equals("https")) {
         throw new IllegalArgumentException("HttpWatcher requires a url using the HTTP or HTTPS protocol, not " + source.getURI().getScheme());
      } else {
         try {
            this.url = source.getURI().toURL();
         } catch (MalformedURLException var3) {
            throw new IllegalArgumentException("Invalid URL for HttpWatcher " + source.getURI(), var3);
         }

         super.watching(source);
      }
   }

   @Override
   public Watcher newWatcher(Reconfigurable reconfigurable, List<ConfigurationListener> listeners, long lastModifiedMillis) {
      HttpWatcher watcher = new HttpWatcher(this.getConfiguration(), reconfigurable, listeners, lastModifiedMillis);
      if (this.getSource() != null) {
         watcher.watching(this.getSource());
      }

      return watcher;
   }

   private boolean refreshConfiguration() {
      try {
         HttpURLConnection urlConnection = UrlConnectionFactory.createConnection(this.url, this.lastModifiedMillis, this.sslConfiguration);
         urlConnection.connect();

         try {
            int code = urlConnection.getResponseCode();
            switch (code) {
               case 200:
                  try (InputStream is = urlConnection.getInputStream()) {
                     ConfigurationSource configSource = this.getConfiguration().getConfigurationSource();
                     configSource.setData(this.readStream(is));
                     this.lastModifiedMillis = urlConnection.getLastModified();
                     configSource.setModifiedMillis(this.lastModifiedMillis);
                     this.LOGGER.debug("Content was modified for {}", this.url.toString());
                     return true;
                  } catch (IOException var42) {
                     try (InputStream es = urlConnection.getErrorStream()) {
                        this.LOGGER.info("Error accessing configuration at {}: {}", this.url, this.readStream(es));
                     } catch (IOException var40) {
                        this.LOGGER.error("Error accessing configuration at {}: {}", this.url, var42.getMessage());
                     }

                     return false;
                  }
               case 304:
                  this.LOGGER.debug("Configuration Not Modified");
                  return false;
               default:
                  if (code < 0) {
                     this.LOGGER.info("Invalid response code returned");
                  } else {
                     this.LOGGER.info("Unexpected response code returned {}", code);
                  }

                  return false;
            }
         } catch (IOException var43) {
            this.LOGGER.error("Error accessing configuration at {}: {}", this.url, var43.getMessage());
         }
      } catch (IOException var44) {
         this.LOGGER.error("Error connecting to configuration at {}: {}", this.url, var44.getMessage());
      }

      return false;
   }

   private byte[] readStream(InputStream is) throws IOException {
      ByteArrayOutputStream result = new ByteArrayOutputStream();
      byte[] buffer = new byte[1024];

      int length;
      while ((length = is.read(buffer)) != -1) {
         result.write(buffer, 0, length);
      }

      return result.toByteArray();
   }
}
