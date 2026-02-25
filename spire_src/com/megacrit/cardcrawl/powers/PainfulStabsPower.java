package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.common.MakeTempCardInDiscardAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.cards.status.Wound;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class PainfulStabsPower extends AbstractPower {
   public static final String POWER_ID = "Painful Stabs";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Painful Stabs");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;

   public PainfulStabsPower(AbstractCreature owner) {
      this.name = NAME;
      this.ID = "Painful Stabs";
      this.owner = owner;
      this.amount = -1;
      this.updateDescription();
      this.loadRegion("painfulStabs");
   }

   @Override
   public void updateDescription() {
      this.description = DESCRIPTIONS[0];
   }

   @Override
   public void onInflictDamage(DamageInfo info, int damageAmount, AbstractCreature target) {
      if (damageAmount > 0 && info.type != DamageInfo.DamageType.THORNS) {
         this.addToBot(new MakeTempCardInDiscardAction(new Wound(), 1));
      }
   }

   static {
      NAME = powerStrings.NAME;
      DESCRIPTIONS = powerStrings.DESCRIPTIONS;
   }
}
