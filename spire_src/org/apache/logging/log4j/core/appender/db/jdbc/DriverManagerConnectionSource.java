package org.apache.logging.log4j.core.appender.db.jdbc;

import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;

@Plugin(name = "DriverManager", category = "Core", elementType = "connectionSource", printObject = true)
public class DriverManagerConnectionSource extends AbstractDriverManagerConnectionSource {
   @PluginBuilderFactory
   public static <B extends DriverManagerConnectionSource.Builder<B>> B newBuilder() {
      return new DriverManagerConnectionSource.Builder<B>().asBuilder();
   }

   public DriverManagerConnectionSource(
      final String driverClassName,
      final String connectionString,
      final String actualConnectionString,
      final char[] userName,
      final char[] password,
      final Property[] properties
   ) {
      super(driverClassName, connectionString, actualConnectionString, userName, password, properties);
   }

   public static class Builder<B extends DriverManagerConnectionSource.Builder<B>>
      extends AbstractDriverManagerConnectionSource.Builder<B>
      implements org.apache.logging.log4j.core.util.Builder<DriverManagerConnectionSource> {
      public DriverManagerConnectionSource build() {
         return new DriverManagerConnectionSource(
            this.getDriverClassName(), this.getConnectionString(), this.getConnectionString(), this.getUserName(), this.getPassword(), this.getProperties()
         );
      }
   }
}
