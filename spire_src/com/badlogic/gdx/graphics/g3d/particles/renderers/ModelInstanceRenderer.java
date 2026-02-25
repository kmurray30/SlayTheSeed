package com.badlogic.gdx.graphics.g3d.particles.renderers;

import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.particles.ParticleChannels;
import com.badlogic.gdx.graphics.g3d.particles.ParticleControllerComponent;
import com.badlogic.gdx.graphics.g3d.particles.batches.ModelInstanceParticleBatch;
import com.badlogic.gdx.graphics.g3d.particles.batches.ParticleBatch;

public class ModelInstanceRenderer extends ParticleControllerRenderer<ModelInstanceControllerRenderData, ModelInstanceParticleBatch> {
   private boolean hasColor;
   private boolean hasScale;
   private boolean hasRotation;

   public ModelInstanceRenderer() {
      super(new ModelInstanceControllerRenderData());
   }

   public ModelInstanceRenderer(ModelInstanceParticleBatch batch) {
      this();
      this.setBatch(batch);
   }

   @Override
   public void allocateChannels() {
      this.renderData.positionChannel = this.controller.particles.addChannel(ParticleChannels.Position);
   }

   @Override
   public void init() {
      this.renderData.modelInstanceChannel = this.controller.particles.getChannel(ParticleChannels.ModelInstance);
      this.renderData.colorChannel = this.controller.particles.getChannel(ParticleChannels.Color);
      this.renderData.scaleChannel = this.controller.particles.getChannel(ParticleChannels.Scale);
      this.renderData.rotationChannel = this.controller.particles.getChannel(ParticleChannels.Rotation3D);
      this.hasColor = this.renderData.colorChannel != null;
      this.hasScale = this.renderData.scaleChannel != null;
      this.hasRotation = this.renderData.rotationChannel != null;
   }

   @Override
   public void update() {
      int i = 0;
      int positionOffset = 0;

      for (int c = this.controller.particles.size; i < c; positionOffset += this.renderData.positionChannel.strideSize) {
         ModelInstance instance = this.renderData.modelInstanceChannel.data[i];
         float scale = this.hasScale ? this.renderData.scaleChannel.data[i] : 1.0F;
         float qx = 0.0F;
         float qy = 0.0F;
         float qz = 0.0F;
         float qw = 1.0F;
         if (this.hasRotation) {
            int rotationOffset = i * this.renderData.rotationChannel.strideSize;
            qx = this.renderData.rotationChannel.data[rotationOffset + 0];
            qy = this.renderData.rotationChannel.data[rotationOffset + 1];
            qz = this.renderData.rotationChannel.data[rotationOffset + 2];
            qw = this.renderData.rotationChannel.data[rotationOffset + 3];
         }

         instance.transform
            .set(
               this.renderData.positionChannel.data[positionOffset + 0],
               this.renderData.positionChannel.data[positionOffset + 1],
               this.renderData.positionChannel.data[positionOffset + 2],
               qx,
               qy,
               qz,
               qw,
               scale,
               scale,
               scale
            );
         if (this.hasColor) {
            int colorOffset = i * this.renderData.colorChannel.strideSize;
            ColorAttribute colorAttribute = (ColorAttribute)instance.materials.get(0).get(ColorAttribute.Diffuse);
            BlendingAttribute blendingAttribute = (BlendingAttribute)instance.materials.get(0).get(BlendingAttribute.Type);
            colorAttribute.color.r = this.renderData.colorChannel.data[colorOffset + 0];
            colorAttribute.color.g = this.renderData.colorChannel.data[colorOffset + 1];
            colorAttribute.color.b = this.renderData.colorChannel.data[colorOffset + 2];
            if (blendingAttribute != null) {
               blendingAttribute.opacity = this.renderData.colorChannel.data[colorOffset + 3];
            }
         }

         i++;
      }

      super.update();
   }

   @Override
   public ParticleControllerComponent copy() {
      return new ModelInstanceRenderer(this.batch);
   }

   @Override
   public boolean isCompatible(ParticleBatch<?> batch) {
      return batch instanceof ModelInstanceParticleBatch;
   }
}
