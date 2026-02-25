package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class EnvenomPower extends AbstractPower {
   public static final String POWER_ID = "Envenom";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Envenom");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;

   public EnvenomPower(AbstractCreature owner, int newAmount) {
      this.name = NAME;
      this.ID = "Envenom";
      this.owner = owner;
      this.amount = newAmount;
      this.updateDescription();
      this.loadRegion("envenom");
   }

   @Override
   public void updateDescription() {
      this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1];
   }

   @Override
   public void onAttack(DamageInfo info, int damageAmount, AbstractCreature target) {
      if (damageAmount > 0 && target != this.owner && info.type == DamageInfo.DamageType.NORMAL) {
         this.flash();
         this.addToTop(new ApplyPowerAction(target, this.owner, new PoisonPower(target, this.owner, this.amount), this.amount, true));
      }
   }

   static {
      NAME = powerStrings.NAME;
      DESCRIPTIONS = powerStrings.DESCRIPTIONS;
   }
}
