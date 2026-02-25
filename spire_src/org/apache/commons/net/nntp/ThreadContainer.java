package org.apache.commons.net.nntp;

class ThreadContainer {
   Threadable threadable;
   ThreadContainer parent;
   ThreadContainer next;
   ThreadContainer child;

   boolean findChild(ThreadContainer target) {
      if (this.child == null) {
         return false;
      } else {
         return this.child == target ? true : this.child.findChild(target);
      }
   }

   void flush() {
      if (this.parent != null && this.threadable == null) {
         throw new RuntimeException("no threadable in " + this.toString());
      } else {
         this.parent = null;
         if (this.threadable != null) {
            this.threadable.setChild(this.child == null ? null : this.child.threadable);
         }

         if (this.child != null) {
            this.child.flush();
            this.child = null;
         }

         if (this.threadable != null) {
            this.threadable.setNext(this.next == null ? null : this.next.threadable);
         }

         if (this.next != null) {
            this.next.flush();
            this.next = null;
         }

         this.threadable = null;
      }
   }

   void reverseChildren() {
      if (this.child != null) {
         ThreadContainer prev = null;
         ThreadContainer kid = this.child;

         for (ThreadContainer rest = kid.next; kid != null; rest = rest == null ? null : rest.next) {
            kid.next = prev;
            prev = kid;
            kid = rest;
         }

         this.child = prev;

         for (ThreadContainer var4 = this.child; var4 != null; var4 = var4.next) {
            var4.reverseChildren();
         }
      }
   }
}
