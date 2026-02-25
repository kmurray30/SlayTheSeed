package org.apache.logging.log4j.core.script;

import java.io.File;
import java.io.Serializable;
import java.nio.file.Path;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.util.FileWatcher;
import org.apache.logging.log4j.core.util.WatchManager;
import org.apache.logging.log4j.status.StatusLogger;

public class ScriptManager implements FileWatcher, Serializable {
   private static final long serialVersionUID = -2534169384971965196L;
   private static final String KEY_THREADING = "THREADING";
   private static final Logger logger = StatusLogger.getLogger();
   private final Configuration configuration;
   private final ScriptEngineManager manager = new ScriptEngineManager();
   private final ConcurrentMap<String, ScriptManager.ScriptRunner> scriptRunners = new ConcurrentHashMap<>();
   private final String languages;
   private final WatchManager watchManager;

   public ScriptManager(final Configuration configuration, final WatchManager watchManager) {
      this.configuration = configuration;
      this.watchManager = watchManager;
      List<ScriptEngineFactory> factories = this.manager.getEngineFactories();
      if (logger.isDebugEnabled()) {
         StringBuilder sb = new StringBuilder();
         int factorySize = factories.size();
         logger.debug("Installed {} script engine{}", factorySize, factorySize != 1 ? "s" : "");

         for (ScriptEngineFactory factory : factories) {
            String threading = Objects.toString(factory.getParameter("THREADING"), null);
            if (threading == null) {
               threading = "Not Thread Safe";
            }

            StringBuilder names = new StringBuilder();
            List<String> languageNames = factory.getNames();

            for (String name : languageNames) {
               if (names.length() > 0) {
                  names.append(", ");
               }

               names.append(name);
            }

            if (sb.length() > 0) {
               sb.append(", ");
            }

            sb.append((CharSequence)names);
            boolean compiled = factory.getScriptEngine() instanceof Compilable;
            logger.debug(
               "{} version: {}, language: {}, threading: {}, compile: {}, names: {}, factory class: {}",
               factory.getEngineName(),
               factory.getEngineVersion(),
               factory.getLanguageName(),
               threading,
               compiled,
               languageNames,
               factory.getClass().getName()
            );
         }

         this.languages = sb.toString();
      } else {
         StringBuilder names = new StringBuilder();

         for (ScriptEngineFactory factory : factories) {
            for (String name : factory.getNames()) {
               if (names.length() > 0) {
                  names.append(", ");
               }

               names.append(name);
            }
         }

         this.languages = names.toString();
      }
   }

   public void addScript(final AbstractScript script) {
      ScriptEngine engine = this.manager.getEngineByName(script.getLanguage());
      if (engine == null) {
         logger.error("No ScriptEngine found for language " + script.getLanguage() + ". Available languages are: " + this.languages);
      } else {
         if (engine.getFactory().getParameter("THREADING") == null) {
            this.scriptRunners.put(script.getName(), new ScriptManager.ThreadLocalScriptRunner(script));
         } else {
            this.scriptRunners.put(script.getName(), new ScriptManager.MainScriptRunner(engine, script));
         }

         if (script instanceof ScriptFile) {
            ScriptFile scriptFile = (ScriptFile)script;
            Path path = scriptFile.getPath();
            if (scriptFile.isWatched() && path != null) {
               this.watchManager.watchFile(path.toFile(), this);
            }
         }
      }
   }

   public Bindings createBindings(final AbstractScript script) {
      return this.getScriptRunner(script).createBindings();
   }

   public AbstractScript getScript(final String name) {
      ScriptManager.ScriptRunner runner = this.scriptRunners.get(name);
      return runner != null ? runner.getScript() : null;
   }

   @Override
   public void fileModified(final File file) {
      ScriptManager.ScriptRunner runner = this.scriptRunners.get(file.toString());
      if (runner == null) {
         logger.info("{} is not a running script", file.getName());
      } else {
         ScriptEngine engine = runner.getScriptEngine();
         AbstractScript script = runner.getScript();
         if (engine.getFactory().getParameter("THREADING") == null) {
            this.scriptRunners.put(script.getName(), new ScriptManager.ThreadLocalScriptRunner(script));
         } else {
            this.scriptRunners.put(script.getName(), new ScriptManager.MainScriptRunner(engine, script));
         }
      }
   }

   public Object execute(final String name, final Bindings bindings) {
      ScriptManager.ScriptRunner scriptRunner = this.scriptRunners.get(name);
      if (scriptRunner == null) {
         logger.warn("No script named {} could be found", name);
         return null;
      } else {
         return AccessController.doPrivileged((PrivilegedAction)(() -> scriptRunner.execute(bindings)));
      }
   }

   private ScriptManager.ScriptRunner getScriptRunner(final AbstractScript script) {
      return this.scriptRunners.get(script.getName());
   }

   private abstract class AbstractScriptRunner implements ScriptManager.ScriptRunner {
      private static final String KEY_STATUS_LOGGER = "statusLogger";
      private static final String KEY_CONFIGURATION = "configuration";

      private AbstractScriptRunner() {
      }

      @Override
      public Bindings createBindings() {
         SimpleBindings bindings = new SimpleBindings();
         bindings.put("configuration", ScriptManager.this.configuration);
         bindings.put("statusLogger", ScriptManager.logger);
         return bindings;
      }
   }

   private class MainScriptRunner extends ScriptManager.AbstractScriptRunner {
      private final AbstractScript script;
      private final CompiledScript compiledScript;
      private final ScriptEngine scriptEngine;

      public MainScriptRunner(final ScriptEngine scriptEngine, final AbstractScript script) {
         this.script = script;
         this.scriptEngine = scriptEngine;
         CompiledScript compiled = null;
         if (scriptEngine instanceof Compilable) {
            ScriptManager.logger.debug("Script {} is compilable", script.getName());
            compiled = AccessController.doPrivileged((PrivilegedAction<CompiledScript>)(() -> {
               try {
                  return ((Compilable)scriptEngine).compile(script.getScriptText());
               } catch (Throwable var3) {
                  ScriptManager.logger.warn("Error compiling script", var3);
                  return null;
               }
            }));
         }

         this.compiledScript = compiled;
      }

      @Override
      public ScriptEngine getScriptEngine() {
         return this.scriptEngine;
      }

      @Override
      public Object execute(final Bindings bindings) {
         if (this.compiledScript != null) {
            try {
               return this.compiledScript.eval(bindings);
            } catch (ScriptException var3) {
               ScriptManager.logger.error("Error running script " + this.script.getName(), (Throwable)var3);
               return null;
            }
         } else {
            try {
               return this.scriptEngine.eval(this.script.getScriptText(), bindings);
            } catch (ScriptException var4) {
               ScriptManager.logger.error("Error running script " + this.script.getName(), (Throwable)var4);
               return null;
            }
         }
      }

      @Override
      public AbstractScript getScript() {
         return this.script;
      }
   }

   private interface ScriptRunner {
      Bindings createBindings();

      Object execute(Bindings bindings);

      AbstractScript getScript();

      ScriptEngine getScriptEngine();
   }

   private class ThreadLocalScriptRunner extends ScriptManager.AbstractScriptRunner {
      private final AbstractScript script;
      private final ThreadLocal<ScriptManager.MainScriptRunner> runners = new ThreadLocal<ScriptManager.MainScriptRunner>() {
         protected ScriptManager.MainScriptRunner initialValue() {
            ScriptEngine engine = ScriptManager.this.manager.getEngineByName(ThreadLocalScriptRunner.this.script.getLanguage());
            return ScriptManager.this.new MainScriptRunner(engine, ThreadLocalScriptRunner.this.script);
         }
      };

      public ThreadLocalScriptRunner(final AbstractScript script) {
         this.script = script;
      }

      @Override
      public Object execute(final Bindings bindings) {
         return this.runners.get().execute(bindings);
      }

      @Override
      public AbstractScript getScript() {
         return this.script;
      }

      @Override
      public ScriptEngine getScriptEngine() {
         return this.runners.get().getScriptEngine();
      }
   }
}
