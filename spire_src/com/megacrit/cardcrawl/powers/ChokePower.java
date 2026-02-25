package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.common.LoseHPAction;
import com.megacrit.cardcrawl.actions.common.RemoveSpecificPowerAction;
import com.megacrit.cardcrawl.actions.utility.UseCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class ChokePower extends AbstractPower {
   public static final String POWER_ID = "Choked";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Choked");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;

   public ChokePower(AbstractCreature owner, int amount) {
      this.name = NAME;
      this.ID = "Choked";
      this.owner = owner;
      this.amount = amount;
      this.description = DESCRIPTIONS[0] + amount + DESCRIPTIONS[1];
      this.loadRegion("choke");
      this.type = AbstractPower.PowerType.DEBUFF;
   }

   @Override
   public void atStartOfTurn() {
      this.addToBot(new RemoveSpecificPowerAction(this.owner, this.owner, "Choked"));
   }

   @Override
   public void onUseCard(AbstractCard card, UseCardAction action) {
      this.flash();
      this.addToBot(new LoseHPAction(this.owner, null, this.amount));
   }

   static {
      NAME = powerStrings.NAME;
      DESCRIPTIONS = powerStrings.DESCRIPTIONS;
   }
}
