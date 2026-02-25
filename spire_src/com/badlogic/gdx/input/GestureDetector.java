package com.badlogic.gdx.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.TimeUtils;
import com.badlogic.gdx.utils.Timer;

public class GestureDetector extends InputAdapter {
   final GestureDetector.GestureListener listener;
   private float tapSquareSize;
   private long tapCountInterval;
   private float longPressSeconds;
   private long maxFlingDelay;
   private boolean inTapSquare;
   private int tapCount;
   private long lastTapTime;
   private float lastTapX;
   private float lastTapY;
   private int lastTapButton;
   private int lastTapPointer;
   boolean longPressFired;
   private boolean pinching;
   private boolean panning;
   private final GestureDetector.VelocityTracker tracker = new GestureDetector.VelocityTracker();
   private float tapSquareCenterX;
   private float tapSquareCenterY;
   private long gestureStartTime;
   Vector2 pointer1 = new Vector2();
   private final Vector2 pointer2 = new Vector2();
   private final Vector2 initialPointer1 = new Vector2();
   private final Vector2 initialPointer2 = new Vector2();
   private final Timer.Task longPressTask = new Timer.Task() {
      @Override
      public void run() {
         if (!GestureDetector.this.longPressFired) {
            GestureDetector.this.longPressFired = GestureDetector.this.listener.longPress(GestureDetector.this.pointer1.x, GestureDetector.this.pointer1.y);
         }
      }
   };

   public GestureDetector(GestureDetector.GestureListener listener) {
      this(20.0F, 0.4F, 1.1F, 0.15F, listener);
   }

   public GestureDetector(
      float halfTapSquareSize, float tapCountInterval, float longPressDuration, float maxFlingDelay, GestureDetector.GestureListener listener
   ) {
      this.tapSquareSize = halfTapSquareSize;
      this.tapCountInterval = (long)(tapCountInterval * 1.0E9F);
      this.longPressSeconds = longPressDuration;
      this.maxFlingDelay = (long)(maxFlingDelay * 1.0E9F);
      this.listener = listener;
   }

   @Override
   public boolean touchDown(int x, int y, int pointer, int button) {
      return this.touchDown((float)x, (float)y, pointer, button);
   }

   public boolean touchDown(float x, float y, int pointer, int button) {
      if (pointer > 1) {
         return false;
      } else {
         if (pointer == 0) {
            this.pointer1.set(x, y);
            this.gestureStartTime = Gdx.input.getCurrentEventTime();
            this.tracker.start(x, y, this.gestureStartTime);
            if (Gdx.input.isTouched(1)) {
               this.inTapSquare = false;
               this.pinching = true;
               this.initialPointer1.set(this.pointer1);
               this.initialPointer2.set(this.pointer2);
               this.longPressTask.cancel();
            } else {
               this.inTapSquare = true;
               this.pinching = false;
               this.longPressFired = false;
               this.tapSquareCenterX = x;
               this.tapSquareCenterY = y;
               if (!this.longPressTask.isScheduled()) {
                  Timer.schedule(this.longPressTask, this.longPressSeconds);
               }
            }
         } else {
            this.pointer2.set(x, y);
            this.inTapSquare = false;
            this.pinching = true;
            this.initialPointer1.set(this.pointer1);
            this.initialPointer2.set(this.pointer2);
            this.longPressTask.cancel();
         }

         return this.listener.touchDown(x, y, pointer, button);
      }
   }

   @Override
   public boolean touchDragged(int x, int y, int pointer) {
      return this.touchDragged((float)x, (float)y, pointer);
   }

   public boolean touchDragged(float x, float y, int pointer) {
      if (pointer > 1) {
         return false;
      } else if (this.longPressFired) {
         return false;
      } else {
         if (pointer == 0) {
            this.pointer1.set(x, y);
         } else {
            this.pointer2.set(x, y);
         }

         if (this.pinching) {
            if (this.listener == null) {
               return false;
            } else {
               boolean result = this.listener.pinch(this.initialPointer1, this.initialPointer2, this.pointer1, this.pointer2);
               return this.listener.zoom(this.initialPointer1.dst(this.initialPointer2), this.pointer1.dst(this.pointer2)) || result;
            }
         } else {
            this.tracker.update(x, y, Gdx.input.getCurrentEventTime());
            if (this.inTapSquare && !this.isWithinTapSquare(x, y, this.tapSquareCenterX, this.tapSquareCenterY)) {
               this.longPressTask.cancel();
               this.inTapSquare = false;
            }

            if (!this.inTapSquare) {
               this.panning = true;
               return this.listener.pan(x, y, this.tracker.deltaX, this.tracker.deltaY);
            } else {
               return false;
            }
         }
      }
   }

   @Override
   public boolean touchUp(int x, int y, int pointer, int button) {
      return this.touchUp((float)x, (float)y, pointer, button);
   }

   public boolean touchUp(float x, float y, int pointer, int button) {
      if (pointer > 1) {
         return false;
      } else {
         if (this.inTapSquare && !this.isWithinTapSquare(x, y, this.tapSquareCenterX, this.tapSquareCenterY)) {
            this.inTapSquare = false;
         }

         boolean wasPanning = this.panning;
         this.panning = false;
         this.longPressTask.cancel();
         if (this.longPressFired) {
            return false;
         } else if (this.inTapSquare) {
            if (this.lastTapButton != button
               || this.lastTapPointer != pointer
               || TimeUtils.nanoTime() - this.lastTapTime > this.tapCountInterval
               || !this.isWithinTapSquare(x, y, this.lastTapX, this.lastTapY)) {
               this.tapCount = 0;
            }

            this.tapCount++;
            this.lastTapTime = TimeUtils.nanoTime();
            this.lastTapX = x;
            this.lastTapY = y;
            this.lastTapButton = button;
            this.lastTapPointer = pointer;
            this.gestureStartTime = 0L;
            return this.listener.tap(x, y, this.tapCount, button);
         } else if (this.pinching) {
            this.pinching = false;
            this.listener.pinchStop();
            this.panning = true;
            if (pointer == 0) {
               this.tracker.start(this.pointer2.x, this.pointer2.y, Gdx.input.getCurrentEventTime());
            } else {
               this.tracker.start(this.pointer1.x, this.pointer1.y, Gdx.input.getCurrentEventTime());
            }

            return false;
         } else {
            boolean handled = false;
            if (wasPanning && !this.panning) {
               handled = this.listener.panStop(x, y, pointer, button);
            }

            this.gestureStartTime = 0L;
            long time = Gdx.input.getCurrentEventTime();
            if (time - this.tracker.lastTime < this.maxFlingDelay) {
               this.tracker.update(x, y, time);
               handled = this.listener.fling(this.tracker.getVelocityX(), this.tracker.getVelocityY(), button) || handled;
            }

            return handled;
         }
      }
   }

   public void cancel() {
      this.longPressTask.cancel();
      this.longPressFired = true;
   }

   public boolean isLongPressed() {
      return this.isLongPressed(this.longPressSeconds);
   }

   public boolean isLongPressed(float duration) {
      return this.gestureStartTime == 0L ? false : TimeUtils.nanoTime() - this.gestureStartTime > (long)(duration * 1.0E9F);
   }

   public boolean isPanning() {
      return this.panning;
   }

   public void reset() {
      this.gestureStartTime = 0L;
      this.panning = false;
      this.inTapSquare = false;
   }

   private boolean isWithinTapSquare(float x, float y, float centerX, float centerY) {
      return Math.abs(x - centerX) < this.tapSquareSize && Math.abs(y - centerY) < this.tapSquareSize;
   }

   public void invalidateTapSquare() {
      this.inTapSquare = false;
   }

   public void setTapSquareSize(float halfTapSquareSize) {
      this.tapSquareSize = halfTapSquareSize;
   }

   public void setTapCountInterval(float tapCountInterval) {
      this.tapCountInterval = (long)(tapCountInterval * 1.0E9F);
   }

   public void setLongPressSeconds(float longPressSeconds) {
      this.longPressSeconds = longPressSeconds;
   }

   public void setMaxFlingDelay(long maxFlingDelay) {
      this.maxFlingDelay = maxFlingDelay;
   }

   public static class GestureAdapter implements GestureDetector.GestureListener {
      @Override
      public boolean touchDown(float x, float y, int pointer, int button) {
         return false;
      }

      @Override
      public boolean tap(float x, float y, int count, int button) {
         return false;
      }

      @Override
      public boolean longPress(float x, float y) {
         return false;
      }

      @Override
      public boolean fling(float velocityX, float velocityY, int button) {
         return false;
      }

      @Override
      public boolean pan(float x, float y, float deltaX, float deltaY) {
         return false;
      }

      @Override
      public boolean panStop(float x, float y, int pointer, int button) {
         return false;
      }

      @Override
      public boolean zoom(float initialDistance, float distance) {
         return false;
      }

      @Override
      public boolean pinch(Vector2 initialPointer1, Vector2 initialPointer2, Vector2 pointer1, Vector2 pointer2) {
         return false;
      }

      @Override
      public void pinchStop() {
      }
   }

   public interface GestureListener {
      boolean touchDown(float var1, float var2, int var3, int var4);

      boolean tap(float var1, float var2, int var3, int var4);

      boolean longPress(float var1, float var2);

      boolean fling(float var1, float var2, int var3);

      boolean pan(float var1, float var2, float var3, float var4);

      boolean panStop(float var1, float var2, int var3, int var4);

      boolean zoom(float var1, float var2);

      boolean pinch(Vector2 var1, Vector2 var2, Vector2 var3, Vector2 var4);

      void pinchStop();
   }

   static class VelocityTracker {
      int sampleSize = 10;
      float lastX;
      float lastY;
      float deltaX;
      float deltaY;
      long lastTime;
      int numSamples;
      float[] meanX = new float[this.sampleSize];
      float[] meanY = new float[this.sampleSize];
      long[] meanTime = new long[this.sampleSize];

      public void start(float x, float y, long timeStamp) {
         this.lastX = x;
         this.lastY = y;
         this.deltaX = 0.0F;
         this.deltaY = 0.0F;
         this.numSamples = 0;

         for (int i = 0; i < this.sampleSize; i++) {
            this.meanX[i] = 0.0F;
            this.meanY[i] = 0.0F;
            this.meanTime[i] = 0L;
         }

         this.lastTime = timeStamp;
      }

      public void update(float x, float y, long timeStamp) {
         this.deltaX = x - this.lastX;
         this.deltaY = y - this.lastY;
         this.lastX = x;
         this.lastY = y;
         long deltaTime = timeStamp - this.lastTime;
         this.lastTime = timeStamp;
         int index = this.numSamples % this.sampleSize;
         this.meanX[index] = this.deltaX;
         this.meanY[index] = this.deltaY;
         this.meanTime[index] = deltaTime;
         this.numSamples++;
      }

      public float getVelocityX() {
         float meanX = this.getAverage(this.meanX, this.numSamples);
         float meanTime = (float)this.getAverage(this.meanTime, this.numSamples) / 1.0E9F;
         return meanTime == 0.0F ? 0.0F : meanX / meanTime;
      }

      public float getVelocityY() {
         float meanY = this.getAverage(this.meanY, this.numSamples);
         float meanTime = (float)this.getAverage(this.meanTime, this.numSamples) / 1.0E9F;
         return meanTime == 0.0F ? 0.0F : meanY / meanTime;
      }

      private float getAverage(float[] values, int numSamples) {
         numSamples = Math.min(this.sampleSize, numSamples);
         float sum = 0.0F;

         for (int i = 0; i < numSamples; i++) {
            sum += values[i];
         }

         return sum / numSamples;
      }

      private long getAverage(long[] values, int numSamples) {
         numSamples = Math.min(this.sampleSize, numSamples);
         long sum = 0L;

         for (int i = 0; i < numSamples; i++) {
            sum += values[i];
         }

         return numSamples == 0 ? 0L : sum / numSamples;
      }

      private float getSum(float[] values, int numSamples) {
         numSamples = Math.min(this.sampleSize, numSamples);
         float sum = 0.0F;

         for (int i = 0; i < numSamples; i++) {
            sum += values[i];
         }

         return numSamples == 0 ? 0.0F : sum;
      }
   }
}
