package com.esotericsoftware.spine;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;

public class AnimationState {
   private AnimationStateData data;
   private Array<AnimationState.TrackEntry> tracks = new Array<>();
   private final Array<Event> events = new Array<>();
   private final Array<AnimationState.AnimationStateListener> listeners = new Array<>();
   private float timeScale = 1.0F;
   private Pool<AnimationState.TrackEntry> trackEntryPool = new Pool() {
      @Override
      protected Object newObject() {
         return new AnimationState.TrackEntry();
      }
   };

   public AnimationState() {
   }

   public AnimationState(AnimationStateData data) {
      if (data == null) {
         throw new IllegalArgumentException("data cannot be null.");
      } else {
         this.data = data;
      }
   }

   public void update(float delta) {
      delta *= this.timeScale;

      for (int i = 0; i < this.tracks.size; i++) {
         AnimationState.TrackEntry current = this.tracks.get(i);
         if (current != null) {
            AnimationState.TrackEntry next = current.next;
            if (next != null) {
               float nextTime = current.lastTime - next.delay;
               if (nextTime >= 0.0F) {
                  float nextDelta = delta * next.timeScale;
                  next.time = nextTime + nextDelta;
                  current.time = current.time + delta * current.timeScale;
                  this.setCurrent(i, next);
                  next.time -= nextDelta;
                  current = next;
               }
            } else if (!current.loop && current.lastTime >= current.endTime) {
               this.clearTrack(i);
               continue;
            }

            current.time = current.time + delta * current.timeScale;
            if (current.previous != null) {
               float previousDelta = delta * current.previous.timeScale;
               current.previous.time += previousDelta;
               current.mixTime += previousDelta;
            }
         }
      }
   }

   public void apply(Skeleton skeleton) {
      Array<Event> events = this.events;
      int listenerCount = this.listeners.size;

      for (int i = 0; i < this.tracks.size; i++) {
         AnimationState.TrackEntry current = this.tracks.get(i);
         if (current != null) {
            events.size = 0;
            float time = current.time;
            float lastTime = current.lastTime;
            float endTime = current.endTime;
            boolean loop = current.loop;
            if (!loop && time > endTime) {
               time = endTime;
            }

            AnimationState.TrackEntry previous = current.previous;
            if (previous == null) {
               current.animation.mix(skeleton, lastTime, time, loop, events, current.mix);
            } else {
               float previousTime = previous.time;
               if (!previous.loop && previousTime > previous.endTime) {
                  previousTime = previous.endTime;
               }

               previous.animation.apply(skeleton, previousTime, previousTime, previous.loop, null);
               float alpha = current.mixTime / current.mixDuration * current.mix;
               if (alpha >= 1.0F) {
                  alpha = 1.0F;
                  this.trackEntryPool.free(previous);
                  current.previous = null;
               }

               current.animation.mix(skeleton, lastTime, time, loop, events, alpha);
            }

            int ii = 0;

            for (int nn = events.size; ii < nn; ii++) {
               Event event = events.get(ii);
               if (current.listener != null) {
                  current.listener.event(i, event);
               }

               for (int iii = 0; iii < listenerCount; iii++) {
                  this.listeners.get(iii).event(i, event);
               }
            }

            if (loop ? lastTime % endTime > time % endTime : lastTime < endTime && time >= endTime) {
               ii = (int)(time / endTime);
               if (current.listener != null) {
                  current.listener.complete(i, ii);
               }

               int iix = 0;

               for (int nn = this.listeners.size; iix < nn; iix++) {
                  this.listeners.get(iix).complete(i, ii);
               }
            }

            current.lastTime = current.time;
         }
      }
   }

   public void clearTracks() {
      int i = 0;

      for (int n = this.tracks.size; i < n; i++) {
         this.clearTrack(i);
      }

      this.tracks.clear();
   }

   public void clearTrack(int trackIndex) {
      if (trackIndex < this.tracks.size) {
         AnimationState.TrackEntry current = this.tracks.get(trackIndex);
         if (current != null) {
            if (current.listener != null) {
               current.listener.end(trackIndex);
            }

            int i = 0;

            for (int n = this.listeners.size; i < n; i++) {
               this.listeners.get(i).end(trackIndex);
            }

            this.tracks.set(trackIndex, null);
            this.freeAll(current);
            if (current.previous != null) {
               this.trackEntryPool.free(current.previous);
            }
         }
      }
   }

   private void freeAll(AnimationState.TrackEntry entry) {
      while (entry != null) {
         AnimationState.TrackEntry next = entry.next;
         this.trackEntryPool.free(entry);
         entry = next;
      }
   }

   private AnimationState.TrackEntry expandToIndex(int index) {
      if (index < this.tracks.size) {
         return this.tracks.get(index);
      } else {
         this.tracks.ensureCapacity(index - this.tracks.size + 1);
         this.tracks.size = index + 1;
         return null;
      }
   }

   private void setCurrent(int index, AnimationState.TrackEntry entry) {
      AnimationState.TrackEntry current = this.expandToIndex(index);
      if (current != null) {
         AnimationState.TrackEntry previous = current.previous;
         current.previous = null;
         if (current.listener != null) {
            current.listener.end(index);
         }

         int i = 0;

         for (int n = this.listeners.size; i < n; i++) {
            this.listeners.get(i).end(index);
         }

         entry.mixDuration = this.data.getMix(current.animation, entry.animation);
         if (entry.mixDuration > 0.0F) {
            entry.mixTime = 0.0F;
            if (previous != null && current.mixTime / current.mixDuration < 0.5F) {
               entry.previous = previous;
               previous = current;
            } else {
               entry.previous = current;
            }
         } else {
            this.trackEntryPool.free(current);
         }

         if (previous != null) {
            this.trackEntryPool.free(previous);
         }
      }

      this.tracks.set(index, entry);
      if (entry.listener != null) {
         entry.listener.start(index);
      }

      int i = 0;

      for (int n = this.listeners.size; i < n; i++) {
         this.listeners.get(i).start(index);
      }
   }

   public AnimationState.TrackEntry setAnimation(int trackIndex, String animationName, boolean loop) {
      Animation animation = this.data.getSkeletonData().findAnimation(animationName);
      if (animation == null) {
         throw new IllegalArgumentException("Animation not found: " + animationName);
      } else {
         return this.setAnimation(trackIndex, animation, loop);
      }
   }

   public AnimationState.TrackEntry setAnimation(int trackIndex, Animation animation, boolean loop) {
      AnimationState.TrackEntry current = this.expandToIndex(trackIndex);
      if (current != null) {
         this.freeAll(current.next);
      }

      AnimationState.TrackEntry entry = this.trackEntryPool.obtain();
      entry.animation = animation;
      entry.loop = loop;
      entry.endTime = animation.getDuration();
      this.setCurrent(trackIndex, entry);
      return entry;
   }

   public AnimationState.TrackEntry addAnimation(int trackIndex, String animationName, boolean loop, float delay) {
      Animation animation = this.data.getSkeletonData().findAnimation(animationName);
      if (animation == null) {
         throw new IllegalArgumentException("Animation not found: " + animationName);
      } else {
         return this.addAnimation(trackIndex, animation, loop, delay);
      }
   }

   public AnimationState.TrackEntry addAnimation(int trackIndex, Animation animation, boolean loop, float delay) {
      AnimationState.TrackEntry entry = this.trackEntryPool.obtain();
      entry.animation = animation;
      entry.loop = loop;
      entry.endTime = animation.getDuration();
      AnimationState.TrackEntry last = this.expandToIndex(trackIndex);
      if (last == null) {
         this.tracks.set(trackIndex, entry);
      } else {
         while (last.next != null) {
            last = last.next;
         }

         last.next = entry;
      }

      if (delay <= 0.0F) {
         if (last != null) {
            delay += last.endTime - this.data.getMix(last.animation, animation);
         } else {
            delay = 0.0F;
         }
      }

      entry.delay = delay;
      return entry;
   }

   public AnimationState.TrackEntry getCurrent(int trackIndex) {
      return trackIndex >= this.tracks.size ? null : this.tracks.get(trackIndex);
   }

   public void addListener(AnimationState.AnimationStateListener listener) {
      if (listener == null) {
         throw new IllegalArgumentException("listener cannot be null.");
      } else {
         this.listeners.add(listener);
      }
   }

   public void removeListener(AnimationState.AnimationStateListener listener) {
      this.listeners.removeValue(listener, true);
   }

   public void clearListeners() {
      this.listeners.clear();
   }

   public float getTimeScale() {
      return this.timeScale;
   }

   public void setTimeScale(float timeScale) {
      this.timeScale = timeScale;
   }

   public AnimationStateData getData() {
      return this.data;
   }

   public void setData(AnimationStateData data) {
      this.data = data;
   }

   public Array<AnimationState.TrackEntry> getTracks() {
      return this.tracks;
   }

   @Override
   public String toString() {
      StringBuilder buffer = new StringBuilder(64);
      int i = 0;

      for (int n = this.tracks.size; i < n; i++) {
         AnimationState.TrackEntry entry = this.tracks.get(i);
         if (entry != null) {
            if (buffer.length() > 0) {
               buffer.append(", ");
            }

            buffer.append(entry.toString());
         }
      }

      return buffer.length() == 0 ? "<none>" : buffer.toString();
   }

   public abstract static class AnimationStateAdapter implements AnimationState.AnimationStateListener {
      @Override
      public void event(int trackIndex, Event event) {
      }

      @Override
      public void complete(int trackIndex, int loopCount) {
      }

      @Override
      public void start(int trackIndex) {
      }

      @Override
      public void end(int trackIndex) {
      }
   }

   public interface AnimationStateListener {
      void event(int var1, Event var2);

      void complete(int var1, int var2);

      void start(int var1);

      void end(int var1);
   }

   public static class TrackEntry implements Pool.Poolable {
      AnimationState.TrackEntry next;
      AnimationState.TrackEntry previous;
      Animation animation;
      boolean loop;
      float delay;
      float time;
      float lastTime = -1.0F;
      float endTime;
      float timeScale = 1.0F;
      float mixTime;
      float mixDuration;
      AnimationState.AnimationStateListener listener;
      float mix = 1.0F;

      @Override
      public void reset() {
         this.next = null;
         this.previous = null;
         this.animation = null;
         this.listener = null;
         this.timeScale = 1.0F;
         this.lastTime = -1.0F;
         this.time = 0.0F;
      }

      public Animation getAnimation() {
         return this.animation;
      }

      public void setAnimation(Animation animation) {
         this.animation = animation;
      }

      public boolean getLoop() {
         return this.loop;
      }

      public void setLoop(boolean loop) {
         this.loop = loop;
      }

      public float getDelay() {
         return this.delay;
      }

      public void setDelay(float delay) {
         this.delay = delay;
      }

      public float getTime() {
         return this.time;
      }

      public void setTime(float time) {
         this.time = time;
      }

      public float getEndTime() {
         return this.endTime;
      }

      public void setEndTime(float endTime) {
         this.endTime = endTime;
      }

      public AnimationState.AnimationStateListener getListener() {
         return this.listener;
      }

      public void setListener(AnimationState.AnimationStateListener listener) {
         this.listener = listener;
      }

      public float getLastTime() {
         return this.lastTime;
      }

      public void setLastTime(float lastTime) {
         this.lastTime = lastTime;
      }

      public float getMix() {
         return this.mix;
      }

      public void setMix(float mix) {
         this.mix = mix;
      }

      public float getTimeScale() {
         return this.timeScale;
      }

      public void setTimeScale(float timeScale) {
         this.timeScale = timeScale;
      }

      public AnimationState.TrackEntry getNext() {
         return this.next;
      }

      public void setNext(AnimationState.TrackEntry next) {
         this.next = next;
      }

      public boolean isComplete() {
         return this.time >= this.endTime;
      }

      @Override
      public String toString() {
         return this.animation == null ? "<none>" : this.animation.name;
      }
   }
}
