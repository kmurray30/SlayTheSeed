package com.esotericsoftware.spine;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.FloatArray;
import com.esotericsoftware.spine.attachments.Attachment;

public class Slot {
   final SlotData data;
   final Bone bone;
   final Color color;
   Attachment attachment;
   private float attachmentTime;
   private FloatArray attachmentVertices = new FloatArray();

   public Slot(SlotData data, Bone bone) {
      if (data == null) {
         throw new IllegalArgumentException("data cannot be null.");
      } else if (bone == null) {
         throw new IllegalArgumentException("bone cannot be null.");
      } else {
         this.data = data;
         this.bone = bone;
         this.color = new Color();
         this.setToSetupPose();
      }
   }

   public Slot(Slot slot, Bone bone) {
      if (slot == null) {
         throw new IllegalArgumentException("slot cannot be null.");
      } else if (bone == null) {
         throw new IllegalArgumentException("bone cannot be null.");
      } else {
         this.data = slot.data;
         this.bone = bone;
         this.color = new Color(slot.color);
         this.attachment = slot.attachment;
         this.attachmentTime = slot.attachmentTime;
      }
   }

   public SlotData getData() {
      return this.data;
   }

   public Bone getBone() {
      return this.bone;
   }

   public Skeleton getSkeleton() {
      return this.bone.skeleton;
   }

   public Color getColor() {
      return this.color;
   }

   public Attachment getAttachment() {
      return this.attachment;
   }

   public void setAttachment(Attachment attachment) {
      if (this.attachment != attachment) {
         this.attachment = attachment;
         this.attachmentTime = this.bone.skeleton.time;
         this.attachmentVertices.clear();
      }
   }

   public void setAttachmentTime(float time) {
      this.attachmentTime = this.bone.skeleton.time - time;
   }

   public float getAttachmentTime() {
      return this.bone.skeleton.time - this.attachmentTime;
   }

   public void setAttachmentVertices(FloatArray attachmentVertices) {
      if (attachmentVertices == null) {
         throw new IllegalArgumentException("attachmentVertices cannot be null.");
      } else {
         this.attachmentVertices = attachmentVertices;
      }
   }

   public FloatArray getAttachmentVertices() {
      return this.attachmentVertices;
   }

   public void setToSetupPose() {
      this.color.set(this.data.color);
      if (this.data.attachmentName == null) {
         this.setAttachment(null);
      } else {
         this.attachment = null;
         this.setAttachment(this.bone.skeleton.getAttachment(this.data.index, this.data.attachmentName));
      }
   }

   @Override
   public String toString() {
      return this.data.name;
   }
}
