package com.esotericsoftware.spine;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.esotericsoftware.spine.attachments.Attachment;
import com.esotericsoftware.spine.attachments.MeshAttachment;
import com.esotericsoftware.spine.attachments.PathAttachment;
import com.esotericsoftware.spine.attachments.RegionAttachment;

public class Skeleton {
   final SkeletonData data;
   final Array<Bone> bones;
   final Array<Slot> slots;
   Array<Slot> drawOrder;
   final Array<IkConstraint> ikConstraints;
   final Array<IkConstraint> ikConstraintsSorted;
   final Array<TransformConstraint> transformConstraints;
   final Array<PathConstraint> pathConstraints;
   final Array<Updatable> updateCache = new Array<>();
   Skin skin;
   final Color color;
   float time;
   boolean flipX;
   boolean flipY;
   float x;
   float y;

   public Skeleton(SkeletonData data) {
      if (data == null) {
         throw new IllegalArgumentException("data cannot be null.");
      } else {
         this.data = data;
         this.bones = new Array<>(data.bones.size);

         for (BoneData boneData : data.bones) {
            Bone bone;
            if (boneData.parent == null) {
               bone = new Bone(boneData, this, null);
            } else {
               Bone parent = this.bones.get(boneData.parent.index);
               bone = new Bone(boneData, this, parent);
               parent.children.add(bone);
            }

            this.bones.add(bone);
         }

         this.slots = new Array<>(data.slots.size);
         this.drawOrder = new Array<>(data.slots.size);

         for (SlotData slotData : data.slots) {
            Bone bone = this.bones.get(slotData.boneData.index);
            Slot slot = new Slot(slotData, bone);
            this.slots.add(slot);
            this.drawOrder.add(slot);
         }

         this.ikConstraints = new Array<>(data.ikConstraints.size);
         this.ikConstraintsSorted = new Array<>(this.ikConstraints.size);

         for (IkConstraintData ikConstraintData : data.ikConstraints) {
            this.ikConstraints.add(new IkConstraint(ikConstraintData, this));
         }

         this.transformConstraints = new Array<>(data.transformConstraints.size);

         for (TransformConstraintData transformConstraintData : data.transformConstraints) {
            this.transformConstraints.add(new TransformConstraint(transformConstraintData, this));
         }

         this.pathConstraints = new Array<>(data.pathConstraints.size);

         for (PathConstraintData pathConstraintData : data.pathConstraints) {
            this.pathConstraints.add(new PathConstraint(pathConstraintData, this));
         }

         this.color = new Color(1.0F, 1.0F, 1.0F, 1.0F);
         this.updateCache();
      }
   }

   public Skeleton(Skeleton skeleton) {
      if (skeleton == null) {
         throw new IllegalArgumentException("skeleton cannot be null.");
      } else {
         this.data = skeleton.data;
         this.bones = new Array<>(skeleton.bones.size);

         for (Bone bone : skeleton.bones) {
            Bone copy;
            if (bone.parent == null) {
               copy = new Bone(bone, this, null);
            } else {
               Bone parent = this.bones.get(bone.parent.data.index);
               copy = new Bone(bone, this, parent);
               parent.children.add(copy);
            }

            this.bones.add(copy);
         }

         this.slots = new Array<>(skeleton.slots.size);

         for (Slot slot : skeleton.slots) {
            Bone bone = this.bones.get(slot.bone.data.index);
            this.slots.add(new Slot(slot, bone));
         }

         this.drawOrder = new Array<>(this.slots.size);

         for (Slot slot : skeleton.drawOrder) {
            this.drawOrder.add(this.slots.get(slot.data.index));
         }

         this.ikConstraints = new Array<>(skeleton.ikConstraints.size);
         this.ikConstraintsSorted = new Array<>(this.ikConstraints.size);

         for (IkConstraint ikConstraint : skeleton.ikConstraints) {
            this.ikConstraints.add(new IkConstraint(ikConstraint, this));
         }

         this.transformConstraints = new Array<>(skeleton.transformConstraints.size);

         for (TransformConstraint transformConstraint : skeleton.transformConstraints) {
            this.transformConstraints.add(new TransformConstraint(transformConstraint, this));
         }

         this.pathConstraints = new Array<>(skeleton.pathConstraints.size);

         for (PathConstraint pathConstraint : skeleton.pathConstraints) {
            this.pathConstraints.add(new PathConstraint(pathConstraint, this));
         }

         this.skin = skeleton.skin;
         this.color = new Color(skeleton.color);
         this.time = skeleton.time;
         this.flipX = skeleton.flipX;
         this.flipY = skeleton.flipY;
         this.updateCache();
      }
   }

   public void updateCache() {
      Array<Updatable> updateCache = this.updateCache;
      updateCache.clear();
      Array<Bone> bones = this.bones;
      int i = 0;

      for (int n = bones.size; i < n; i++) {
         bones.get(i).sorted = false;
      }

      Array<IkConstraint> ikConstraints = this.ikConstraintsSorted;
      ikConstraints.clear();
      ikConstraints.addAll(this.ikConstraints);
      int ikCount = ikConstraints.size;
      int ix = 0;

      for (int n = ikCount; ix < n; ix++) {
         IkConstraint ik = ikConstraints.get(ix);
         Bone bone = ik.bones.first().parent;

         int level;
         for (level = 0; bone != null; level++) {
            bone = bone.parent;
         }

         ik.level = level;
      }

      for (int ixx = 1; ixx < ikCount; ixx++) {
         IkConstraint ik = ikConstraints.get(ixx);
         int level = ik.level;

         int ii;
         for (ii = ixx - 1; ii >= 0; ii--) {
            IkConstraint other = ikConstraints.get(ii);
            if (other.level < level) {
               break;
            }

            ikConstraints.set(ii + 1, other);
         }

         ikConstraints.set(ii + 1, ik);
      }

      ix = 0;

      for (int n = ikConstraints.size; ix < n; ix++) {
         IkConstraint constraint = ikConstraints.get(ix);
         Bone target = constraint.target;
         this.sortBone(target);
         Array<Bone> constrained = constraint.bones;
         Bone parent = constrained.first();
         this.sortBone(parent);
         updateCache.add(constraint);
         this.sortReset(parent.children);
         constrained.peek().sorted = true;
      }

      Array<PathConstraint> pathConstraints = this.pathConstraints;
      int ixx = 0;

      for (int n = pathConstraints.size; ixx < n; ixx++) {
         PathConstraint constraint = pathConstraints.get(ixx);
         Slot slot = constraint.target;
         int slotIndex = slot.getData().index;
         Bone slotBone = slot.bone;
         if (this.skin != null) {
            this.sortPathConstraintAttachment(this.skin, slotIndex, slotBone);
         }

         if (this.data.defaultSkin != null && this.data.defaultSkin != this.skin) {
            this.sortPathConstraintAttachment(this.data.defaultSkin, slotIndex, slotBone);
         }

         int ii = 0;

         for (int nn = this.data.skins.size; ii < nn; ii++) {
            this.sortPathConstraintAttachment(this.data.skins.get(ii), slotIndex, slotBone);
         }

         Attachment attachment = slot.attachment;
         if (attachment instanceof PathAttachment) {
            this.sortPathConstraintAttachment(attachment, slotBone);
         }

         Array<Bone> constrained = constraint.bones;
         int boneCount = constrained.size;

         for (int iix = 0; iix < boneCount; iix++) {
            this.sortBone(constrained.get(iix));
         }

         updateCache.add(constraint);

         for (int iix = 0; iix < boneCount; iix++) {
            this.sortReset(constrained.get(iix).children);
         }

         for (int iix = 0; iix < boneCount; iix++) {
            constrained.get(iix).sorted = true;
         }
      }

      Array<TransformConstraint> transformConstraints = this.transformConstraints;
      int ixxx = 0;

      for (int n = transformConstraints.size; ixxx < n; ixxx++) {
         TransformConstraint constraintx = transformConstraints.get(ixxx);
         this.sortBone(constraintx.target);
         Array<Bone> constrained = constraintx.bones;
         int boneCount = constrained.size;

         for (int ii = 0; ii < boneCount; ii++) {
            this.sortBone(constrained.get(ii));
         }

         updateCache.add(constraintx);

         for (int ii = 0; ii < boneCount; ii++) {
            this.sortReset(constrained.get(ii).children);
         }

         for (int ii = 0; ii < boneCount; ii++) {
            constrained.get(ii).sorted = true;
         }
      }

      ixxx = 0;

      for (int n = bones.size; ixxx < n; ixxx++) {
         this.sortBone(bones.get(ixxx));
      }
   }

   private void sortPathConstraintAttachment(Skin skin, int slotIndex, Bone slotBone) {
      for (ObjectMap.Entry<Skin.Key, Attachment> entry : skin.attachments.entries()) {
         if (entry.key.slotIndex == slotIndex) {
            this.sortPathConstraintAttachment(entry.value, slotBone);
         }
      }
   }

   private void sortPathConstraintAttachment(Attachment attachment, Bone slotBone) {
      if (attachment instanceof PathAttachment) {
         int[] pathBones = ((PathAttachment)attachment).getBones();
         if (pathBones == null) {
            this.sortBone(slotBone);
         } else {
            Array<Bone> bones = this.bones;

            for (int boneIndex : pathBones) {
               this.sortBone(bones.get(boneIndex));
            }
         }
      }
   }

   private void sortBone(Bone bone) {
      if (!bone.sorted) {
         Bone parent = bone.parent;
         if (parent != null) {
            this.sortBone(parent);
         }

         bone.sorted = true;
         this.updateCache.add(bone);
      }
   }

   private void sortReset(Array<Bone> bones) {
      int i = 0;

      for (int n = bones.size; i < n; i++) {
         Bone bone = bones.get(i);
         if (bone.sorted) {
            this.sortReset(bone.children);
         }

         bone.sorted = false;
      }
   }

   public void updateWorldTransform() {
      Array<Updatable> updateCache = this.updateCache;
      int i = 0;

      for (int n = updateCache.size; i < n; i++) {
         updateCache.get(i).update();
      }
   }

   public void setToSetupPose() {
      this.setBonesToSetupPose();
      this.setSlotsToSetupPose();
   }

   public void setBonesToSetupPose() {
      Array<Bone> bones = this.bones;
      int i = 0;

      for (int n = bones.size; i < n; i++) {
         bones.get(i).setToSetupPose();
      }

      Array<IkConstraint> ikConstraints = this.ikConstraints;
      int ix = 0;

      for (int n = ikConstraints.size; ix < n; ix++) {
         IkConstraint constraint = ikConstraints.get(ix);
         constraint.bendDirection = constraint.data.bendDirection;
         constraint.mix = constraint.data.mix;
      }

      Array<TransformConstraint> transformConstraints = this.transformConstraints;
      int ixx = 0;

      for (int n = transformConstraints.size; ixx < n; ixx++) {
         TransformConstraint constraint = transformConstraints.get(ixx);
         TransformConstraintData data = constraint.data;
         constraint.rotateMix = data.rotateMix;
         constraint.translateMix = data.translateMix;
         constraint.scaleMix = data.scaleMix;
         constraint.shearMix = data.shearMix;
      }

      Array<PathConstraint> pathConstraints = this.pathConstraints;
      int ixxx = 0;

      for (int n = pathConstraints.size; ixxx < n; ixxx++) {
         PathConstraint constraint = pathConstraints.get(ixxx);
         PathConstraintData data = constraint.data;
         constraint.position = data.position;
         constraint.spacing = data.spacing;
         constraint.rotateMix = data.rotateMix;
         constraint.translateMix = data.translateMix;
      }
   }

   public void setSlotsToSetupPose() {
      Array<Slot> slots = this.slots;
      System.arraycopy(slots.items, 0, this.drawOrder.items, 0, slots.size);
      int i = 0;

      for (int n = slots.size; i < n; i++) {
         slots.get(i).setToSetupPose();
      }
   }

   public SkeletonData getData() {
      return this.data;
   }

   public Array<Bone> getBones() {
      return this.bones;
   }

   public Array<Updatable> getUpdateCache() {
      return this.updateCache;
   }

   public Bone getRootBone() {
      return this.bones.size == 0 ? null : this.bones.first();
   }

   public Bone findBone(String boneName) {
      if (boneName == null) {
         throw new IllegalArgumentException("boneName cannot be null.");
      } else {
         Array<Bone> bones = this.bones;
         int i = 0;

         for (int n = bones.size; i < n; i++) {
            Bone bone = bones.get(i);
            if (bone.data.name.equals(boneName)) {
               return bone;
            }
         }

         return null;
      }
   }

   public int findBoneIndex(String boneName) {
      if (boneName == null) {
         throw new IllegalArgumentException("boneName cannot be null.");
      } else {
         Array<Bone> bones = this.bones;
         int i = 0;

         for (int n = bones.size; i < n; i++) {
            if (bones.get(i).data.name.equals(boneName)) {
               return i;
            }
         }

         return -1;
      }
   }

   public Array<Slot> getSlots() {
      return this.slots;
   }

   public Slot findSlot(String slotName) {
      if (slotName == null) {
         throw new IllegalArgumentException("slotName cannot be null.");
      } else {
         Array<Slot> slots = this.slots;
         int i = 0;

         for (int n = slots.size; i < n; i++) {
            Slot slot = slots.get(i);
            if (slot.data.name.equals(slotName)) {
               return slot;
            }
         }

         return null;
      }
   }

   public int findSlotIndex(String slotName) {
      if (slotName == null) {
         throw new IllegalArgumentException("slotName cannot be null.");
      } else {
         Array<Slot> slots = this.slots;
         int i = 0;

         for (int n = slots.size; i < n; i++) {
            if (slots.get(i).data.name.equals(slotName)) {
               return i;
            }
         }

         return -1;
      }
   }

   public Array<Slot> getDrawOrder() {
      return this.drawOrder;
   }

   public void setDrawOrder(Array<Slot> drawOrder) {
      if (drawOrder == null) {
         throw new IllegalArgumentException("drawOrder cannot be null.");
      } else {
         this.drawOrder = drawOrder;
      }
   }

   public Skin getSkin() {
      return this.skin;
   }

   public void setSkin(String skinName) {
      Skin skin = this.data.findSkin(skinName);
      if (skin == null) {
         throw new IllegalArgumentException("Skin not found: " + skinName);
      } else {
         this.setSkin(skin);
      }
   }

   public void setSkin(Skin newSkin) {
      if (newSkin != null) {
         if (this.skin != null) {
            newSkin.attachAll(this, this.skin);
         } else {
            Array<Slot> slots = this.slots;
            int i = 0;

            for (int n = slots.size; i < n; i++) {
               Slot slot = slots.get(i);
               String name = slot.data.attachmentName;
               if (name != null) {
                  Attachment attachment = newSkin.getAttachment(i, name);
                  if (attachment != null) {
                     slot.setAttachment(attachment);
                  }
               }
            }
         }
      }

      this.skin = newSkin;
   }

   public Attachment getAttachment(String slotName, String attachmentName) {
      return this.getAttachment(this.data.findSlotIndex(slotName), attachmentName);
   }

   public Attachment getAttachment(int slotIndex, String attachmentName) {
      if (attachmentName == null) {
         throw new IllegalArgumentException("attachmentName cannot be null.");
      } else {
         if (this.skin != null) {
            Attachment attachment = this.skin.getAttachment(slotIndex, attachmentName);
            if (attachment != null) {
               return attachment;
            }
         }

         return this.data.defaultSkin != null ? this.data.defaultSkin.getAttachment(slotIndex, attachmentName) : null;
      }
   }

   public void setAttachment(String slotName, String attachmentName) {
      if (slotName == null) {
         throw new IllegalArgumentException("slotName cannot be null.");
      } else {
         Array<Slot> slots = this.slots;
         int i = 0;

         for (int n = slots.size; i < n; i++) {
            Slot slot = slots.get(i);
            if (slot.data.name.equals(slotName)) {
               Attachment attachment = null;
               if (attachmentName != null) {
                  attachment = this.getAttachment(i, attachmentName);
                  if (attachment == null) {
                     throw new IllegalArgumentException("Attachment not found: " + attachmentName + ", for slot: " + slotName);
                  }
               }

               slot.setAttachment(attachment);
               return;
            }
         }

         throw new IllegalArgumentException("Slot not found: " + slotName);
      }
   }

   public Array<IkConstraint> getIkConstraints() {
      return this.ikConstraints;
   }

   public IkConstraint findIkConstraint(String constraintName) {
      if (constraintName == null) {
         throw new IllegalArgumentException("constraintName cannot be null.");
      } else {
         Array<IkConstraint> ikConstraints = this.ikConstraints;
         int i = 0;

         for (int n = ikConstraints.size; i < n; i++) {
            IkConstraint ikConstraint = ikConstraints.get(i);
            if (ikConstraint.data.name.equals(constraintName)) {
               return ikConstraint;
            }
         }

         return null;
      }
   }

   public Array<TransformConstraint> getTransformConstraints() {
      return this.transformConstraints;
   }

   public TransformConstraint findTransformConstraint(String constraintName) {
      if (constraintName == null) {
         throw new IllegalArgumentException("constraintName cannot be null.");
      } else {
         Array<TransformConstraint> transformConstraints = this.transformConstraints;
         int i = 0;

         for (int n = transformConstraints.size; i < n; i++) {
            TransformConstraint constraint = transformConstraints.get(i);
            if (constraint.data.name.equals(constraintName)) {
               return constraint;
            }
         }

         return null;
      }
   }

   public Array<PathConstraint> getPathConstraints() {
      return this.pathConstraints;
   }

   public PathConstraint findPathConstraint(String constraintName) {
      if (constraintName == null) {
         throw new IllegalArgumentException("constraintName cannot be null.");
      } else {
         Array<PathConstraint> pathConstraints = this.pathConstraints;
         int i = 0;

         for (int n = pathConstraints.size; i < n; i++) {
            PathConstraint constraint = pathConstraints.get(i);
            if (constraint.data.name.equals(constraintName)) {
               return constraint;
            }
         }

         return null;
      }
   }

   public void getBounds(Vector2 offset, Vector2 size) {
      if (offset == null) {
         throw new IllegalArgumentException("offset cannot be null.");
      } else if (size == null) {
         throw new IllegalArgumentException("size cannot be null.");
      } else {
         Array<Slot> drawOrder = this.drawOrder;
         float minX = 2.1474836E9F;
         float minY = 2.1474836E9F;
         float maxX = -2.1474836E9F;
         float maxY = -2.1474836E9F;
         int i = 0;

         for (int n = drawOrder.size; i < n; i++) {
            Slot slot = drawOrder.get(i);
            float[] vertices = null;
            Attachment attachment = slot.attachment;
            if (attachment instanceof RegionAttachment) {
               vertices = ((RegionAttachment)attachment).updateWorldVertices(slot, false);
            } else if (attachment instanceof MeshAttachment) {
               vertices = ((MeshAttachment)attachment).updateWorldVertices(slot, true);
            }

            if (vertices != null) {
               int ii = 0;

               for (int nn = vertices.length; ii < nn; ii += 5) {
                  float x = vertices[ii];
                  float y = vertices[ii + 1];
                  minX = Math.min(minX, x);
                  minY = Math.min(minY, y);
                  maxX = Math.max(maxX, x);
                  maxY = Math.max(maxY, y);
               }
            }
         }

         offset.set(minX, minY);
         size.set(maxX - minX, maxY - minY);
      }
   }

   public Color getColor() {
      return this.color;
   }

   public void setColor(Color color) {
      if (color == null) {
         throw new IllegalArgumentException("color cannot be null.");
      } else {
         this.color.set(color);
      }
   }

   public boolean getFlipX() {
      return this.flipX;
   }

   public void setFlipX(boolean flipX) {
      this.flipX = flipX;
   }

   public boolean getFlipY() {
      return this.flipY;
   }

   public void setFlipY(boolean flipY) {
      this.flipY = flipY;
   }

   public void setFlip(boolean flipX, boolean flipY) {
      this.flipX = flipX;
      this.flipY = flipY;
   }

   public float getX() {
      return this.x;
   }

   public void setX(float x) {
      this.x = x;
   }

   public float getY() {
      return this.y;
   }

   public void setY(float y) {
      this.y = y;
   }

   public void setPosition(float x, float y) {
      this.x = x;
      this.y = y;
   }

   public float getTime() {
      return this.time;
   }

   public void setTime(float time) {
      this.time = time;
   }

   public void update(float delta) {
      this.time += delta;
   }

   @Override
   public String toString() {
      return this.data.name != null ? this.data.name : super.toString();
   }
}
