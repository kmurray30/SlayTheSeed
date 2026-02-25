package com.badlogic.gdx.graphics.g2d;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;

public class Animation<T> {
   T[] keyFrames;
   private float frameDuration;
   private float animationDuration;
   private int lastFrameNumber;
   private float lastStateTime;
   private Animation.PlayMode playMode = Animation.PlayMode.NORMAL;

   public Animation(float frameDuration, Array<? extends T> keyFrames) {
      this.frameDuration = frameDuration;
      T[] frames = (T[])(new Object[keyFrames.size]);
      int i = 0;

      for (int n = keyFrames.size; i < n; i++) {
         frames[i] = (T)keyFrames.get(i);
      }

      this.setKeyFrames(frames);
   }

   public Animation(float frameDuration, Array<? extends T> keyFrames, Animation.PlayMode playMode) {
      this(frameDuration, keyFrames);
      this.setPlayMode(playMode);
   }

   public Animation(float frameDuration, T... keyFrames) {
      this.frameDuration = frameDuration;
      this.setKeyFrames(keyFrames);
   }

   public T getKeyFrame(float stateTime, boolean looping) {
      Animation.PlayMode oldPlayMode = this.playMode;
      if (looping && (this.playMode == Animation.PlayMode.NORMAL || this.playMode == Animation.PlayMode.REVERSED)) {
         if (this.playMode == Animation.PlayMode.NORMAL) {
            this.playMode = Animation.PlayMode.LOOP;
         } else {
            this.playMode = Animation.PlayMode.LOOP_REVERSED;
         }
      } else if (!looping && this.playMode != Animation.PlayMode.NORMAL && this.playMode != Animation.PlayMode.REVERSED) {
         if (this.playMode == Animation.PlayMode.LOOP_REVERSED) {
            this.playMode = Animation.PlayMode.REVERSED;
         } else {
            this.playMode = Animation.PlayMode.LOOP;
         }
      }

      T frame = this.getKeyFrame(stateTime);
      this.playMode = oldPlayMode;
      return frame;
   }

   public T getKeyFrame(float stateTime) {
      int frameNumber = this.getKeyFrameIndex(stateTime);
      return this.keyFrames[frameNumber];
   }

   public int getKeyFrameIndex(float stateTime) {
      if (this.keyFrames.length == 1) {
         return 0;
      } else {
         int frameNumber = (int)(stateTime / this.frameDuration);
         switch (this.playMode) {
            case NORMAL:
               frameNumber = Math.min(this.keyFrames.length - 1, frameNumber);
               break;
            case LOOP:
               frameNumber %= this.keyFrames.length;
               break;
            case LOOP_PINGPONG:
               frameNumber %= this.keyFrames.length * 2 - 2;
               if (frameNumber >= this.keyFrames.length) {
                  frameNumber = this.keyFrames.length - 2 - (frameNumber - this.keyFrames.length);
               }
               break;
            case LOOP_RANDOM:
               int lastFrameNumber = (int)(this.lastStateTime / this.frameDuration);
               if (lastFrameNumber != frameNumber) {
                  frameNumber = MathUtils.random(this.keyFrames.length - 1);
               } else {
                  frameNumber = this.lastFrameNumber;
               }
               break;
            case REVERSED:
               frameNumber = Math.max(this.keyFrames.length - frameNumber - 1, 0);
               break;
            case LOOP_REVERSED:
               frameNumber %= this.keyFrames.length;
               frameNumber = this.keyFrames.length - frameNumber - 1;
         }

         this.lastFrameNumber = frameNumber;
         this.lastStateTime = stateTime;
         return frameNumber;
      }
   }

   public T[] getKeyFrames() {
      return this.keyFrames;
   }

   protected void setKeyFrames(T... keyFrames) {
      this.keyFrames = keyFrames;
      this.animationDuration = keyFrames.length * this.frameDuration;
   }

   public Animation.PlayMode getPlayMode() {
      return this.playMode;
   }

   public void setPlayMode(Animation.PlayMode playMode) {
      this.playMode = playMode;
   }

   public boolean isAnimationFinished(float stateTime) {
      int frameNumber = (int)(stateTime / this.frameDuration);
      return this.keyFrames.length - 1 < frameNumber;
   }

   public void setFrameDuration(float frameDuration) {
      this.frameDuration = frameDuration;
      this.animationDuration = this.keyFrames.length * frameDuration;
   }

   public float getFrameDuration() {
      return this.frameDuration;
   }

   public float getAnimationDuration() {
      return this.animationDuration;
   }

   public static enum PlayMode {
      NORMAL,
      REVERSED,
      LOOP,
      LOOP_REVERSED,
      LOOP_PINGPONG,
      LOOP_RANDOM;
   }
}
