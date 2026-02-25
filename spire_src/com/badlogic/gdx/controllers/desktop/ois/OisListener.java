package com.badlogic.gdx.controllers.desktop.ois;

public interface OisListener {
   void buttonPressed(OisJoystick var1, int var2);

   void buttonReleased(OisJoystick var1, int var2);

   void axisMoved(OisJoystick var1, int var2, float var3);

   void povMoved(OisJoystick var1, int var2, OisJoystick.OisPov var3);

   void xSliderMoved(OisJoystick var1, int var2, boolean var3);

   void ySliderMoved(OisJoystick var1, int var2, boolean var3);
}
