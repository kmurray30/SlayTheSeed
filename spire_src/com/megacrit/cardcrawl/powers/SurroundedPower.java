package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class SurroundedPower extends AbstractPower {
   public static final String POWER_ID = "Surrounded";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Surrounded");

   public SurroundedPower(AbstractCreature owner) {
      this.name = powerStrings.NAME;
      this.ID = "Surrounded";
      this.owner = owner;
      this.amount = -1;
      this.updateDescription();
      this.loadRegion("surrounded");
   }

   @Override
   public void updateDescription() {
      this.description = powerStrings.DESCRIPTIONS[0];
   }
}
