package org.apache.logging.log4j.core.filter;

import javax.script.SimpleBindings;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginConfiguration;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.script.AbstractScript;
import org.apache.logging.log4j.core.script.ScriptRef;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.ObjectMessage;
import org.apache.logging.log4j.message.SimpleMessage;
import org.apache.logging.log4j.status.StatusLogger;

@Plugin(name = "ScriptFilter", category = "Core", elementType = "filter", printObject = true)
public final class ScriptFilter extends AbstractFilter {
   private static Logger logger = StatusLogger.getLogger();
   private final AbstractScript script;
   private final Configuration configuration;

   private ScriptFilter(final AbstractScript script, final Configuration configuration, final Filter.Result onMatch, final Filter.Result onMismatch) {
      super(onMatch, onMismatch);
      this.script = script;
      this.configuration = configuration;
      if (!(script instanceof ScriptRef)) {
         configuration.getScriptManager().addScript(script);
      }
   }

   @Override
   public Filter.Result filter(
      final org.apache.logging.log4j.core.Logger logger, final Level level, final Marker marker, final String msg, final Object... params
   ) {
      SimpleBindings bindings = new SimpleBindings();
      bindings.put("logger", logger);
      bindings.put("level", level);
      bindings.put("marker", marker);
      bindings.put("message", new SimpleMessage(msg));
      bindings.put("parameters", params);
      bindings.put("throwable", null);
      bindings.putAll(this.configuration.getProperties());
      bindings.put("substitutor", this.configuration.getStrSubstitutor());
      Object object = this.configuration.getScriptManager().execute(this.script.getName(), bindings);
      return object != null && Boolean.TRUE.equals(object) ? this.onMatch : this.onMismatch;
   }

   @Override
   public Filter.Result filter(final org.apache.logging.log4j.core.Logger logger, final Level level, final Marker marker, final Object msg, final Throwable t) {
      SimpleBindings bindings = new SimpleBindings();
      bindings.put("logger", logger);
      bindings.put("level", level);
      bindings.put("marker", marker);
      bindings.put("message", msg instanceof String ? new SimpleMessage((String)msg) : new ObjectMessage(msg));
      bindings.put("parameters", null);
      bindings.put("throwable", t);
      bindings.putAll(this.configuration.getProperties());
      bindings.put("substitutor", this.configuration.getStrSubstitutor());
      Object object = this.configuration.getScriptManager().execute(this.script.getName(), bindings);
      return object != null && Boolean.TRUE.equals(object) ? this.onMatch : this.onMismatch;
   }

   @Override
   public Filter.Result filter(final org.apache.logging.log4j.core.Logger logger, final Level level, final Marker marker, final Message msg, final Throwable t) {
      SimpleBindings bindings = new SimpleBindings();
      bindings.put("logger", logger);
      bindings.put("level", level);
      bindings.put("marker", marker);
      bindings.put("message", msg);
      bindings.put("parameters", null);
      bindings.put("throwable", t);
      bindings.putAll(this.configuration.getProperties());
      bindings.put("substitutor", this.configuration.getStrSubstitutor());
      Object object = this.configuration.getScriptManager().execute(this.script.getName(), bindings);
      return object != null && Boolean.TRUE.equals(object) ? this.onMatch : this.onMismatch;
   }

   @Override
   public Filter.Result filter(final LogEvent event) {
      SimpleBindings bindings = new SimpleBindings();
      bindings.put("logEvent", event);
      bindings.putAll(this.configuration.getProperties());
      bindings.put("substitutor", this.configuration.getStrSubstitutor());
      Object object = this.configuration.getScriptManager().execute(this.script.getName(), bindings);
      return object != null && Boolean.TRUE.equals(object) ? this.onMatch : this.onMismatch;
   }

   @Override
   public String toString() {
      return this.script.getName();
   }

   @PluginFactory
   public static ScriptFilter createFilter(
      @PluginElement("Script") final AbstractScript script,
      @PluginAttribute("onMatch") final Filter.Result match,
      @PluginAttribute("onMismatch") final Filter.Result mismatch,
      @PluginConfiguration final Configuration configuration
   ) {
      if (script == null) {
         LOGGER.error("A Script, ScriptFile or ScriptRef element must be provided for this ScriptFilter");
         return null;
      } else if (script instanceof ScriptRef && configuration.getScriptManager().getScript(script.getName()) == null) {
         logger.error("No script with name {} has been declared.", script.getName());
         return null;
      } else {
         return new ScriptFilter(script, configuration, match, mismatch);
      }
   }
}
