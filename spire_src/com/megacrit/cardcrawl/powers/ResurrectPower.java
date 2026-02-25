package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class ResurrectPower extends AbstractPower {
   public static final String POWER_ID = "Life Link";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Life Link");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;

   public ResurrectPower(AbstractCreature owner) {
      this.name = NAME;
      this.ID = "Life Link";
      this.owner = owner;
      this.updateDescription();
      this.loadRegion("regrow");
      this.type = AbstractPower.PowerType.BUFF;
   }

   @Override
   public void updateDescription() {
      this.description = DESCRIPTIONS[0];
   }

   static {
      NAME = powerStrings.NAME;
      DESCRIPTIONS = powerStrings.DESCRIPTIONS;
   }
}
