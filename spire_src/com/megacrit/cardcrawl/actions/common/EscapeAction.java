package com.megacrit.cardcrawl.actions.common;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class EscapeAction extends AbstractGameAction {
   public EscapeAction(AbstractMonster source) {
      this.setValues(source, source);
      this.duration = 0.5F;
      this.actionType = AbstractGameAction.ActionType.TEXT;
   }

   @Override
   public void update() {
      if (this.duration == 0.5F) {
         AbstractMonster m = (AbstractMonster)this.source;
         m.escape();
      }

      this.tickDuration();
   }
}
