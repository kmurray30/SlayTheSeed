package com.megacrit.cardcrawl.cards.curses;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class Normality extends AbstractCard {
   public static final String ID = "Normality";
   private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Normality");
   private static final int PLAY_LIMIT = 3;

   public Normality() {
      super(
         "Normality",
         cardStrings.NAME,
         "curse/normality",
         -2,
         cardStrings.DESCRIPTION,
         AbstractCard.CardType.CURSE,
         AbstractCard.CardColor.CURSE,
         AbstractCard.CardRarity.CURSE,
         AbstractCard.CardTarget.NONE
      );
   }

   @Override
   public boolean canPlay(AbstractCard card) {
      if (AbstractDungeon.actionManager.cardsPlayedThisTurn.size() >= 3) {
         card.cantUseMessage = cardStrings.EXTENDED_DESCRIPTION[0];
         return false;
      } else {
         return true;
      }
   }

   @Override
   public void use(AbstractPlayer p, AbstractMonster m) {
   }

   @Override
   public void applyPowers() {
      super.applyPowers();
      if (AbstractDungeon.actionManager.cardsPlayedThisTurn.size() == 0) {
         this.rawDescription = cardStrings.EXTENDED_DESCRIPTION[1] + 3 + cardStrings.EXTENDED_DESCRIPTION[2];
      } else if (AbstractDungeon.actionManager.cardsPlayedThisTurn.size() == 1) {
         this.rawDescription = cardStrings.EXTENDED_DESCRIPTION[1]
            + 3
            + cardStrings.EXTENDED_DESCRIPTION[3]
            + AbstractDungeon.actionManager.cardsPlayedThisTurn.size()
            + cardStrings.EXTENDED_DESCRIPTION[4];
      } else {
         this.rawDescription = cardStrings.EXTENDED_DESCRIPTION[1]
            + 3
            + cardStrings.EXTENDED_DESCRIPTION[3]
            + AbstractDungeon.actionManager.cardsPlayedThisTurn.size()
            + cardStrings.EXTENDED_DESCRIPTION[5];
      }

      this.initializeDescription();
   }

   @Override
   public void upgrade() {
   }

   @Override
   public AbstractCard makeCopy() {
      return new Normality();
   }
}
