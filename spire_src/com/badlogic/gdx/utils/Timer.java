package com.badlogic.gdx.utils;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.LifecycleListener;

public class Timer {
   static final Array<Timer> instances = new Array<>(1);
   static Timer.TimerThread thread;
   private static final int CANCELLED = -1;
   private static final int FOREVER = -2;
   static Timer instance = new Timer();
   private final Array<Timer.Task> tasks = new Array<>(false, 8);

   public static Timer instance() {
      if (instance == null) {
         instance = new Timer();
      }

      return instance;
   }

   public Timer() {
      this.start();
   }

   public Timer.Task postTask(Timer.Task task) {
      return this.scheduleTask(task, 0.0F, 0.0F, 0);
   }

   public Timer.Task scheduleTask(Timer.Task task, float delaySeconds) {
      return this.scheduleTask(task, delaySeconds, 0.0F, 0);
   }

   public Timer.Task scheduleTask(Timer.Task task, float delaySeconds, float intervalSeconds) {
      return this.scheduleTask(task, delaySeconds, intervalSeconds, -2);
   }

   public Timer.Task scheduleTask(Timer.Task task, float delaySeconds, float intervalSeconds, int repeatCount) {
      synchronized (task) {
         if (task.repeatCount != -1) {
            throw new IllegalArgumentException("The same task may not be scheduled twice.");
         }

         task.executeTimeMillis = System.nanoTime() / 1000000L + (long)(delaySeconds * 1000.0F);
         task.intervalMillis = (long)(intervalSeconds * 1000.0F);
         task.repeatCount = repeatCount;
      }

      synchronized (this) {
         this.tasks.add(task);
      }

      wake();
      return task;
   }

   public void stop() {
      synchronized (instances) {
         instances.removeValue(this, true);
      }
   }

   public void start() {
      synchronized (instances) {
         if (!instances.contains(this, true)) {
            instances.add(this);
            if (thread == null) {
               thread = new Timer.TimerThread();
            }

            wake();
         }
      }
   }

   public void clear() {
      synchronized (this) {
         int i = 0;

         for (int n = this.tasks.size; i < n; i++) {
            this.tasks.get(i).cancel();
         }

         this.tasks.clear();
      }
   }

   public boolean isEmpty() {
      synchronized (this) {
         return this.tasks.size == 0;
      }
   }

   long update(long timeMillis, long waitMillis) {
      synchronized (this) {
         int i = 0;

         for (int n = this.tasks.size; i < n; i++) {
            Timer.Task task = this.tasks.get(i);
            synchronized (task) {
               if (task.executeTimeMillis > timeMillis) {
                  waitMillis = Math.min(waitMillis, task.executeTimeMillis - timeMillis);
               } else {
                  if (task.repeatCount != -1) {
                     if (task.repeatCount == 0) {
                        task.repeatCount = -1;
                     }

                     task.app.postRunnable(task);
                  }

                  if (task.repeatCount == -1) {
                     this.tasks.removeIndex(i);
                     i--;
                     n--;
                  } else {
                     task.executeTimeMillis = timeMillis + task.intervalMillis;
                     waitMillis = Math.min(waitMillis, task.intervalMillis);
                     if (task.repeatCount > 0) {
                        task.repeatCount--;
                     }
                  }
               }
            }
         }

         return waitMillis;
      }
   }

   public void delay(long delayMillis) {
      synchronized (this) {
         int i = 0;

         for (int n = this.tasks.size; i < n; i++) {
            Timer.Task task = this.tasks.get(i);
            synchronized (task) {
               task.executeTimeMillis += delayMillis;
            }
         }
      }
   }

   static void wake() {
      synchronized (instances) {
         instances.notifyAll();
      }
   }

   public static Timer.Task post(Timer.Task task) {
      return instance().postTask(task);
   }

   public static Timer.Task schedule(Timer.Task task, float delaySeconds) {
      return instance().scheduleTask(task, delaySeconds);
   }

   public static Timer.Task schedule(Timer.Task task, float delaySeconds, float intervalSeconds) {
      return instance().scheduleTask(task, delaySeconds, intervalSeconds);
   }

   public static Timer.Task schedule(Timer.Task task, float delaySeconds, float intervalSeconds, int repeatCount) {
      return instance().scheduleTask(task, delaySeconds, intervalSeconds, repeatCount);
   }

   public abstract static class Task implements Runnable {
      long executeTimeMillis;
      long intervalMillis;
      int repeatCount = -1;
      Application app;

      public Task() {
         this.app = Gdx.app;
         if (this.app == null) {
            throw new IllegalStateException("Gdx.app not available.");
         }
      }

      @Override
      public abstract void run();

      public synchronized void cancel() {
         this.executeTimeMillis = 0L;
         this.repeatCount = -1;
      }

      public synchronized boolean isScheduled() {
         return this.repeatCount != -1;
      }

      public synchronized long getExecuteTimeMillis() {
         return this.executeTimeMillis;
      }
   }

   static class TimerThread implements Runnable, LifecycleListener {
      Files files;
      private long pauseMillis;

      public TimerThread() {
         Gdx.app.addLifecycleListener(this);
         this.resume();
      }

      @Override
      public void run() {
         while (true) {
            synchronized (Timer.instances) {
               if (this.files != Gdx.files) {
                  return;
               }

               long timeMillis = System.nanoTime() / 1000000L;
               long waitMillis = 5000L;
               int i = 0;

               for (int n = Timer.instances.size; i < n; i++) {
                  try {
                     waitMillis = Timer.instances.get(i).update(timeMillis, waitMillis);
                  } catch (Throwable var11) {
                     throw new GdxRuntimeException("Task failed: " + Timer.instances.get(i).getClass().getName(), var11);
                  }
               }

               if (this.files != Gdx.files) {
                  return;
               }

               try {
                  if (waitMillis > 0L) {
                     Timer.instances.wait(waitMillis);
                  }
               } catch (InterruptedException var10) {
               }
            }
         }
      }

      @Override
      public void resume() {
         long delayMillis = System.nanoTime() / 1000000L - this.pauseMillis;
         synchronized (Timer.instances) {
            int i = 0;

            for (int n = Timer.instances.size; i < n; i++) {
               Timer.instances.get(i).delay(delayMillis);
            }
         }

         this.files = Gdx.files;
         Thread t = new Thread(this, "Timer");
         t.setDaemon(true);
         t.start();
         Timer.thread = this;
      }

      @Override
      public void pause() {
         this.pauseMillis = System.nanoTime() / 1000000L;
         synchronized (Timer.instances) {
            this.files = null;
            Timer.wake();
         }

         Timer.thread = null;
      }

      @Override
      public void dispose() {
         this.pause();
         Gdx.app.removeLifecycleListener(this);
         Timer.instances.clear();
         Timer.instance = null;
      }
   }
}
