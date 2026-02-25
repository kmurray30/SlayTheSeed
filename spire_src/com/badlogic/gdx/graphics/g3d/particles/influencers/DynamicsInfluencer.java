package com.badlogic.gdx.graphics.g3d.particles.influencers;

import com.badlogic.gdx.graphics.g3d.particles.ParallelArray;
import com.badlogic.gdx.graphics.g3d.particles.ParticleChannels;
import com.badlogic.gdx.graphics.g3d.particles.ParticleController;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import java.util.Arrays;

public class DynamicsInfluencer extends Influencer {
   public Array<DynamicsModifier> velocities;
   private ParallelArray.FloatChannel accellerationChannel;
   private ParallelArray.FloatChannel positionChannel;
   private ParallelArray.FloatChannel previousPositionChannel;
   private ParallelArray.FloatChannel rotationChannel;
   private ParallelArray.FloatChannel angularVelocityChannel;
   boolean hasAcceleration;
   boolean has2dAngularVelocity;
   boolean has3dAngularVelocity;

   public DynamicsInfluencer() {
      this.velocities = new Array<>(true, 3, DynamicsModifier.class);
   }

   public DynamicsInfluencer(DynamicsModifier... velocities) {
      this.velocities = new Array<>(true, velocities.length, DynamicsModifier.class);

      for (DynamicsModifier value : velocities) {
         this.velocities.add((DynamicsModifier)value.copy());
      }
   }

   public DynamicsInfluencer(DynamicsInfluencer velocityInfluencer) {
      this(velocityInfluencer.velocities.toArray(DynamicsModifier.class));
   }

   @Override
   public void allocateChannels() {
      for (int k = 0; k < this.velocities.size; k++) {
         this.velocities.items[k].allocateChannels();
      }

      this.accellerationChannel = this.controller.particles.getChannel(ParticleChannels.Acceleration);
      this.hasAcceleration = this.accellerationChannel != null;
      if (this.hasAcceleration) {
         this.positionChannel = this.controller.particles.addChannel(ParticleChannels.Position);
         this.previousPositionChannel = this.controller.particles.addChannel(ParticleChannels.PreviousPosition);
      }

      this.angularVelocityChannel = this.controller.particles.getChannel(ParticleChannels.AngularVelocity2D);
      this.has2dAngularVelocity = this.angularVelocityChannel != null;
      if (this.has2dAngularVelocity) {
         this.rotationChannel = this.controller.particles.addChannel(ParticleChannels.Rotation2D);
         this.has3dAngularVelocity = false;
      } else {
         this.angularVelocityChannel = this.controller.particles.getChannel(ParticleChannels.AngularVelocity3D);
         this.has3dAngularVelocity = this.angularVelocityChannel != null;
         if (this.has3dAngularVelocity) {
            this.rotationChannel = this.controller.particles.addChannel(ParticleChannels.Rotation3D);
         }
      }
   }

   @Override
   public void set(ParticleController particleController) {
      super.set(particleController);

      for (int k = 0; k < this.velocities.size; k++) {
         this.velocities.items[k].set(particleController);
      }
   }

   @Override
   public void init() {
      for (int k = 0; k < this.velocities.size; k++) {
         this.velocities.items[k].init();
      }
   }

   @Override
   public void activateParticles(int startIndex, int count) {
      if (this.hasAcceleration) {
         int i = startIndex * this.positionChannel.strideSize;

         for (int c = i + count * this.positionChannel.strideSize; i < c; i += this.positionChannel.strideSize) {
            this.previousPositionChannel.data[i + 0] = this.positionChannel.data[i + 0];
            this.previousPositionChannel.data[i + 1] = this.positionChannel.data[i + 1];
            this.previousPositionChannel.data[i + 2] = this.positionChannel.data[i + 2];
         }
      }

      if (this.has2dAngularVelocity) {
         int i = startIndex * this.rotationChannel.strideSize;

         for (int c = i + count * this.rotationChannel.strideSize; i < c; i += this.rotationChannel.strideSize) {
            this.rotationChannel.data[i + 0] = 1.0F;
            this.rotationChannel.data[i + 1] = 0.0F;
         }
      } else if (this.has3dAngularVelocity) {
         int i = startIndex * this.rotationChannel.strideSize;

         for (int c = i + count * this.rotationChannel.strideSize; i < c; i += this.rotationChannel.strideSize) {
            this.rotationChannel.data[i + 0] = 0.0F;
            this.rotationChannel.data[i + 1] = 0.0F;
            this.rotationChannel.data[i + 2] = 0.0F;
            this.rotationChannel.data[i + 3] = 1.0F;
         }
      }

      for (int k = 0; k < this.velocities.size; k++) {
         this.velocities.items[k].activateParticles(startIndex, count);
      }
   }

   @Override
   public void update() {
      if (this.hasAcceleration) {
         Arrays.fill(this.accellerationChannel.data, 0, this.controller.particles.size * this.accellerationChannel.strideSize, 0.0F);
      }

      if (this.has2dAngularVelocity || this.has3dAngularVelocity) {
         Arrays.fill(this.angularVelocityChannel.data, 0, this.controller.particles.size * this.angularVelocityChannel.strideSize, 0.0F);
      }

      for (int k = 0; k < this.velocities.size; k++) {
         this.velocities.items[k].update();
      }

      if (this.hasAcceleration) {
         int i = 0;

         for (int offset = 0; i < this.controller.particles.size; offset += this.positionChannel.strideSize) {
            float x = this.positionChannel.data[offset + 0];
            float y = this.positionChannel.data[offset + 1];
            float z = this.positionChannel.data[offset + 2];
            this.positionChannel.data[offset + 0] = 2.0F * x
               - this.previousPositionChannel.data[offset + 0]
               + this.accellerationChannel.data[offset + 0] * this.controller.deltaTimeSqr;
            this.positionChannel.data[offset + 1] = 2.0F * y
               - this.previousPositionChannel.data[offset + 1]
               + this.accellerationChannel.data[offset + 1] * this.controller.deltaTimeSqr;
            this.positionChannel.data[offset + 2] = 2.0F * z
               - this.previousPositionChannel.data[offset + 2]
               + this.accellerationChannel.data[offset + 2] * this.controller.deltaTimeSqr;
            this.previousPositionChannel.data[offset + 0] = x;
            this.previousPositionChannel.data[offset + 1] = y;
            this.previousPositionChannel.data[offset + 2] = z;
            i++;
         }
      }

      if (this.has2dAngularVelocity) {
         int i = 0;

         for (int offset = 0; i < this.controller.particles.size; offset += this.rotationChannel.strideSize) {
            float rotation = this.angularVelocityChannel.data[i] * this.controller.deltaTime;
            if (rotation != 0.0F) {
               float cosBeta = MathUtils.cosDeg(rotation);
               float sinBeta = MathUtils.sinDeg(rotation);
               float currentCosine = this.rotationChannel.data[offset + 0];
               float currentSine = this.rotationChannel.data[offset + 1];
               float newCosine = currentCosine * cosBeta - currentSine * sinBeta;
               float newSine = currentSine * cosBeta + currentCosine * sinBeta;
               this.rotationChannel.data[offset + 0] = newCosine;
               this.rotationChannel.data[offset + 1] = newSine;
            }

            i++;
         }
      } else if (this.has3dAngularVelocity) {
         int i = 0;
         int offset = 0;

         for (int angularOffset = 0; i < this.controller.particles.size; angularOffset += this.angularVelocityChannel.strideSize) {
            float wx = this.angularVelocityChannel.data[angularOffset + 0];
            float wy = this.angularVelocityChannel.data[angularOffset + 1];
            float wz = this.angularVelocityChannel.data[angularOffset + 2];
            float qx = this.rotationChannel.data[offset + 0];
            float qy = this.rotationChannel.data[offset + 1];
            float qz = this.rotationChannel.data[offset + 2];
            float qw = this.rotationChannel.data[offset + 3];
            TMP_Q.set(wx, wy, wz, 0.0F).mul(qx, qy, qz, qw).mul(0.5F * this.controller.deltaTime).add(qx, qy, qz, qw).nor();
            this.rotationChannel.data[offset + 0] = TMP_Q.x;
            this.rotationChannel.data[offset + 1] = TMP_Q.y;
            this.rotationChannel.data[offset + 2] = TMP_Q.z;
            this.rotationChannel.data[offset + 3] = TMP_Q.w;
            i++;
            offset += this.rotationChannel.strideSize;
         }
      }
   }

   public DynamicsInfluencer copy() {
      return new DynamicsInfluencer(this);
   }

   @Override
   public void write(Json json) {
      json.writeValue("velocities", this.velocities, Array.class, DynamicsModifier.class);
   }

   @Override
   public void read(Json json, JsonValue jsonData) {
      this.velocities.addAll(json.readValue("velocities", Array.class, DynamicsModifier.class, jsonData));
   }
}
