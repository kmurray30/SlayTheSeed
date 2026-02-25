package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.utility.UseCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.PowerStrings;
import com.megacrit.cardcrawl.vfx.cardManip.ExhaustCardEffect;

public class TimeMazePower extends AbstractPower {
   public static final String POWER_ID = "TimeMazePower";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("TimeMazePower");
   public static final String NAME;
   public static final String[] DESC = powerStrings.DESCRIPTIONS;
   private int maxAmount;

   public TimeMazePower(AbstractCreature owner, int maxAmount) {
      this.name = NAME;
      this.ID = "TimeMazePower";
      this.owner = owner;
      this.amount = maxAmount;
      this.maxAmount = maxAmount;
      this.updateDescription();
      this.loadRegion("time");
      this.type = AbstractPower.PowerType.BUFF;
   }

   @Override
   public void updateDescription() {
      this.description = DESC[0] + this.maxAmount + DESC[1];
   }

   @Override
   public void onAfterUseCard(AbstractCard card, UseCardAction action) {
      this.flashWithoutSound();
      this.amount--;
      if (this.amount == 0) {
         this.amount = this.maxAmount;
         AbstractDungeon.actionManager.cardQueue.clear();

         for (AbstractCard c : AbstractDungeon.player.limbo.group) {
            AbstractDungeon.effectList.add(new ExhaustCardEffect(c));
         }

         AbstractDungeon.player.limbo.group.clear();
         AbstractDungeon.player.releaseCard();
         AbstractDungeon.overlayMenu.endTurnButton.disable(true);
      }

      this.updateDescription();
   }

   @Override
   public void atStartOfTurn() {
      this.amount = 15;
   }

   static {
      NAME = powerStrings.NAME;
   }
}
