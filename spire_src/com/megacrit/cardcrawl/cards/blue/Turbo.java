package com.megacrit.cardcrawl.cards.blue;

import com.megacrit.cardcrawl.actions.common.GainEnergyAction;
import com.megacrit.cardcrawl.actions.common.MakeTempCardInDiscardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.status.VoidCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class Turbo extends AbstractCard {
   public static final String ID = "Turbo";
   private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Turbo");

   public Turbo() {
      super(
         "Turbo",
         cardStrings.NAME,
         "blue/skill/turbo",
         0,
         cardStrings.DESCRIPTION,
         AbstractCard.CardType.SKILL,
         AbstractCard.CardColor.BLUE,
         AbstractCard.CardRarity.COMMON,
         AbstractCard.CardTarget.SELF
      );
      this.baseMagicNumber = 2;
      this.magicNumber = this.baseMagicNumber;
      this.cardsToPreview = new VoidCard();
   }

   @Override
   public void use(AbstractPlayer p, AbstractMonster m) {
      this.addToBot(new GainEnergyAction(this.magicNumber));
      this.addToBot(new MakeTempCardInDiscardAction(new VoidCard(), 1));
   }

   @Override
   public void upgrade() {
      if (!this.upgraded) {
         this.upgradeName();
         this.upgradeMagicNumber(1);
         this.rawDescription = cardStrings.UPGRADE_DESCRIPTION;
         this.initializeDescription();
      }
   }

   @Override
   public AbstractCard makeCopy() {
      return new Turbo();
   }
}
