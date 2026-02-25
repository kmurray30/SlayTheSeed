package com.badlogic.gdx.graphics.g3d.particles.influencers;

import com.badlogic.gdx.graphics.g3d.particles.ParallelArray;
import com.badlogic.gdx.graphics.g3d.particles.ParticleChannels;
import com.badlogic.gdx.graphics.g3d.particles.ParticleController;
import com.badlogic.gdx.utils.GdxRuntimeException;

public class ParticleControllerFinalizerInfluencer extends Influencer {
   ParallelArray.FloatChannel positionChannel;
   ParallelArray.FloatChannel scaleChannel;
   ParallelArray.FloatChannel rotationChannel;
   ParallelArray.ObjectChannel<ParticleController> controllerChannel;
   boolean hasScale;
   boolean hasRotation;

   @Override
   public void init() {
      this.controllerChannel = this.controller.particles.getChannel(ParticleChannels.ParticleController);
      if (this.controllerChannel == null) {
         throw new GdxRuntimeException("ParticleController channel not found, specify an influencer which will allocate it please.");
      } else {
         this.scaleChannel = this.controller.particles.getChannel(ParticleChannels.Scale);
         this.rotationChannel = this.controller.particles.getChannel(ParticleChannels.Rotation3D);
         this.hasScale = this.scaleChannel != null;
         this.hasRotation = this.rotationChannel != null;
      }
   }

   @Override
   public void allocateChannels() {
      this.positionChannel = this.controller.particles.addChannel(ParticleChannels.Position);
   }

   @Override
   public void update() {
      int i = 0;
      int positionOffset = 0;

      for (int c = this.controller.particles.size; i < c; positionOffset += this.positionChannel.strideSize) {
         ParticleController particleController = this.controllerChannel.data[i];
         float scale = this.hasScale ? this.scaleChannel.data[i] : 1.0F;
         float qx = 0.0F;
         float qy = 0.0F;
         float qz = 0.0F;
         float qw = 1.0F;
         if (this.hasRotation) {
            int rotationOffset = i * this.rotationChannel.strideSize;
            qx = this.rotationChannel.data[rotationOffset + 0];
            qy = this.rotationChannel.data[rotationOffset + 1];
            qz = this.rotationChannel.data[rotationOffset + 2];
            qw = this.rotationChannel.data[rotationOffset + 3];
         }

         particleController.setTransform(
            this.positionChannel.data[positionOffset + 0],
            this.positionChannel.data[positionOffset + 1],
            this.positionChannel.data[positionOffset + 2],
            qx,
            qy,
            qz,
            qw,
            scale
         );
         particleController.update();
         i++;
      }
   }

   public ParticleControllerFinalizerInfluencer copy() {
      return new ParticleControllerFinalizerInfluencer();
   }
}
