package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class LightningMasteryPower extends AbstractPower {
   public static final String POWER_ID = "Lightning Mastery";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Lightning Mastery");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;

   public LightningMasteryPower(AbstractCreature owner, int amount) {
      this.name = NAME;
      this.ID = "Lightning Mastery";
      this.owner = owner;
      this.amount = amount;
      this.updateDescription();
      this.loadRegion("mastery");
   }

   @Override
   public void updateDescription() {
      this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1];
   }

   static {
      NAME = powerStrings.NAME;
      DESCRIPTIONS = powerStrings.DESCRIPTIONS;
   }
}
