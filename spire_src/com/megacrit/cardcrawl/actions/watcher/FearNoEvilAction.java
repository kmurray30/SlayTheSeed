package com.megacrit.cardcrawl.actions.watcher;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class FearNoEvilAction extends AbstractGameAction {
   private AbstractMonster m;
   private DamageInfo info;

   public FearNoEvilAction(AbstractMonster m, DamageInfo info) {
      this.m = m;
      this.info = info;
   }

   @Override
   public void update() {
      if (this.m != null
         && (
            this.m.intent == AbstractMonster.Intent.ATTACK
               || this.m.intent == AbstractMonster.Intent.ATTACK_BUFF
               || this.m.intent == AbstractMonster.Intent.ATTACK_DEBUFF
               || this.m.intent == AbstractMonster.Intent.ATTACK_DEFEND
         )) {
         this.addToTop(new ChangeStanceAction("Calm"));
      }

      this.addToTop(new DamageAction(this.m, this.info, AbstractGameAction.AttackEffect.SLASH_HEAVY));
      this.isDone = true;
   }
}
