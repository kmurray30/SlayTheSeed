package com.megacrit.cardcrawl.cards.blue;

import com.megacrit.cardcrawl.actions.common.DrawCardAction;
import com.megacrit.cardcrawl.actions.common.MakeTempCardInDiscardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.status.Burn;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class Overclock extends AbstractCard {
   public static final String ID = "Steam Power";
   private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Steam Power");

   public Overclock() {
      super(
         "Steam Power",
         cardStrings.NAME,
         "blue/skill/overclock",
         0,
         cardStrings.DESCRIPTION,
         AbstractCard.CardType.SKILL,
         AbstractCard.CardColor.BLUE,
         AbstractCard.CardRarity.UNCOMMON,
         AbstractCard.CardTarget.SELF
      );
      this.baseMagicNumber = 2;
      this.magicNumber = this.baseMagicNumber;
      this.cardsToPreview = new Burn();
   }

   @Override
   public void use(AbstractPlayer p, AbstractMonster m) {
      this.addToBot(new DrawCardAction(p, this.magicNumber, false));
      this.addToBot(new MakeTempCardInDiscardAction(new Burn(), 1));
   }

   @Override
   public void upgrade() {
      if (!this.upgraded) {
         this.upgradeName();
         this.upgradeMagicNumber(1);
      }
   }

   @Override
   public AbstractCard makeCopy() {
      return new Overclock();
   }
}
