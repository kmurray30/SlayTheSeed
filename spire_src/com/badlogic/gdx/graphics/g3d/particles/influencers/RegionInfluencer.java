package com.badlogic.gdx.graphics.g3d.particles.influencers;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g3d.particles.ParallelArray;
import com.badlogic.gdx.graphics.g3d.particles.ParticleChannels;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;

public abstract class RegionInfluencer extends Influencer {
   public Array<RegionInfluencer.AspectTextureRegion> regions;
   ParallelArray.FloatChannel regionChannel;

   public RegionInfluencer(int regionsCount) {
      this.regions = new Array<>(false, regionsCount, RegionInfluencer.AspectTextureRegion.class);
   }

   public RegionInfluencer() {
      this(1);
      RegionInfluencer.AspectTextureRegion aspectRegion = new RegionInfluencer.AspectTextureRegion();
      aspectRegion.u = aspectRegion.v = 0.0F;
      aspectRegion.u2 = aspectRegion.v2 = 1.0F;
      aspectRegion.halfInvAspectRatio = 0.5F;
      this.regions.add(aspectRegion);
   }

   public RegionInfluencer(TextureRegion... regions) {
      this.regions = new Array<>(false, regions.length, RegionInfluencer.AspectTextureRegion.class);
      this.add(regions);
   }

   public RegionInfluencer(Texture texture) {
      this(new TextureRegion(texture));
   }

   public RegionInfluencer(RegionInfluencer regionInfluencer) {
      this(regionInfluencer.regions.size);
      this.regions.ensureCapacity(regionInfluencer.regions.size);

      for (int i = 0; i < regionInfluencer.regions.size; i++) {
         this.regions.add(new RegionInfluencer.AspectTextureRegion(regionInfluencer.regions.get(i)));
      }
   }

   public void add(TextureRegion... regions) {
      this.regions.ensureCapacity(regions.length);

      for (TextureRegion region : regions) {
         this.regions.add(new RegionInfluencer.AspectTextureRegion(region));
      }
   }

   public void clear() {
      this.regions.clear();
   }

   @Override
   public void allocateChannels() {
      this.regionChannel = this.controller.particles.addChannel(ParticleChannels.TextureRegion);
   }

   @Override
   public void write(Json json) {
      json.writeValue("regions", this.regions, Array.class, RegionInfluencer.AspectTextureRegion.class);
   }

   @Override
   public void read(Json json, JsonValue jsonData) {
      this.regions.clear();
      this.regions.addAll(json.readValue("regions", Array.class, RegionInfluencer.AspectTextureRegion.class, jsonData));
   }

   public static class Animated extends RegionInfluencer {
      ParallelArray.FloatChannel lifeChannel;

      public Animated() {
      }

      public Animated(RegionInfluencer.Animated regionInfluencer) {
         super(regionInfluencer);
      }

      public Animated(TextureRegion textureRegion) {
         super(textureRegion);
      }

      public Animated(Texture texture) {
         super(texture);
      }

      @Override
      public void allocateChannels() {
         super.allocateChannels();
         this.lifeChannel = this.controller.particles.addChannel(ParticleChannels.Life);
      }

      @Override
      public void update() {
         int i = 0;
         int l = 2;

         for (int c = this.controller.particles.size * this.regionChannel.strideSize; i < c; l += this.lifeChannel.strideSize) {
            RegionInfluencer.AspectTextureRegion region = this.regions.get((int)(this.lifeChannel.data[l] * (this.regions.size - 1)));
            this.regionChannel.data[i + 0] = region.u;
            this.regionChannel.data[i + 1] = region.v;
            this.regionChannel.data[i + 2] = region.u2;
            this.regionChannel.data[i + 3] = region.v2;
            this.regionChannel.data[i + 4] = 0.5F;
            this.regionChannel.data[i + 5] = region.halfInvAspectRatio;
            i += this.regionChannel.strideSize;
         }
      }

      public RegionInfluencer.Animated copy() {
         return new RegionInfluencer.Animated(this);
      }
   }

   public static class AspectTextureRegion {
      public float u;
      public float v;
      public float u2;
      public float v2;
      public float halfInvAspectRatio;

      public AspectTextureRegion() {
      }

      public AspectTextureRegion(RegionInfluencer.AspectTextureRegion aspectTextureRegion) {
         this.set(aspectTextureRegion);
      }

      public AspectTextureRegion(TextureRegion region) {
         this.set(region);
      }

      public void set(TextureRegion region) {
         this.u = region.getU();
         this.v = region.getV();
         this.u2 = region.getU2();
         this.v2 = region.getV2();
         this.halfInvAspectRatio = 0.5F * ((float)region.getRegionHeight() / region.getRegionWidth());
      }

      public void set(RegionInfluencer.AspectTextureRegion aspectTextureRegion) {
         this.u = aspectTextureRegion.u;
         this.v = aspectTextureRegion.v;
         this.u2 = aspectTextureRegion.u2;
         this.v2 = aspectTextureRegion.v2;
         this.halfInvAspectRatio = aspectTextureRegion.halfInvAspectRatio;
      }
   }

   public static class Random extends RegionInfluencer {
      public Random() {
      }

      public Random(RegionInfluencer.Random regionInfluencer) {
         super(regionInfluencer);
      }

      public Random(TextureRegion textureRegion) {
         super(textureRegion);
      }

      public Random(Texture texture) {
         super(texture);
      }

      @Override
      public void activateParticles(int startIndex, int count) {
         int i = startIndex * this.regionChannel.strideSize;

         for (int c = i + count * this.regionChannel.strideSize; i < c; i += this.regionChannel.strideSize) {
            RegionInfluencer.AspectTextureRegion region = this.regions.random();
            this.regionChannel.data[i + 0] = region.u;
            this.regionChannel.data[i + 1] = region.v;
            this.regionChannel.data[i + 2] = region.u2;
            this.regionChannel.data[i + 3] = region.v2;
            this.regionChannel.data[i + 4] = 0.5F;
            this.regionChannel.data[i + 5] = region.halfInvAspectRatio;
         }
      }

      public RegionInfluencer.Random copy() {
         return new RegionInfluencer.Random(this);
      }
   }

   public static class Single extends RegionInfluencer {
      public Single() {
      }

      public Single(RegionInfluencer.Single regionInfluencer) {
         super(regionInfluencer);
      }

      public Single(TextureRegion textureRegion) {
         super(textureRegion);
      }

      public Single(Texture texture) {
         super(texture);
      }

      @Override
      public void init() {
         RegionInfluencer.AspectTextureRegion region = this.regions.items[0];
         int i = 0;

         for (int c = this.controller.emitter.maxParticleCount * this.regionChannel.strideSize; i < c; i += this.regionChannel.strideSize) {
            this.regionChannel.data[i + 0] = region.u;
            this.regionChannel.data[i + 1] = region.v;
            this.regionChannel.data[i + 2] = region.u2;
            this.regionChannel.data[i + 3] = region.v2;
            this.regionChannel.data[i + 4] = 0.5F;
            this.regionChannel.data[i + 5] = region.halfInvAspectRatio;
         }
      }

      public RegionInfluencer.Single copy() {
         return new RegionInfluencer.Single(this);
      }
   }
}
