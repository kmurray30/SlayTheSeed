package com.megacrit.cardcrawl.actions.utility;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.core.Settings;

public class WaitAction extends AbstractGameAction {
   public WaitAction(float setDur) {
      this.setValues(null, null, 0);
      if (Settings.FAST_MODE && setDur > 0.1F) {
         this.duration = 0.1F;
      } else {
         this.duration = setDur;
      }

      this.actionType = AbstractGameAction.ActionType.WAIT;
   }

   @Override
   public void update() {
      this.tickDuration();
   }
}
