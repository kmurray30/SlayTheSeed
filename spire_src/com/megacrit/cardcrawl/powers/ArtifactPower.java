package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.common.ReducePowerAction;
import com.megacrit.cardcrawl.actions.common.RemoveSpecificPowerAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class ArtifactPower extends AbstractPower {
   public static final String POWER_ID = "Artifact";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Artifact");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;

   public ArtifactPower(AbstractCreature owner, int amount) {
      this.name = NAME;
      this.ID = "Artifact";
      this.owner = owner;
      this.amount = amount;
      this.updateDescription();
      this.loadRegion("artifact");
      this.type = AbstractPower.PowerType.BUFF;
   }

   @Override
   public void onSpecificTrigger() {
      if (this.amount <= 0) {
         this.addToTop(new RemoveSpecificPowerAction(this.owner, this.owner, "Artifact"));
      } else {
         this.addToTop(new ReducePowerAction(this.owner, this.owner, "Artifact", 1));
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

   static {
      NAME = powerStrings.NAME;
      DESCRIPTIONS = powerStrings.DESCRIPTIONS;
   }
}
