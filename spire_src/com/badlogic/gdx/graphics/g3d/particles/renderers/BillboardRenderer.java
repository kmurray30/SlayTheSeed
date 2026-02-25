package com.badlogic.gdx.graphics.g3d.particles.renderers;

import com.badlogic.gdx.graphics.g3d.particles.ParticleChannels;
import com.badlogic.gdx.graphics.g3d.particles.ParticleControllerComponent;
import com.badlogic.gdx.graphics.g3d.particles.batches.BillboardParticleBatch;
import com.badlogic.gdx.graphics.g3d.particles.batches.ParticleBatch;

public class BillboardRenderer extends ParticleControllerRenderer<BillboardControllerRenderData, BillboardParticleBatch> {
   public BillboardRenderer() {
      super(new BillboardControllerRenderData());
   }

   public BillboardRenderer(BillboardParticleBatch batch) {
      this();
      this.setBatch(batch);
   }

   @Override
   public void allocateChannels() {
      this.renderData.positionChannel = this.controller.particles.addChannel(ParticleChannels.Position);
      this.renderData.regionChannel = this.controller.particles.addChannel(ParticleChannels.TextureRegion, ParticleChannels.TextureRegionInitializer.get());
      this.renderData.colorChannel = this.controller.particles.addChannel(ParticleChannels.Color, ParticleChannels.ColorInitializer.get());
      this.renderData.scaleChannel = this.controller.particles.addChannel(ParticleChannels.Scale, ParticleChannels.ScaleInitializer.get());
      this.renderData.rotationChannel = this.controller.particles.addChannel(ParticleChannels.Rotation2D, ParticleChannels.Rotation2dInitializer.get());
   }

   @Override
   public ParticleControllerComponent copy() {
      return new BillboardRenderer(this.batch);
   }

   @Override
   public boolean isCompatible(ParticleBatch<?> batch) {
      return batch instanceof BillboardParticleBatch;
   }
}
