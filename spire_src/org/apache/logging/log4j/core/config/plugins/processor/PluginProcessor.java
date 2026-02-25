package org.apache.logging.log4j.core.config.plugins.processor;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleElementVisitor7;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import javax.tools.Diagnostic.Kind;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAliases;

@SupportedAnnotationTypes("org.apache.logging.log4j.core.config.plugins.*")
public class PluginProcessor extends AbstractProcessor {
   public static final String PLUGIN_CACHE_FILE = "META-INF/org/apache/logging/log4j/core/config/plugins/Log4j2Plugins.dat";
   private final PluginCache pluginCache = new PluginCache();

   @Override
   public SourceVersion getSupportedSourceVersion() {
      return SourceVersion.latest();
   }

   @Override
   public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
      Messager messager = this.processingEnv.getMessager();
      messager.printMessage(Kind.NOTE, "Processing Log4j annotations");

      try {
         Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(Plugin.class);
         if (elements.isEmpty()) {
            messager.printMessage(Kind.NOTE, "No elements to process");
            return false;
         } else {
            this.collectPlugins(elements);
            this.writeCacheFile(elements.toArray(new Element[elements.size()]));
            messager.printMessage(Kind.NOTE, "Annotations processed");
            return true;
         }
      } catch (Exception var5) {
         var5.printStackTrace();
         this.error(var5.getMessage());
         return false;
      }
   }

   private void error(final CharSequence message) {
      this.processingEnv.getMessager().printMessage(Kind.ERROR, message);
   }

   private void collectPlugins(final Iterable<? extends Element> elements) {
      Elements elementUtils = this.processingEnv.getElementUtils();
      ElementVisitor<PluginEntry, Plugin> pluginVisitor = new PluginProcessor.PluginElementVisitor(elementUtils);
      ElementVisitor<Collection<PluginEntry>, Plugin> pluginAliasesVisitor = new PluginProcessor.PluginAliasesElementVisitor(elementUtils);

      for (Element element : elements) {
         Plugin plugin = element.getAnnotation(Plugin.class);
         if (plugin != null) {
            PluginEntry entry = element.accept(pluginVisitor, plugin);
            Map<String, PluginEntry> category = this.pluginCache.getCategory(entry.getCategory());
            category.put(entry.getKey(), entry);

            for (PluginEntry pluginEntry : element.accept(pluginAliasesVisitor, plugin)) {
               category.put(pluginEntry.getKey(), pluginEntry);
            }
         }
      }
   }

   private void writeCacheFile(final Element... elements) throws IOException {
      FileObject fileObject = this.processingEnv
         .getFiler()
         .createResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/org/apache/logging/log4j/core/config/plugins/Log4j2Plugins.dat", elements);

      try (OutputStream out = fileObject.openOutputStream()) {
         this.pluginCache.writeCache(out);
      }
   }

   private static class PluginAliasesElementVisitor extends SimpleElementVisitor7<Collection<PluginEntry>, Plugin> {
      private final Elements elements;

      private PluginAliasesElementVisitor(final Elements elements) {
         super(Collections.emptyList());
         this.elements = elements;
      }

      public Collection<PluginEntry> visitType(final TypeElement e, final Plugin plugin) {
         PluginAliases aliases = e.getAnnotation(PluginAliases.class);
         if (aliases == null) {
            return this.DEFAULT_VALUE;
         } else {
            Collection<PluginEntry> entries = new ArrayList<>(aliases.value().length);

            for (String alias : aliases.value()) {
               PluginEntry entry = new PluginEntry();
               entry.setKey(alias.toLowerCase(Locale.US));
               entry.setClassName(this.elements.getBinaryName(e).toString());
               entry.setName("".equals(plugin.elementType()) ? alias : plugin.elementType());
               entry.setPrintable(plugin.printObject());
               entry.setDefer(plugin.deferChildren());
               entry.setCategory(plugin.category());
               entries.add(entry);
            }

            return entries;
         }
      }
   }

   private static class PluginElementVisitor extends SimpleElementVisitor7<PluginEntry, Plugin> {
      private final Elements elements;

      private PluginElementVisitor(final Elements elements) {
         this.elements = elements;
      }

      public PluginEntry visitType(final TypeElement e, final Plugin plugin) {
         Objects.requireNonNull(plugin, "Plugin annotation is null.");
         PluginEntry entry = new PluginEntry();
         entry.setKey(plugin.name().toLowerCase(Locale.US));
         entry.setClassName(this.elements.getBinaryName(e).toString());
         entry.setName("".equals(plugin.elementType()) ? plugin.name() : plugin.elementType());
         entry.setPrintable(plugin.printObject());
         entry.setDefer(plugin.deferChildren());
         entry.setCategory(plugin.category());
         return entry;
      }
   }
}
