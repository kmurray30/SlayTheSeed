package com.esotericsoftware.spine.attachments;

import com.esotericsoftware.spine.Skeleton;

public class SkeletonAttachment extends Attachment {
   private Skeleton skeleton;

   public SkeletonAttachment(String name) {
      super(name);
   }

   public Skeleton getSkeleton() {
      return this.skeleton;
   }

   public void setSkeleton(Skeleton skeleton) {
      this.skeleton = skeleton;
   }
}
