package com.badlogic.gdx.graphics.g3d.utils;

import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.model.Animation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.Pool;

public class AnimationController extends BaseAnimationController {
   protected final Pool<AnimationController.AnimationDesc> animationPool = new Pool<AnimationController.AnimationDesc>() {
      protected AnimationController.AnimationDesc newObject() {
         return new AnimationController.AnimationDesc();
      }
   };
   public AnimationController.AnimationDesc current;
   public AnimationController.AnimationDesc queued;
   public float queuedTransitionTime;
   public AnimationController.AnimationDesc previous;
   public float transitionCurrentTime;
   public float transitionTargetTime;
   public boolean inAction;
   public boolean paused;
   public boolean allowSameAnimation;
   private boolean justChangedAnimation = false;

   public AnimationController(ModelInstance target) {
      super(target);
   }

   private AnimationController.AnimationDesc obtain(
      Animation anim, float offset, float duration, int loopCount, float speed, AnimationController.AnimationListener listener
   ) {
      if (anim == null) {
         return null;
      } else {
         AnimationController.AnimationDesc result = this.animationPool.obtain();
         result.animation = anim;
         result.listener = listener;
         result.loopCount = loopCount;
         result.speed = speed;
         result.offset = offset;
         result.duration = duration < 0.0F ? anim.duration - offset : duration;
         result.time = speed < 0.0F ? result.duration : 0.0F;
         return result;
      }
   }

   private AnimationController.AnimationDesc obtain(
      String id, float offset, float duration, int loopCount, float speed, AnimationController.AnimationListener listener
   ) {
      if (id == null) {
         return null;
      } else {
         Animation anim = this.target.getAnimation(id);
         if (anim == null) {
            throw new GdxRuntimeException("Unknown animation: " + id);
         } else {
            return this.obtain(anim, offset, duration, loopCount, speed, listener);
         }
      }
   }

   private AnimationController.AnimationDesc obtain(AnimationController.AnimationDesc anim) {
      return this.obtain(anim.animation, anim.offset, anim.duration, anim.loopCount, anim.speed, anim.listener);
   }

   public void update(float delta) {
      if (!this.paused) {
         if (this.previous != null && (this.transitionCurrentTime += delta) >= this.transitionTargetTime) {
            this.removeAnimation(this.previous.animation);
            this.justChangedAnimation = true;
            this.animationPool.free(this.previous);
            this.previous = null;
         }

         if (this.justChangedAnimation) {
            this.target.calculateTransforms();
            this.justChangedAnimation = false;
         }

         if (this.current != null && this.current.loopCount != 0 && this.current.animation != null) {
            float remain = this.current.update(delta);
            if (remain != 0.0F && this.queued != null) {
               this.inAction = false;
               this.animate(this.queued, this.queuedTransitionTime);
               this.queued = null;
               this.update(remain);
            } else {
               if (this.previous != null) {
                  this.applyAnimations(
                     this.previous.animation,
                     this.previous.offset + this.previous.time,
                     this.current.animation,
                     this.current.offset + this.current.time,
                     this.transitionCurrentTime / this.transitionTargetTime
                  );
               } else {
                  this.applyAnimation(this.current.animation, this.current.offset + this.current.time);
               }
            }
         }
      }
   }

   public AnimationController.AnimationDesc setAnimation(String id) {
      return this.setAnimation(id, 1, 1.0F, null);
   }

   public AnimationController.AnimationDesc setAnimation(String id, int loopCount) {
      return this.setAnimation(id, loopCount, 1.0F, null);
   }

   public AnimationController.AnimationDesc setAnimation(String id, AnimationController.AnimationListener listener) {
      return this.setAnimation(id, 1, 1.0F, listener);
   }

   public AnimationController.AnimationDesc setAnimation(String id, int loopCount, AnimationController.AnimationListener listener) {
      return this.setAnimation(id, loopCount, 1.0F, listener);
   }

   public AnimationController.AnimationDesc setAnimation(String id, int loopCount, float speed, AnimationController.AnimationListener listener) {
      return this.setAnimation(id, 0.0F, -1.0F, loopCount, speed, listener);
   }

   public AnimationController.AnimationDesc setAnimation(
      String id, float offset, float duration, int loopCount, float speed, AnimationController.AnimationListener listener
   ) {
      return this.setAnimation(this.obtain(id, offset, duration, loopCount, speed, listener));
   }

   protected AnimationController.AnimationDesc setAnimation(
      Animation anim, float offset, float duration, int loopCount, float speed, AnimationController.AnimationListener listener
   ) {
      return this.setAnimation(this.obtain(anim, offset, duration, loopCount, speed, listener));
   }

   protected AnimationController.AnimationDesc setAnimation(AnimationController.AnimationDesc anim) {
      if (this.current == null) {
         this.current = anim;
      } else {
         if (!this.allowSameAnimation && anim != null && this.current.animation == anim.animation) {
            anim.time = this.current.time;
         } else {
            this.removeAnimation(this.current.animation);
         }

         this.animationPool.free(this.current);
         this.current = anim;
      }

      this.justChangedAnimation = true;
      return anim;
   }

   public AnimationController.AnimationDesc animate(String id, float transitionTime) {
      return this.animate(id, 1, 1.0F, null, transitionTime);
   }

   public AnimationController.AnimationDesc animate(String id, AnimationController.AnimationListener listener, float transitionTime) {
      return this.animate(id, 1, 1.0F, listener, transitionTime);
   }

   public AnimationController.AnimationDesc animate(String id, int loopCount, AnimationController.AnimationListener listener, float transitionTime) {
      return this.animate(id, loopCount, 1.0F, listener, transitionTime);
   }

   public AnimationController.AnimationDesc animate(String id, int loopCount, float speed, AnimationController.AnimationListener listener, float transitionTime) {
      return this.animate(id, 0.0F, -1.0F, loopCount, speed, listener, transitionTime);
   }

   public AnimationController.AnimationDesc animate(
      String id, float offset, float duration, int loopCount, float speed, AnimationController.AnimationListener listener, float transitionTime
   ) {
      return this.animate(this.obtain(id, offset, duration, loopCount, speed, listener), transitionTime);
   }

   protected AnimationController.AnimationDesc animate(
      Animation anim, float offset, float duration, int loopCount, float speed, AnimationController.AnimationListener listener, float transitionTime
   ) {
      return this.animate(this.obtain(anim, offset, duration, loopCount, speed, listener), transitionTime);
   }

   protected AnimationController.AnimationDesc animate(AnimationController.AnimationDesc anim, float transitionTime) {
      if (this.current == null) {
         this.current = anim;
      } else if (this.inAction) {
         this.queue(anim, transitionTime);
      } else if (!this.allowSameAnimation && anim != null && this.current.animation == anim.animation) {
         anim.time = this.current.time;
         this.animationPool.free(this.current);
         this.current = anim;
      } else {
         if (this.previous != null) {
            this.removeAnimation(this.previous.animation);
            this.animationPool.free(this.previous);
         }

         this.previous = this.current;
         this.current = anim;
         this.transitionCurrentTime = 0.0F;
         this.transitionTargetTime = transitionTime;
      }

      return anim;
   }

   public AnimationController.AnimationDesc queue(String id, int loopCount, float speed, AnimationController.AnimationListener listener, float transitionTime) {
      return this.queue(id, 0.0F, -1.0F, loopCount, speed, listener, transitionTime);
   }

   public AnimationController.AnimationDesc queue(
      String id, float offset, float duration, int loopCount, float speed, AnimationController.AnimationListener listener, float transitionTime
   ) {
      return this.queue(this.obtain(id, offset, duration, loopCount, speed, listener), transitionTime);
   }

   protected AnimationController.AnimationDesc queue(
      Animation anim, float offset, float duration, int loopCount, float speed, AnimationController.AnimationListener listener, float transitionTime
   ) {
      return this.queue(this.obtain(anim, offset, duration, loopCount, speed, listener), transitionTime);
   }

   protected AnimationController.AnimationDesc queue(AnimationController.AnimationDesc anim, float transitionTime) {
      if (this.current != null && this.current.loopCount != 0) {
         if (this.queued != null) {
            this.animationPool.free(this.queued);
         }

         this.queued = anim;
         this.queuedTransitionTime = transitionTime;
         if (this.current.loopCount < 0) {
            this.current.loopCount = 1;
         }
      } else {
         this.animate(anim, transitionTime);
      }

      return anim;
   }

   public AnimationController.AnimationDesc action(String id, int loopCount, float speed, AnimationController.AnimationListener listener, float transitionTime) {
      return this.action(id, 0.0F, -1.0F, loopCount, speed, listener, transitionTime);
   }

   public AnimationController.AnimationDesc action(
      String id, float offset, float duration, int loopCount, float speed, AnimationController.AnimationListener listener, float transitionTime
   ) {
      return this.action(this.obtain(id, offset, duration, loopCount, speed, listener), transitionTime);
   }

   protected AnimationController.AnimationDesc action(
      Animation anim, float offset, float duration, int loopCount, float speed, AnimationController.AnimationListener listener, float transitionTime
   ) {
      return this.action(this.obtain(anim, offset, duration, loopCount, speed, listener), transitionTime);
   }

   protected AnimationController.AnimationDesc action(AnimationController.AnimationDesc anim, float transitionTime) {
      if (anim.loopCount < 0) {
         throw new GdxRuntimeException("An action cannot be continuous");
      } else {
         if (this.current != null && this.current.loopCount != 0) {
            AnimationController.AnimationDesc toQueue = this.inAction ? null : this.obtain(this.current);
            this.inAction = false;
            this.animate(anim, transitionTime);
            this.inAction = true;
            if (toQueue != null) {
               this.queue(toQueue, transitionTime);
            }
         } else {
            this.animate(anim, transitionTime);
         }

         return anim;
      }
   }

   public static class AnimationDesc {
      public AnimationController.AnimationListener listener;
      public Animation animation;
      public float speed;
      public float time;
      public float offset;
      public float duration;
      public int loopCount;

      protected AnimationDesc() {
      }

      protected float update(float delta) {
         if (this.loopCount != 0 && this.animation != null) {
            float diff = this.speed * delta;
            int loops;
            if (!MathUtils.isZero(this.duration)) {
               this.time += diff;
               loops = (int)Math.abs(this.time / this.duration);
               if (this.time < 0.0F) {
                  loops++;

                  while (this.time < 0.0F) {
                     this.time = this.time + this.duration;
                  }
               }

               this.time = Math.abs(this.time % this.duration);
            } else {
               loops = 1;
            }

            for (int i = 0; i < loops; i++) {
               if (this.loopCount > 0) {
                  this.loopCount--;
               }

               if (this.loopCount != 0 && this.listener != null) {
                  this.listener.onLoop(this);
               }

               if (this.loopCount == 0) {
                  float result = (loops - 1 - i) * this.duration + (diff < 0.0F ? this.duration - this.time : this.time);
                  this.time = diff < 0.0F ? 0.0F : this.duration;
                  if (this.listener != null) {
                     this.listener.onEnd(this);
                  }

                  return result;
               }
            }

            return 0.0F;
         } else {
            return delta;
         }
      }
   }

   public interface AnimationListener {
      void onEnd(AnimationController.AnimationDesc var1);

      void onLoop(AnimationController.AnimationDesc var1);
   }
}
