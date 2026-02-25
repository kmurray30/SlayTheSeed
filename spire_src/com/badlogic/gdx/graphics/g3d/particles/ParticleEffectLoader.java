package com.badlogic.gdx.graphics.g3d.particles;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetLoaderParameters;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.AsynchronousAssetLoader;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g3d.particles.batches.ParticleBatch;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.reflect.ClassReflection;
import java.io.IOException;

public class ParticleEffectLoader extends AsynchronousAssetLoader<ParticleEffect, ParticleEffectLoader.ParticleEffectLoadParameter> {
   protected Array<ObjectMap.Entry<String, ResourceData<ParticleEffect>>> items = new Array<>();

   public ParticleEffectLoader(FileHandleResolver resolver) {
      super(resolver);
   }

   public void loadAsync(AssetManager manager, String fileName, FileHandle file, ParticleEffectLoader.ParticleEffectLoadParameter parameter) {
   }

   public Array<AssetDescriptor> getDependencies(String fileName, FileHandle file, ParticleEffectLoader.ParticleEffectLoadParameter parameter) {
      Json json = new Json();
      ResourceData<ParticleEffect> data = json.fromJson(ResourceData.class, file);
      Array<ResourceData.AssetData> assets = null;
      synchronized (this.items) {
         ObjectMap.Entry<String, ResourceData<ParticleEffect>> entry = new ObjectMap.Entry<>();
         entry.key = fileName;
         entry.value = data;
         this.items.add(entry);
         assets = data.getAssets();
      }

      Array<AssetDescriptor> descriptors = new Array<>();

      for (ResourceData.AssetData<?> assetData : assets) {
         if (!this.resolve(assetData.filename).exists()) {
            assetData.filename = file.parent().child(Gdx.files.internal(assetData.filename).name()).path();
         }

         if (assetData.type == ParticleEffect.class) {
            descriptors.add(new AssetDescriptor<>(assetData.filename, assetData.type, parameter));
         } else {
            descriptors.add(new AssetDescriptor<>(assetData.filename, assetData.type));
         }
      }

      return descriptors;
   }

   public void save(ParticleEffect effect, ParticleEffectLoader.ParticleEffectSaveParameter parameter) throws IOException {
      ResourceData<ParticleEffect> data = new ResourceData<>(effect);
      effect.save(parameter.manager, data);
      if (parameter.batches != null) {
         for (ParticleBatch<?> batch : parameter.batches) {
            boolean save = false;

            for (ParticleController controller : effect.getControllers()) {
               if (controller.renderer.isCompatible(batch)) {
                  save = true;
                  break;
               }
            }

            if (save) {
               batch.save(parameter.manager, data);
            }
         }
      }

      Json json = new Json();
      json.toJson(data, parameter.file);
   }

   public ParticleEffect loadSync(AssetManager manager, String fileName, FileHandle file, ParticleEffectLoader.ParticleEffectLoadParameter parameter) {
      ResourceData<ParticleEffect> effectData = null;
      synchronized (this.items) {
         for (int i = 0; i < this.items.size; i++) {
            ObjectMap.Entry<String, ResourceData<ParticleEffect>> entry = this.items.get(i);
            if (entry.key.equals(fileName)) {
               effectData = entry.value;
               this.items.removeIndex(i);
               break;
            }
         }
      }

      effectData.resource.load(manager, effectData);
      if (parameter != null) {
         if (parameter.batches != null) {
            for (ParticleBatch<?> batch : parameter.batches) {
               batch.load(manager, effectData);
            }
         }

         effectData.resource.setBatch(parameter.batches);
      }

      return effectData.resource;
   }

   private <T> T find(Array<?> array, Class<T> type) {
      for (Object object : array) {
         if (ClassReflection.isAssignableFrom(type, object.getClass())) {
            return (T)object;
         }
      }

      return null;
   }

   public static class ParticleEffectLoadParameter extends AssetLoaderParameters<ParticleEffect> {
      Array<ParticleBatch<?>> batches;

      public ParticleEffectLoadParameter(Array<ParticleBatch<?>> batches) {
         this.batches = batches;
      }
   }

   public static class ParticleEffectSaveParameter extends AssetLoaderParameters<ParticleEffect> {
      Array<ParticleBatch<?>> batches;
      FileHandle file;
      AssetManager manager;

      public ParticleEffectSaveParameter(FileHandle file, AssetManager manager, Array<ParticleBatch<?>> batches) {
         this.batches = batches;
         this.file = file;
         this.manager = manager;
      }
   }
}
