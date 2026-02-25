package com.megacrit.cardcrawl.powers.watcher;

import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.PowerStrings;
import com.megacrit.cardcrawl.powers.AbstractPower;

public class DevaPower extends AbstractPower {
   public static final String POWER_ID = "DevaForm";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("DevaForm");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;
   private int energyGainAmount = 1;

   public DevaPower(AbstractCreature owner) {
      this.name = NAME;
      this.ID = "DevaForm";
      this.owner = owner;
      this.amount = 1;
      this.energyGainAmount = 1;
      this.updateDescription();
      this.loadRegion("deva2");
   }

   @Override
   public void stackPower(int stackAmount) {
      super.stackPower(stackAmount);
      this.energyGainAmount++;
   }

   @Override
   public void updateDescription() {
      if (this.energyGainAmount == 1) {
         this.description = DESCRIPTIONS[0] + DESCRIPTIONS[3] + this.amount + DESCRIPTIONS[4];
      } else {
         this.description = DESCRIPTIONS[1] + this.energyGainAmount + DESCRIPTIONS[2] + DESCRIPTIONS[3] + this.amount + DESCRIPTIONS[4];
      }
   }

   @Override
   public void onEnergyRecharge() {
      this.flash();
      AbstractDungeon.player.gainEnergy(this.energyGainAmount);
      this.energyGainAmount = this.energyGainAmount + this.amount;
      this.updateDescription();
   }

   static {
      NAME = powerStrings.NAME;
      DESCRIPTIONS = powerStrings.DESCRIPTIONS;
   }
}
