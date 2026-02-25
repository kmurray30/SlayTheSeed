package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.common.ChangeStateAction;
import com.megacrit.cardcrawl.actions.common.GainBlockAction;
import com.megacrit.cardcrawl.actions.common.ReducePowerAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class PlatedArmorPower extends AbstractPower {
   public static final String POWER_ID = "Plated Armor";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Plated Armor");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;
   private static final int DECREMENT_AMT = 1;

   public PlatedArmorPower(AbstractCreature owner, int amt) {
      this.name = NAME;
      this.ID = "Plated Armor";
      this.owner = owner;
      this.amount = amt;
      this.updateDescription();
      this.loadRegion("platedarmor");
   }

   @Override
   public void stackPower(int stackAmount) {
      this.fontScale = 8.0F;
      this.amount += stackAmount;
      if (this.amount > 999) {
         this.amount = 999;
      }

      this.updateDescription();
   }

   @Override
   public void playApplyPowerSfx() {
      CardCrawlGame.sound.play("POWER_PLATED", 0.05F);
   }

   @Override
   public void updateDescription() {
      if (this.owner.isPlayer) {
         this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1];
      } else {
         this.description = DESCRIPTIONS[2] + this.amount + DESCRIPTIONS[3];
      }
   }

   @Override
   public void wasHPLost(DamageInfo info, int damageAmount) {
      if (info.owner != null
         && info.owner != this.owner
         && info.type != DamageInfo.DamageType.HP_LOSS
         && info.type != DamageInfo.DamageType.THORNS
         && damageAmount > 0) {
         this.flash();
         this.addToBot(new ReducePowerAction(this.owner, this.owner, "Plated Armor", 1));
      }
   }

   @Override
   public void onRemove() {
      if (!this.owner.isPlayer) {
         this.addToBot(new ChangeStateAction((AbstractMonster)this.owner, "ARMOR_BREAK"));
      }
   }

   @Override
   public void atEndOfTurnPreEndTurnCards(boolean isPlayer) {
      this.flash();
      this.addToBot(new GainBlockAction(this.owner, this.owner, this.amount));
   }

   static {
      NAME = powerStrings.NAME;
      DESCRIPTIONS = powerStrings.DESCRIPTIONS;
   }
}
