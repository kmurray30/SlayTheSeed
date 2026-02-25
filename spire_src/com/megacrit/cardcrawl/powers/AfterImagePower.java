package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.common.GainBlockAction;
import com.megacrit.cardcrawl.actions.utility.UseCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class AfterImagePower extends AbstractPower {
   public static final String POWER_ID = "After Image";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("After Image");

   public AfterImagePower(AbstractCreature owner, int amount) {
      this.name = powerStrings.NAME;
      this.ID = "After Image";
      this.owner = owner;
      this.amount = amount;
      this.updateDescription();
      this.loadRegion("afterImage");
   }

   @Override
   public void updateDescription() {
      this.description = powerStrings.DESCRIPTIONS[0] + this.amount + powerStrings.DESCRIPTIONS[1];
   }

   @Override
   public void onUseCard(AbstractCard card, UseCardAction action) {
      if (Settings.FAST_MODE) {
         this.addToBot(new GainBlockAction(AbstractDungeon.player, AbstractDungeon.player, this.amount, true));
      } else {
         this.addToBot(new GainBlockAction(AbstractDungeon.player, AbstractDungeon.player, this.amount));
      }

      this.flash();
   }
}
