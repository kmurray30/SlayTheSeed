package com.badlogic.gdx.graphics.g3d.particles;

import com.badlogic.gdx.graphics.g3d.ModelInstance;
import java.util.Arrays;

public class ParticleChannels {
   private static int currentGlobalId;
   public static final ParallelArray.ChannelDescriptor Life = new ParallelArray.ChannelDescriptor(newGlobalId(), float.class, 3);
   public static final ParallelArray.ChannelDescriptor Position = new ParallelArray.ChannelDescriptor(newGlobalId(), float.class, 3);
   public static final ParallelArray.ChannelDescriptor PreviousPosition = new ParallelArray.ChannelDescriptor(newGlobalId(), float.class, 3);
   public static final ParallelArray.ChannelDescriptor Color = new ParallelArray.ChannelDescriptor(newGlobalId(), float.class, 4);
   public static final ParallelArray.ChannelDescriptor TextureRegion = new ParallelArray.ChannelDescriptor(newGlobalId(), float.class, 6);
   public static final ParallelArray.ChannelDescriptor Rotation2D = new ParallelArray.ChannelDescriptor(newGlobalId(), float.class, 2);
   public static final ParallelArray.ChannelDescriptor Rotation3D = new ParallelArray.ChannelDescriptor(newGlobalId(), float.class, 4);
   public static final ParallelArray.ChannelDescriptor Scale = new ParallelArray.ChannelDescriptor(newGlobalId(), float.class, 1);
   public static final ParallelArray.ChannelDescriptor ModelInstance = new ParallelArray.ChannelDescriptor(newGlobalId(), ModelInstance.class, 1);
   public static final ParallelArray.ChannelDescriptor ParticleController = new ParallelArray.ChannelDescriptor(newGlobalId(), ParticleController.class, 1);
   public static final ParallelArray.ChannelDescriptor Acceleration = new ParallelArray.ChannelDescriptor(newGlobalId(), float.class, 3);
   public static final ParallelArray.ChannelDescriptor AngularVelocity2D = new ParallelArray.ChannelDescriptor(newGlobalId(), float.class, 1);
   public static final ParallelArray.ChannelDescriptor AngularVelocity3D = new ParallelArray.ChannelDescriptor(newGlobalId(), float.class, 3);
   public static final ParallelArray.ChannelDescriptor Interpolation = new ParallelArray.ChannelDescriptor(-1, float.class, 2);
   public static final ParallelArray.ChannelDescriptor Interpolation4 = new ParallelArray.ChannelDescriptor(-1, float.class, 4);
   public static final ParallelArray.ChannelDescriptor Interpolation6 = new ParallelArray.ChannelDescriptor(-1, float.class, 6);
   public static final int CurrentLifeOffset = 0;
   public static final int TotalLifeOffset = 1;
   public static final int LifePercentOffset = 2;
   public static final int RedOffset = 0;
   public static final int GreenOffset = 1;
   public static final int BlueOffset = 2;
   public static final int AlphaOffset = 3;
   public static final int InterpolationStartOffset = 0;
   public static final int InterpolationDiffOffset = 1;
   public static final int VelocityStrengthStartOffset = 0;
   public static final int VelocityStrengthDiffOffset = 1;
   public static final int VelocityThetaStartOffset = 0;
   public static final int VelocityThetaDiffOffset = 1;
   public static final int VelocityPhiStartOffset = 2;
   public static final int VelocityPhiDiffOffset = 3;
   public static final int XOffset = 0;
   public static final int YOffset = 1;
   public static final int ZOffset = 2;
   public static final int WOffset = 3;
   public static final int UOffset = 0;
   public static final int VOffset = 1;
   public static final int U2Offset = 2;
   public static final int V2Offset = 3;
   public static final int HalfWidthOffset = 4;
   public static final int HalfHeightOffset = 5;
   public static final int CosineOffset = 0;
   public static final int SineOffset = 1;
   private int currentId;

   public static int newGlobalId() {
      return currentGlobalId++;
   }

   public ParticleChannels() {
      this.resetIds();
   }

   public int newId() {
      return this.currentId++;
   }

   protected void resetIds() {
      this.currentId = currentGlobalId;
   }

   public static class ColorInitializer implements ParallelArray.ChannelInitializer<ParallelArray.FloatChannel> {
      private static ParticleChannels.ColorInitializer instance;

      public static ParticleChannels.ColorInitializer get() {
         if (instance == null) {
            instance = new ParticleChannels.ColorInitializer();
         }

         return instance;
      }

      public void init(ParallelArray.FloatChannel channel) {
         Arrays.fill(channel.data, 0, channel.data.length, 1.0F);
      }
   }

   public static class Rotation2dInitializer implements ParallelArray.ChannelInitializer<ParallelArray.FloatChannel> {
      private static ParticleChannels.Rotation2dInitializer instance;

      public static ParticleChannels.Rotation2dInitializer get() {
         if (instance == null) {
            instance = new ParticleChannels.Rotation2dInitializer();
         }

         return instance;
      }

      public void init(ParallelArray.FloatChannel channel) {
         int i = 0;

         for (int c = channel.data.length; i < c; i += channel.strideSize) {
            channel.data[i + 0] = 1.0F;
            channel.data[i + 1] = 0.0F;
         }
      }
   }

   public static class Rotation3dInitializer implements ParallelArray.ChannelInitializer<ParallelArray.FloatChannel> {
      private static ParticleChannels.Rotation3dInitializer instance;

      public static ParticleChannels.Rotation3dInitializer get() {
         if (instance == null) {
            instance = new ParticleChannels.Rotation3dInitializer();
         }

         return instance;
      }

      public void init(ParallelArray.FloatChannel channel) {
         int i = 0;

         for (int c = channel.data.length; i < c; i += channel.strideSize) {
            channel.data[i + 0] = channel.data[i + 1] = channel.data[i + 2] = 0.0F;
            channel.data[i + 3] = 1.0F;
         }
      }
   }

   public static class ScaleInitializer implements ParallelArray.ChannelInitializer<ParallelArray.FloatChannel> {
      private static ParticleChannels.ScaleInitializer instance;

      public static ParticleChannels.ScaleInitializer get() {
         if (instance == null) {
            instance = new ParticleChannels.ScaleInitializer();
         }

         return instance;
      }

      public void init(ParallelArray.FloatChannel channel) {
         Arrays.fill(channel.data, 0, channel.data.length, 1.0F);
      }
   }

   public static class TextureRegionInitializer implements ParallelArray.ChannelInitializer<ParallelArray.FloatChannel> {
      private static ParticleChannels.TextureRegionInitializer instance;

      public static ParticleChannels.TextureRegionInitializer get() {
         if (instance == null) {
            instance = new ParticleChannels.TextureRegionInitializer();
         }

         return instance;
      }

      public void init(ParallelArray.FloatChannel channel) {
         int i = 0;

         for (int c = channel.data.length; i < c; i += channel.strideSize) {
            channel.data[i + 0] = 0.0F;
            channel.data[i + 1] = 0.0F;
            channel.data[i + 2] = 1.0F;
            channel.data[i + 3] = 1.0F;
            channel.data[i + 4] = 0.5F;
            channel.data[i + 5] = 0.5F;
         }
      }
   }
}
