package com.badlogic.gdx.scenes.scene2d.utils;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

public class TiledDrawable extends TextureRegionDrawable {
   private final Color color = new Color(1.0F, 1.0F, 1.0F, 1.0F);

   public TiledDrawable() {
   }

   public TiledDrawable(TextureRegion region) {
      super(region);
   }

   public TiledDrawable(TextureRegionDrawable drawable) {
      super(drawable);
   }

   @Override
   public void draw(Batch batch, float x, float y, float width, float height) {
      float batchColor = batch.getPackedColor();
      batch.setColor(batch.getColor().mul(this.color));
      TextureRegion region = this.getRegion();
      float regionWidth = region.getRegionWidth();
      float regionHeight = region.getRegionHeight();
      int fullX = (int)(width / regionWidth);
      int fullY = (int)(height / regionHeight);
      float remainingX = width - regionWidth * fullX;
      float remainingY = height - regionHeight * fullY;
      float startX = x;
      float startY = y;
      float endX = x + width - remainingX;
      float endY = y + height - remainingY;

      for (int i = 0; i < fullX; i++) {
         y = startY;

         for (int ii = 0; ii < fullY; ii++) {
            batch.draw(region, x, y, regionWidth, regionHeight);
            y += regionHeight;
         }

         x += regionWidth;
      }

      Texture texture = region.getTexture();
      float u = region.getU();
      float v2 = region.getV2();
      if (remainingX > 0.0F) {
         float u2 = u + remainingX / texture.getWidth();
         float v = region.getV();
         y = startY;

         for (int ii = 0; ii < fullY; ii++) {
            batch.draw(texture, x, y, remainingX, regionHeight, u, v2, u2, v);
            y += regionHeight;
         }

         if (remainingY > 0.0F) {
            v = v2 - remainingY / texture.getHeight();
            batch.draw(texture, x, y, remainingX, remainingY, u, v2, u2, v);
         }
      }

      if (remainingY > 0.0F) {
         float u2 = region.getU2();
         float v = v2 - remainingY / texture.getHeight();
         x = startX;

         for (int i = 0; i < fullX; i++) {
            batch.draw(texture, x, y, regionWidth, remainingY, u, v2, u2, v);
            x += regionWidth;
         }
      }

      batch.setColor(batchColor);
   }

   @Override
   public void draw(Batch batch, float x, float y, float originX, float originY, float width, float height, float scaleX, float scaleY, float rotation) {
      throw new UnsupportedOperationException();
   }

   public Color getColor() {
      return this.color;
   }

   public TiledDrawable tint(Color tint) {
      TiledDrawable drawable = new TiledDrawable(this);
      drawable.color.set(tint);
      drawable.setLeftWidth(this.getLeftWidth());
      drawable.setRightWidth(this.getRightWidth());
      drawable.setTopHeight(this.getTopHeight());
      drawable.setBottomHeight(this.getBottomHeight());
      return drawable;
   }
}
