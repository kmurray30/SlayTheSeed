package org.apache.logging.log4j.core.appender.mom.kafka;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractManager;
import org.apache.logging.log4j.core.appender.ManagerFactory;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.util.Log4jThread;

public class KafkaManager extends AbstractManager {
   public static final String DEFAULT_TIMEOUT_MILLIS = "30000";
   static KafkaProducerFactory producerFactory = new DefaultKafkaProducerFactory();
   private final Properties config = new Properties();
   private Producer<byte[], byte[]> producer;
   private final int timeoutMillis;
   private final String topic;
   private final String key;
   private final boolean syncSend;
   private static final KafkaManager.KafkaManagerFactory factory = new KafkaManager.KafkaManagerFactory();

   public KafkaManager(
      final LoggerContext loggerContext, final String name, final String topic, final boolean syncSend, final Property[] properties, final String key
   ) {
      super(loggerContext, name);
      this.topic = Objects.requireNonNull(topic, "topic");
      this.syncSend = syncSend;
      this.config.setProperty("key.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");
      this.config.setProperty("value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");
      this.config.setProperty("batch.size", "0");

      for (Property property : properties) {
         this.config.setProperty(property.getName(), property.getValue());
      }

      this.key = key;
      this.timeoutMillis = Integer.parseInt(this.config.getProperty("timeout.ms", "30000"));
   }

   @Override
   public boolean releaseSub(final long timeout, final TimeUnit timeUnit) {
      if (timeout > 0L) {
         this.closeProducer(timeout, timeUnit);
      } else {
         this.closeProducer(this.timeoutMillis, TimeUnit.MILLISECONDS);
      }

      return true;
   }

   private void closeProducer(final long timeout, final TimeUnit timeUnit) {
      if (this.producer != null) {
         Thread closeThread = new Log4jThread(() -> {
            if (this.producer != null) {
               this.producer.close();
            }
         }, "KafkaManager-CloseThread");
         closeThread.setDaemon(true);
         closeThread.start();

         try {
            closeThread.join(timeUnit.toMillis(timeout));
         } catch (InterruptedException var6) {
            Thread.currentThread().interrupt();
         }
      }
   }

   public void send(final byte[] msg) throws ExecutionException, InterruptedException, TimeoutException {
      if (this.producer != null) {
         byte[] newKey = null;
         if (this.key != null && this.key.contains("${")) {
            newKey = this.getLoggerContext().getConfiguration().getStrSubstitutor().replace(this.key).getBytes(StandardCharsets.UTF_8);
         } else if (this.key != null) {
            newKey = this.key.getBytes(StandardCharsets.UTF_8);
         }

         ProducerRecord<byte[], byte[]> newRecord = new ProducerRecord(this.topic, newKey, msg);
         if (this.syncSend) {
            Future<RecordMetadata> response = this.producer.send(newRecord);
            response.get(this.timeoutMillis, TimeUnit.MILLISECONDS);
         } else {
            this.producer.send(newRecord, (metadata, e) -> {
               if (e != null) {
                  LOGGER.error("Unable to write to Kafka in appender [" + this.getName() + "]", (Throwable)e);
               }
            });
         }
      }
   }

   public void startup() {
      if (this.producer == null) {
         this.producer = producerFactory.newKafkaProducer(this.config);
      }
   }

   public String getTopic() {
      return this.topic;
   }

   public static KafkaManager getManager(
      final LoggerContext loggerContext, final String name, final String topic, final boolean syncSend, final Property[] properties, final String key
   ) {
      StringBuilder sb = new StringBuilder(name);
      sb.append(" ").append(topic).append(" ").append(syncSend + "");

      for (Property prop : properties) {
         sb.append(" ").append(prop.getName()).append("=").append(prop.getValue());
      }

      return getManager(sb.toString(), factory, new KafkaManager.FactoryData(loggerContext, topic, syncSend, properties, key));
   }

   private static class FactoryData {
      private final LoggerContext loggerContext;
      private final String topic;
      private final boolean syncSend;
      private final Property[] properties;
      private final String key;

      public FactoryData(final LoggerContext loggerContext, final String topic, final boolean syncSend, final Property[] properties, final String key) {
         this.loggerContext = loggerContext;
         this.topic = topic;
         this.syncSend = syncSend;
         this.properties = properties;
         this.key = key;
      }
   }

   private static class KafkaManagerFactory implements ManagerFactory<KafkaManager, KafkaManager.FactoryData> {
      private KafkaManagerFactory() {
      }

      public KafkaManager createManager(String name, KafkaManager.FactoryData data) {
         return new KafkaManager(data.loggerContext, name, data.topic, data.syncSend, data.properties, data.key);
      }
   }
}
