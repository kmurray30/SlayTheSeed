package com.esotericsoftware.spine;

import com.badlogic.gdx.utils.Array;

public class SkeletonData {
   String name;
   final Array<BoneData> bones = new Array<>();
   final Array<SlotData> slots = new Array<>();
   final Array<Skin> skins = new Array<>();
   Skin defaultSkin;
   final Array<EventData> events = new Array<>();
   final Array<Animation> animations = new Array<>();
   final Array<IkConstraintData> ikConstraints = new Array<>();
   final Array<TransformConstraintData> transformConstraints = new Array<>();
   final Array<PathConstraintData> pathConstraints = new Array<>();
   float width;
   float height;
   String version;
   String hash;
   String imagesPath;

   public Array<BoneData> getBones() {
      return this.bones;
   }

   public BoneData findBone(String boneName) {
      if (boneName == null) {
         throw new IllegalArgumentException("boneName cannot be null.");
      } else {
         Array<BoneData> bones = this.bones;
         int i = 0;

         for (int n = bones.size; i < n; i++) {
            BoneData bone = bones.get(i);
            if (bone.name.equals(boneName)) {
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
         Array<BoneData> bones = this.bones;
         int i = 0;

         for (int n = bones.size; i < n; i++) {
            if (bones.get(i).name.equals(boneName)) {
               return i;
            }
         }

         return -1;
      }
   }

   public Array<SlotData> getSlots() {
      return this.slots;
   }

   public SlotData findSlot(String slotName) {
      if (slotName == null) {
         throw new IllegalArgumentException("slotName cannot be null.");
      } else {
         Array<SlotData> slots = this.slots;
         int i = 0;

         for (int n = slots.size; i < n; i++) {
            SlotData slot = slots.get(i);
            if (slot.name.equals(slotName)) {
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
         Array<SlotData> slots = this.slots;
         int i = 0;

         for (int n = slots.size; i < n; i++) {
            if (slots.get(i).name.equals(slotName)) {
               return i;
            }
         }

         return -1;
      }
   }

   public Skin getDefaultSkin() {
      return this.defaultSkin;
   }

   public void setDefaultSkin(Skin defaultSkin) {
      this.defaultSkin = defaultSkin;
   }

   public Skin findSkin(String skinName) {
      if (skinName == null) {
         throw new IllegalArgumentException("skinName cannot be null.");
      } else {
         for (Skin skin : this.skins) {
            if (skin.name.equals(skinName)) {
               return skin;
            }
         }

         return null;
      }
   }

   public Array<Skin> getSkins() {
      return this.skins;
   }

   public EventData findEvent(String eventDataName) {
      if (eventDataName == null) {
         throw new IllegalArgumentException("eventDataName cannot be null.");
      } else {
         for (EventData eventData : this.events) {
            if (eventData.name.equals(eventDataName)) {
               return eventData;
            }
         }

         return null;
      }
   }

   public Array<EventData> getEvents() {
      return this.events;
   }

   public Array<Animation> getAnimations() {
      return this.animations;
   }

   public Animation findAnimation(String animationName) {
      if (animationName == null) {
         throw new IllegalArgumentException("animationName cannot be null.");
      } else {
         Array<Animation> animations = this.animations;
         int i = 0;

         for (int n = animations.size; i < n; i++) {
            Animation animation = animations.get(i);
            if (animation.name.equals(animationName)) {
               return animation;
            }
         }

         return null;
      }
   }

   public Array<IkConstraintData> getIkConstraints() {
      return this.ikConstraints;
   }

   public IkConstraintData findIkConstraint(String constraintName) {
      if (constraintName == null) {
         throw new IllegalArgumentException("constraintName cannot be null.");
      } else {
         Array<IkConstraintData> ikConstraints = this.ikConstraints;
         int i = 0;

         for (int n = ikConstraints.size; i < n; i++) {
            IkConstraintData constraint = ikConstraints.get(i);
            if (constraint.name.equals(constraintName)) {
               return constraint;
            }
         }

         return null;
      }
   }

   public Array<TransformConstraintData> getTransformConstraints() {
      return this.transformConstraints;
   }

   public TransformConstraintData findTransformConstraint(String constraintName) {
      if (constraintName == null) {
         throw new IllegalArgumentException("constraintName cannot be null.");
      } else {
         Array<TransformConstraintData> transformConstraints = this.transformConstraints;
         int i = 0;

         for (int n = transformConstraints.size; i < n; i++) {
            TransformConstraintData constraint = transformConstraints.get(i);
            if (constraint.name.equals(constraintName)) {
               return constraint;
            }
         }

         return null;
      }
   }

   public Array<PathConstraintData> getPathConstraints() {
      return this.pathConstraints;
   }

   public PathConstraintData findPathConstraint(String constraintName) {
      if (constraintName == null) {
         throw new IllegalArgumentException("constraintName cannot be null.");
      } else {
         Array<PathConstraintData> pathConstraints = this.pathConstraints;
         int i = 0;

         for (int n = pathConstraints.size; i < n; i++) {
            PathConstraintData constraint = pathConstraints.get(i);
            if (constraint.name.equals(constraintName)) {
               return constraint;
            }
         }

         return null;
      }
   }

   public int findPathConstraintIndex(String pathConstraintName) {
      if (pathConstraintName == null) {
         throw new IllegalArgumentException("pathConstraintName cannot be null.");
      } else {
         Array<PathConstraintData> pathConstraints = this.pathConstraints;
         int i = 0;

         for (int n = pathConstraints.size; i < n; i++) {
            if (pathConstraints.get(i).name.equals(pathConstraintName)) {
               return i;
            }
         }

         return -1;
      }
   }

   public String getName() {
      return this.name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public float getWidth() {
      return this.width;
   }

   public void setWidth(float width) {
      this.width = width;
   }

   public float getHeight() {
      return this.height;
   }

   public void setHeight(float height) {
      this.height = height;
   }

   public String getVersion() {
      return this.version;
   }

   public void setVersion(String version) {
      this.version = version;
   }

   public String getHash() {
      return this.hash;
   }

   public void setHash(String hash) {
      this.hash = hash;
   }

   public String getImagesPath() {
      return this.imagesPath;
   }

   public void setImagesPath(String imagesPath) {
      this.imagesPath = imagesPath;
   }

   @Override
   public String toString() {
      return this.name != null ? this.name : super.toString();
   }
}
