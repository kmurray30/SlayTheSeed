package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class BarricadePower extends AbstractPower {
   public static final String POWER_ID = "Barricade";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Barricade");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;

   public BarricadePower(AbstractCreature owner) {
      this.name = NAME;
      this.ID = "Barricade";
      this.owner = owner;
      this.amount = -1;
      this.updateDescription();
      this.loadRegion("barricade");
   }

   @Override
   public void updateDescription() {
      if (this.owner.isPlayer) {
         this.description = DESCRIPTIONS[0];
      } else {
         this.description = DESCRIPTIONS[1];
      }
   }

   static {
      NAME = powerStrings.NAME;
      DESCRIPTIONS = powerStrings.DESCRIPTIONS;
   }
}
