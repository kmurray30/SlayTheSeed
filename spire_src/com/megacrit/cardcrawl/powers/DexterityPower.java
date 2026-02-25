package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.common.RemoveSpecificPowerAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class DexterityPower extends AbstractPower {
   public static final String POWER_ID = "Dexterity";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Dexterity");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;

   public DexterityPower(AbstractCreature owner, int amount) {
      this.name = NAME;
      this.ID = "Dexterity";
      this.owner = owner;
      this.amount = amount;
      if (this.amount >= 999) {
         this.amount = 999;
      }

      if (this.amount <= -999) {
         this.amount = -999;
      }

      this.updateDescription();
      this.loadRegion("dexterity");
      this.canGoNegative = true;
   }

   @Override
   public void playApplyPowerSfx() {
      CardCrawlGame.sound.play("POWER_DEXTERITY", 0.05F);
   }

   @Override
   public void stackPower(int stackAmount) {
      this.fontScale = 8.0F;
      this.amount += stackAmount;
      if (this.amount == 0) {
         this.addToTop(new RemoveSpecificPowerAction(this.owner, this.owner, "Dexterity"));
      }

      if (this.amount >= 999) {
         this.amount = 999;
      }

      if (this.amount <= -999) {
         this.amount = -999;
      }
   }

   @Override
   public void reducePower(int reduceAmount) {
      this.fontScale = 8.0F;
      this.amount -= reduceAmount;
      if (this.amount == 0) {
         this.addToTop(new RemoveSpecificPowerAction(this.owner, this.owner, "Dexterity"));
      }

      if (this.amount >= 999) {
         this.amount = 999;
      }

      if (this.amount <= -999) {
         this.amount = -999;
      }
   }

   @Override
   public void updateDescription() {
      if (this.amount > 0) {
         this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[2];
         this.type = AbstractPower.PowerType.BUFF;
      } else {
         int tmp = -this.amount;
         this.description = DESCRIPTIONS[1] + tmp + DESCRIPTIONS[2];
         this.type = AbstractPower.PowerType.DEBUFF;
      }
   }

   @Override
   public float modifyBlock(float blockAmount) {
      float var2;
      return (var2 = blockAmount + this.amount) < 0.0F ? 0.0F : var2;
   }

   static {
      NAME = powerStrings.NAME;
      DESCRIPTIONS = powerStrings.DESCRIPTIONS;
   }
}
