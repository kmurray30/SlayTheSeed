package com.badlogic.gdx.scenes.scene2d.actions;

import com.badlogic.gdx.scenes.scene2d.Action;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.utils.Pool;

public abstract class DelegateAction extends Action {
   protected Action action;

   public void setAction(Action action) {
      this.action = action;
   }

   public Action getAction() {
      return this.action;
   }

   protected abstract boolean delegate(float var1);

   @Override
   public final boolean act(float delta) {
      Pool pool = this.getPool();
      this.setPool(null);

      boolean var3;
      try {
         var3 = this.delegate(delta);
      } finally {
         this.setPool(pool);
      }

      return var3;
   }

   @Override
   public void restart() {
      if (this.action != null) {
         this.action.restart();
      }
   }

   @Override
   public void reset() {
      super.reset();
      this.action = null;
   }

   @Override
   public void setActor(Actor actor) {
      if (this.action != null) {
         this.action.setActor(actor);
      }

      super.setActor(actor);
   }

   @Override
   public void setTarget(Actor target) {
      if (this.action != null) {
         this.action.setTarget(target);
      }

      super.setTarget(target);
   }

   @Override
   public String toString() {
      return super.toString() + (this.action == null ? "" : "(" + this.action + ")");
   }
}
