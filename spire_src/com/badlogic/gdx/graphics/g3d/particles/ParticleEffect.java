package com.badlogic.gdx.graphics.g3d.particles;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.g3d.particles.batches.ParticleBatch;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;

public class ParticleEffect implements Disposable, ResourceData.Configurable {
   private Array<ParticleController> controllers;
   private BoundingBox bounds;

   public ParticleEffect() {
      this.controllers = new Array<>(true, 3, ParticleController.class);
   }

   public ParticleEffect(ParticleEffect effect) {
      this.controllers = new Array<>(true, effect.controllers.size);
      int i = 0;

      for (int n = effect.controllers.size; i < n; i++) {
         this.controllers.add(effect.controllers.get(i).copy());
      }
   }

   public ParticleEffect(ParticleController... emitters) {
      this.controllers = new Array<>(emitters);
   }

   public void init() {
      int i = 0;

      for (int n = this.controllers.size; i < n; i++) {
         this.controllers.get(i).init();
      }
   }

   public void start() {
      int i = 0;

      for (int n = this.controllers.size; i < n; i++) {
         this.controllers.get(i).start();
      }
   }

   public void end() {
      int i = 0;

      for (int n = this.controllers.size; i < n; i++) {
         this.controllers.get(i).end();
      }
   }

   public void reset() {
      int i = 0;

      for (int n = this.controllers.size; i < n; i++) {
         this.controllers.get(i).reset();
      }
   }

   public void update() {
      int i = 0;

      for (int n = this.controllers.size; i < n; i++) {
         this.controllers.get(i).update();
      }
   }

   public void draw() {
      int i = 0;

      for (int n = this.controllers.size; i < n; i++) {
         this.controllers.get(i).draw();
      }
   }

   public boolean isComplete() {
      int i = 0;

      for (int n = this.controllers.size; i < n; i++) {
         if (!this.controllers.get(i).isComplete()) {
            return false;
         }
      }

      return true;
   }

   public void setTransform(Matrix4 transform) {
      int i = 0;

      for (int n = this.controllers.size; i < n; i++) {
         this.controllers.get(i).setTransform(transform);
      }
   }

   public void rotate(Quaternion rotation) {
      int i = 0;

      for (int n = this.controllers.size; i < n; i++) {
         this.controllers.get(i).rotate(rotation);
      }
   }

   public void rotate(Vector3 axis, float angle) {
      int i = 0;

      for (int n = this.controllers.size; i < n; i++) {
         this.controllers.get(i).rotate(axis, angle);
      }
   }

   public void translate(Vector3 translation) {
      int i = 0;

      for (int n = this.controllers.size; i < n; i++) {
         this.controllers.get(i).translate(translation);
      }
   }

   public void scale(float scaleX, float scaleY, float scaleZ) {
      int i = 0;

      for (int n = this.controllers.size; i < n; i++) {
         this.controllers.get(i).scale(scaleX, scaleY, scaleZ);
      }
   }

   public void scale(Vector3 scale) {
      int i = 0;

      for (int n = this.controllers.size; i < n; i++) {
         this.controllers.get(i).scale(scale.x, scale.y, scale.z);
      }
   }

   public Array<ParticleController> getControllers() {
      return this.controllers;
   }

   public ParticleController findController(String name) {
      int i = 0;

      for (int n = this.controllers.size; i < n; i++) {
         ParticleController emitter = this.controllers.get(i);
         if (emitter.name.equals(name)) {
            return emitter;
         }
      }

      return null;
   }

   @Override
   public void dispose() {
      int i = 0;

      for (int n = this.controllers.size; i < n; i++) {
         this.controllers.get(i).dispose();
      }
   }

   public BoundingBox getBoundingBox() {
      if (this.bounds == null) {
         this.bounds = new BoundingBox();
      }

      BoundingBox bounds = this.bounds;
      bounds.inf();

      for (ParticleController emitter : this.controllers) {
         bounds.ext(emitter.getBoundingBox());
      }

      return bounds;
   }

   public void setBatch(Array<ParticleBatch<?>> batches) {
      for (ParticleController controller : this.controllers) {
         for (ParticleBatch<?> batch : batches) {
            if (controller.renderer.setBatch(batch)) {
               break;
            }
         }
      }
   }

   public ParticleEffect copy() {
      return new ParticleEffect(this);
   }

   @Override
   public void save(AssetManager assetManager, ResourceData data) {
      for (ParticleController controller : this.controllers) {
         controller.save(assetManager, data);
      }
   }

   @Override
   public void load(AssetManager assetManager, ResourceData data) {
      int i = 0;

      for (ParticleController controller : this.controllers) {
         controller.load(assetManager, data);
      }
   }
}
