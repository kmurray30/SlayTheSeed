package com.badlogic.gdx.utils.viewport;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.OrthographicCamera;

public class ScreenViewport extends Viewport {
   private float unitsPerPixel = 1.0F;

   public ScreenViewport() {
      this(new OrthographicCamera());
   }

   public ScreenViewport(Camera camera) {
      this.setCamera(camera);
   }

   @Override
   public void update(int screenWidth, int screenHeight, boolean centerCamera) {
      this.setScreenBounds(0, 0, screenWidth, screenHeight);
      this.setWorldSize(screenWidth * this.unitsPerPixel, screenHeight * this.unitsPerPixel);
      this.apply(centerCamera);
   }

   public float getUnitsPerPixel() {
      return this.unitsPerPixel;
   }

   public void setUnitsPerPixel(float unitsPerPixel) {
      this.unitsPerPixel = unitsPerPixel;
   }
}
