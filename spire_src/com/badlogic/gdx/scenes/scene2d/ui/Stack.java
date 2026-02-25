package com.badlogic.gdx.scenes.scene2d.ui;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.utils.Layout;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.SnapshotArray;

public class Stack extends WidgetGroup {
   private float prefWidth;
   private float prefHeight;
   private float minWidth;
   private float minHeight;
   private float maxWidth;
   private float maxHeight;
   private boolean sizeInvalid = true;

   public Stack() {
      this.setTransform(false);
      this.setWidth(150.0F);
      this.setHeight(150.0F);
      this.setTouchable(Touchable.childrenOnly);
   }

   public Stack(Actor... actors) {
      this();

      for (Actor actor : actors) {
         this.addActor(actor);
      }
   }

   @Override
   public void invalidate() {
      super.invalidate();
      this.sizeInvalid = true;
   }

   private void computeSize() {
      this.sizeInvalid = false;
      this.prefWidth = 0.0F;
      this.prefHeight = 0.0F;
      this.minWidth = 0.0F;
      this.minHeight = 0.0F;
      this.maxWidth = 0.0F;
      this.maxHeight = 0.0F;
      SnapshotArray<Actor> children = this.getChildren();
      int i = 0;

      for (int n = children.size; i < n; i++) {
         Actor child = children.get(i);
         float childMaxWidth;
         float childMaxHeight;
         if (child instanceof Layout) {
            Layout layout = (Layout)child;
            this.prefWidth = Math.max(this.prefWidth, layout.getPrefWidth());
            this.prefHeight = Math.max(this.prefHeight, layout.getPrefHeight());
            this.minWidth = Math.max(this.minWidth, layout.getMinWidth());
            this.minHeight = Math.max(this.minHeight, layout.getMinHeight());
            childMaxWidth = layout.getMaxWidth();
            childMaxHeight = layout.getMaxHeight();
         } else {
            this.prefWidth = Math.max(this.prefWidth, child.getWidth());
            this.prefHeight = Math.max(this.prefHeight, child.getHeight());
            this.minWidth = Math.max(this.minWidth, child.getWidth());
            this.minHeight = Math.max(this.minHeight, child.getHeight());
            childMaxWidth = 0.0F;
            childMaxHeight = 0.0F;
         }

         if (childMaxWidth > 0.0F) {
            this.maxWidth = this.maxWidth == 0.0F ? childMaxWidth : Math.min(this.maxWidth, childMaxWidth);
         }

         if (childMaxHeight > 0.0F) {
            this.maxHeight = this.maxHeight == 0.0F ? childMaxHeight : Math.min(this.maxHeight, childMaxHeight);
         }
      }
   }

   public void add(Actor actor) {
      this.addActor(actor);
   }

   @Override
   public void layout() {
      if (this.sizeInvalid) {
         this.computeSize();
      }

      float width = this.getWidth();
      float height = this.getHeight();
      Array<Actor> children = this.getChildren();
      int i = 0;

      for (int n = children.size; i < n; i++) {
         Actor child = children.get(i);
         child.setBounds(0.0F, 0.0F, width, height);
         if (child instanceof Layout) {
            ((Layout)child).validate();
         }
      }
   }

   @Override
   public float getPrefWidth() {
      if (this.sizeInvalid) {
         this.computeSize();
      }

      return this.prefWidth;
   }

   @Override
   public float getPrefHeight() {
      if (this.sizeInvalid) {
         this.computeSize();
      }

      return this.prefHeight;
   }

   @Override
   public float getMinWidth() {
      if (this.sizeInvalid) {
         this.computeSize();
      }

      return this.minWidth;
   }

   @Override
   public float getMinHeight() {
      if (this.sizeInvalid) {
         this.computeSize();
      }

      return this.minHeight;
   }

   @Override
   public float getMaxWidth() {
      if (this.sizeInvalid) {
         this.computeSize();
      }

      return this.maxWidth;
   }

   @Override
   public float getMaxHeight() {
      if (this.sizeInvalid) {
         this.computeSize();
      }

      return this.maxHeight;
   }
}
