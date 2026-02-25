package com.megacrit.cardcrawl.cards.colorless;

import com.megacrit.cardcrawl.actions.utility.DrawPileToHandAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class Violence extends AbstractCard {
   public static final String ID = "Violence";
   private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Violence");

   public Violence() {
      super(
         "Violence",
         cardStrings.NAME,
         "colorless/skill/violence",
         0,
         cardStrings.DESCRIPTION,
         AbstractCard.CardType.SKILL,
         AbstractCard.CardColor.COLORLESS,
         AbstractCard.CardRarity.RARE,
         AbstractCard.CardTarget.NONE
      );
      this.baseMagicNumber = 3;
      this.magicNumber = this.baseMagicNumber;
      this.exhaust = true;
   }

   @Override
   public void use(AbstractPlayer p, AbstractMonster m) {
      this.addToBot(new DrawPileToHandAction(this.magicNumber, AbstractCard.CardType.ATTACK));
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
      return new Violence();
   }
}
