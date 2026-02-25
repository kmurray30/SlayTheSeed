package com.megacrit.cardcrawl.cards.green;

import com.megacrit.cardcrawl.actions.common.GainBlockAction;
import com.megacrit.cardcrawl.actions.common.MakeTempCardInHandAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.tempCards.Shiv;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class CloakAndDagger extends AbstractCard {
   public static final String ID = "Cloak And Dagger";
   private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Cloak And Dagger");

   public CloakAndDagger() {
      super(
         "Cloak And Dagger",
         cardStrings.NAME,
         "green/skill/cloak_and_dagger",
         1,
         cardStrings.DESCRIPTION,
         AbstractCard.CardType.SKILL,
         AbstractCard.CardColor.GREEN,
         AbstractCard.CardRarity.COMMON,
         AbstractCard.CardTarget.SELF
      );
      this.baseBlock = 6;
      this.baseMagicNumber = 1;
      this.magicNumber = this.baseMagicNumber;
      this.cardsToPreview = new Shiv();
   }

   @Override
   public void use(AbstractPlayer p, AbstractMonster m) {
      this.addToBot(new GainBlockAction(p, p, this.block));
      this.addToBot(new MakeTempCardInHandAction(new Shiv(), this.magicNumber));
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
      return new CloakAndDagger();
   }
}
