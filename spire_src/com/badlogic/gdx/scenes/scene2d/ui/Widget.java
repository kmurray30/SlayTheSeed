package com.badlogic.gdx.scenes.scene2d.ui;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.utils.Layout;

public class Widget extends Actor implements Layout {
   private boolean needsLayout = true;
   private boolean fillParent;
   private boolean layoutEnabled = true;

   @Override
   public float getMinWidth() {
      return this.getPrefWidth();
   }

   @Override
   public float getMinHeight() {
      return this.getPrefHeight();
   }

   @Override
   public float getPrefWidth() {
      return 0.0F;
   }

   @Override
   public float getPrefHeight() {
      return 0.0F;
   }

   @Override
   public float getMaxWidth() {
      return 0.0F;
   }

   @Override
   public float getMaxHeight() {
      return 0.0F;
   }

   @Override
   public void setLayoutEnabled(boolean enabled) {
      this.layoutEnabled = enabled;
      if (enabled) {
         this.invalidateHierarchy();
      }
   }

   @Override
   public void validate() {
      if (this.layoutEnabled) {
         Group parent = this.getParent();
         if (this.fillParent && parent != null) {
            Stage stage = this.getStage();
            float parentWidth;
            float parentHeight;
            if (stage != null && parent == stage.getRoot()) {
               parentWidth = stage.getWidth();
               parentHeight = stage.getHeight();
            } else {
               parentWidth = parent.getWidth();
               parentHeight = parent.getHeight();
            }

            this.setSize(parentWidth, parentHeight);
         }

         if (this.needsLayout) {
            this.needsLayout = false;
            this.layout();
         }
      }
   }

   public boolean needsLayout() {
      return this.needsLayout;
   }

   @Override
   public void invalidate() {
      this.needsLayout = true;
   }

   @Override
   public void invalidateHierarchy() {
      if (this.layoutEnabled) {
         this.invalidate();
         Group parent = this.getParent();
         if (parent instanceof Layout) {
            ((Layout)parent).invalidateHierarchy();
         }
      }
   }

   @Override
   protected void sizeChanged() {
      this.invalidate();
   }

   @Override
   public void pack() {
      this.setSize(this.getPrefWidth(), this.getPrefHeight());
      this.validate();
   }

   @Override
   public void setFillParent(boolean fillParent) {
      this.fillParent = fillParent;
   }

   @Override
   public void draw(Batch batch, float parentAlpha) {
      this.validate();
   }

   @Override
   public void layout() {
   }
}
