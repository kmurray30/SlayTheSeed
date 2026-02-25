package org.apache.logging.log4j.core.config.builder.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.AbstractConfiguration;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.Reconfigurable;
import org.apache.logging.log4j.core.config.builder.api.Component;
import org.apache.logging.log4j.core.config.plugins.util.PluginManager;
import org.apache.logging.log4j.core.config.plugins.util.PluginType;
import org.apache.logging.log4j.core.config.plugins.util.ResolverUtil;
import org.apache.logging.log4j.core.config.status.StatusConfiguration;
import org.apache.logging.log4j.core.util.Patterns;

public class BuiltConfiguration extends AbstractConfiguration {
   private static final String[] VERBOSE_CLASSES = new String[]{ResolverUtil.class.getName()};
   private final StatusConfiguration statusConfig;
   protected Component rootComponent;
   private Component loggersComponent;
   private Component appendersComponent;
   private Component filtersComponent;
   private Component propertiesComponent;
   private Component customLevelsComponent;
   private Component scriptsComponent;
   private String contentType = "text";

   public BuiltConfiguration(final LoggerContext loggerContext, final ConfigurationSource source, final Component rootComponent) {
      super(loggerContext, source);
      this.statusConfig = new StatusConfiguration().withVerboseClasses(VERBOSE_CLASSES).withStatus(this.getDefaultStatus());

      for (Component component : rootComponent.getComponents()) {
         String var6 = component.getPluginType();
         switch (var6) {
            case "Scripts":
               this.scriptsComponent = component;
               break;
            case "Loggers":
               this.loggersComponent = component;
               break;
            case "Appenders":
               this.appendersComponent = component;
               break;
            case "Filters":
               this.filtersComponent = component;
               break;
            case "Properties":
               this.propertiesComponent = component;
               break;
            case "CustomLevels":
               this.customLevelsComponent = component;
         }
      }

      this.rootComponent = rootComponent;
   }

   @Override
   public void setup() {
      List<Node> children = this.rootNode.getChildren();
      if (this.propertiesComponent.getComponents().size() > 0) {
         children.add(this.convertToNode(this.rootNode, this.propertiesComponent));
      }

      if (this.scriptsComponent.getComponents().size() > 0) {
         children.add(this.convertToNode(this.rootNode, this.scriptsComponent));
      }

      if (this.customLevelsComponent.getComponents().size() > 0) {
         children.add(this.convertToNode(this.rootNode, this.customLevelsComponent));
      }

      children.add(this.convertToNode(this.rootNode, this.loggersComponent));
      children.add(this.convertToNode(this.rootNode, this.appendersComponent));
      if (this.filtersComponent.getComponents().size() > 0) {
         if (this.filtersComponent.getComponents().size() == 1) {
            children.add(this.convertToNode(this.rootNode, this.filtersComponent.getComponents().get(0)));
         } else {
            children.add(this.convertToNode(this.rootNode, this.filtersComponent));
         }
      }

      this.rootComponent = null;
   }

   public String getContentType() {
      return this.contentType;
   }

   public void setContentType(final String contentType) {
      this.contentType = contentType;
   }

   public void createAdvertiser(final String advertiserString, final ConfigurationSource configSource) {
      byte[] buffer = null;

      try {
         if (configSource != null) {
            InputStream is = configSource.getInputStream();
            if (is != null) {
               buffer = toByteArray(is);
            }
         }
      } catch (IOException var5) {
         LOGGER.warn("Unable to read configuration source " + configSource.toString());
      }

      super.createAdvertiser(advertiserString, configSource, buffer, this.contentType);
   }

   public StatusConfiguration getStatusConfiguration() {
      return this.statusConfig;
   }

   public void setPluginPackages(final String packages) {
      this.pluginPackages.addAll(Arrays.asList(packages.split(Patterns.COMMA_SEPARATOR)));
   }

   public void setShutdownHook(final String flag) {
      this.isShutdownHookEnabled = !"disable".equalsIgnoreCase(flag);
   }

   public void setShutdownTimeoutMillis(final long shutdownTimeoutMillis) {
      this.shutdownTimeoutMillis = shutdownTimeoutMillis;
   }

   public void setMonitorInterval(final int intervalSeconds) {
      if (this instanceof Reconfigurable && intervalSeconds > 0) {
         this.initializeWatchers((Reconfigurable)this, this.getConfigurationSource(), intervalSeconds);
      }
   }

   @Override
   public PluginManager getPluginManager() {
      return this.pluginManager;
   }

   protected Node convertToNode(final Node parent, final Component component) {
      String name = component.getPluginType();
      PluginType<?> pluginType = this.pluginManager.getPluginType(name);
      Node node = new Node(parent, name, pluginType);
      node.getAttributes().putAll(component.getAttributes());
      node.setValue(component.getValue());
      List<Node> children = node.getChildren();

      for (Component child : component.getComponents()) {
         children.add(this.convertToNode(node, child));
      }

      return node;
   }
}
