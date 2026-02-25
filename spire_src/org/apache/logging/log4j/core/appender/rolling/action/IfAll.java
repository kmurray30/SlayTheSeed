package org.apache.logging.log4j.core.appender.rolling.action;

import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Objects;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

@Plugin(name = "IfAll", category = "Core", printObject = true)
public final class IfAll implements PathCondition {
   private final PathCondition[] components;

   private IfAll(final PathCondition... filters) {
      this.components = Objects.requireNonNull(filters, "filters");
   }

   public PathCondition[] getDeleteFilters() {
      return this.components;
   }

   @Override
   public boolean accept(final Path baseDir, final Path relativePath, final BasicFileAttributes attrs) {
      return this.components != null && this.components.length != 0 ? accept(this.components, baseDir, relativePath, attrs) : false;
   }

   public static boolean accept(final PathCondition[] list, final Path baseDir, final Path relativePath, final BasicFileAttributes attrs) {
      for (PathCondition component : list) {
         if (!component.accept(baseDir, relativePath, attrs)) {
            return false;
         }
      }

      return true;
   }

   @Override
   public void beforeFileTreeWalk() {
      beforeFileTreeWalk(this.components);
   }

   public static void beforeFileTreeWalk(final PathCondition[] nestedConditions) {
      for (PathCondition condition : nestedConditions) {
         condition.beforeFileTreeWalk();
      }
   }

   @PluginFactory
   public static IfAll createAndCondition(@PluginElement("PathConditions") final PathCondition... components) {
      return new IfAll(components);
   }

   @Override
   public String toString() {
      return "IfAll" + Arrays.toString((Object[])this.components);
   }
}
