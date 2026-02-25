package com.esotericsoftware.spine;

import com.badlogic.gdx.utils.ObjectFloatMap;

public class AnimationStateData {
   private final SkeletonData skeletonData;
   final ObjectFloatMap<AnimationStateData.Key> animationToMixTime = new ObjectFloatMap<>();
   final AnimationStateData.Key tempKey = new AnimationStateData.Key();
   float defaultMix;

   public AnimationStateData(SkeletonData skeletonData) {
      if (skeletonData == null) {
         throw new IllegalArgumentException("skeletonData cannot be null.");
      } else {
         this.skeletonData = skeletonData;
      }
   }

   public SkeletonData getSkeletonData() {
      return this.skeletonData;
   }

   public void setMix(String fromName, String toName, float duration) {
      Animation from = this.skeletonData.findAnimation(fromName);
      if (from == null) {
         throw new IllegalArgumentException("Animation not found: " + fromName);
      } else {
         Animation to = this.skeletonData.findAnimation(toName);
         if (to == null) {
            throw new IllegalArgumentException("Animation not found: " + toName);
         } else {
            this.setMix(from, to, duration);
         }
      }
   }

   public void setMix(Animation from, Animation to, float duration) {
      if (from == null) {
         throw new IllegalArgumentException("from cannot be null.");
      } else if (to == null) {
         throw new IllegalArgumentException("to cannot be null.");
      } else {
         AnimationStateData.Key key = new AnimationStateData.Key();
         key.a1 = from;
         key.a2 = to;
         this.animationToMixTime.put(key, duration);
      }
   }

   public float getMix(Animation from, Animation to) {
      this.tempKey.a1 = from;
      this.tempKey.a2 = to;
      return this.animationToMixTime.get(this.tempKey, this.defaultMix);
   }

   public float getDefaultMix() {
      return this.defaultMix;
   }

   public void setDefaultMix(float defaultMix) {
      this.defaultMix = defaultMix;
   }

   static class Key {
      Animation a1;
      Animation a2;

      @Override
      public int hashCode() {
         return 31 * (31 + this.a1.hashCode()) + this.a2.hashCode();
      }

      @Override
      public boolean equals(Object obj) {
         if (this == obj) {
            return true;
         } else if (obj == null) {
            return false;
         } else {
            AnimationStateData.Key other = (AnimationStateData.Key)obj;
            if (this.a1 == null) {
               if (other.a1 != null) {
                  return false;
               }
            } else if (!this.a1.equals(other.a1)) {
               return false;
            }

            if (this.a2 == null) {
               if (other.a2 != null) {
                  return false;
               }
            } else if (!this.a2.equals(other.a2)) {
               return false;
            }

            return true;
         }
      }
   }
}
