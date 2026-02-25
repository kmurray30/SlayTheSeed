package com.badlogic.gdx.scenes.scene2d;

import com.badlogic.gdx.math.Vector2;

public class InputListener implements EventListener {
   private static final Vector2 tmpCoords = new Vector2();

   @Override
   public boolean handle(Event e) {
      if (!(e instanceof InputEvent)) {
         return false;
      } else {
         InputEvent event = (InputEvent)e;
         switch (event.getType()) {
            case keyDown:
               return this.keyDown(event, event.getKeyCode());
            case keyUp:
               return this.keyUp(event, event.getKeyCode());
            case keyTyped:
               return this.keyTyped(event, event.getCharacter());
            default:
               event.toCoordinates(event.getListenerActor(), tmpCoords);
               switch (event.getType()) {
                  case touchDown:
                     return this.touchDown(event, tmpCoords.x, tmpCoords.y, event.getPointer(), event.getButton());
                  case touchUp:
                     this.touchUp(event, tmpCoords.x, tmpCoords.y, event.getPointer(), event.getButton());
                     return true;
                  case touchDragged:
                     this.touchDragged(event, tmpCoords.x, tmpCoords.y, event.getPointer());
                     return true;
                  case mouseMoved:
                     return this.mouseMoved(event, tmpCoords.x, tmpCoords.y);
                  case scrolled:
                     return this.scrolled(event, tmpCoords.x, tmpCoords.y, event.getScrollAmount());
                  case enter:
                     this.enter(event, tmpCoords.x, tmpCoords.y, event.getPointer(), event.getRelatedActor());
                     return false;
                  case exit:
                     this.exit(event, tmpCoords.x, tmpCoords.y, event.getPointer(), event.getRelatedActor());
                     return false;
                  default:
                     return false;
               }
         }
      }
   }

   public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
      return false;
   }

   public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
   }

   public void touchDragged(InputEvent event, float x, float y, int pointer) {
   }

   public boolean mouseMoved(InputEvent event, float x, float y) {
      return false;
   }

   public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
   }

   public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
   }

   public boolean scrolled(InputEvent event, float x, float y, int amount) {
      return false;
   }

   public boolean keyDown(InputEvent event, int keycode) {
      return false;
   }

   public boolean keyUp(InputEvent event, int keycode) {
      return false;
   }

   public boolean keyTyped(InputEvent event, char character) {
      return false;
   }
}
