package com.badlogic.gdx.scenes.scene2d.actions;

import com.badlogic.gdx.scenes.scene2d.Action;
import com.badlogic.gdx.utils.Pool;

public class SequenceAction extends ParallelAction {
   private int index;

   public SequenceAction() {
   }

   public SequenceAction(Action action1) {
      this.addAction(action1);
   }

   public SequenceAction(Action action1, Action action2) {
      this.addAction(action1);
      this.addAction(action2);
   }

   public SequenceAction(Action action1, Action action2, Action action3) {
      this.addAction(action1);
      this.addAction(action2);
      this.addAction(action3);
   }

   public SequenceAction(Action action1, Action action2, Action action3, Action action4) {
      this.addAction(action1);
      this.addAction(action2);
      this.addAction(action3);
      this.addAction(action4);
   }

   public SequenceAction(Action action1, Action action2, Action action3, Action action4, Action action5) {
      this.addAction(action1);
      this.addAction(action2);
      this.addAction(action3);
      this.addAction(action4);
      this.addAction(action5);
   }

   @Override
   public boolean act(float delta) {
      if (this.index >= this.actions.size) {
         return true;
      } else {
         Pool pool = this.getPool();
         this.setPool(null);

         try {
            if (this.actions.get(this.index).act(delta)) {
               if (this.actor == null) {
                  return true;
               }

               this.index++;
               if (this.index >= this.actions.size) {
                  return true;
               }
            }

            return false;
         } finally {
            this.setPool(pool);
         }
      }
   }

   @Override
   public void restart() {
      super.restart();
      this.index = 0;
   }
}
