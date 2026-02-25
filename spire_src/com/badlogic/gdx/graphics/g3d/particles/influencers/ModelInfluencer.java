package com.badlogic.gdx.graphics.g3d.particles.influencers;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.particles.ParallelArray;
import com.badlogic.gdx.graphics.g3d.particles.ParticleChannels;
import com.badlogic.gdx.graphics.g3d.particles.ResourceData;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;

public abstract class ModelInfluencer extends Influencer {
   public Array<Model> models;
   ParallelArray.ObjectChannel<ModelInstance> modelChannel;

   public ModelInfluencer() {
      this.models = new Array<>(true, 1, Model.class);
   }

   public ModelInfluencer(Model... models) {
      this.models = new Array<>(models);
   }

   public ModelInfluencer(ModelInfluencer influencer) {
      this(influencer.models.toArray(Model.class));
   }

   @Override
   public void allocateChannels() {
      this.modelChannel = this.controller.particles.addChannel(ParticleChannels.ModelInstance);
   }

   @Override
   public void save(AssetManager manager, ResourceData resources) {
      ResourceData.SaveData data = resources.createSaveData();

      for (Model model : this.models) {
         data.saveAsset(manager.getAssetFileName(model), Model.class);
      }
   }

   @Override
   public void load(AssetManager manager, ResourceData resources) {
      ResourceData.SaveData data = resources.getSaveData();

      AssetDescriptor descriptor;
      while ((descriptor = data.loadAsset()) != null) {
         Model model = manager.get(descriptor);
         if (model == null) {
            throw new RuntimeException("Model is null");
         }

         this.models.add(model);
      }
   }

   public static class Random extends ModelInfluencer {
      ModelInfluencer.Random.ModelInstancePool pool = new ModelInfluencer.Random.ModelInstancePool();

      public Random() {
      }

      public Random(ModelInfluencer.Random influencer) {
         super(influencer);
      }

      public Random(Model... models) {
         super(models);
      }

      @Override
      public void init() {
         this.pool.clear();
      }

      @Override
      public void activateParticles(int startIndex, int count) {
         int i = startIndex;

         for (int c = startIndex + count; i < c; i++) {
            this.modelChannel.data[i] = this.pool.obtain();
         }
      }

      @Override
      public void killParticles(int startIndex, int count) {
         int i = startIndex;

         for (int c = startIndex + count; i < c; i++) {
            this.pool.free(this.modelChannel.data[i]);
            this.modelChannel.data[i] = null;
         }
      }

      public ModelInfluencer.Random copy() {
         return new ModelInfluencer.Random(this);
      }

      private class ModelInstancePool extends Pool<ModelInstance> {
         public ModelInstancePool() {
         }

         public ModelInstance newObject() {
            return new ModelInstance(Random.this.models.random());
         }
      }
   }

   public static class Single extends ModelInfluencer {
      public Single() {
      }

      public Single(ModelInfluencer.Single influencer) {
         super(influencer);
      }

      public Single(Model... models) {
         super(models);
      }

      @Override
      public void init() {
         Model first = this.models.first();
         int i = 0;

         for (int c = this.controller.emitter.maxParticleCount; i < c; i++) {
            this.modelChannel.data[i] = new ModelInstance(first);
         }
      }

      public ModelInfluencer.Single copy() {
         return new ModelInfluencer.Single(this);
      }
   }
}
