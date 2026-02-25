package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.common.MakeTempCardInHandAction;
import com.megacrit.cardcrawl.actions.common.RemoveSpecificPowerAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class NightmarePower extends AbstractPower {
   public static final String POWER_ID = "Night Terror";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Night Terror");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;
   private AbstractCard card;

   public NightmarePower(AbstractCreature owner, int cardAmt, AbstractCard copyMe) {
      this.name = NAME;
      this.ID = "Night Terror";
      this.owner = owner;
      this.amount = cardAmt;
      this.loadRegion("nightmare");
      this.card = copyMe.makeStatEquivalentCopy();
      this.card.resetAttributes();
      this.updateDescription();
   }

   @Override
   public void updateDescription() {
      this.description = DESCRIPTIONS[0] + this.amount + " " + FontHelper.colorString(this.card.name, "y") + DESCRIPTIONS[1];
   }

   @Override
   public void atStartOfTurn() {
      this.addToBot(new MakeTempCardInHandAction(this.card, this.amount));
      this.addToBot(new RemoveSpecificPowerAction(this.owner, this.owner, "Night Terror"));
   }

   static {
      NAME = powerStrings.NAME;
      DESCRIPTIONS = powerStrings.DESCRIPTIONS;
   }
}
