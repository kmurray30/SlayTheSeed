package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.common.ReducePowerAction;
import com.megacrit.cardcrawl.actions.common.RemoveSpecificPowerAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class DoubleDamagePower extends AbstractPower {
   public static final String POWER_ID = "Double Damage";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Double Damage");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;
   private boolean justApplied = false;

   public DoubleDamagePower(AbstractCreature owner, int amount, boolean isSourceMonster) {
      this.name = NAME;
      this.ID = "Double Damage";
      this.owner = owner;
      this.amount = amount;
      this.updateDescription();
      this.loadRegion("doubleDamage");
      this.justApplied = isSourceMonster;
      this.type = AbstractPower.PowerType.BUFF;
      this.isTurnBased = true;
      this.priority = 6;
   }

   @Override
   public void atEndOfRound() {
      if (this.justApplied) {
         this.justApplied = false;
      } else {
         if (this.amount == 0) {
            this.addToBot(new RemoveSpecificPowerAction(this.owner, this.owner, "Double Damage"));
         } else {
            this.addToBot(new ReducePowerAction(this.owner, this.owner, "Double Damage", 1));
         }
      }
   }

   @Override
   public void updateDescription() {
      if (this.amount == 1) {
         this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1];
      } else {
         this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[2];
      }
   }

   @Override
   public float atDamageGive(float damage, DamageInfo.DamageType type) {
      return type == DamageInfo.DamageType.NORMAL ? damage * 2.0F : damage;
   }

   static {
      NAME = powerStrings.NAME;
      DESCRIPTIONS = powerStrings.DESCRIPTIONS;
   }
}
