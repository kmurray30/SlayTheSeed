package com.megacrit.cardcrawl.actions.defect;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class EvokeOrbAction extends AbstractGameAction {
   private int orbCount;

   public EvokeOrbAction(int amount) {
      if (Settings.FAST_MODE) {
         this.duration = Settings.ACTION_DUR_XFAST;
      } else {
         this.duration = Settings.ACTION_DUR_FAST;
      }

      this.duration = this.startDuration;
      this.orbCount = amount;
      this.actionType = AbstractGameAction.ActionType.DAMAGE;
   }

   @Override
   public void update() {
      if (this.duration == this.startDuration) {
         for (int i = 0; i < this.orbCount; i++) {
            AbstractDungeon.player.evokeOrb();
         }
      }

      this.tickDuration();
   }
}
