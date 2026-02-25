package com.badlogic.gdx.scenes.scene2d.ui;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Scaling;

public class ImageButton extends Button {
   private final Image image = new Image();
   private ImageButton.ImageButtonStyle style;

   public ImageButton(Skin skin) {
      this(skin.get(ImageButton.ImageButtonStyle.class));
      this.setSkin(skin);
   }

   public ImageButton(Skin skin, String styleName) {
      this(skin.get(styleName, ImageButton.ImageButtonStyle.class));
      this.setSkin(skin);
   }

   public ImageButton(ImageButton.ImageButtonStyle style) {
      super(style);
      this.image.setScaling(Scaling.fit);
      this.add(this.image);
      this.setStyle(style);
      this.setSize(this.getPrefWidth(), this.getPrefHeight());
   }

   public ImageButton(Drawable imageUp) {
      this(new ImageButton.ImageButtonStyle(null, null, null, imageUp, null, null));
   }

   public ImageButton(Drawable imageUp, Drawable imageDown) {
      this(new ImageButton.ImageButtonStyle(null, null, null, imageUp, imageDown, null));
   }

   public ImageButton(Drawable imageUp, Drawable imageDown, Drawable imageChecked) {
      this(new ImageButton.ImageButtonStyle(null, null, null, imageUp, imageDown, imageChecked));
   }

   @Override
   public void setStyle(Button.ButtonStyle style) {
      if (!(style instanceof ImageButton.ImageButtonStyle)) {
         throw new IllegalArgumentException("style must be an ImageButtonStyle.");
      } else {
         super.setStyle(style);
         this.style = (ImageButton.ImageButtonStyle)style;
         if (this.image != null) {
            this.updateImage();
         }
      }
   }

   public ImageButton.ImageButtonStyle getStyle() {
      return this.style;
   }

   private void updateImage() {
      Drawable drawable = null;
      if (this.isDisabled() && this.style.imageDisabled != null) {
         drawable = this.style.imageDisabled;
      } else if (this.isPressed() && this.style.imageDown != null) {
         drawable = this.style.imageDown;
      } else if (this.isChecked && this.style.imageChecked != null) {
         drawable = this.style.imageCheckedOver != null && this.isOver() ? this.style.imageCheckedOver : this.style.imageChecked;
      } else if (this.isOver() && this.style.imageOver != null) {
         drawable = this.style.imageOver;
      } else if (this.style.imageUp != null) {
         drawable = this.style.imageUp;
      }

      this.image.setDrawable(drawable);
   }

   @Override
   public void draw(Batch batch, float parentAlpha) {
      this.updateImage();
      super.draw(batch, parentAlpha);
   }

   public Image getImage() {
      return this.image;
   }

   public Cell getImageCell() {
      return this.getCell(this.image);
   }

   public static class ImageButtonStyle extends Button.ButtonStyle {
      public Drawable imageUp;
      public Drawable imageDown;
      public Drawable imageOver;
      public Drawable imageChecked;
      public Drawable imageCheckedOver;
      public Drawable imageDisabled;

      public ImageButtonStyle() {
      }

      public ImageButtonStyle(Drawable up, Drawable down, Drawable checked, Drawable imageUp, Drawable imageDown, Drawable imageChecked) {
         super(up, down, checked);
         this.imageUp = imageUp;
         this.imageDown = imageDown;
         this.imageChecked = imageChecked;
      }

      public ImageButtonStyle(ImageButton.ImageButtonStyle style) {
         super(style);
         this.imageUp = style.imageUp;
         this.imageDown = style.imageDown;
         this.imageOver = style.imageOver;
         this.imageChecked = style.imageChecked;
         this.imageCheckedOver = style.imageCheckedOver;
         this.imageDisabled = style.imageDisabled;
      }

      public ImageButtonStyle(Button.ButtonStyle style) {
         super(style);
      }
   }
}
