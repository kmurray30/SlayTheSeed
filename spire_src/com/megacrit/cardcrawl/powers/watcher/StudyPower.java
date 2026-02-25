package com.megacrit.cardcrawl.powers.watcher;

import com.megacrit.cardcrawl.actions.common.MakeTempCardInDrawPileAction;
import com.megacrit.cardcrawl.cards.tempCards.Insight;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;
import com.megacrit.cardcrawl.powers.AbstractPower;

public class StudyPower extends AbstractPower {
   public static final String POWER_ID = "Study";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Study");

   public StudyPower(AbstractCreature owner, int amount) {
      this.name = powerStrings.NAME;
      this.ID = "Study";
      this.owner = owner;
      this.amount = amount;
      this.updateDescription();
      this.loadRegion("draw");
      this.type = AbstractPower.PowerType.BUFF;
      this.isTurnBased = false;
   }

   @Override
   public void atEndOfTurn(boolean playerTurn) {
      this.addToBot(new MakeTempCardInDrawPileAction(new Insight(), this.amount, true, true));
   }

   @Override
   public void updateDescription() {
      if (this.amount > 1) {
         this.description = powerStrings.DESCRIPTIONS[0] + this.amount + powerStrings.DESCRIPTIONS[1];
      } else {
         this.description = powerStrings.DESCRIPTIONS[0] + this.amount + powerStrings.DESCRIPTIONS[2];
      }
   }
}
