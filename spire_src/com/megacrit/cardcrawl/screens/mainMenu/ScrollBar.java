package com.megacrit.cardcrawl.screens.mainMenu;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.MathHelper;
import com.megacrit.cardcrawl.helpers.ShaderHelper;
import com.megacrit.cardcrawl.helpers.input.InputHelper;

public class ScrollBar {
   public ScrollBarListener sliderListener;
   public boolean isBackgroundVisible = true;
   private Hitbox sliderHb;
   private float currentScrollPercent;
   private boolean isDragging;
   private boolean isInteractable = true;
   public static final float TRACK_W = 54.0F * Settings.scale;
   private final float TRACK_CAP_HEIGHT = TRACK_W;
   private final float TRACK_CAP_VISIBLE_HEIGHT = 22.0F * Settings.scale;
   private final float CURSOR_W = 38.0F * Settings.scale;
   private final float CURSOR_H = 60.0F * Settings.scale;
   private final float DRAW_BORDER = this.CURSOR_H / 4.0F;
   private float cursorDrawPosition = 0.0F;

   public ScrollBar(ScrollBarListener listener) {
      this(listener, Settings.WIDTH - 150.0F * Settings.scale - TRACK_W / 2.0F, Settings.HEIGHT / 2.0F, Settings.HEIGHT - 256.0F * Settings.scale);
      this.cursorDrawPosition = this.getYPositionForPercent(0.0F);
   }

   public ScrollBar(ScrollBarListener listener, float x, float y, float height) {
      this.sliderListener = listener;
      this.currentScrollPercent = 0.0F;
      this.isDragging = false;
      this.sliderHb = new Hitbox(TRACK_W, height);
      this.sliderHb.move(x, y);
      this.reset();
   }

   public ScrollBar(ScrollBarListener listener, float x, float y, float height, boolean isProgressBar) {
      this.isInteractable = !isProgressBar;
      this.sliderListener = listener;
      this.currentScrollPercent = 0.0F;
      this.isDragging = false;
      this.sliderHb = new Hitbox(TRACK_W / 2.0F, height);
      this.sliderHb.move(x, y);
      this.reset();
   }

   public void setCenter(float x, float y) {
      this.sliderHb.move(x, y);
      this.reset();
   }

   public void move(float xOffset, float yOffset) {
      this.sliderHb.move(this.sliderHb.cX + xOffset, this.sliderHb.cY + yOffset);
      this.reset();
   }

   public void changeHeight(float newHeight) {
      this.sliderHb.height = newHeight;
      this.sliderHb.move(this.sliderHb.cX, this.sliderHb.cY);
      this.reset();
   }

   public void positionWithinOnRight(float right, float top, float bottom) {
      this.sliderHb.x = right - TRACK_W;
      this.setCenter(right - TRACK_W / 2.0F, (bottom + top) / 2.0F);
      this.changeHeight(top - bottom - 2.0F * this.TRACK_CAP_VISIBLE_HEIGHT);
      this.reset();
   }

   public float width() {
      return this.sliderHb.width;
   }

   public void reset() {
      this.cursorDrawPosition = this.getYPositionForPercent(this.currentScrollPercent);
   }

   public boolean update() {
      if (!this.isInteractable) {
         return false;
      } else {
         this.sliderHb.update();
         if (this.sliderHb.hovered && InputHelper.isMouseDown) {
            this.isDragging = true;
         }

         if (this.isDragging && InputHelper.justReleasedClickLeft) {
            this.isDragging = false;
            return true;
         } else if (this.isDragging) {
            float newPercent = this.getPercentFromY(InputHelper.mY);
            this.sliderListener.scrolledUsingBar(newPercent);
            return true;
         } else {
            return false;
         }
      }
   }

   public void parentScrolledToPercent(float percent) {
      this.currentScrollPercent = this.boundedPercent(percent);
   }

   private float getPercentFromY(float y) {
      float minY = this.sliderHb.y + this.sliderHb.height - this.DRAW_BORDER;
      float maxY = this.sliderHb.y + this.DRAW_BORDER;
      return this.boundedPercent(MathHelper.percentFromValueBetween(minY, maxY, y));
   }

   public void render(SpriteBatch sb) {
      if (this.isInteractable) {
         sb.setColor(Color.WHITE);
         if (this.isBackgroundVisible) {
            this.renderTrack(sb);
         }

         this.renderCursor(sb);
         this.sliderHb.render(sb);
      }
   }

   private float getYPositionForPercent(float percent) {
      float topY = this.sliderHb.y + this.sliderHb.height - this.CURSOR_H + this.DRAW_BORDER;
      float bottomY = this.sliderHb.y - this.DRAW_BORDER;
      return MathHelper.valueFromPercentBetween(topY, bottomY, this.boundedPercent(percent));
   }

   private void renderCursor(SpriteBatch sb) {
      float x = this.sliderHb.cX - this.CURSOR_W / 2.0F;
      float yForPercent = this.getYPositionForPercent(this.currentScrollPercent);
      this.cursorDrawPosition = MathHelper.scrollSnapLerpSpeed(this.cursorDrawPosition, yForPercent);
      if (this.isInteractable) {
         sb.draw(ImageMaster.SCROLL_BAR_TRAIN, x, this.cursorDrawPosition, this.CURSOR_W, this.CURSOR_H);
      } else {
         ShaderHelper.setShader(sb, ShaderHelper.Shader.GRAYSCALE);
         sb.draw(ImageMaster.SCROLL_BAR_TRAIN, x + this.CURSOR_W / 4.0F, this.cursorDrawPosition, this.CURSOR_W / 2.0F, this.CURSOR_H);
         ShaderHelper.setShader(sb, ShaderHelper.Shader.DEFAULT);
      }
   }

   private void renderTrack(SpriteBatch sb) {
      sb.draw(ImageMaster.SCROLL_BAR_MIDDLE, this.sliderHb.x, this.sliderHb.y, this.sliderHb.width, this.sliderHb.height);
      sb.draw(ImageMaster.SCROLL_BAR_TOP, this.sliderHb.x, this.sliderHb.y + this.sliderHb.height, this.sliderHb.width, this.TRACK_CAP_HEIGHT);
      sb.draw(ImageMaster.SCROLL_BAR_BOTTOM, this.sliderHb.x, this.sliderHb.y - this.TRACK_CAP_HEIGHT, this.sliderHb.width, this.TRACK_CAP_HEIGHT);
   }

   private float boundedPercent(float percent) {
      return Math.max(0.0F, Math.min(percent, 1.0F));
   }
}
