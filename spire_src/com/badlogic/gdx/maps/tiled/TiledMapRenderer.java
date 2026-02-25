package com.badlogic.gdx.maps.tiled;

import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.MapRenderer;

public interface TiledMapRenderer extends MapRenderer {
   void renderObjects(MapLayer var1);

   void renderObject(MapObject var1);

   void renderTileLayer(TiledMapTileLayer var1);

   void renderImageLayer(TiledMapImageLayer var1);
}
