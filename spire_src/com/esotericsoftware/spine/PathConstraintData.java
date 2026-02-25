package com.esotericsoftware.spine;

import com.badlogic.gdx.utils.Array;

public class PathConstraintData {
   final String name;
   final Array<BoneData> bones = new Array<>();
   SlotData target;
   PathConstraintData.PositionMode positionMode;
   PathConstraintData.SpacingMode spacingMode;
   PathConstraintData.RotateMode rotateMode;
   float offsetRotation;
   float position;
   float spacing;
   float rotateMix;
   float translateMix;

   public PathConstraintData(String name) {
      if (name == null) {
         throw new IllegalArgumentException("name cannot be null.");
      } else {
         this.name = name;
      }
   }

   public Array<BoneData> getBones() {
      return this.bones;
   }

   public SlotData getTarget() {
      return this.target;
   }

   public void setTarget(SlotData target) {
      this.target = target;
   }

   public PathConstraintData.PositionMode getPositionMode() {
      return this.positionMode;
   }

   public void setPositionMode(PathConstraintData.PositionMode positionMode) {
      this.positionMode = positionMode;
   }

   public PathConstraintData.SpacingMode getSpacingMode() {
      return this.spacingMode;
   }

   public void setSpacingMode(PathConstraintData.SpacingMode spacingMode) {
      this.spacingMode = spacingMode;
   }

   public PathConstraintData.RotateMode getRotateMode() {
      return this.rotateMode;
   }

   public void setRotateMode(PathConstraintData.RotateMode rotateMode) {
      this.rotateMode = rotateMode;
   }

   public float getOffsetRotation() {
      return this.offsetRotation;
   }

   public void setOffsetRotation(float offsetRotation) {
      this.offsetRotation = offsetRotation;
   }

   public float getPosition() {
      return this.position;
   }

   public void setPosition(float position) {
      this.position = position;
   }

   public float getSpacing() {
      return this.spacing;
   }

   public void setSpacing(float spacing) {
      this.spacing = spacing;
   }

   public float getRotateMix() {
      return this.rotateMix;
   }

   public void setRotateMix(float rotateMix) {
      this.rotateMix = rotateMix;
   }

   public float getTranslateMix() {
      return this.translateMix;
   }

   public void setTranslateMix(float translateMix) {
      this.translateMix = translateMix;
   }

   public String getName() {
      return this.name;
   }

   @Override
   public String toString() {
      return this.name;
   }

   public static enum PositionMode {
      fixed,
      percent;

      public static final PathConstraintData.PositionMode[] values = values();
   }

   public static enum RotateMode {
      tangent,
      chain,
      chainScale;

      public static final PathConstraintData.RotateMode[] values = values();
   }

   public static enum SpacingMode {
      length,
      fixed,
      percent;

      public static final PathConstraintData.SpacingMode[] values = values();
   }
}
