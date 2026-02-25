package com.badlogic.gdx.graphics;

public interface CubemapData {
   boolean isPrepared();

   void prepare();

   void consumeCubemapData();

   int getWidth();

   int getHeight();

   boolean isManaged();
}
