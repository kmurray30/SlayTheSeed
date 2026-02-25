package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class MinionPower extends AbstractPower {
   public static final String POWER_ID = "Minion";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Minion");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;

   public MinionPower(AbstractCreature owner) {
      this.name = NAME;
      this.ID = "Minion";
      this.owner = owner;
      this.updateDescription();
      this.loadRegion("minion");
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
