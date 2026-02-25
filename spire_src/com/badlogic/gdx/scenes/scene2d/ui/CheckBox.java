package com.badlogic.gdx.scenes.scene2d.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Scaling;

public class CheckBox extends TextButton {
   private Image image;
   private Cell imageCell;
   private CheckBox.CheckBoxStyle style;

   public CheckBox(String text, Skin skin) {
      this(text, skin.get(CheckBox.CheckBoxStyle.class));
   }

   public CheckBox(String text, Skin skin, String styleName) {
      this(text, skin.get(styleName, CheckBox.CheckBoxStyle.class));
   }

   public CheckBox(String text, CheckBox.CheckBoxStyle style) {
      super(text, style);
      this.clearChildren();
      Label label = this.getLabel();
      this.imageCell = this.add(this.image = new Image(style.checkboxOff, Scaling.none));
      this.add(label);
      label.setAlignment(8);
      this.setSize(this.getPrefWidth(), this.getPrefHeight());
   }

   @Override
   public void setStyle(Button.ButtonStyle style) {
      if (!(style instanceof CheckBox.CheckBoxStyle)) {
         throw new IllegalArgumentException("style must be a CheckBoxStyle.");
      } else {
         super.setStyle(style);
         this.style = (CheckBox.CheckBoxStyle)style;
      }
   }

   public CheckBox.CheckBoxStyle getStyle() {
      return this.style;
   }

   @Override
   public void draw(Batch batch, float parentAlpha) {
      Drawable checkbox = null;
      if (this.isDisabled()) {
         if (this.isChecked && this.style.checkboxOnDisabled != null) {
            checkbox = this.style.checkboxOnDisabled;
         } else {
            checkbox = this.style.checkboxOffDisabled;
         }
      }

      if (checkbox == null) {
         if (this.isChecked && this.style.checkboxOn != null) {
            checkbox = this.style.checkboxOn;
         } else if (this.isOver() && this.style.checkboxOver != null && !this.isDisabled()) {
            checkbox = this.style.checkboxOver;
         } else {
            checkbox = this.style.checkboxOff;
         }
      }

      this.image.setDrawable(checkbox);
      super.draw(batch, parentAlpha);
   }

   public Image getImage() {
      return this.image;
   }

   public Cell getImageCell() {
      return this.imageCell;
   }

   public static class CheckBoxStyle extends TextButton.TextButtonStyle {
      public Drawable checkboxOn;
      public Drawable checkboxOff;
      public Drawable checkboxOver;
      public Drawable checkboxOnDisabled;
      public Drawable checkboxOffDisabled;

      public CheckBoxStyle() {
      }

      public CheckBoxStyle(Drawable checkboxOff, Drawable checkboxOn, BitmapFont font, Color fontColor) {
         this.checkboxOff = checkboxOff;
         this.checkboxOn = checkboxOn;
         this.font = font;
         this.fontColor = fontColor;
      }

      public CheckBoxStyle(CheckBox.CheckBoxStyle style) {
         super(style);
         this.checkboxOff = style.checkboxOff;
         this.checkboxOn = style.checkboxOn;
         this.checkboxOver = style.checkboxOver;
         this.checkboxOffDisabled = style.checkboxOffDisabled;
         this.checkboxOnDisabled = style.checkboxOnDisabled;
      }
   }
}
