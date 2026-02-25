package com.megacrit.cardcrawl.powers.deprecated;

import com.megacrit.cardcrawl.actions.common.GainEnergyAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.stances.AbstractStance;

public class DEPRECATEDMasteryPower extends AbstractPower {
   public static final String POWER_ID = "Mastery";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Mastery");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;

   public DEPRECATEDMasteryPower(AbstractCreature owner, int amount) {
      this.name = NAME;
      this.ID = "Mastery";
      this.owner = owner;
      this.amount = amount;
      this.updateDescription();
      this.loadRegion("corruption");
   }

   @Override
   public void updateDescription() {
      this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1];
   }

   @Override
   public void onChangeStance(AbstractStance oldStance, AbstractStance newStance) {
      if (oldStance.ID.equals(newStance.ID) && !newStance.ID.equals("Neutral")) {
         this.flash();
         this.addToBot(new GainEnergyAction(this.amount));
      }
   }

   static {
      NAME = powerStrings.NAME;
      DESCRIPTIONS = powerStrings.DESCRIPTIONS;
   }
}
