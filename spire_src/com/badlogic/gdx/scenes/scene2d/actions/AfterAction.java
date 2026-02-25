package com.badlogic.gdx.scenes.scene2d.actions;

import com.badlogic.gdx.scenes.scene2d.Action;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.utils.Array;

public class AfterAction extends DelegateAction {
   private Array<Action> waitForActions = new Array<>(false, 4);

   @Override
   public void setTarget(Actor target) {
      if (target != null) {
         this.waitForActions.addAll(target.getActions());
      }

      super.setTarget(target);
   }

   @Override
   public void restart() {
      super.restart();
      this.waitForActions.clear();
   }

   @Override
   protected boolean delegate(float delta) {
      Array<Action> currentActions = this.target.getActions();
      if (currentActions.size == 1) {
         this.waitForActions.clear();
      }

      for (int i = this.waitForActions.size - 1; i >= 0; i--) {
         Action action = this.waitForActions.get(i);
         int index = currentActions.indexOf(action, true);
         if (index == -1) {
            this.waitForActions.removeIndex(i);
         }
      }

      return this.waitForActions.size > 0 ? false : this.action.act(delta);
   }
}
