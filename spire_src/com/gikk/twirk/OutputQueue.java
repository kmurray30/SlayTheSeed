package com.gikk.twirk;

import java.util.LinkedList;

class OutputQueue {
   private final LinkedList<String> queue = new LinkedList<>();

   public void add(String s) {
      synchronized (this.queue) {
         this.queue.add(s);
         this.queue.notify();
      }
   }

   public void addFirst(String s) {
      synchronized (this.queue) {
         this.queue.addFirst(s);
         this.queue.notify();
      }
   }

   public String next() {
      synchronized (this.queue) {
         if (!this.hasNext()) {
            try {
               this.queue.wait();
            } catch (InterruptedException var4) {
            }
         }

         if (!this.hasNext()) {
            return null;
         } else {
            String message = this.queue.getFirst();
            this.queue.removeFirst();
            return message;
         }
      }
   }

   public boolean hasNext() {
      synchronized (this.queue) {
         return this.queue.size() > 0;
      }
   }

   void releaseWaitingThreads() {
      synchronized (this.queue) {
         this.queue.notifyAll();
      }
   }
}
