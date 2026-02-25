package com.badlogic.gdx;

public interface Screen {
   void show();

   void render(float var1);

   void resize(int var1, int var2);

   void pause();

   void resume();

   void hide();

   void dispose();
}
