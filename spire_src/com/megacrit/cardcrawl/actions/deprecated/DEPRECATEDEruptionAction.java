package com.megacrit.cardcrawl.actions.deprecated;

import com.megacrit.cardcrawl.actions.AbstractGameAction;

public class DEPRECATEDEruptionAction extends AbstractGameAction {
   private int baseDamage;

   public DEPRECATEDEruptionAction(int baseDamage) {
      this.baseDamage = baseDamage;
   }

   @Override
   public void update() {
      this.isDone = true;
   }
}
