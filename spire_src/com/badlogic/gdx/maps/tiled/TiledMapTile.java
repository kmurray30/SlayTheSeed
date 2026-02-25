package com.badlogic.gdx.maps.tiled;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.MapProperties;

public interface TiledMapTile {
   int getId();

   void setId(int var1);

   TiledMapTile.BlendMode getBlendMode();

   void setBlendMode(TiledMapTile.BlendMode var1);

   TextureRegion getTextureRegion();

   void setTextureRegion(TextureRegion var1);

   float getOffsetX();

   void setOffsetX(float var1);

   float getOffsetY();

   void setOffsetY(float var1);

   MapProperties getProperties();

   public static enum BlendMode {
      NONE,
      ALPHA;
   }
}
