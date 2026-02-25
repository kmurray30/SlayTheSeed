package com.badlogic.gdx.graphics.g3d.particles.influencers;

import com.badlogic.gdx.graphics.g3d.particles.ParallelArray;
import com.badlogic.gdx.graphics.g3d.particles.ParticleChannels;
import com.badlogic.gdx.graphics.g3d.particles.ParticleControllerComponent;
import com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;

public abstract class DynamicsModifier extends Influencer {
   protected static final Vector3 TMP_V1 = new Vector3();
   protected static final Vector3 TMP_V2 = new Vector3();
   protected static final Vector3 TMP_V3 = new Vector3();
   protected static final Quaternion TMP_Q = new Quaternion();
   public boolean isGlobal = false;
   protected ParallelArray.FloatChannel lifeChannel;

   public DynamicsModifier() {
   }

   public DynamicsModifier(DynamicsModifier modifier) {
      this.isGlobal = modifier.isGlobal;
   }

   @Override
   public void allocateChannels() {
      this.lifeChannel = this.controller.particles.addChannel(ParticleChannels.Life);
   }

   @Override
   public void write(Json json) {
      super.write(json);
      json.writeValue("isGlobal", this.isGlobal);
   }

   @Override
   public void read(Json json, JsonValue jsonData) {
      super.read(json, jsonData);
      this.isGlobal = json.readValue("isGlobal", boolean.class, jsonData);
   }

   public abstract static class Angular extends DynamicsModifier.Strength {
      protected ParallelArray.FloatChannel angularChannel;
      public ScaledNumericValue thetaValue = new ScaledNumericValue();
      public ScaledNumericValue phiValue = new ScaledNumericValue();

      public Angular() {
      }

      public Angular(DynamicsModifier.Angular value) {
         super(value);
         this.thetaValue.load(value.thetaValue);
         this.phiValue.load(value.phiValue);
      }

      @Override
      public void allocateChannels() {
         super.allocateChannels();
         ParticleChannels.Interpolation4.id = this.controller.particleChannels.newId();
         this.angularChannel = this.controller.particles.addChannel(ParticleChannels.Interpolation4);
      }

      @Override
      public void activateParticles(int startIndex, int count) {
         super.activateParticles(startIndex, count);
         int i = startIndex * this.angularChannel.strideSize;

         for (int c = i + count * this.angularChannel.strideSize; i < c; i += this.angularChannel.strideSize) {
            float start = this.thetaValue.newLowValue();
            float diff = this.thetaValue.newHighValue();
            if (!this.thetaValue.isRelative()) {
               diff -= start;
            }

            this.angularChannel.data[i + 0] = start;
            this.angularChannel.data[i + 1] = diff;
            start = this.phiValue.newLowValue();
            diff = this.phiValue.newHighValue();
            if (!this.phiValue.isRelative()) {
               diff -= start;
            }

            this.angularChannel.data[i + 2] = start;
            this.angularChannel.data[i + 3] = diff;
         }
      }

      @Override
      public void write(Json json) {
         super.write(json);
         json.writeValue("thetaValue", this.thetaValue);
         json.writeValue("phiValue", this.phiValue);
      }

      @Override
      public void read(Json json, JsonValue jsonData) {
         super.read(json, jsonData);
         this.thetaValue = json.readValue("thetaValue", ScaledNumericValue.class, jsonData);
         this.phiValue = json.readValue("phiValue", ScaledNumericValue.class, jsonData);
      }
   }

   public static class BrownianAcceleration extends DynamicsModifier.Strength {
      ParallelArray.FloatChannel accelerationChannel;

      public BrownianAcceleration() {
      }

      public BrownianAcceleration(DynamicsModifier.BrownianAcceleration rotation) {
         super(rotation);
      }

      @Override
      public void allocateChannels() {
         super.allocateChannels();
         this.accelerationChannel = this.controller.particles.addChannel(ParticleChannels.Acceleration);
      }

      @Override
      public void update() {
         int lifeOffset = 2;
         int strengthOffset = 0;
         int forceOffset = 0;
         int i = 0;

         for (int c = this.controller.particles.size; i < c; lifeOffset += this.lifeChannel.strideSize) {
            float strength = this.strengthChannel.data[strengthOffset + 0]
               + this.strengthChannel.data[strengthOffset + 1] * this.strengthValue.getScale(this.lifeChannel.data[lifeOffset]);
            TMP_V3.set(MathUtils.random(-1.0F, 1.0F), MathUtils.random(-1.0F, 1.0F), MathUtils.random(-1.0F, 1.0F)).nor().scl(strength);
            this.accelerationChannel.data[forceOffset + 0] = this.accelerationChannel.data[forceOffset + 0] + TMP_V3.x;
            this.accelerationChannel.data[forceOffset + 1] = this.accelerationChannel.data[forceOffset + 1] + TMP_V3.y;
            this.accelerationChannel.data[forceOffset + 2] = this.accelerationChannel.data[forceOffset + 2] + TMP_V3.z;
            i++;
            strengthOffset += this.strengthChannel.strideSize;
            forceOffset += this.accelerationChannel.strideSize;
         }
      }

      public DynamicsModifier.BrownianAcceleration copy() {
         return new DynamicsModifier.BrownianAcceleration(this);
      }
   }

   public static class CentripetalAcceleration extends DynamicsModifier.Strength {
      ParallelArray.FloatChannel accelerationChannel;
      ParallelArray.FloatChannel positionChannel;

      public CentripetalAcceleration() {
      }

      public CentripetalAcceleration(DynamicsModifier.CentripetalAcceleration rotation) {
         super(rotation);
      }

      @Override
      public void allocateChannels() {
         super.allocateChannels();
         this.accelerationChannel = this.controller.particles.addChannel(ParticleChannels.Acceleration);
         this.positionChannel = this.controller.particles.addChannel(ParticleChannels.Position);
      }

      @Override
      public void update() {
         float cx = 0.0F;
         float cy = 0.0F;
         float cz = 0.0F;
         if (!this.isGlobal) {
            float[] val = this.controller.transform.val;
            cx = val[12];
            cy = val[13];
            cz = val[14];
         }

         int lifeOffset = 2;
         int strengthOffset = 0;
         int positionOffset = 0;
         int forceOffset = 0;
         int i = 0;

         for (int c = this.controller.particles.size; i < c; lifeOffset += this.lifeChannel.strideSize) {
            float strength = this.strengthChannel.data[strengthOffset + 0]
               + this.strengthChannel.data[strengthOffset + 1] * this.strengthValue.getScale(this.lifeChannel.data[lifeOffset]);
            TMP_V3.set(
                  this.positionChannel.data[positionOffset + 0] - cx,
                  this.positionChannel.data[positionOffset + 1] - cy,
                  this.positionChannel.data[positionOffset + 2] - cz
               )
               .nor()
               .scl(strength);
            this.accelerationChannel.data[forceOffset + 0] = this.accelerationChannel.data[forceOffset + 0] + TMP_V3.x;
            this.accelerationChannel.data[forceOffset + 1] = this.accelerationChannel.data[forceOffset + 1] + TMP_V3.y;
            this.accelerationChannel.data[forceOffset + 2] = this.accelerationChannel.data[forceOffset + 2] + TMP_V3.z;
            i++;
            positionOffset += this.positionChannel.strideSize;
            strengthOffset += this.strengthChannel.strideSize;
            forceOffset += this.accelerationChannel.strideSize;
         }
      }

      public DynamicsModifier.CentripetalAcceleration copy() {
         return new DynamicsModifier.CentripetalAcceleration(this);
      }
   }

   public static class FaceDirection extends DynamicsModifier {
      ParallelArray.FloatChannel rotationChannel;
      ParallelArray.FloatChannel accellerationChannel;

      public FaceDirection() {
      }

      public FaceDirection(DynamicsModifier.FaceDirection rotation) {
         super(rotation);
      }

      @Override
      public void allocateChannels() {
         this.rotationChannel = this.controller.particles.addChannel(ParticleChannels.Rotation3D);
         this.accellerationChannel = this.controller.particles.addChannel(ParticleChannels.Acceleration);
      }

      @Override
      public void update() {
         int i = 0;
         int accelOffset = 0;

         for (int c = i + this.controller.particles.size * this.rotationChannel.strideSize; i < c; accelOffset += this.accellerationChannel.strideSize) {
            Vector3 axisZ = TMP_V1.set(
                  this.accellerationChannel.data[accelOffset + 0],
                  this.accellerationChannel.data[accelOffset + 1],
                  this.accellerationChannel.data[accelOffset + 2]
               )
               .nor();
            Vector3 axisY = TMP_V2.set(TMP_V1).crs(Vector3.Y).nor().crs(TMP_V1).nor();
            Vector3 axisX = TMP_V3.set(axisY).crs(axisZ).nor();
            TMP_Q.setFromAxes(false, axisX.x, axisY.x, axisZ.x, axisX.y, axisY.y, axisZ.y, axisX.z, axisY.z, axisZ.z);
            this.rotationChannel.data[i + 0] = TMP_Q.x;
            this.rotationChannel.data[i + 1] = TMP_Q.y;
            this.rotationChannel.data[i + 2] = TMP_Q.z;
            this.rotationChannel.data[i + 3] = TMP_Q.w;
            i += this.rotationChannel.strideSize;
         }
      }

      @Override
      public ParticleControllerComponent copy() {
         return new DynamicsModifier.FaceDirection(this);
      }
   }

   public static class PolarAcceleration extends DynamicsModifier.Angular {
      ParallelArray.FloatChannel directionalVelocityChannel;

      public PolarAcceleration() {
      }

      public PolarAcceleration(DynamicsModifier.PolarAcceleration rotation) {
         super(rotation);
      }

      @Override
      public void allocateChannels() {
         super.allocateChannels();
         this.directionalVelocityChannel = this.controller.particles.addChannel(ParticleChannels.Acceleration);
      }

      @Override
      public void update() {
         int i = 0;
         int l = 2;
         int s = 0;
         int a = 0;

         for (int c = i + this.controller.particles.size * this.directionalVelocityChannel.strideSize; i < c; l += this.lifeChannel.strideSize) {
            float lifePercent = this.lifeChannel.data[l];
            float strength = this.strengthChannel.data[s + 0] + this.strengthChannel.data[s + 1] * this.strengthValue.getScale(lifePercent);
            float phi = this.angularChannel.data[a + 2] + this.angularChannel.data[a + 3] * this.phiValue.getScale(lifePercent);
            float theta = this.angularChannel.data[a + 0] + this.angularChannel.data[a + 1] * this.thetaValue.getScale(lifePercent);
            float cosTheta = MathUtils.cosDeg(theta);
            float sinTheta = MathUtils.sinDeg(theta);
            float cosPhi = MathUtils.cosDeg(phi);
            float sinPhi = MathUtils.sinDeg(phi);
            TMP_V3.set(cosTheta * sinPhi, cosPhi, sinTheta * sinPhi).nor().scl(strength);
            this.directionalVelocityChannel.data[i + 0] = this.directionalVelocityChannel.data[i + 0] + TMP_V3.x;
            this.directionalVelocityChannel.data[i + 1] = this.directionalVelocityChannel.data[i + 1] + TMP_V3.y;
            this.directionalVelocityChannel.data[i + 2] = this.directionalVelocityChannel.data[i + 2] + TMP_V3.z;
            s += this.strengthChannel.strideSize;
            i += this.directionalVelocityChannel.strideSize;
            a += this.angularChannel.strideSize;
         }
      }

      public DynamicsModifier.PolarAcceleration copy() {
         return new DynamicsModifier.PolarAcceleration(this);
      }
   }

   public static class Rotational2D extends DynamicsModifier.Strength {
      ParallelArray.FloatChannel rotationalVelocity2dChannel;

      public Rotational2D() {
      }

      public Rotational2D(DynamicsModifier.Rotational2D rotation) {
         super(rotation);
      }

      @Override
      public void allocateChannels() {
         super.allocateChannels();
         this.rotationalVelocity2dChannel = this.controller.particles.addChannel(ParticleChannels.AngularVelocity2D);
      }

      @Override
      public void update() {
         int i = 0;
         int l = 2;
         int s = 0;

         for (int c = i + this.controller.particles.size * this.rotationalVelocity2dChannel.strideSize; i < c; l += this.lifeChannel.strideSize) {
            this.rotationalVelocity2dChannel.data[i] = this.rotationalVelocity2dChannel.data[i]
               + (this.strengthChannel.data[s + 0] + this.strengthChannel.data[s + 1] * this.strengthValue.getScale(this.lifeChannel.data[l]));
            s += this.strengthChannel.strideSize;
            i += this.rotationalVelocity2dChannel.strideSize;
         }
      }

      public DynamicsModifier.Rotational2D copy() {
         return new DynamicsModifier.Rotational2D(this);
      }
   }

   public static class Rotational3D extends DynamicsModifier.Angular {
      ParallelArray.FloatChannel rotationChannel;
      ParallelArray.FloatChannel rotationalForceChannel;

      public Rotational3D() {
      }

      public Rotational3D(DynamicsModifier.Rotational3D rotation) {
         super(rotation);
      }

      @Override
      public void allocateChannels() {
         super.allocateChannels();
         this.rotationChannel = this.controller.particles.addChannel(ParticleChannels.Rotation3D);
         this.rotationalForceChannel = this.controller.particles.addChannel(ParticleChannels.AngularVelocity3D);
      }

      @Override
      public void update() {
         int i = 0;
         int l = 2;
         int s = 0;
         int a = 0;

         for (int c = this.controller.particles.size * this.rotationalForceChannel.strideSize; i < c; l += this.lifeChannel.strideSize) {
            float lifePercent = this.lifeChannel.data[l];
            float strength = this.strengthChannel.data[s + 0] + this.strengthChannel.data[s + 1] * this.strengthValue.getScale(lifePercent);
            float phi = this.angularChannel.data[a + 2] + this.angularChannel.data[a + 3] * this.phiValue.getScale(lifePercent);
            float theta = this.angularChannel.data[a + 0] + this.angularChannel.data[a + 1] * this.thetaValue.getScale(lifePercent);
            float cosTheta = MathUtils.cosDeg(theta);
            float sinTheta = MathUtils.sinDeg(theta);
            float cosPhi = MathUtils.cosDeg(phi);
            float sinPhi = MathUtils.sinDeg(phi);
            TMP_V3.set(cosTheta * sinPhi, cosPhi, sinTheta * sinPhi);
            TMP_V3.scl(strength * (float) (Math.PI / 180.0));
            this.rotationalForceChannel.data[i + 0] = this.rotationalForceChannel.data[i + 0] + TMP_V3.x;
            this.rotationalForceChannel.data[i + 1] = this.rotationalForceChannel.data[i + 1] + TMP_V3.y;
            this.rotationalForceChannel.data[i + 2] = this.rotationalForceChannel.data[i + 2] + TMP_V3.z;
            s += this.strengthChannel.strideSize;
            i += this.rotationalForceChannel.strideSize;
            a += this.angularChannel.strideSize;
         }
      }

      public DynamicsModifier.Rotational3D copy() {
         return new DynamicsModifier.Rotational3D(this);
      }
   }

   public abstract static class Strength extends DynamicsModifier {
      protected ParallelArray.FloatChannel strengthChannel;
      public ScaledNumericValue strengthValue = new ScaledNumericValue();

      public Strength() {
      }

      public Strength(DynamicsModifier.Strength rotation) {
         super(rotation);
         this.strengthValue.load(rotation.strengthValue);
      }

      @Override
      public void allocateChannels() {
         super.allocateChannels();
         ParticleChannels.Interpolation.id = this.controller.particleChannels.newId();
         this.strengthChannel = this.controller.particles.addChannel(ParticleChannels.Interpolation);
      }

      @Override
      public void activateParticles(int startIndex, int count) {
         int i = startIndex * this.strengthChannel.strideSize;

         for (int c = i + count * this.strengthChannel.strideSize; i < c; i += this.strengthChannel.strideSize) {
            float start = this.strengthValue.newLowValue();
            float diff = this.strengthValue.newHighValue();
            if (!this.strengthValue.isRelative()) {
               diff -= start;
            }

            this.strengthChannel.data[i + 0] = start;
            this.strengthChannel.data[i + 1] = diff;
         }
      }

      @Override
      public void write(Json json) {
         super.write(json);
         json.writeValue("strengthValue", this.strengthValue);
      }

      @Override
      public void read(Json json, JsonValue jsonData) {
         super.read(json, jsonData);
         this.strengthValue = json.readValue("strengthValue", ScaledNumericValue.class, jsonData);
      }
   }

   public static class TangentialAcceleration extends DynamicsModifier.Angular {
      ParallelArray.FloatChannel directionalVelocityChannel;
      ParallelArray.FloatChannel positionChannel;

      public TangentialAcceleration() {
      }

      public TangentialAcceleration(DynamicsModifier.TangentialAcceleration rotation) {
         super(rotation);
      }

      @Override
      public void allocateChannels() {
         super.allocateChannels();
         this.directionalVelocityChannel = this.controller.particles.addChannel(ParticleChannels.Acceleration);
         this.positionChannel = this.controller.particles.addChannel(ParticleChannels.Position);
      }

      @Override
      public void update() {
         int i = 0;
         int l = 2;
         int s = 0;
         int a = 0;
         int positionOffset = 0;

         for (int c = i + this.controller.particles.size * this.directionalVelocityChannel.strideSize; i < c; positionOffset += this.positionChannel.strideSize) {
            float lifePercent = this.lifeChannel.data[l];
            float strength = this.strengthChannel.data[s + 0] + this.strengthChannel.data[s + 1] * this.strengthValue.getScale(lifePercent);
            float phi = this.angularChannel.data[a + 2] + this.angularChannel.data[a + 3] * this.phiValue.getScale(lifePercent);
            float theta = this.angularChannel.data[a + 0] + this.angularChannel.data[a + 1] * this.thetaValue.getScale(lifePercent);
            float cosTheta = MathUtils.cosDeg(theta);
            float sinTheta = MathUtils.sinDeg(theta);
            float cosPhi = MathUtils.cosDeg(phi);
            float sinPhi = MathUtils.sinDeg(phi);
            TMP_V3.set(cosTheta * sinPhi, cosPhi, sinTheta * sinPhi)
               .crs(this.positionChannel.data[positionOffset + 0], this.positionChannel.data[positionOffset + 1], this.positionChannel.data[positionOffset + 2])
               .nor()
               .scl(strength);
            this.directionalVelocityChannel.data[i + 0] = this.directionalVelocityChannel.data[i + 0] + TMP_V3.x;
            this.directionalVelocityChannel.data[i + 1] = this.directionalVelocityChannel.data[i + 1] + TMP_V3.y;
            this.directionalVelocityChannel.data[i + 2] = this.directionalVelocityChannel.data[i + 2] + TMP_V3.z;
            s += this.strengthChannel.strideSize;
            i += this.directionalVelocityChannel.strideSize;
            a += this.angularChannel.strideSize;
            l += this.lifeChannel.strideSize;
         }
      }

      public DynamicsModifier.TangentialAcceleration copy() {
         return new DynamicsModifier.TangentialAcceleration(this);
      }
   }
}
