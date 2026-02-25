package com.esotericsoftware.spine.utils;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import com.esotericsoftware.spine.AnimationState;
import com.esotericsoftware.spine.AnimationStateData;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.SkeletonRenderer;
import com.esotericsoftware.spine.Skin;

public class SkeletonActorPool extends Pool<SkeletonActor> {
   private SkeletonRenderer renderer;
   SkeletonData skeletonData;
   AnimationStateData stateData;
   private final Pool<Skeleton> skeletonPool;
   private final Pool<AnimationState> statePool;
   private final Array<SkeletonActor> obtained;

   public SkeletonActorPool(SkeletonRenderer renderer, SkeletonData skeletonData, AnimationStateData stateData) {
      this(renderer, skeletonData, stateData, 16, Integer.MAX_VALUE);
   }

   public SkeletonActorPool(SkeletonRenderer renderer, SkeletonData skeletonData, AnimationStateData stateData, int initialCapacity, int max) {
      super(initialCapacity, max);
      this.renderer = renderer;
      this.skeletonData = skeletonData;
      this.stateData = stateData;
      this.obtained = new Array<>(false, initialCapacity);
      this.skeletonPool = new Pool<Skeleton>(initialCapacity, max) {
         protected Skeleton newObject() {
            return new Skeleton(SkeletonActorPool.this.skeletonData);
         }

         protected void reset(Skeleton skeleton) {
            skeleton.setColor(Color.WHITE);
            skeleton.setFlip(false, false);
            skeleton.setSkin((Skin)null);
            skeleton.setSkin(SkeletonActorPool.this.skeletonData.getDefaultSkin());
            skeleton.setToSetupPose();
         }
      };
      this.statePool = new Pool<AnimationState>(initialCapacity, max) {
         protected AnimationState newObject() {
            return new AnimationState(SkeletonActorPool.this.stateData);
         }

         protected void reset(AnimationState state) {
            state.clearTracks();
            state.clearListeners();
         }
      };
   }

   public void freeComplete() {
      Array<SkeletonActor> obtained = this.obtained;

      label24:
      for (int i = obtained.size - 1; i >= 0; i--) {
         SkeletonActor actor = obtained.get(i);
         Array<AnimationState.TrackEntry> tracks = actor.state.getTracks();
         int ii = 0;

         for (int nn = tracks.size; ii < nn; ii++) {
            if (tracks.get(ii) != null) {
               continue label24;
            }
         }

         this.free(actor);
      }
   }

   protected SkeletonActor newObject() {
      SkeletonActor actor = new SkeletonActor();
      actor.setRenderer(this.renderer);
      return actor;
   }

   public SkeletonActor obtain() {
      SkeletonActor actor = (SkeletonActor)super.obtain();
      actor.setSkeleton(this.skeletonPool.obtain());
      actor.setAnimationState(this.statePool.obtain());
      this.obtained.add(actor);
      return actor;
   }

   protected void reset(SkeletonActor actor) {
      actor.remove();
      this.obtained.removeValue(actor, true);
      this.skeletonPool.free(actor.getSkeleton());
      this.statePool.free(actor.getAnimationState());
   }

   public Array<SkeletonActor> getObtained() {
      return this.obtained;
   }
}
