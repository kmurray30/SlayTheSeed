package org.apache.logging.log4j.core.appender.db.jdbc;

import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.util.Loader;
import org.apache.logging.log4j.status.StatusLogger;
import org.apache.logging.log4j.util.Strings;

@Plugin(name = "ConnectionFactory", category = "Core", elementType = "connectionSource", printObject = true)
public final class FactoryMethodConnectionSource extends AbstractConnectionSource {
   private static final Logger LOGGER = StatusLogger.getLogger();
   private final DataSource dataSource;
   private final String description;

   private FactoryMethodConnectionSource(final DataSource dataSource, final String className, final String methodName, final String returnType) {
      this.dataSource = dataSource;
      this.description = "factory{ public static " + returnType + ' ' + className + '.' + methodName + "() }";
   }

   @Override
   public Connection getConnection() throws SQLException {
      return this.dataSource.getConnection();
   }

   @Override
   public String toString() {
      return this.description;
   }

   @PluginFactory
   public static FactoryMethodConnectionSource createConnectionSource(
      @PluginAttribute("class") final String className, @PluginAttribute("method") final String methodName
   ) {
      if (!Strings.isEmpty(className) && !Strings.isEmpty(methodName)) {
         final Method method;
         try {
            Class<?> factoryClass = Loader.loadClass(className);
            method = factoryClass.getMethod(methodName);
         } catch (Exception var8) {
            LOGGER.error(var8.toString(), (Throwable)var8);
            return null;
         }

         Class<?> returnType = method.getReturnType();
         String returnTypeString = returnType.getName();
         DataSource dataSource;
         if (returnType == DataSource.class) {
            try {
               dataSource = (DataSource)method.invoke(null);
               returnTypeString = returnTypeString + "[" + dataSource + ']';
            } catch (Exception var7) {
               LOGGER.error(var7.toString(), (Throwable)var7);
               return null;
            }
         } else {
            if (returnType != Connection.class) {
               LOGGER.error("Method [{}.{}()] returns unsupported type [{}].", className, methodName, returnType.getName());
               return null;
            }

            dataSource = new DataSource() {
               @Override
               public Connection getConnection() throws SQLException {
                  try {
                     return (Connection)method.invoke(null);
                  } catch (Exception var2) {
                     throw new SQLException("Failed to obtain connection from factory method.", var2);
                  }
               }

               @Override
               public Connection getConnection(final String username, final String password) throws SQLException {
                  throw new UnsupportedOperationException();
               }

               @Override
               public int getLoginTimeout() throws SQLException {
                  throw new UnsupportedOperationException();
               }

               @Override
               public PrintWriter getLogWriter() throws SQLException {
                  throw new UnsupportedOperationException();
               }

               @Override
               public java.util.logging.Logger getParentLogger() {
                  throw new UnsupportedOperationException();
               }

               @Override
               public boolean isWrapperFor(final Class<?> iface) throws SQLException {
                  return false;
               }

               @Override
               public void setLoginTimeout(final int seconds) throws SQLException {
                  throw new UnsupportedOperationException();
               }

               @Override
               public void setLogWriter(final PrintWriter out) throws SQLException {
                  throw new UnsupportedOperationException();
               }

               @Override
               public <T> T unwrap(final Class<T> iface) throws SQLException {
                  return null;
               }
            };
         }

         return new FactoryMethodConnectionSource(dataSource, className, methodName, returnTypeString);
      } else {
         LOGGER.error("No class name or method name specified for the connection factory method.");
         return null;
      }
   }
}
