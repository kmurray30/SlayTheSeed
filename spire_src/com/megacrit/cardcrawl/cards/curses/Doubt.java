package com.megacrit.cardcrawl.cards.curses;

import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.common.SetDontTriggerAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardQueueItem;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.WeakPower;

public class Doubt extends AbstractCard {
   public static final String ID = "Doubt";
   private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Doubt");

   public Doubt() {
      super(
         "Doubt",
         cardStrings.NAME,
         "curse/doubt",
         -2,
         cardStrings.DESCRIPTION,
         AbstractCard.CardType.CURSE,
         AbstractCard.CardColor.CURSE,
         AbstractCard.CardRarity.CURSE,
         AbstractCard.CardTarget.NONE
      );
   }

   @Override
   public void use(AbstractPlayer p, AbstractMonster m) {
      if (this.dontTriggerOnUseCard) {
         this.addToTop(new ApplyPowerAction(AbstractDungeon.player, AbstractDungeon.player, new WeakPower(AbstractDungeon.player, 1, true), 1));
      }
   }

   @Override
   public void triggerWhenDrawn() {
      this.addToBot(new SetDontTriggerAction(this, false));
   }

   @Override
   public void triggerOnEndOfTurnForPlayingCard() {
      this.dontTriggerOnUseCard = true;
      AbstractDungeon.actionManager.cardQueue.add(new CardQueueItem(this, true));
   }

   @Override
   public void upgrade() {
   }

   @Override
   public AbstractCard makeCopy() {
      return new Doubt();
   }
}
