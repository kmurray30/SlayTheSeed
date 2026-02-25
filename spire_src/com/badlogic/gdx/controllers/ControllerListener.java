package com.badlogic.gdx.controllers;

import com.badlogic.gdx.math.Vector3;

public interface ControllerListener {
   void connected(Controller var1);

   void disconnected(Controller var1);

   boolean buttonDown(Controller var1, int var2);

   boolean buttonUp(Controller var1, int var2);

   boolean axisMoved(Controller var1, int var2, float var3);

   boolean povMoved(Controller var1, int var2, PovDirection var3);

   boolean xSliderMoved(Controller var1, int var2, boolean var3);

   boolean ySliderMoved(Controller var1, int var2, boolean var3);

   boolean accelerometerMoved(Controller var1, int var2, Vector3 var3);
}
