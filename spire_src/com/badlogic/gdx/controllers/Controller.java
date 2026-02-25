package com.badlogic.gdx.controllers;

import com.badlogic.gdx.math.Vector3;

public interface Controller {
   boolean getButton(int var1);

   float getAxis(int var1);

   PovDirection getPov(int var1);

   boolean getSliderX(int var1);

   boolean getSliderY(int var1);

   Vector3 getAccelerometer(int var1);

   void setAccelerometerSensitivity(float var1);

   String getName();

   void addListener(ControllerListener var1);

   void removeListener(ControllerListener var1);
}
