package com.badlogic.gdx.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.utils.GdxRuntimeException;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class RemoteInput implements Runnable, Input {
   public static int DEFAULT_PORT = 8190;
   private ServerSocket serverSocket;
   private float[] accel = new float[3];
   private float[] gyrate = new float[3];
   private float[] compass = new float[3];
   private boolean multiTouch = false;
   private float remoteWidth = 0.0F;
   private float remoteHeight = 0.0F;
   private boolean connected = false;
   private RemoteInput.RemoteInputListener listener;
   int keyCount = 0;
   boolean[] keys = new boolean[256];
   boolean keyJustPressed = false;
   boolean[] justPressedKeys = new boolean[256];
   int[] touchX = new int[20];
   int[] touchY = new int[20];
   boolean[] isTouched = new boolean[20];
   boolean justTouched = false;
   InputProcessor processor = null;
   private final int port;
   public final String[] ips;

   public RemoteInput() {
      this(DEFAULT_PORT);
   }

   public RemoteInput(RemoteInput.RemoteInputListener listener) {
      this(DEFAULT_PORT, listener);
   }

   public RemoteInput(int port) {
      this(port, null);
   }

   public RemoteInput(int port, RemoteInput.RemoteInputListener listener) {
      this.listener = listener;

      try {
         this.port = port;
         this.serverSocket = new ServerSocket(port);
         Thread thread = new Thread(this);
         thread.setDaemon(true);
         thread.start();
         InetAddress[] allByName = InetAddress.getAllByName(InetAddress.getLocalHost().getHostName());
         this.ips = new String[allByName.length];

         for (int i = 0; i < allByName.length; i++) {
            this.ips[i] = allByName[i].getHostAddress();
         }
      } catch (Exception var6) {
         throw new GdxRuntimeException("Couldn't open listening socket at port '" + port + "'", var6);
      }
   }

   @Override
   public void run() {
      while (true) {
         try {
            this.connected = false;
            if (this.listener != null) {
               this.listener.onDisconnected();
            }

            System.out.println("listening, port " + this.port);
            Socket socket = null;
            socket = this.serverSocket.accept();
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(3000);
            this.connected = true;
            if (this.listener != null) {
               this.listener.onConnected();
            }

            DataInputStream in = new DataInputStream(socket.getInputStream());
            this.multiTouch = in.readBoolean();

            while (true) {
               int event = in.readInt();
               RemoteInput.KeyEvent keyEvent = null;
               RemoteInput.TouchEvent touchEvent = null;
               switch (event) {
                  case 0:
                     keyEvent = new RemoteInput.KeyEvent();
                     keyEvent.keyCode = in.readInt();
                     keyEvent.type = 0;
                     break;
                  case 1:
                     keyEvent = new RemoteInput.KeyEvent();
                     keyEvent.keyCode = in.readInt();
                     keyEvent.type = 1;
                     break;
                  case 2:
                     keyEvent = new RemoteInput.KeyEvent();
                     keyEvent.keyChar = in.readChar();
                     keyEvent.type = 2;
                     break;
                  case 3:
                     touchEvent = new RemoteInput.TouchEvent();
                     touchEvent.x = (int)(in.readInt() / this.remoteWidth * Gdx.graphics.getWidth());
                     touchEvent.y = (int)(in.readInt() / this.remoteHeight * Gdx.graphics.getHeight());
                     touchEvent.pointer = in.readInt();
                     touchEvent.type = 0;
                     break;
                  case 4:
                     touchEvent = new RemoteInput.TouchEvent();
                     touchEvent.x = (int)(in.readInt() / this.remoteWidth * Gdx.graphics.getWidth());
                     touchEvent.y = (int)(in.readInt() / this.remoteHeight * Gdx.graphics.getHeight());
                     touchEvent.pointer = in.readInt();
                     touchEvent.type = 1;
                     break;
                  case 5:
                     touchEvent = new RemoteInput.TouchEvent();
                     touchEvent.x = (int)(in.readInt() / this.remoteWidth * Gdx.graphics.getWidth());
                     touchEvent.y = (int)(in.readInt() / this.remoteHeight * Gdx.graphics.getHeight());
                     touchEvent.pointer = in.readInt();
                     touchEvent.type = 2;
                     break;
                  case 6:
                     this.accel[0] = in.readFloat();
                     this.accel[1] = in.readFloat();
                     this.accel[2] = in.readFloat();
                     break;
                  case 7:
                     this.compass[0] = in.readFloat();
                     this.compass[1] = in.readFloat();
                     this.compass[2] = in.readFloat();
                     break;
                  case 8:
                     this.remoteWidth = in.readFloat();
                     this.remoteHeight = in.readFloat();
                     break;
                  case 9:
                     this.gyrate[0] = in.readFloat();
                     this.gyrate[1] = in.readFloat();
                     this.gyrate[2] = in.readFloat();
               }

               Gdx.app.postRunnable(new RemoteInput.EventTrigger(touchEvent, keyEvent));
            }
         } catch (IOException var6) {
            var6.printStackTrace();
         }
      }
   }

   public boolean isConnected() {
      return this.connected;
   }

   @Override
   public float getAccelerometerX() {
      return this.accel[0];
   }

   @Override
   public float getAccelerometerY() {
      return this.accel[1];
   }

   @Override
   public float getAccelerometerZ() {
      return this.accel[2];
   }

   @Override
   public float getGyroscopeX() {
      return this.gyrate[0];
   }

   @Override
   public float getGyroscopeY() {
      return this.gyrate[1];
   }

   @Override
   public float getGyroscopeZ() {
      return this.gyrate[2];
   }

   @Override
   public int getX() {
      return this.touchX[0];
   }

   @Override
   public int getX(int pointer) {
      return this.touchX[pointer];
   }

   @Override
   public int getY() {
      return this.touchY[0];
   }

   @Override
   public int getY(int pointer) {
      return this.touchY[pointer];
   }

   @Override
   public boolean isTouched() {
      return this.isTouched[0];
   }

   @Override
   public boolean justTouched() {
      return this.justTouched;
   }

   @Override
   public boolean isTouched(int pointer) {
      return this.isTouched[pointer];
   }

   @Override
   public boolean isButtonPressed(int button) {
      if (button != 0) {
         return false;
      } else {
         for (int i = 0; i < this.isTouched.length; i++) {
            if (this.isTouched[i]) {
               return true;
            }
         }

         return false;
      }
   }

   @Override
   public boolean isKeyPressed(int key) {
      if (key == -1) {
         return this.keyCount > 0;
      } else {
         return key >= 0 && key <= 255 ? this.keys[key] : false;
      }
   }

   @Override
   public boolean isKeyJustPressed(int key) {
      if (key == -1) {
         return this.keyJustPressed;
      } else {
         return key >= 0 && key <= 255 ? this.justPressedKeys[key] : false;
      }
   }

   @Override
   public void getTextInput(Input.TextInputListener listener, String title, String text, String hint) {
      Gdx.app.getInput().getTextInput(listener, title, text, hint);
   }

   @Override
   public void setOnscreenKeyboardVisible(boolean visible) {
   }

   @Override
   public void vibrate(int milliseconds) {
   }

   @Override
   public void vibrate(long[] pattern, int repeat) {
   }

   @Override
   public void cancelVibrate() {
   }

   @Override
   public float getAzimuth() {
      return this.compass[0];
   }

   @Override
   public float getPitch() {
      return this.compass[1];
   }

   @Override
   public float getRoll() {
      return this.compass[2];
   }

   @Override
   public void setCatchBackKey(boolean catchBack) {
   }

   @Override
   public boolean isCatchBackKey() {
      return false;
   }

   @Override
   public void setCatchMenuKey(boolean catchMenu) {
   }

   @Override
   public boolean isCatchMenuKey() {
      return false;
   }

   @Override
   public void setInputProcessor(InputProcessor processor) {
      this.processor = processor;
   }

   @Override
   public InputProcessor getInputProcessor() {
      return this.processor;
   }

   public String[] getIPs() {
      return this.ips;
   }

   @Override
   public boolean isPeripheralAvailable(Input.Peripheral peripheral) {
      if (peripheral == Input.Peripheral.Accelerometer) {
         return true;
      } else if (peripheral == Input.Peripheral.Compass) {
         return true;
      } else {
         return peripheral == Input.Peripheral.MultitouchScreen ? this.multiTouch : false;
      }
   }

   @Override
   public int getRotation() {
      return 0;
   }

   @Override
   public Input.Orientation getNativeOrientation() {
      return Input.Orientation.Landscape;
   }

   @Override
   public void setCursorCatched(boolean catched) {
   }

   @Override
   public boolean isCursorCatched() {
      return false;
   }

   @Override
   public int getDeltaX() {
      return 0;
   }

   @Override
   public int getDeltaX(int pointer) {
      return 0;
   }

   @Override
   public int getDeltaY() {
      return 0;
   }

   @Override
   public int getDeltaY(int pointer) {
      return 0;
   }

   @Override
   public void setCursorPosition(int x, int y) {
   }

   @Override
   public long getCurrentEventTime() {
      return 0L;
   }

   @Override
   public void getRotationMatrix(float[] matrix) {
   }

   class EventTrigger implements Runnable {
      RemoteInput.TouchEvent touchEvent;
      RemoteInput.KeyEvent keyEvent;

      public EventTrigger(RemoteInput.TouchEvent touchEvent, RemoteInput.KeyEvent keyEvent) {
         this.touchEvent = touchEvent;
         this.keyEvent = keyEvent;
      }

      @Override
      public void run() {
         RemoteInput.this.justTouched = false;
         if (RemoteInput.this.keyJustPressed) {
            RemoteInput.this.keyJustPressed = false;

            for (int i = 0; i < RemoteInput.this.justPressedKeys.length; i++) {
               RemoteInput.this.justPressedKeys[i] = false;
            }
         }

         if (RemoteInput.this.processor != null) {
            if (this.touchEvent != null) {
               RemoteInput.this.touchX[this.touchEvent.pointer] = this.touchEvent.x;
               RemoteInput.this.touchY[this.touchEvent.pointer] = this.touchEvent.y;
               switch (this.touchEvent.type) {
                  case 0:
                     RemoteInput.this.processor.touchDown(this.touchEvent.x, this.touchEvent.y, this.touchEvent.pointer, 0);
                     RemoteInput.this.isTouched[this.touchEvent.pointer] = true;
                     RemoteInput.this.justTouched = true;
                     break;
                  case 1:
                     RemoteInput.this.processor.touchUp(this.touchEvent.x, this.touchEvent.y, this.touchEvent.pointer, 0);
                     RemoteInput.this.isTouched[this.touchEvent.pointer] = false;
                     break;
                  case 2:
                     RemoteInput.this.processor.touchDragged(this.touchEvent.x, this.touchEvent.y, this.touchEvent.pointer);
               }
            }

            if (this.keyEvent != null) {
               switch (this.keyEvent.type) {
                  case 0:
                     RemoteInput.this.processor.keyDown(this.keyEvent.keyCode);
                     if (!RemoteInput.this.keys[this.keyEvent.keyCode]) {
                        RemoteInput.this.keyCount++;
                        RemoteInput.this.keys[this.keyEvent.keyCode] = true;
                     }

                     RemoteInput.this.keyJustPressed = true;
                     RemoteInput.this.justPressedKeys[this.keyEvent.keyCode] = true;
                     break;
                  case 1:
                     RemoteInput.this.processor.keyUp(this.keyEvent.keyCode);
                     if (RemoteInput.this.keys[this.keyEvent.keyCode]) {
                        RemoteInput.this.keyCount--;
                        RemoteInput.this.keys[this.keyEvent.keyCode] = false;
                     }
                     break;
                  case 2:
                     RemoteInput.this.processor.keyTyped(this.keyEvent.keyChar);
               }
            }
         } else {
            if (this.touchEvent != null) {
               RemoteInput.this.touchX[this.touchEvent.pointer] = this.touchEvent.x;
               RemoteInput.this.touchY[this.touchEvent.pointer] = this.touchEvent.y;
               if (this.touchEvent.type == 0) {
                  RemoteInput.this.isTouched[this.touchEvent.pointer] = true;
                  RemoteInput.this.justTouched = true;
               }

               if (this.touchEvent.type == 1) {
                  RemoteInput.this.isTouched[this.touchEvent.pointer] = false;
               }
            }

            if (this.keyEvent != null) {
               if (this.keyEvent.type == 0) {
                  if (!RemoteInput.this.keys[this.keyEvent.keyCode]) {
                     RemoteInput.this.keyCount++;
                     RemoteInput.this.keys[this.keyEvent.keyCode] = true;
                  }

                  RemoteInput.this.keyJustPressed = true;
                  RemoteInput.this.justPressedKeys[this.keyEvent.keyCode] = true;
               }

               if (this.keyEvent.type == 1 && RemoteInput.this.keys[this.keyEvent.keyCode]) {
                  RemoteInput.this.keyCount--;
                  RemoteInput.this.keys[this.keyEvent.keyCode] = false;
               }
            }
         }
      }
   }

   class KeyEvent {
      static final int KEY_DOWN = 0;
      static final int KEY_UP = 1;
      static final int KEY_TYPED = 2;
      long timeStamp;
      int type;
      int keyCode;
      char keyChar;
   }

   public interface RemoteInputListener {
      void onConnected();

      void onDisconnected();
   }

   class TouchEvent {
      static final int TOUCH_DOWN = 0;
      static final int TOUCH_UP = 1;
      static final int TOUCH_DRAGGED = 2;
      long timeStamp;
      int type;
      int x;
      int y;
      int pointer;
   }
}
