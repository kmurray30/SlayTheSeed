package com.megacrit.cardcrawl.actions;

import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class IntentFlashAction extends AbstractGameAction {
   private AbstractMonster m;

   public IntentFlashAction(AbstractMonster m) {
      if (Settings.FAST_MODE) {
         this.startDuration = Settings.ACTION_DUR_MED;
      } else {
         this.startDuration = Settings.ACTION_DUR_XLONG;
      }

      this.duration = this.startDuration;
      this.m = m;
      this.actionType = AbstractGameAction.ActionType.WAIT;
   }

   @Override
   public void update() {
      if (this.duration == this.startDuration) {
         this.m.flashIntent();
      }

      this.tickDuration();
   }
}
