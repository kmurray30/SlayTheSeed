package com.badlogic.gdx.controllers;

import com.badlogic.gdx.utils.Array;

public interface ControllerManager {
   Array<Controller> getControllers();

   void addListener(ControllerListener var1);

   void removeListener(ControllerListener var1);

   Array<ControllerListener> getListeners();

   void clearListeners();
}
