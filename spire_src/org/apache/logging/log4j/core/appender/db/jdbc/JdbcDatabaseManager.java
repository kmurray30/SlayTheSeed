package org.apache.logging.log4j.core.appender.db.jdbc;

import java.io.Serializable;
import java.io.StringReader;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLTransactionRollbackException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.StringLayout;
import org.apache.logging.log4j.core.appender.AppenderLoggingException;
import org.apache.logging.log4j.core.appender.ManagerFactory;
import org.apache.logging.log4j.core.appender.db.AbstractDatabaseManager;
import org.apache.logging.log4j.core.appender.db.ColumnMapping;
import org.apache.logging.log4j.core.appender.db.DbAppenderLoggingException;
import org.apache.logging.log4j.core.config.plugins.convert.DateTypeConverter;
import org.apache.logging.log4j.core.config.plugins.convert.TypeConverters;
import org.apache.logging.log4j.core.util.Closer;
import org.apache.logging.log4j.core.util.Log4jThread;
import org.apache.logging.log4j.message.MapMessage;
import org.apache.logging.log4j.spi.ThreadContextMap;
import org.apache.logging.log4j.spi.ThreadContextStack;
import org.apache.logging.log4j.util.IndexedReadOnlyStringMap;
import org.apache.logging.log4j.util.ReadOnlyStringMap;
import org.apache.logging.log4j.util.Strings;

public final class JdbcDatabaseManager extends AbstractDatabaseManager {
   private static final JdbcDatabaseManager.JdbcDatabaseManagerFactory INSTANCE = new JdbcDatabaseManager.JdbcDatabaseManagerFactory();
   private final List<ColumnConfig> columnConfigs;
   private final String sqlStatement;
   private final JdbcDatabaseManager.FactoryData factoryData;
   private volatile Connection connection;
   private volatile PreparedStatement statement;
   private volatile JdbcDatabaseManager.Reconnector reconnector;
   private volatile boolean isBatchSupported;
   private volatile Map<String, JdbcDatabaseManager.ResultSetColumnMetaData> columnMetaData;

   private static void appendColumnName(final int i, final String columnName, final StringBuilder sb) {
      if (i > 1) {
         sb.append(',');
      }

      sb.append(columnName);
   }

   private static void appendColumnNames(final String sqlVerb, final JdbcDatabaseManager.FactoryData data, final StringBuilder sb) {
      int i = 1;
      String messagePattern = "Appending {} {}[{}]: {}={} ";
      if (data.columnMappings != null) {
         for (ColumnMapping colMapping : data.columnMappings) {
            String columnName = colMapping.getName();
            appendColumnName(i, columnName, sb);
            logger().trace("Appending {} {}[{}]: {}={} ", sqlVerb, colMapping.getClass().getSimpleName(), i, columnName, colMapping);
            i++;
         }
      }

      if (data.columnConfigs != null) {
         for (ColumnConfig colConfig : data.columnConfigs) {
            String columnName = colConfig.getColumnName();
            appendColumnName(i, columnName, sb);
            logger().trace("Appending {} {}[{}]: {}={} ", sqlVerb, colConfig.getClass().getSimpleName(), i, columnName, colConfig);
            i++;
         }
      }
   }

   private static JdbcDatabaseManager.JdbcDatabaseManagerFactory getFactory() {
      return INSTANCE;
   }

   @Deprecated
   public static JdbcDatabaseManager getJDBCDatabaseManager(
      final String name, final int bufferSize, final ConnectionSource connectionSource, final String tableName, final ColumnConfig[] columnConfigs
   ) {
      return getManager(
         name,
         new JdbcDatabaseManager.FactoryData(bufferSize, null, connectionSource, tableName, columnConfigs, ColumnMapping.EMPTY_ARRAY, false, 5000L, true),
         getFactory()
      );
   }

   @Deprecated
   public static JdbcDatabaseManager getManager(
      final String name,
      final int bufferSize,
      final Layout<? extends Serializable> layout,
      final ConnectionSource connectionSource,
      final String tableName,
      final ColumnConfig[] columnConfigs,
      final ColumnMapping[] columnMappings
   ) {
      return getManager(
         name,
         new JdbcDatabaseManager.FactoryData(bufferSize, layout, connectionSource, tableName, columnConfigs, columnMappings, false, 5000L, true),
         getFactory()
      );
   }

   @Deprecated
   public static JdbcDatabaseManager getManager(
      final String name,
      final int bufferSize,
      final Layout<? extends Serializable> layout,
      final ConnectionSource connectionSource,
      final String tableName,
      final ColumnConfig[] columnConfigs,
      final ColumnMapping[] columnMappings,
      final boolean immediateFail,
      final long reconnectIntervalMillis
   ) {
      return getManager(
         name,
         new JdbcDatabaseManager.FactoryData(bufferSize, null, connectionSource, tableName, columnConfigs, columnMappings, false, 5000L, true),
         getFactory()
      );
   }

   public static JdbcDatabaseManager getManager(
      final String name,
      final int bufferSize,
      final Layout<? extends Serializable> layout,
      final ConnectionSource connectionSource,
      final String tableName,
      final ColumnConfig[] columnConfigs,
      final ColumnMapping[] columnMappings,
      final boolean immediateFail,
      final long reconnectIntervalMillis,
      final boolean truncateStrings
   ) {
      return getManager(
         name,
         new JdbcDatabaseManager.FactoryData(
            bufferSize, layout, connectionSource, tableName, columnConfigs, columnMappings, immediateFail, reconnectIntervalMillis, truncateStrings
         ),
         getFactory()
      );
   }

   private JdbcDatabaseManager(
      final String name, final String sqlStatement, final List<ColumnConfig> columnConfigs, final JdbcDatabaseManager.FactoryData factoryData
   ) {
      super(name, factoryData.getBufferSize());
      this.sqlStatement = sqlStatement;
      this.columnConfigs = columnConfigs;
      this.factoryData = factoryData;
   }

   private void checkConnection() {
      boolean connClosed = true;

      try {
         connClosed = this.isClosed(this.connection);
      } catch (SQLException var5) {
      }

      boolean stmtClosed = true;

      try {
         stmtClosed = this.isClosed(this.statement);
      } catch (SQLException var4) {
      }

      if (!this.isRunning() || connClosed || stmtClosed) {
         this.closeResources(false);
         if (this.reconnector != null && !this.factoryData.immediateFail) {
            this.reconnector.latch();
            if (this.connection == null) {
               throw new AppenderLoggingException(
                  "Error writing to JDBC Manager '%s': JDBC connection not available [%s]", this.getName(), this.fieldsToString()
               );
            }

            if (this.statement == null) {
               throw new AppenderLoggingException(
                  "Error writing to JDBC Manager '%s': JDBC statement not available [%s].", this.getName(), this.connection, this.fieldsToString()
               );
            }
         }
      }
   }

   protected void closeResources(final boolean logExceptions) {
      PreparedStatement tempPreparedStatement = this.statement;
      this.statement = null;

      try {
         Closer.close(tempPreparedStatement);
      } catch (Exception var6) {
         if (logExceptions) {
            this.logWarn("Failed to close SQL statement logging event or flushing buffer", var6);
         }
      }

      Connection tempConnection = this.connection;
      this.connection = null;

      try {
         Closer.close(tempConnection);
      } catch (Exception var5) {
         if (logExceptions) {
            this.logWarn("Failed to close database connection logging event or flushing buffer", var5);
         }
      }
   }

   @Override
   protected boolean commitAndClose() {
      boolean closed = true;

      try {
         if (this.connection != null && !this.connection.isClosed()) {
            if (this.isBuffered() && this.isBatchSupported && this.statement != null) {
               logger().debug("Executing batch PreparedStatement {}", this.statement);

               int[] result;
               try {
                  result = this.statement.executeBatch();
               } catch (SQLTransactionRollbackException var8) {
                  logger().debug("{} executing batch PreparedStatement {}, retrying.", var8, this.statement);
                  result = this.statement.executeBatch();
               }

               logger().debug("Batch result: {}", Arrays.toString(result));
            }

            logger().debug("Committing Connection {}", this.connection);
            this.connection.commit();
         }
      } catch (SQLException var9) {
         throw new DbAppenderLoggingException(var9, "Failed to commit transaction logging event or flushing buffer [%s]", this.fieldsToString());
      } finally {
         this.closeResources(true);
      }

      return true;
   }

   private boolean commitAndCloseAll() {
      if (this.connection != null || this.statement != null) {
         try {
            this.commitAndClose();
            return true;
         } catch (AppenderLoggingException var4) {
            Throwable cause = var4.getCause();
            Throwable actual = (Throwable)(cause == null ? var4 : cause);
            logger().debug("{} committing and closing connection: {}", actual, actual.getClass().getSimpleName(), var4.toString(), var4);
         }
      }

      if (this.factoryData.connectionSource != null) {
         this.factoryData.connectionSource.stop();
      }

      return true;
   }

   private void connectAndPrepare() throws SQLException {
      logger().debug("Acquiring JDBC connection from {}", this.getConnectionSource());
      this.connection = this.getConnectionSource().getConnection();
      logger().debug("Acquired JDBC connection {}", this.connection);
      logger().debug("Getting connection metadata {}", this.connection);
      DatabaseMetaData databaseMetaData = this.connection.getMetaData();
      logger().debug("Connection metadata {}", databaseMetaData);
      this.isBatchSupported = databaseMetaData.supportsBatchUpdates();
      logger().debug("Connection supportsBatchUpdates: {}", this.isBatchSupported);
      this.connection.setAutoCommit(false);
      logger().debug("Preparing SQL {}", this.sqlStatement);
      this.statement = this.connection.prepareStatement(this.sqlStatement);
      logger().debug("Prepared SQL {}", this.statement);
      if (this.factoryData.truncateStrings) {
         this.initColumnMetaData();
      }
   }

   @Override
   protected void connectAndStart() {
      this.checkConnection();
      synchronized (this) {
         try {
            this.connectAndPrepare();
         } catch (SQLException var4) {
            this.reconnectOn(var4);
         }
      }
   }

   private JdbcDatabaseManager.Reconnector createReconnector() {
      JdbcDatabaseManager.Reconnector recon = new JdbcDatabaseManager.Reconnector();
      recon.setDaemon(true);
      recon.setPriority(1);
      return recon;
   }

   private String createSqlSelect() {
      StringBuilder sb = new StringBuilder("select ");
      appendColumnNames("SELECT", this.factoryData, sb);
      sb.append(" from ");
      sb.append(this.factoryData.tableName);
      sb.append(" where 1=0");
      return sb.toString();
   }

   private String fieldsToString() {
      return String.format(
         "columnConfigs=%s, sqlStatement=%s, factoryData=%s, connection=%s, statement=%s, reconnector=%s, isBatchSupported=%s, columnMetaData=%s",
         this.columnConfigs,
         this.sqlStatement,
         this.factoryData,
         this.connection,
         this.statement,
         this.reconnector,
         this.isBatchSupported,
         this.columnMetaData
      );
   }

   public ConnectionSource getConnectionSource() {
      return this.factoryData.connectionSource;
   }

   public String getSqlStatement() {
      return this.sqlStatement;
   }

   public String getTableName() {
      return this.factoryData.tableName;
   }

   private void initColumnMetaData() throws SQLException {
      String sqlSelect = this.createSqlSelect();
      logger().debug("Getting SQL metadata for table {}: {}", this.factoryData.tableName, sqlSelect);

      try (PreparedStatement mdStatement = this.connection.prepareStatement(sqlSelect)) {
         ResultSetMetaData rsMetaData = mdStatement.getMetaData();
         logger().debug("SQL metadata: {}", rsMetaData);
         if (rsMetaData != null) {
            int columnCount = rsMetaData.getColumnCount();
            this.columnMetaData = new HashMap<>(columnCount);
            int i = 0;

            for (int j = 1; i < columnCount; j++) {
               JdbcDatabaseManager.ResultSetColumnMetaData value = new JdbcDatabaseManager.ResultSetColumnMetaData(rsMetaData, j);
               this.columnMetaData.put(value.getNameKey(), value);
               i++;
            }
         } else {
            logger()
               .warn(
                  "{}: truncateStrings is true and ResultSetMetaData is null for statement: {}; manager will not perform truncation.",
                  this.getClass().getSimpleName(),
                  mdStatement
               );
         }
      }
   }

   private boolean isClosed(final Statement statement) throws SQLException {
      return statement == null || statement.isClosed();
   }

   private boolean isClosed(final Connection connection) throws SQLException {
      return connection == null || connection.isClosed();
   }

   private void reconnectOn(final Exception exception) {
      if (!this.factoryData.retry) {
         throw new AppenderLoggingException("Cannot connect and prepare", exception);
      } else {
         if (this.reconnector == null) {
            this.reconnector = this.createReconnector();

            try {
               this.reconnector.reconnect();
            } catch (SQLException var3) {
               logger()
                  .debug(
                     "Cannot reestablish JDBC connection to {}: {}; starting reconnector thread {}", this.factoryData, var3, this.reconnector.getName(), var3
                  );
               this.reconnector.start();
               this.reconnector.latch();
               if (this.connection == null || this.statement == null) {
                  throw new AppenderLoggingException(exception, "Error sending to %s for %s [%s]", this.getName(), this.factoryData, this.fieldsToString());
               }
            }
         }
      }
   }

   private void setFields(final MapMessage<?, ?> mapMessage) throws SQLException {
      IndexedReadOnlyStringMap map = mapMessage.getIndexedReadOnlyStringMap();
      String simpleName = this.statement.getClass().getName();
      int j = 1;
      if (this.factoryData.columnMappings != null) {
         for (ColumnMapping mapping : this.factoryData.columnMappings) {
            if (mapping.getLiteralValue() == null) {
               String source = mapping.getSource();
               String key = Strings.isEmpty(source) ? mapping.getName() : source;
               Object value = map.getValue(key);
               if (logger().isTraceEnabled()) {
                  String valueStr = value instanceof String ? "\"" + value + "\"" : Objects.toString(value, null);
                  logger().trace("{} setObject({}, {}) for key '{}' and mapping '{}'", simpleName, j, valueStr, key, mapping.getName());
               }

               this.setStatementObject(j, mapping.getNameKey(), value);
               j++;
            }
         }
      }
   }

   private void setStatementObject(final int j, final String nameKey, final Object value) throws SQLException {
      if (this.statement == null) {
         throw new AppenderLoggingException("Cannot set a value when the PreparedStatement is null.");
      } else {
         if (value == null) {
            if (this.columnMetaData == null) {
               throw new AppenderLoggingException("Cannot set a value when the column metadata is null.");
            }

            this.statement.setNull(j, this.columnMetaData.get(nameKey).getType());
         } else {
            this.statement.setObject(j, this.truncate(nameKey, value));
         }
      }
   }

   @Override
   protected boolean shutdownInternal() {
      if (this.reconnector != null) {
         this.reconnector.shutdown();
         this.reconnector.interrupt();
         this.reconnector = null;
      }

      return this.commitAndCloseAll();
   }

   @Override
   protected void startupInternal() throws Exception {
   }

   private Object truncate(final String nameKey, Object value) {
      if (value != null && this.factoryData.truncateStrings && this.columnMetaData != null) {
         JdbcDatabaseManager.ResultSetColumnMetaData resultSetColumnMetaData = this.columnMetaData.get(nameKey);
         if (resultSetColumnMetaData != null) {
            if (resultSetColumnMetaData.isStringType()) {
               value = resultSetColumnMetaData.truncate(value.toString());
            }
         } else {
            logger().error("Missing ResultSetColumnMetaData for {}, connection={}, statement={}", nameKey, this.connection, this.statement);
         }
      }

      return value;
   }

   @Override
   protected void writeInternal(final LogEvent event, final Serializable serializable) {
      StringReader reader = null;

      try {
         if (!this.isRunning() || this.isClosed(this.connection) || this.isClosed(this.statement)) {
            throw new AppenderLoggingException(
               "Cannot write logging event; JDBC manager not connected to the database, running=%s, [%s]).", this.isRunning(), this.fieldsToString()
            );
         }

         this.statement.clearParameters();
         if (serializable instanceof MapMessage) {
            this.setFields((MapMessage<?, ?>)serializable);
         }

         int j = 1;
         if (this.factoryData.columnMappings != null) {
            for (ColumnMapping mapping : this.factoryData.columnMappings) {
               if (ThreadContextMap.class.isAssignableFrom(mapping.getType()) || ReadOnlyStringMap.class.isAssignableFrom(mapping.getType())) {
                  this.statement.setObject(j++, event.getContextData().toMap());
               } else if (ThreadContextStack.class.isAssignableFrom(mapping.getType())) {
                  this.statement.setObject(j++, event.getContextStack().asList());
               } else if (Date.class.isAssignableFrom(mapping.getType())) {
                  this.statement.setObject(j++, DateTypeConverter.fromMillis(event.getTimeMillis(), mapping.getType().asSubclass(Date.class)));
               } else {
                  StringLayout layout = mapping.getLayout();
                  if (layout != null) {
                     if (Clob.class.isAssignableFrom(mapping.getType())) {
                        this.statement.setClob(j++, new StringReader(layout.toSerializable(event)));
                     } else if (NClob.class.isAssignableFrom(mapping.getType())) {
                        this.statement.setNClob(j++, new StringReader(layout.toSerializable(event)));
                     } else {
                        Object value = TypeConverters.convert(layout.toSerializable(event), mapping.getType(), null);
                        this.setStatementObject(j++, mapping.getNameKey(), value);
                     }
                  }
               }
            }
         }

         for (ColumnConfig column : this.columnConfigs) {
            if (column.isEventTimestamp()) {
               this.statement.setTimestamp(j++, new Timestamp(event.getTimeMillis()));
            } else if (column.isClob()) {
               reader = new StringReader(column.getLayout().toSerializable(event));
               if (column.isUnicode()) {
                  this.statement.setNClob(j++, reader);
               } else {
                  this.statement.setClob(j++, reader);
               }
            } else if (column.isUnicode()) {
               this.statement.setNString(j++, Objects.toString(this.truncate(column.getColumnNameKey(), column.getLayout().toSerializable(event)), null));
            } else {
               this.statement.setString(j++, Objects.toString(this.truncate(column.getColumnNameKey(), column.getLayout().toSerializable(event)), null));
            }
         }

         if (this.isBuffered() && this.isBatchSupported) {
            logger().debug("addBatch for {}", this.statement);
            this.statement.addBatch();
         } else {
            int executeUpdate = this.statement.executeUpdate();
            logger().debug("executeUpdate = {} for {}", executeUpdate, this.statement);
            if (executeUpdate == 0) {
               throw new AppenderLoggingException("No records inserted in database table for log event in JDBC manager [%s].", this.fieldsToString());
            }
         }
      } catch (SQLException var18) {
         throw new DbAppenderLoggingException(var18, "Failed to insert record for log event in JDBC manager: %s [%s]", var18, this.fieldsToString());
      } finally {
         try {
            if (this.statement != null) {
               this.statement.clearParameters();
            }
         } catch (SQLException var17) {
         }

         Closer.closeSilently(reader);
      }
   }

   @Override
   protected void writeThrough(final LogEvent event, final Serializable serializable) {
      this.connectAndStart();

      try {
         try {
            this.writeInternal(event, serializable);
         } finally {
            this.commitAndClose();
         }
      } catch (DbAppenderLoggingException var13) {
         this.reconnectOn(var13);

         try {
            this.writeInternal(event, serializable);
         } finally {
            this.commitAndClose();
         }
      }
   }

   private static final class FactoryData extends AbstractDatabaseManager.AbstractFactoryData {
      private final ConnectionSource connectionSource;
      private final String tableName;
      private final ColumnConfig[] columnConfigs;
      private final ColumnMapping[] columnMappings;
      private final boolean immediateFail;
      private final boolean retry;
      private final long reconnectIntervalMillis;
      private final boolean truncateStrings;

      protected FactoryData(
         final int bufferSize,
         final Layout<? extends Serializable> layout,
         final ConnectionSource connectionSource,
         final String tableName,
         final ColumnConfig[] columnConfigs,
         final ColumnMapping[] columnMappings,
         final boolean immediateFail,
         final long reconnectIntervalMillis,
         final boolean truncateStrings
      ) {
         super(bufferSize, layout);
         this.connectionSource = connectionSource;
         this.tableName = tableName;
         this.columnConfigs = columnConfigs;
         this.columnMappings = columnMappings;
         this.immediateFail = immediateFail;
         this.retry = reconnectIntervalMillis > 0L;
         this.reconnectIntervalMillis = reconnectIntervalMillis;
         this.truncateStrings = truncateStrings;
      }

      @Override
      public String toString() {
         return String.format(
            "FactoryData [connectionSource=%s, tableName=%s, columnConfigs=%s, columnMappings=%s, immediateFail=%s, retry=%s, reconnectIntervalMillis=%s, truncateStrings=%s]",
            this.connectionSource,
            this.tableName,
            Arrays.toString((Object[])this.columnConfigs),
            Arrays.toString((Object[])this.columnMappings),
            this.immediateFail,
            this.retry,
            this.reconnectIntervalMillis,
            this.truncateStrings
         );
      }
   }

   private static final class JdbcDatabaseManagerFactory implements ManagerFactory<JdbcDatabaseManager, JdbcDatabaseManager.FactoryData> {
      private static final char PARAMETER_MARKER = '?';

      private JdbcDatabaseManagerFactory() {
      }

      public JdbcDatabaseManager createManager(final String name, final JdbcDatabaseManager.FactoryData data) {
         StringBuilder sb = new StringBuilder("insert into ").append(data.tableName).append(" (");
         JdbcDatabaseManager.appendColumnNames("INSERT", data, sb);
         sb.append(") values (");
         int i = 1;
         if (data.columnMappings != null) {
            for (ColumnMapping mapping : data.columnMappings) {
               String mappingName = mapping.getName();
               if (Strings.isNotEmpty(mapping.getLiteralValue())) {
                  JdbcDatabaseManager.logger().trace("Adding INSERT VALUES literal for ColumnMapping[{}]: {}={} ", i, mappingName, mapping.getLiteralValue());
                  sb.append(mapping.getLiteralValue());
               } else if (Strings.isNotEmpty(mapping.getParameter())) {
                  JdbcDatabaseManager.logger().trace("Adding INSERT VALUES parameter for ColumnMapping[{}]: {}={} ", i, mappingName, mapping.getParameter());
                  sb.append(mapping.getParameter());
               } else {
                  JdbcDatabaseManager.logger().trace("Adding INSERT VALUES parameter marker for ColumnMapping[{}]: {}={} ", i, mappingName, '?');
                  sb.append('?');
               }

               sb.append(',');
               i++;
            }
         }

         int columnConfigsLen = data.columnConfigs == null ? 0 : data.columnConfigs.length;
         List<ColumnConfig> columnConfigs = new ArrayList<>(columnConfigsLen);
         if (data.columnConfigs != null) {
            for (ColumnConfig config : data.columnConfigs) {
               if (Strings.isNotEmpty(config.getLiteralValue())) {
                  sb.append(config.getLiteralValue());
               } else {
                  sb.append('?');
                  columnConfigs.add(config);
               }

               sb.append(',');
            }
         }

         sb.setCharAt(sb.length() - 1, ')');
         String sqlStatement = sb.toString();
         return new JdbcDatabaseManager(name, sqlStatement, columnConfigs, data);
      }
   }

   private final class Reconnector extends Log4jThread {
      private final CountDownLatch latch = new CountDownLatch(1);
      private volatile boolean shutdown;

      private Reconnector() {
         super("JdbcDatabaseManager-Reconnector");
      }

      public void latch() {
         try {
            this.latch.await();
         } catch (InterruptedException var2) {
         }
      }

      void reconnect() throws SQLException {
         JdbcDatabaseManager.this.closeResources(false);
         JdbcDatabaseManager.this.connectAndPrepare();
         JdbcDatabaseManager.this.reconnector = null;
         this.shutdown = true;
         JdbcDatabaseManager.logger().debug("Connection reestablished to {}", JdbcDatabaseManager.this.factoryData);
      }

      @Override
      public void run() {
         while (!this.shutdown) {
            try {
               sleep(JdbcDatabaseManager.this.factoryData.reconnectIntervalMillis);
               this.reconnect();
            } catch (SQLException | InterruptedException var5) {
               JdbcDatabaseManager.logger()
                  .debug("Cannot reestablish JDBC connection to {}: {}", JdbcDatabaseManager.this.factoryData, var5.getLocalizedMessage(), var5);
            } finally {
               this.latch.countDown();
            }
         }
      }

      public void shutdown() {
         this.shutdown = true;
      }

      @Override
      public String toString() {
         return String.format("Reconnector [latch=%s, shutdown=%s]", this.latch, this.shutdown);
      }
   }

   private static final class ResultSetColumnMetaData {
      private final String schemaName;
      private final String catalogName;
      private final String tableName;
      private final String name;
      private final String nameKey;
      private final String label;
      private final int displaySize;
      private final int type;
      private final String typeName;
      private final String className;
      private final int precision;
      private final int scale;
      private final boolean isStringType;

      public ResultSetColumnMetaData(final ResultSetMetaData rsMetaData, final int j) throws SQLException {
         this(
            rsMetaData.getSchemaName(j),
            rsMetaData.getCatalogName(j),
            rsMetaData.getTableName(j),
            rsMetaData.getColumnName(j),
            rsMetaData.getColumnLabel(j),
            rsMetaData.getColumnDisplaySize(j),
            rsMetaData.getColumnType(j),
            rsMetaData.getColumnTypeName(j),
            rsMetaData.getColumnClassName(j),
            rsMetaData.getPrecision(j),
            rsMetaData.getScale(j)
         );
      }

      private ResultSetColumnMetaData(
         final String schemaName,
         final String catalogName,
         final String tableName,
         final String name,
         final String label,
         final int displaySize,
         final int type,
         final String typeName,
         final String className,
         final int precision,
         final int scale
      ) {
         this.schemaName = schemaName;
         this.catalogName = catalogName;
         this.tableName = tableName;
         this.name = name;
         this.nameKey = ColumnMapping.toKey(name);
         this.label = label;
         this.displaySize = displaySize;
         this.type = type;
         this.typeName = typeName;
         this.className = className;
         this.precision = precision;
         this.scale = scale;
         this.isStringType = type == 1 || type == -16 || type == -1 || type == -9 || type == 12;
      }

      public String getCatalogName() {
         return this.catalogName;
      }

      public String getClassName() {
         return this.className;
      }

      public int getDisplaySize() {
         return this.displaySize;
      }

      public String getLabel() {
         return this.label;
      }

      public String getName() {
         return this.name;
      }

      public String getNameKey() {
         return this.nameKey;
      }

      public int getPrecision() {
         return this.precision;
      }

      public int getScale() {
         return this.scale;
      }

      public String getSchemaName() {
         return this.schemaName;
      }

      public String getTableName() {
         return this.tableName;
      }

      public int getType() {
         return this.type;
      }

      public String getTypeName() {
         return this.typeName;
      }

      public boolean isStringType() {
         return this.isStringType;
      }

      @Override
      public String toString() {
         return String.format(
            "ColumnMetaData [schemaName=%s, catalogName=%s, tableName=%s, name=%s, nameKey=%s, label=%s, displaySize=%s, type=%s, typeName=%s, className=%s, precision=%s, scale=%s, isStringType=%s]",
            this.schemaName,
            this.catalogName,
            this.tableName,
            this.name,
            this.nameKey,
            this.label,
            this.displaySize,
            this.type,
            this.typeName,
            this.className,
            this.precision,
            this.scale,
            this.isStringType
         );
      }

      public String truncate(final String string) {
         return this.precision > 0 ? Strings.left(string, this.precision) : string;
      }
   }
}
