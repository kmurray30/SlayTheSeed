package org.apache.logging.log4j.core.config;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Objects;
import javax.net.ssl.HttpsURLConnection;
import org.apache.logging.log4j.core.net.UrlConnectionFactory;
import org.apache.logging.log4j.core.net.ssl.LaxHostnameVerifier;
import org.apache.logging.log4j.core.net.ssl.SslConfiguration;
import org.apache.logging.log4j.core.net.ssl.SslConfigurationFactory;
import org.apache.logging.log4j.core.util.AuthorizationProvider;
import org.apache.logging.log4j.core.util.FileUtils;
import org.apache.logging.log4j.core.util.Loader;
import org.apache.logging.log4j.core.util.Source;
import org.apache.logging.log4j.util.Constants;
import org.apache.logging.log4j.util.LoaderUtil;
import org.apache.logging.log4j.util.PropertiesUtil;

public class ConfigurationSource {
   public static final ConfigurationSource NULL_SOURCE = new ConfigurationSource(Constants.EMPTY_BYTE_ARRAY, null, 0L);
   public static final ConfigurationSource COMPOSITE_SOURCE = new ConfigurationSource(Constants.EMPTY_BYTE_ARRAY, null, 0L);
   private static final String HTTPS = "https";
   private final File file;
   private final URL url;
   private final String location;
   private final InputStream stream;
   private volatile byte[] data;
   private volatile Source source;
   private final long lastModified;
   private volatile long modifiedMillis;

   public ConfigurationSource(final InputStream stream, final File file) {
      this.stream = Objects.requireNonNull(stream, "stream is null");
      this.file = Objects.requireNonNull(file, "file is null");
      this.location = file.getAbsolutePath();
      this.url = null;
      this.data = null;
      long modified = 0L;

      try {
         modified = file.lastModified();
      } catch (Exception var6) {
      }

      this.lastModified = modified;
   }

   public ConfigurationSource(final InputStream stream, final URL url) {
      this.stream = Objects.requireNonNull(stream, "stream is null");
      this.url = Objects.requireNonNull(url, "URL is null");
      this.location = url.toString();
      this.file = null;
      this.data = null;
      this.lastModified = 0L;
   }

   public ConfigurationSource(final InputStream stream, final URL url, long lastModified) {
      this.stream = Objects.requireNonNull(stream, "stream is null");
      this.url = Objects.requireNonNull(url, "URL is null");
      this.location = url.toString();
      this.file = null;
      this.data = null;
      this.lastModified = lastModified;
   }

   public ConfigurationSource(final InputStream stream) throws IOException {
      this(toByteArray(stream), null, 0L);
   }

   public ConfigurationSource(final Source source, final byte[] data, long lastModified) throws IOException {
      Objects.requireNonNull(source, "source is null");
      this.data = Objects.requireNonNull(data, "data is null");
      this.stream = new ByteArrayInputStream(data);
      this.file = source.getFile();
      this.url = source.getURI().toURL();
      this.location = source.getLocation();
      this.lastModified = lastModified;
   }

   private ConfigurationSource(final byte[] data, final URL url, long lastModified) {
      this.data = Objects.requireNonNull(data, "data is null");
      this.stream = new ByteArrayInputStream(data);
      this.file = null;
      this.url = url;
      this.location = null;
      this.lastModified = lastModified;
      if (url == null) {
         this.data = data;
      }
   }

   private static byte[] toByteArray(final InputStream inputStream) throws IOException {
      int buffSize = Math.max(4096, inputStream.available());
      ByteArrayOutputStream contents = new ByteArrayOutputStream(buffSize);
      byte[] buff = new byte[buffSize];

      for (int length = inputStream.read(buff); length > 0; length = inputStream.read(buff)) {
         contents.write(buff, 0, length);
      }

      return contents.toByteArray();
   }

   public File getFile() {
      return this.file;
   }

   public URL getURL() {
      return this.url;
   }

   public void setSource(Source source) {
      this.source = source;
   }

   public void setData(byte[] data) {
      this.data = data;
   }

   public void setModifiedMillis(long modifiedMillis) {
      this.modifiedMillis = modifiedMillis;
   }

   public URI getURI() {
      URI sourceURI = null;
      if (this.url != null) {
         try {
            sourceURI = this.url.toURI();
         } catch (URISyntaxException var6) {
         }
      }

      if (sourceURI == null && this.file != null) {
         sourceURI = this.file.toURI();
      }

      if (sourceURI == null && this.location != null) {
         try {
            sourceURI = new URI(this.location);
         } catch (URISyntaxException var5) {
            try {
               sourceURI = new URI("file://" + this.location);
            } catch (URISyntaxException var4) {
            }
         }
      }

      return sourceURI;
   }

   public long getLastModified() {
      return this.lastModified;
   }

   public String getLocation() {
      return this.location;
   }

   public InputStream getInputStream() {
      return this.stream;
   }

   public ConfigurationSource resetInputStream() throws IOException {
      if (this.source != null) {
         return new ConfigurationSource(this.source, this.data, this.lastModified);
      } else if (this.file != null) {
         return new ConfigurationSource(new FileInputStream(this.file), this.file);
      } else if (this.url != null && this.data != null) {
         return new ConfigurationSource(this.data, this.url, this.modifiedMillis == 0L ? this.lastModified : this.modifiedMillis);
      } else if (this.url != null) {
         return fromUri(this.getURI());
      } else {
         return this.data != null ? new ConfigurationSource(this.data, null, this.lastModified) : null;
      }
   }

   @Override
   public String toString() {
      if (this.location != null) {
         return this.location;
      } else if (this == NULL_SOURCE) {
         return "NULL_SOURCE";
      } else {
         int length = this.data == null ? -1 : this.data.length;
         return "stream (" + length + " bytes, unknown location)";
      }
   }

   public static ConfigurationSource fromUri(final URI configLocation) {
      File configFile = FileUtils.fileFromUri(configLocation);
      if (configFile != null && configFile.exists() && configFile.canRead()) {
         try {
            return new ConfigurationSource(new FileInputStream(configFile), configFile);
         } catch (FileNotFoundException var10) {
            ConfigurationFactory.LOGGER.error("Cannot locate file {}", configLocation.getPath(), var10);
         }
      }

      if (ConfigurationFactory.isClassLoaderUri(configLocation)) {
         ClassLoader loader = LoaderUtil.getThreadContextClassLoader();
         String path = ConfigurationFactory.extractClassLoaderUriPath(configLocation);
         return fromResource(path, loader);
      } else if (!configLocation.isAbsolute()) {
         ConfigurationFactory.LOGGER.error("File not found in file system or classpath: {}", configLocation.toString());
         return null;
      } else {
         try {
            URL url = configLocation.toURL();
            URLConnection urlConnection = UrlConnectionFactory.createConnection(url);
            InputStream is = urlConnection.getInputStream();
            long lastModified = urlConnection.getLastModified();
            return new ConfigurationSource(is, configLocation.toURL(), lastModified);
         } catch (FileNotFoundException var7) {
            ConfigurationFactory.LOGGER.warn("Could not locate file {}", configLocation.toString());
         } catch (MalformedURLException var8) {
            ConfigurationFactory.LOGGER.error("Invalid URL {}", configLocation.toString(), var8);
         } catch (Exception var9) {
            ConfigurationFactory.LOGGER.error("Unable to access {}", configLocation.toString(), var9);
         }

         return null;
      }
   }

   public static ConfigurationSource fromResource(final String resource, final ClassLoader loader) {
      URL url = Loader.getResource(resource, loader);
      return url == null ? null : getConfigurationSource(url);
   }

   private static ConfigurationSource getConfigurationSource(URL url) {
      try {
         URLConnection urlConnection = url.openConnection();
         AuthorizationProvider provider = ConfigurationFactory.authorizationProvider(PropertiesUtil.getProperties());
         provider.addAuthorization(urlConnection);
         if (url.getProtocol().equals("https")) {
            SslConfiguration sslConfiguration = SslConfigurationFactory.getSslConfiguration();
            if (sslConfiguration != null) {
               ((HttpsURLConnection)urlConnection).setSSLSocketFactory(sslConfiguration.getSslSocketFactory());
               if (!sslConfiguration.isVerifyHostName()) {
                  ((HttpsURLConnection)urlConnection).setHostnameVerifier(LaxHostnameVerifier.INSTANCE);
               }
            }
         }

         File file = FileUtils.fileFromUri(url.toURI());

         try {
            return file != null
               ? new ConfigurationSource(urlConnection.getInputStream(), FileUtils.fileFromUri(url.toURI()))
               : new ConfigurationSource(urlConnection.getInputStream(), url, urlConnection.getLastModified());
         } catch (FileNotFoundException var5) {
            ConfigurationFactory.LOGGER.info("Unable to locate file {}, ignoring.", url.toString());
            return null;
         }
      } catch (URISyntaxException | IOException var6) {
         ConfigurationFactory.LOGGER.warn("Error accessing {} due to {}, ignoring.", url.toString(), var6.getMessage());
         return null;
      }
   }
}
