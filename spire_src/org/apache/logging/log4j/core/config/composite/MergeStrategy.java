package org.apache.logging.log4j.core.config.composite;

import org.apache.logging.log4j.core.config.AbstractConfiguration;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.plugins.util.PluginManager;

public interface MergeStrategy {
   void mergeRootProperties(Node rootNode, AbstractConfiguration configuration);

   void mergConfigurations(Node target, Node source, PluginManager pluginManager);
}
