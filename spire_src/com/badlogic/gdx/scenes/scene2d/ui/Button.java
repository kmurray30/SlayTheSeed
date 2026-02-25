package com.badlogic.gdx.scenes.scene2d.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Disableable;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pools;

public class Button extends Table implements Disableable {
   private Button.ButtonStyle style;
   boolean isChecked;
   boolean isDisabled;
   ButtonGroup buttonGroup;
   private ClickListener clickListener;
   private boolean programmaticChangeEvents = true;

   public Button(Skin skin) {
      super(skin);
      this.initialize();
      this.setStyle(skin.get(Button.ButtonStyle.class));
      this.setSize(this.getPrefWidth(), this.getPrefHeight());
   }

   public Button(Skin skin, String styleName) {
      super(skin);
      this.initialize();
      this.setStyle(skin.get(styleName, Button.ButtonStyle.class));
      this.setSize(this.getPrefWidth(), this.getPrefHeight());
   }

   public Button(Actor child, Skin skin, String styleName) {
      this(child, skin.get(styleName, Button.ButtonStyle.class));
      this.setSkin(skin);
   }

   public Button(Actor child, Button.ButtonStyle style) {
      this.initialize();
      this.add(child);
      this.setStyle(style);
      this.setSize(this.getPrefWidth(), this.getPrefHeight());
   }

   public Button(Button.ButtonStyle style) {
      this.initialize();
      this.setStyle(style);
      this.setSize(this.getPrefWidth(), this.getPrefHeight());
   }

   public Button() {
      this.initialize();
   }

   private void initialize() {
      this.setTouchable(Touchable.enabled);
      this.addListener(this.clickListener = new ClickListener() {
         @Override
         public void clicked(InputEvent event, float x, float y) {
            if (!Button.this.isDisabled()) {
               Button.this.setChecked(!Button.this.isChecked, true);
            }
         }
      });
   }

   public Button(Drawable up) {
      this(new Button.ButtonStyle(up, null, null));
   }

   public Button(Drawable up, Drawable down) {
      this(new Button.ButtonStyle(up, down, null));
   }

   public Button(Drawable up, Drawable down, Drawable checked) {
      this(new Button.ButtonStyle(up, down, checked));
   }

   public Button(Actor child, Skin skin) {
      this(child, skin.get(Button.ButtonStyle.class));
   }

   public void setChecked(boolean isChecked) {
      this.setChecked(isChecked, this.programmaticChangeEvents);
   }

   void setChecked(boolean isChecked, boolean fireEvent) {
      if (this.isChecked != isChecked) {
         if (this.buttonGroup == null || this.buttonGroup.canCheck(this, isChecked)) {
            this.isChecked = isChecked;
            if (fireEvent) {
               ChangeListener.ChangeEvent changeEvent = Pools.obtain(ChangeListener.ChangeEvent.class);
               if (this.fire(changeEvent)) {
                  this.isChecked = !isChecked;
               }

               Pools.free(changeEvent);
            }
         }
      }
   }

   public void toggle() {
      this.setChecked(!this.isChecked);
   }

   public boolean isChecked() {
      return this.isChecked;
   }

   public boolean isPressed() {
      return this.clickListener.isVisualPressed();
   }

   public boolean isOver() {
      return this.clickListener.isOver();
   }

   public ClickListener getClickListener() {
      return this.clickListener;
   }

   @Override
   public boolean isDisabled() {
      return this.isDisabled;
   }

   @Override
   public void setDisabled(boolean isDisabled) {
      this.isDisabled = isDisabled;
   }

   public void setProgrammaticChangeEvents(boolean programmaticChangeEvents) {
      this.programmaticChangeEvents = programmaticChangeEvents;
   }

   public void setStyle(Button.ButtonStyle style) {
      if (style == null) {
         throw new IllegalArgumentException("style cannot be null.");
      } else {
         this.style = style;
         Drawable background = null;
         if (this.isPressed() && !this.isDisabled()) {
            background = style.down == null ? style.up : style.down;
         } else if (this.isDisabled() && style.disabled != null) {
            background = style.disabled;
         } else if (this.isChecked && style.checked != null) {
            background = this.isOver() && style.checkedOver != null ? style.checkedOver : style.checked;
         } else if (this.isOver() && style.over != null) {
            background = style.over;
         } else {
            background = style.up;
         }

         this.setBackground(background);
      }
   }

   public Button.ButtonStyle getStyle() {
      return this.style;
   }

   public ButtonGroup getButtonGroup() {
      return this.buttonGroup;
   }

   @Override
   public void draw(Batch batch, float parentAlpha) {
      this.validate();
      boolean isDisabled = this.isDisabled();
      boolean isPressed = this.isPressed();
      boolean isChecked = this.isChecked();
      boolean isOver = this.isOver();
      Drawable background = null;
      if (isDisabled && this.style.disabled != null) {
         background = this.style.disabled;
      } else if (isPressed && this.style.down != null) {
         background = this.style.down;
      } else if (isChecked && this.style.checked != null) {
         background = this.style.checkedOver != null && isOver ? this.style.checkedOver : this.style.checked;
      } else if (isOver && this.style.over != null) {
         background = this.style.over;
      } else if (this.style.up != null) {
         background = this.style.up;
      }

      this.setBackground(background);
      float offsetX = 0.0F;
      float offsetY = 0.0F;
      if (isPressed && !isDisabled) {
         offsetX = this.style.pressedOffsetX;
         offsetY = this.style.pressedOffsetY;
      } else if (isChecked && !isDisabled) {
         offsetX = this.style.checkedOffsetX;
         offsetY = this.style.checkedOffsetY;
      } else {
         offsetX = this.style.unpressedOffsetX;
         offsetY = this.style.unpressedOffsetY;
      }

      Array<Actor> children = this.getChildren();

      for (int i = 0; i < children.size; i++) {
         children.get(i).moveBy(offsetX, offsetY);
      }

      super.draw(batch, parentAlpha);

      for (int i = 0; i < children.size; i++) {
         children.get(i).moveBy(-offsetX, -offsetY);
      }

      Stage stage = this.getStage();
      if (stage != null && stage.getActionsRequestRendering() && isPressed != this.clickListener.isPressed()) {
         Gdx.graphics.requestRendering();
      }
   }

   @Override
   public float getPrefWidth() {
      float width = super.getPrefWidth();
      if (this.style.up != null) {
         width = Math.max(width, this.style.up.getMinWidth());
      }

      if (this.style.down != null) {
         width = Math.max(width, this.style.down.getMinWidth());
      }

      if (this.style.checked != null) {
         width = Math.max(width, this.style.checked.getMinWidth());
      }

      return width;
   }

   @Override
   public float getPrefHeight() {
      float height = super.getPrefHeight();
      if (this.style.up != null) {
         height = Math.max(height, this.style.up.getMinHeight());
      }

      if (this.style.down != null) {
         height = Math.max(height, this.style.down.getMinHeight());
      }

      if (this.style.checked != null) {
         height = Math.max(height, this.style.checked.getMinHeight());
      }

      return height;
   }

   @Override
   public float getMinWidth() {
      return this.getPrefWidth();
   }

   @Override
   public float getMinHeight() {
      return this.getPrefHeight();
   }

   public static class ButtonStyle {
      public Drawable up;
      public Drawable down;
      public Drawable over;
      public Drawable checked;
      public Drawable checkedOver;
      public Drawable disabled;
      public float pressedOffsetX;
      public float pressedOffsetY;
      public float unpressedOffsetX;
      public float unpressedOffsetY;
      public float checkedOffsetX;
      public float checkedOffsetY;

      public ButtonStyle() {
      }

      public ButtonStyle(Drawable up, Drawable down, Drawable checked) {
         this.up = up;
         this.down = down;
         this.checked = checked;
      }

      public ButtonStyle(Button.ButtonStyle style) {
         this.up = style.up;
         this.down = style.down;
         this.over = style.over;
         this.checked = style.checked;
         this.checkedOver = style.checkedOver;
         this.disabled = style.disabled;
         this.pressedOffsetX = style.pressedOffsetX;
         this.pressedOffsetY = style.pressedOffsetY;
         this.unpressedOffsetX = style.unpressedOffsetX;
         this.unpressedOffsetY = style.unpressedOffsetY;
         this.checkedOffsetX = style.checkedOffsetX;
         this.checkedOffsetY = style.checkedOffsetY;
      }
   }
}
