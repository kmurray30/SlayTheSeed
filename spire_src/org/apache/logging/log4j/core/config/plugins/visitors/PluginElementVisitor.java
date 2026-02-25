package org.apache.logging.log4j.core.config.plugins.visitors;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.util.PluginType;

public class PluginElementVisitor extends AbstractPluginVisitor<PluginElement> {
   public PluginElementVisitor() {
      super(PluginElement.class);
   }

   @Override
   public Object visit(final Configuration configuration, final Node node, final LogEvent event, final StringBuilder log) {
      String name = this.annotation.value();
      if (!this.conversionType.isArray()) {
         Node namedNode = this.findNamedNode(name, node.getChildren());
         if (namedNode == null) {
            log.append(name).append("=null");
            return null;
         } else {
            log.append(namedNode.getName()).append('(').append(namedNode.toString()).append(')');
            node.getChildren().remove(namedNode);
            return namedNode.getObject();
         }
      } else {
         this.setConversionType(this.conversionType.getComponentType());
         List<Object> values = new ArrayList<>();
         Collection<Node> used = new ArrayList<>();
         log.append("={");
         boolean first = true;

         for (Node child : node.getChildren()) {
            PluginType<?> childType = child.getType();
            if (name.equalsIgnoreCase(childType.getElementName()) || this.conversionType.isAssignableFrom(childType.getPluginClass())) {
               if (!first) {
                  log.append(", ");
               }

               first = false;
               used.add(child);
               Object childObject = child.getObject();
               if (childObject == null) {
                  LOGGER.error("Null object returned for {} in {}.", child.getName(), node.getName());
               } else {
                  if (childObject.getClass().isArray()) {
                     log.append(Arrays.toString((Object[])childObject)).append('}');
                     node.getChildren().removeAll(used);
                     return childObject;
                  }

                  log.append(child.toString());
                  values.add(childObject);
               }
            }
         }

         log.append('}');
         if (!values.isEmpty() && !this.conversionType.isAssignableFrom(values.get(0).getClass())) {
            LOGGER.error(
               "Attempted to assign attribute {} to list of type {} which is incompatible with {}.", name, values.get(0).getClass(), this.conversionType
            );
            return null;
         } else {
            node.getChildren().removeAll(used);
            Object[] array = (Object[])Array.newInstance(this.conversionType, values.size());

            for (int i = 0; i < array.length; i++) {
               array[i] = values.get(i);
            }

            return array;
         }
      }
   }

   private Node findNamedNode(final String name, final Iterable<Node> children) {
      for (Node child : children) {
         PluginType<?> childType = child.getType();
         if (childType == null) {
         }

         if (name.equalsIgnoreCase(childType.getElementName()) || this.conversionType.isAssignableFrom(childType.getPluginClass())) {
            return child;
         }
      }

      return null;
   }
}
