package org.apache.logging.log4j.core.script;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.util.ExtensionLanguageMapping;
import org.apache.logging.log4j.core.util.FileUtils;
import org.apache.logging.log4j.core.util.IOUtils;
import org.apache.logging.log4j.core.util.NetUtils;

@Plugin(name = "ScriptFile", category = "Core", printObject = true)
public class ScriptFile extends AbstractScript {
   private final Path filePath;
   private final boolean isWatched;

   public ScriptFile(final String name, final Path filePath, final String language, final boolean isWatched, final String scriptText) {
      super(name, language, scriptText);
      this.filePath = filePath;
      this.isWatched = isWatched;
   }

   public Path getPath() {
      return this.filePath;
   }

   public boolean isWatched() {
      return this.isWatched;
   }

   @PluginFactory
   public static ScriptFile createScript(
      @PluginAttribute("name") String name,
      @PluginAttribute("language") String language,
      @PluginAttribute("path") final String filePathOrUri,
      @PluginAttribute("isWatched") final Boolean isWatched,
      @PluginAttribute("charset") final Charset charset
   ) {
      if (filePathOrUri == null) {
         LOGGER.error("No script path provided for ScriptFile");
         return null;
      } else {
         if (name == null) {
            name = filePathOrUri;
         }

         URI uri = NetUtils.toURI(filePathOrUri);
         File file = FileUtils.fileFromUri(uri);
         if (language == null && file != null) {
            String fileExtension = FileUtils.getFileExtension(file);
            if (fileExtension != null) {
               ExtensionLanguageMapping mapping = ExtensionLanguageMapping.getByExtension(fileExtension);
               if (mapping != null) {
                  language = mapping.getLanguage();
               }
            }
         }

         if (language == null) {
            LOGGER.info("No script language supplied, defaulting to {}", "JavaScript");
            language = "JavaScript";
         }

         Charset actualCharset = charset == null ? Charset.defaultCharset() : charset;

         String scriptText;
         try (Reader reader = new InputStreamReader((InputStream)(file != null ? new FileInputStream(file) : uri.toURL().openStream()), actualCharset)) {
            scriptText = IOUtils.toString(reader);
         } catch (IOException var22) {
            LOGGER.error("{}: language={}, path={}, actualCharset={}", var22.getClass().getSimpleName(), language, filePathOrUri, actualCharset);
            return null;
         }

         Path path = file != null ? Paths.get(file.toURI()) : Paths.get(uri);
         if (path == null) {
            LOGGER.error("Unable to convert {} to a Path", uri.toString());
            return null;
         } else {
            return new ScriptFile(name, path, language, isWatched == null ? Boolean.FALSE : isWatched, scriptText);
         }
      }
   }

   @Override
   public String toString() {
      StringBuilder sb = new StringBuilder();
      if (!this.getName().equals(this.filePath.toString())) {
         sb.append("name=").append(this.getName()).append(", ");
      }

      sb.append("path=").append(this.filePath);
      if (this.getLanguage() != null) {
         sb.append(", language=").append(this.getLanguage());
      }

      sb.append(", isWatched=").append(this.isWatched);
      return sb.toString();
   }
}
