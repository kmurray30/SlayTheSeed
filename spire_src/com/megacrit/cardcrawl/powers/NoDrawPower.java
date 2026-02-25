package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.common.RemoveSpecificPowerAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class NoDrawPower extends AbstractPower {
   public static final String POWER_ID = "No Draw";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("No Draw");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;

   public NoDrawPower(AbstractCreature owner) {
      this.name = NAME;
      this.ID = "No Draw";
      this.owner = owner;
      this.type = AbstractPower.PowerType.DEBUFF;
      this.amount = -1;
      this.description = DESCRIPTIONS[0];
      this.loadRegion("noDraw");
   }

   @Override
   public void atEndOfTurn(boolean isPlayer) {
      if (isPlayer) {
         this.addToBot(new RemoveSpecificPowerAction(this.owner, this.owner, "No Draw"));
      }
   }

   static {
      NAME = powerStrings.NAME;
      DESCRIPTIONS = powerStrings.DESCRIPTIONS;
   }
}
