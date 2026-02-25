package com.badlogic.gdx.controllers.desktop.ois;

public class OisJoystick {
   private static final int MIN_AXIS = -32768;
   private static final int MAX_AXIS = 32767;
   private final String name;
   private final long joystickPtr;
   private final boolean[] buttons;
   private final float[] axes;
   private final int[] povs;
   private final boolean[] slidersX;
   private final boolean[] slidersY;
   private OisListener listener;

   public OisJoystick(long joystickPtr, String name) {
      this.joystickPtr = joystickPtr;
      this.name = name;
      this.initialize(this);
      this.buttons = new boolean[this.getButtonCount()];
      this.axes = new float[this.getAxisCount()];
      this.povs = new int[this.getPovCount()];
      this.slidersX = new boolean[this.getSliderCount()];
      this.slidersY = new boolean[this.getSliderCount()];
   }

   public void setListener(OisListener listener) {
      this.listener = listener;
   }

   private void buttonPressed(int buttonIndex) {
      this.buttons[buttonIndex] = true;
      if (this.listener != null) {
         this.listener.buttonPressed(this, buttonIndex);
      }
   }

   private void buttonReleased(int buttonIndex) {
      this.buttons[buttonIndex] = false;
      if (this.listener != null) {
         this.listener.buttonReleased(this, buttonIndex);
      }
   }

   private void axisMoved(int axisIndex, int value) {
      this.axes[axisIndex] = (value - -32768 << 1) / 65535.0F - 1.0F;
      if (this.listener != null) {
         this.listener.axisMoved(this, axisIndex, this.axes[axisIndex]);
      }
   }

   private void povMoved(int povIndex, int value) {
      this.povs[povIndex] = value;
      if (this.listener != null) {
         this.listener.povMoved(this, povIndex, this.getPov(povIndex));
      }
   }

   private void sliderMoved(int sliderIndex, int x, int y) {
      boolean xChanged = this.slidersX[sliderIndex] != (x == 1);
      boolean yChanged = this.slidersY[sliderIndex] != (y == 1);
      this.slidersX[sliderIndex] = x == 1;
      this.slidersY[sliderIndex] = y == 1;
      if (this.listener != null) {
         if (xChanged) {
            this.listener.xSliderMoved(this, sliderIndex, x == 1);
         }

         if (yChanged) {
            this.listener.ySliderMoved(this, sliderIndex, y == 1);
         }
      }
   }

   public void update() {
      this.update(this.joystickPtr, this);
   }

   public int getAxisCount() {
      return this.getAxesCount(this.joystickPtr);
   }

   public int getButtonCount() {
      return this.getButtonCount(this.joystickPtr);
   }

   public int getPovCount() {
      return this.getPovCount(this.joystickPtr);
   }

   public int getSliderCount() {
      return this.getSliderCount(this.joystickPtr);
   }

   public float getAxis(int axisIndex) {
      return axisIndex >= 0 && axisIndex < this.axes.length ? this.axes[axisIndex] : 0.0F;
   }

   public OisJoystick.OisPov getPov(int povIndex) {
      if (povIndex >= 0 && povIndex < this.povs.length) {
         switch (this.povs[povIndex]) {
            case 0:
               return OisJoystick.OisPov.Centered;
            case 1:
               return OisJoystick.OisPov.North;
            case 16:
               return OisJoystick.OisPov.South;
            case 256:
               return OisJoystick.OisPov.East;
            case 257:
               return OisJoystick.OisPov.NorthEast;
            case 272:
               return OisJoystick.OisPov.SouthEast;
            case 4096:
               return OisJoystick.OisPov.West;
            case 4097:
               return OisJoystick.OisPov.NorthWest;
            case 4112:
               return OisJoystick.OisPov.SouthWest;
            default:
               throw new RuntimeException("Unexpected POV value reported by OIS: " + this.povs[povIndex]);
         }
      } else {
         return OisJoystick.OisPov.Centered;
      }
   }

   public boolean isButtonPressed(int buttonIndex) {
      return buttonIndex >= 0 && buttonIndex < this.buttons.length ? this.buttons[buttonIndex] : false;
   }

   public boolean getSliderX(int sliderIndex) {
      return sliderIndex >= 0 && sliderIndex < this.slidersX.length ? this.slidersX[sliderIndex] : false;
   }

   public boolean getSliderY(int sliderIndex) {
      return sliderIndex >= 0 && sliderIndex < this.slidersY.length ? this.slidersY[sliderIndex] : false;
   }

   public String getName() {
      return this.name;
   }

   @Override
   public String toString() {
      return this.name;
   }

   private native void initialize(OisJoystick var1);

   private native void update(long var1, OisJoystick var3);

   private native int getAxesCount(long var1);

   private native int getButtonCount(long var1);

   private native int getPovCount(long var1);

   private native int getSliderCount(long var1);

   public static enum OisPov {
      Centered,
      North,
      South,
      East,
      West,
      NorthEast,
      SouthEast,
      NorthWest,
      SouthWest;
   }
}
