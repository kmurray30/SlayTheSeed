package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.common.ReducePowerAction;
import com.megacrit.cardcrawl.actions.common.RemoveSpecificPowerAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class IntangiblePlayerPower extends AbstractPower {
   public static final String POWER_ID = "IntangiblePlayer";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("IntangiblePlayer");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;

   public IntangiblePlayerPower(AbstractCreature owner, int turns) {
      this.name = NAME;
      this.ID = "IntangiblePlayer";
      this.owner = owner;
      this.amount = turns;
      this.updateDescription();
      this.loadRegion("intangible");
      this.priority = 75;
   }

   @Override
   public void playApplyPowerSfx() {
      CardCrawlGame.sound.play("POWER_INTANGIBLE", 0.05F);
   }

   @Override
   public float atDamageFinalReceive(float damage, DamageInfo.DamageType type) {
      if (damage > 1.0F) {
         damage = 1.0F;
      }

      return damage;
   }

   @Override
   public void updateDescription() {
      this.description = DESCRIPTIONS[0];
   }

   @Override
   public void atEndOfRound() {
      this.flash();
      if (this.amount == 0) {
         this.addToBot(new RemoveSpecificPowerAction(this.owner, this.owner, "IntangiblePlayer"));
      } else {
         this.addToBot(new ReducePowerAction(this.owner, this.owner, "IntangiblePlayer", 1));
      }
   }

   static {
      NAME = powerStrings.NAME;
      DESCRIPTIONS = powerStrings.DESCRIPTIONS;
   }
}
