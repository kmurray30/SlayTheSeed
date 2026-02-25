package com.badlogic.gdx;

import com.badlogic.gdx.utils.Array;

public class InputMultiplexer implements InputProcessor {
   private Array<InputProcessor> processors = new Array<>(4);

   public InputMultiplexer() {
   }

   public InputMultiplexer(InputProcessor... processors) {
      for (int i = 0; i < processors.length; i++) {
         this.processors.add(processors[i]);
      }
   }

   public void addProcessor(int index, InputProcessor processor) {
      if (processor == null) {
         throw new NullPointerException("processor cannot be null");
      } else {
         this.processors.insert(index, processor);
      }
   }

   public void removeProcessor(int index) {
      this.processors.removeIndex(index);
   }

   public void addProcessor(InputProcessor processor) {
      if (processor == null) {
         throw new NullPointerException("processor cannot be null");
      } else {
         this.processors.add(processor);
      }
   }

   public void removeProcessor(InputProcessor processor) {
      this.processors.removeValue(processor, true);
   }

   public int size() {
      return this.processors.size;
   }

   public void clear() {
      this.processors.clear();
   }

   public void setProcessors(Array<InputProcessor> processors) {
      this.processors = processors;
   }

   public Array<InputProcessor> getProcessors() {
      return this.processors;
   }

   @Override
   public boolean keyDown(int keycode) {
      int i = 0;

      for (int n = this.processors.size; i < n; i++) {
         if (this.processors.get(i).keyDown(keycode)) {
            return true;
         }
      }

      return false;
   }

   @Override
   public boolean keyUp(int keycode) {
      int i = 0;

      for (int n = this.processors.size; i < n; i++) {
         if (this.processors.get(i).keyUp(keycode)) {
            return true;
         }
      }

      return false;
   }

   @Override
   public boolean keyTyped(char character) {
      int i = 0;

      for (int n = this.processors.size; i < n; i++) {
         if (this.processors.get(i).keyTyped(character)) {
            return true;
         }
      }

      return false;
   }

   @Override
   public boolean touchDown(int screenX, int screenY, int pointer, int button) {
      int i = 0;

      for (int n = this.processors.size; i < n; i++) {
         if (this.processors.get(i).touchDown(screenX, screenY, pointer, button)) {
            return true;
         }
      }

      return false;
   }

   @Override
   public boolean touchUp(int screenX, int screenY, int pointer, int button) {
      int i = 0;

      for (int n = this.processors.size; i < n; i++) {
         if (this.processors.get(i).touchUp(screenX, screenY, pointer, button)) {
            return true;
         }
      }

      return false;
   }

   @Override
   public boolean touchDragged(int screenX, int screenY, int pointer) {
      int i = 0;

      for (int n = this.processors.size; i < n; i++) {
         if (this.processors.get(i).touchDragged(screenX, screenY, pointer)) {
            return true;
         }
      }

      return false;
   }

   @Override
   public boolean mouseMoved(int screenX, int screenY) {
      int i = 0;

      for (int n = this.processors.size; i < n; i++) {
         if (this.processors.get(i).mouseMoved(screenX, screenY)) {
            return true;
         }
      }

      return false;
   }

   @Override
   public boolean scrolled(int amount) {
      int i = 0;

      for (int n = this.processors.size; i < n; i++) {
         if (this.processors.get(i).scrolled(amount)) {
            return true;
         }
      }

      return false;
   }
}
