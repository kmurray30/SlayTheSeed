package com.megacrit.cardcrawl.actions.unique;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.RemoveSpecificPowerAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.powers.AbstractPower;

public class RemoveDebuffsAction extends AbstractGameAction {
   private AbstractCreature c;

   public RemoveDebuffsAction(AbstractCreature c) {
      this.c = c;
      this.duration = 0.5F;
   }

   @Override
   public void update() {
      for (AbstractPower p : this.c.powers) {
         if (p.type == AbstractPower.PowerType.DEBUFF) {
            this.addToTop(new RemoveSpecificPowerAction(this.c, this.c, p.ID));
         }
      }

      this.isDone = true;
   }
}
