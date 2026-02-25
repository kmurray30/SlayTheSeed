package com.megacrit.cardcrawl.cards.deprecated;

import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.deprecated.DEPRECATEDCondensePower;

public class DEPRECATEDCondense extends AbstractCard {
   public static final String ID = "Condense";
   private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Condense");

   public DEPRECATEDCondense() {
      super(
         "Condense",
         cardStrings.NAME,
         null,
         2,
         cardStrings.DESCRIPTION,
         AbstractCard.CardType.POWER,
         AbstractCard.CardColor.PURPLE,
         AbstractCard.CardRarity.RARE,
         AbstractCard.CardTarget.SELF
      );
      this.baseMagicNumber = 10;
      this.magicNumber = this.baseMagicNumber;
   }

   @Override
   public void use(AbstractPlayer p, AbstractMonster m) {
      this.addToBot(new ApplyPowerAction(p, p, new DEPRECATEDCondensePower(p, this.magicNumber)));
   }

   @Override
   public void upgrade() {
      if (!this.upgraded) {
         this.upgradeName();
         this.upgradeMagicNumber(-2);
      }
   }

   @Override
   public AbstractCard makeCopy() {
      return new DEPRECATEDCondense();
   }
}
