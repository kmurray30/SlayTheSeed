package org.apache.logging.log4j.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;

public class PropertyFilePropertySource extends PropertiesPropertySource {
   public PropertyFilePropertySource(final String fileName) {
      super(loadPropertiesFile(fileName));
   }

   private static Properties loadPropertiesFile(final String fileName) {
      Properties props = new Properties();

      for (URL url : LoaderUtil.findResources(fileName)) {
         try (InputStream in = url.openStream()) {
            props.load(in);
         } catch (IOException var17) {
            LowLevelLogUtil.logException("Unable to read " + url, var17);
         }
      }

      return props;
   }

   @Override
   public int getPriority() {
      return 0;
   }
}
