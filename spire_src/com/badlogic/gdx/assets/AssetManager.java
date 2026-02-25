package com.badlogic.gdx.assets;

import com.badlogic.gdx.assets.loaders.AssetLoader;
import com.badlogic.gdx.assets.loaders.BitmapFontLoader;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.assets.loaders.I18NBundleLoader;
import com.badlogic.gdx.assets.loaders.MusicLoader;
import com.badlogic.gdx.assets.loaders.ParticleEffectLoader;
import com.badlogic.gdx.assets.loaders.PixmapLoader;
import com.badlogic.gdx.assets.loaders.ShaderProgramLoader;
import com.badlogic.gdx.assets.loaders.SkinLoader;
import com.badlogic.gdx.assets.loaders.SoundLoader;
import com.badlogic.gdx.assets.loaders.TextureAtlasLoader;
import com.badlogic.gdx.assets.loaders.TextureLoader;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.ParticleEffect;
import com.badlogic.gdx.graphics.g2d.PolygonRegion;
import com.badlogic.gdx.graphics.g2d.PolygonRegionLoader;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.loader.G3dModelLoader;
import com.badlogic.gdx.graphics.g3d.loader.ObjLoader;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.I18NBundle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.Logger;
import com.badlogic.gdx.utils.ObjectIntMap;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;
import com.badlogic.gdx.utils.TimeUtils;
import com.badlogic.gdx.utils.UBJsonReader;
import com.badlogic.gdx.utils.async.AsyncExecutor;
import com.badlogic.gdx.utils.async.ThreadUtils;
import com.badlogic.gdx.utils.reflect.ClassReflection;
import java.util.Stack;

public class AssetManager implements Disposable {
   final ObjectMap<Class, ObjectMap<String, RefCountedContainer>> assets = new ObjectMap<>();
   final ObjectMap<String, Class> assetTypes = new ObjectMap<>();
   final ObjectMap<String, Array<String>> assetDependencies = new ObjectMap<>();
   final ObjectSet<String> injected = new ObjectSet<>();
   final ObjectMap<Class, ObjectMap<String, AssetLoader>> loaders = new ObjectMap<>();
   final Array<AssetDescriptor> loadQueue = new Array<>();
   final AsyncExecutor executor;
   final Stack<AssetLoadingTask> tasks = new Stack<>();
   AssetErrorListener listener = null;
   int loaded = 0;
   int toLoad = 0;
   int peakTasks = 0;
   final FileHandleResolver resolver;
   Logger log = new Logger("AssetManager", 0);

   public AssetManager() {
      this(new InternalFileHandleResolver());
   }

   public AssetManager(FileHandleResolver resolver) {
      this(resolver, true);
   }

   public AssetManager(FileHandleResolver resolver, boolean defaultLoaders) {
      this.resolver = resolver;
      if (defaultLoaders) {
         this.setLoader(BitmapFont.class, new BitmapFontLoader(resolver));
         this.setLoader(Music.class, new MusicLoader(resolver));
         this.setLoader(Pixmap.class, new PixmapLoader(resolver));
         this.setLoader(Sound.class, new SoundLoader(resolver));
         this.setLoader(TextureAtlas.class, new TextureAtlasLoader(resolver));
         this.setLoader(Texture.class, new TextureLoader(resolver));
         this.setLoader(Skin.class, new SkinLoader(resolver));
         this.setLoader(ParticleEffect.class, new ParticleEffectLoader(resolver));
         this.setLoader(
            com.badlogic.gdx.graphics.g3d.particles.ParticleEffect.class, new com.badlogic.gdx.graphics.g3d.particles.ParticleEffectLoader(resolver)
         );
         this.setLoader(PolygonRegion.class, new PolygonRegionLoader(resolver));
         this.setLoader(I18NBundle.class, new I18NBundleLoader(resolver));
         this.setLoader(Model.class, ".g3dj", new G3dModelLoader(new JsonReader(), resolver));
         this.setLoader(Model.class, ".g3db", new G3dModelLoader(new UBJsonReader(), resolver));
         this.setLoader(Model.class, ".obj", new ObjLoader(resolver));
         this.setLoader(ShaderProgram.class, new ShaderProgramLoader(resolver));
      }

      this.executor = new AsyncExecutor(1);
   }

   public FileHandleResolver getFileHandleResolver() {
      return this.resolver;
   }

   public synchronized <T> T get(String fileName) {
      Class<T> type = this.assetTypes.get(fileName);
      if (type == null) {
         throw new GdxRuntimeException("Asset not loaded: " + fileName);
      } else {
         ObjectMap<String, RefCountedContainer> assetsByType = this.assets.get(type);
         if (assetsByType == null) {
            throw new GdxRuntimeException("Asset not loaded: " + fileName);
         } else {
            RefCountedContainer assetContainer = assetsByType.get(fileName);
            if (assetContainer == null) {
               throw new GdxRuntimeException("Asset not loaded: " + fileName);
            } else {
               T asset = assetContainer.getObject(type);
               if (asset == null) {
                  throw new GdxRuntimeException("Asset not loaded: " + fileName);
               } else {
                  return asset;
               }
            }
         }
      }
   }

   public synchronized <T> T get(String fileName, Class<T> type) {
      ObjectMap<String, RefCountedContainer> assetsByType = this.assets.get(type);
      if (assetsByType == null) {
         throw new GdxRuntimeException("Asset not loaded: " + fileName);
      } else {
         RefCountedContainer assetContainer = assetsByType.get(fileName);
         if (assetContainer == null) {
            throw new GdxRuntimeException("Asset not loaded: " + fileName);
         } else {
            T asset = assetContainer.getObject(type);
            if (asset == null) {
               throw new GdxRuntimeException("Asset not loaded: " + fileName);
            } else {
               return asset;
            }
         }
      }
   }

   public synchronized <T> Array<T> getAll(Class<T> type, Array<T> out) {
      ObjectMap<String, RefCountedContainer> assetsByType = this.assets.get(type);
      if (assetsByType != null) {
         for (ObjectMap.Entry<String, RefCountedContainer> asset : assetsByType.entries()) {
            out.add(asset.value.getObject(type));
         }
      }

      return out;
   }

   public synchronized <T> T get(AssetDescriptor<T> assetDescriptor) {
      return this.get(assetDescriptor.fileName, assetDescriptor.type);
   }

   public synchronized void unload(String fileName) {
      if (this.tasks.size() > 0) {
         AssetLoadingTask currAsset = this.tasks.firstElement();
         if (currAsset.assetDesc.fileName.equals(fileName)) {
            currAsset.cancel = true;
            this.log.debug("Unload (from tasks): " + fileName);
            return;
         }
      }

      int foundIndex = -1;

      for (int i = 0; i < this.loadQueue.size; i++) {
         if (this.loadQueue.get(i).fileName.equals(fileName)) {
            foundIndex = i;
            break;
         }
      }

      if (foundIndex != -1) {
         this.toLoad--;
         this.loadQueue.removeIndex(foundIndex);
         this.log.debug("Unload (from queue): " + fileName);
      } else {
         Class type = this.assetTypes.get(fileName);
         if (type == null) {
            throw new GdxRuntimeException("Asset not loaded: " + fileName);
         } else {
            RefCountedContainer assetRef = this.assets.get(type).get(fileName);
            assetRef.decRefCount();
            if (assetRef.getRefCount() <= 0) {
               this.log.debug("Unload (dispose): " + fileName);
               if (assetRef.getObject(Object.class) instanceof Disposable) {
                  ((Disposable)assetRef.getObject(Object.class)).dispose();
               }

               this.assetTypes.remove(fileName);
               this.assets.get(type).remove(fileName);
            } else {
               this.log.debug("Unload (decrement): " + fileName);
            }

            Array<String> dependencies = this.assetDependencies.get(fileName);
            if (dependencies != null) {
               for (String dependency : dependencies) {
                  if (this.isLoaded(dependency)) {
                     this.unload(dependency);
                  }
               }
            }

            if (assetRef.getRefCount() <= 0) {
               this.assetDependencies.remove(fileName);
            }
         }
      }
   }

   public synchronized <T> boolean containsAsset(T asset) {
      ObjectMap<String, RefCountedContainer> typedAssets = this.assets.get(asset.getClass());
      if (typedAssets == null) {
         return false;
      } else {
         for (String fileName : typedAssets.keys()) {
            T otherAsset = typedAssets.get(fileName).getObject((Class<T>)Object.class);
            if (otherAsset == asset || asset.equals(otherAsset)) {
               return true;
            }
         }

         return false;
      }
   }

   public synchronized <T> String getAssetFileName(T asset) {
      for (Class assetType : this.assets.keys()) {
         ObjectMap<String, RefCountedContainer> typedAssets = this.assets.get(assetType);

         for (String fileName : typedAssets.keys()) {
            T otherAsset = typedAssets.get(fileName).getObject((Class<T>)Object.class);
            if (otherAsset == asset || asset.equals(otherAsset)) {
               return fileName;
            }
         }
      }

      return null;
   }

   public synchronized boolean isLoaded(String fileName) {
      return fileName == null ? false : this.assetTypes.containsKey(fileName);
   }

   public synchronized boolean isLoaded(String fileName, Class type) {
      ObjectMap<String, RefCountedContainer> assetsByType = this.assets.get(type);
      if (assetsByType == null) {
         return false;
      } else {
         RefCountedContainer assetContainer = assetsByType.get(fileName);
         return assetContainer == null ? false : assetContainer.getObject(type) != null;
      }
   }

   public <T> AssetLoader getLoader(Class<T> type) {
      return this.getLoader(type, null);
   }

   public <T> AssetLoader getLoader(Class<T> type, String fileName) {
      ObjectMap<String, AssetLoader> loaders = this.loaders.get(type);
      if (loaders != null && loaders.size >= 1) {
         if (fileName == null) {
            return loaders.get("");
         } else {
            AssetLoader result = null;
            int l = -1;

            for (ObjectMap.Entry<String, AssetLoader> entry : loaders.entries()) {
               if (entry.key.length() > l && fileName.endsWith(entry.key)) {
                  result = entry.value;
                  l = entry.key.length();
               }
            }

            return result;
         }
      } else {
         return null;
      }
   }

   public synchronized <T> void load(String fileName, Class<T> type) {
      this.load(fileName, type, null);
   }

   public synchronized <T> void load(String fileName, Class<T> type, AssetLoaderParameters<T> parameter) {
      AssetLoader loader = this.getLoader(type, fileName);
      if (loader == null) {
         throw new GdxRuntimeException("No loader for type: " + ClassReflection.getSimpleName(type));
      } else {
         if (this.loadQueue.size == 0) {
            this.loaded = 0;
            this.toLoad = 0;
            this.peakTasks = 0;
         }

         for (int i = 0; i < this.loadQueue.size; i++) {
            AssetDescriptor desc = this.loadQueue.get(i);
            if (desc.fileName.equals(fileName) && !desc.type.equals(type)) {
               throw new GdxRuntimeException(
                  "Asset with name '"
                     + fileName
                     + "' already in preload queue, but has different type (expected: "
                     + ClassReflection.getSimpleName(type)
                     + ", found: "
                     + ClassReflection.getSimpleName(desc.type)
                     + ")"
               );
            }
         }

         for (int ix = 0; ix < this.tasks.size(); ix++) {
            AssetDescriptor desc = this.tasks.get(ix).assetDesc;
            if (desc.fileName.equals(fileName) && !desc.type.equals(type)) {
               throw new GdxRuntimeException(
                  "Asset with name '"
                     + fileName
                     + "' already in task list, but has different type (expected: "
                     + ClassReflection.getSimpleName(type)
                     + ", found: "
                     + ClassReflection.getSimpleName(desc.type)
                     + ")"
               );
            }
         }

         Class otherType = this.assetTypes.get(fileName);
         if (otherType != null && !otherType.equals(type)) {
            throw new GdxRuntimeException(
               "Asset with name '"
                  + fileName
                  + "' already loaded, but has different type (expected: "
                  + ClassReflection.getSimpleName(type)
                  + ", found: "
                  + ClassReflection.getSimpleName(otherType)
                  + ")"
            );
         } else {
            this.toLoad++;
            AssetDescriptor assetDesc = new AssetDescriptor<>(fileName, type, parameter);
            this.loadQueue.add(assetDesc);
            this.log.debug("Queued: " + assetDesc);
         }
      }
   }

   public synchronized void load(AssetDescriptor desc) {
      this.load(desc.fileName, desc.type, desc.params);
   }

   public synchronized boolean update() {
      try {
         if (this.tasks.size() == 0) {
            while (this.loadQueue.size != 0 && this.tasks.size() == 0) {
               this.nextTask();
            }

            if (this.tasks.size() == 0) {
               return true;
            }
         }

         return this.updateTask() && this.loadQueue.size == 0 && this.tasks.size() == 0;
      } catch (Throwable var2) {
         this.handleTaskError(var2);
         return this.loadQueue.size == 0;
      }
   }

   public boolean update(int millis) {
      long endTime = TimeUtils.millis() + millis;

      while (true) {
         boolean done = this.update();
         if (done || TimeUtils.millis() > endTime) {
            return done;
         }

         ThreadUtils.yield();
      }
   }

   public void finishLoading() {
      this.log.debug("Waiting for loading to complete...");

      while (!this.update()) {
         ThreadUtils.yield();
      }

      this.log.debug("Loading complete.");
   }

   public void finishLoadingAsset(String fileName) {
      this.log.debug("Waiting for asset to be loaded: " + fileName);

      while (!this.isLoaded(fileName)) {
         this.update();
         ThreadUtils.yield();
      }

      this.log.debug("Asset loaded: " + fileName);
   }

   synchronized void injectDependencies(String parentAssetFilename, Array<AssetDescriptor> dependendAssetDescs) {
      ObjectSet<String> injected = this.injected;

      for (AssetDescriptor desc : dependendAssetDescs) {
         if (!injected.contains(desc.fileName)) {
            injected.add(desc.fileName);
            this.injectDependency(parentAssetFilename, desc);
         }
      }

      injected.clear();
   }

   private synchronized void injectDependency(String parentAssetFilename, AssetDescriptor dependendAssetDesc) {
      Array<String> dependencies = this.assetDependencies.get(parentAssetFilename);
      if (dependencies == null) {
         dependencies = new Array<>();
         this.assetDependencies.put(parentAssetFilename, dependencies);
      }

      dependencies.add(dependendAssetDesc.fileName);
      if (this.isLoaded(dependendAssetDesc.fileName)) {
         this.log.debug("Dependency already loaded: " + dependendAssetDesc);
         Class type = this.assetTypes.get(dependendAssetDesc.fileName);
         RefCountedContainer assetRef = this.assets.get(type).get(dependendAssetDesc.fileName);
         assetRef.incRefCount();
         this.incrementRefCountedDependencies(dependendAssetDesc.fileName);
      } else {
         this.log.info("Loading dependency: " + dependendAssetDesc);
         this.addTask(dependendAssetDesc);
      }
   }

   private void nextTask() {
      AssetDescriptor assetDesc = this.loadQueue.removeIndex(0);
      if (this.isLoaded(assetDesc.fileName)) {
         this.log.debug("Already loaded: " + assetDesc);
         Class type = this.assetTypes.get(assetDesc.fileName);
         RefCountedContainer assetRef = this.assets.get(type).get(assetDesc.fileName);
         assetRef.incRefCount();
         this.incrementRefCountedDependencies(assetDesc.fileName);
         if (assetDesc.params != null && assetDesc.params.loadedCallback != null) {
            assetDesc.params.loadedCallback.finishedLoading(this, assetDesc.fileName, assetDesc.type);
         }

         this.loaded++;
      } else {
         this.log.info("Loading: " + assetDesc);
         this.addTask(assetDesc);
      }
   }

   private void addTask(AssetDescriptor assetDesc) {
      AssetLoader loader = this.getLoader(assetDesc.type, assetDesc.fileName);
      if (loader == null) {
         throw new GdxRuntimeException("No loader for type: " + ClassReflection.getSimpleName(assetDesc.type));
      } else {
         this.tasks.push(new AssetLoadingTask(this, assetDesc, loader, this.executor));
         this.peakTasks++;
      }
   }

   protected <T> void addAsset(String fileName, Class<T> type, T asset) {
      this.assetTypes.put(fileName, type);
      ObjectMap<String, RefCountedContainer> typeToAssets = this.assets.get(type);
      if (typeToAssets == null) {
         typeToAssets = new ObjectMap<>();
         this.assets.put(type, typeToAssets);
      }

      typeToAssets.put(fileName, new RefCountedContainer(asset));
   }

   private boolean updateTask() {
      AssetLoadingTask task = this.tasks.peek();
      boolean complete = true;

      try {
         complete = task.cancel || task.update();
      } catch (RuntimeException var5) {
         task.cancel = true;
         this.taskFailed(task.assetDesc, var5);
      }

      if (complete) {
         if (this.tasks.size() == 1) {
            this.loaded++;
            this.peakTasks = 0;
         }

         this.tasks.pop();
         if (task.cancel) {
            return true;
         } else {
            this.addAsset(task.assetDesc.fileName, task.assetDesc.type, task.getAsset());
            if (task.assetDesc.params != null && task.assetDesc.params.loadedCallback != null) {
               task.assetDesc.params.loadedCallback.finishedLoading(this, task.assetDesc.fileName, task.assetDesc.type);
            }

            long endTime = TimeUtils.nanoTime();
            this.log.debug("Loaded: " + (float)(endTime - task.startTime) / 1000000.0F + "ms " + task.assetDesc);
            return true;
         }
      } else {
         return false;
      }
   }

   protected void taskFailed(AssetDescriptor assetDesc, RuntimeException ex) {
      throw ex;
   }

   private void incrementRefCountedDependencies(String parent) {
      Array<String> dependencies = this.assetDependencies.get(parent);
      if (dependencies != null) {
         for (String dependency : dependencies) {
            Class type = this.assetTypes.get(dependency);
            RefCountedContainer assetRef = this.assets.get(type).get(dependency);
            assetRef.incRefCount();
            this.incrementRefCountedDependencies(dependency);
         }
      }
   }

   private void handleTaskError(Throwable t) {
      this.log.error("Error loading asset.", t);
      if (this.tasks.isEmpty()) {
         throw new GdxRuntimeException(t);
      } else {
         AssetLoadingTask task = this.tasks.pop();
         AssetDescriptor assetDesc = task.assetDesc;
         if (task.dependenciesLoaded && task.dependencies != null) {
            for (AssetDescriptor desc : task.dependencies) {
               this.unload(desc.fileName);
            }
         }

         this.tasks.clear();
         if (this.listener != null) {
            this.listener.error(assetDesc, t);
         } else {
            throw new GdxRuntimeException(t);
         }
      }
   }

   public synchronized <T, P extends AssetLoaderParameters<T>> void setLoader(Class<T> type, AssetLoader<T, P> loader) {
      this.setLoader(type, null, loader);
   }

   public synchronized <T, P extends AssetLoaderParameters<T>> void setLoader(Class<T> type, String suffix, AssetLoader<T, P> loader) {
      if (type == null) {
         throw new IllegalArgumentException("type cannot be null.");
      } else if (loader == null) {
         throw new IllegalArgumentException("loader cannot be null.");
      } else {
         this.log.debug("Loader set: " + ClassReflection.getSimpleName(type) + " -> " + ClassReflection.getSimpleName(loader.getClass()));
         ObjectMap<String, AssetLoader> loaders = this.loaders.get(type);
         if (loaders == null) {
            this.loaders.put(type, loaders = new ObjectMap<>());
         }

         loaders.put(suffix == null ? "" : suffix, loader);
      }
   }

   public synchronized int getLoadedAssets() {
      return this.assetTypes.size;
   }

   public synchronized int getQueuedAssets() {
      return this.loadQueue.size + this.tasks.size();
   }

   public synchronized float getProgress() {
      if (this.toLoad == 0) {
         return 1.0F;
      } else {
         float fractionalLoaded = this.loaded;
         if (this.peakTasks > 0) {
            fractionalLoaded += (float)(this.peakTasks - this.tasks.size()) / this.peakTasks;
         }

         return Math.min(1.0F, fractionalLoaded / this.toLoad);
      }
   }

   public synchronized void setErrorListener(AssetErrorListener listener) {
      this.listener = listener;
   }

   @Override
   public synchronized void dispose() {
      this.log.debug("Disposing.");
      this.clear();
      this.executor.dispose();
   }

   public synchronized void clear() {
      this.loadQueue.clear();

      while (!this.update()) {
      }

      ObjectIntMap<String> dependencyCount = new ObjectIntMap<>();

      while (this.assetTypes.size > 0) {
         dependencyCount.clear();
         Array<String> assets = this.assetTypes.keys().toArray();

         for (String asset : assets) {
            dependencyCount.put(asset, 0);
         }

         for (String asset : assets) {
            Array<String> dependencies = this.assetDependencies.get(asset);
            if (dependencies != null) {
               for (String dependency : dependencies) {
                  int count = dependencyCount.get(dependency, 0);
                  dependencyCount.put(dependency, ++count);
               }
            }
         }

         for (String assetx : assets) {
            if (dependencyCount.get(assetx, 0) == 0) {
               this.unload(assetx);
            }
         }
      }

      this.assets.clear();
      this.assetTypes.clear();
      this.assetDependencies.clear();
      this.loaded = 0;
      this.toLoad = 0;
      this.peakTasks = 0;
      this.loadQueue.clear();
      this.tasks.clear();
   }

   public Logger getLogger() {
      return this.log;
   }

   public void setLogger(Logger logger) {
      this.log = logger;
   }

   public synchronized int getReferenceCount(String fileName) {
      Class type = this.assetTypes.get(fileName);
      if (type == null) {
         throw new GdxRuntimeException("Asset not loaded: " + fileName);
      } else {
         return this.assets.get(type).get(fileName).getRefCount();
      }
   }

   public synchronized void setReferenceCount(String fileName, int refCount) {
      Class type = this.assetTypes.get(fileName);
      if (type == null) {
         throw new GdxRuntimeException("Asset not loaded: " + fileName);
      } else {
         this.assets.get(type).get(fileName).setRefCount(refCount);
      }
   }

   public synchronized String getDiagnostics() {
      StringBuffer buffer = new StringBuffer();

      for (String fileName : this.assetTypes.keys()) {
         buffer.append(fileName);
         buffer.append(", ");
         Class type = this.assetTypes.get(fileName);
         RefCountedContainer assetRef = this.assets.get(type).get(fileName);
         Array<String> dependencies = this.assetDependencies.get(fileName);
         buffer.append(ClassReflection.getSimpleName(type));
         buffer.append(", refs: ");
         buffer.append(assetRef.getRefCount());
         if (dependencies != null) {
            buffer.append(", deps: [");

            for (String dep : dependencies) {
               buffer.append(dep);
               buffer.append(",");
            }

            buffer.append("]");
         }

         buffer.append("\n");
      }

      return buffer.toString();
   }

   public synchronized Array<String> getAssetNames() {
      return this.assetTypes.keys().toArray();
   }

   public synchronized Array<String> getDependencies(String fileName) {
      return this.assetDependencies.get(fileName);
   }

   public synchronized Class getAssetType(String fileName) {
      return this.assetTypes.get(fileName);
   }
}
