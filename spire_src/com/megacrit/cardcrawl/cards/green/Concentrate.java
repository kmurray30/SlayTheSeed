package com.megacrit.cardcrawl.cards.green;

import com.megacrit.cardcrawl.actions.common.DiscardAction;
import com.megacrit.cardcrawl.actions.common.GainEnergyAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class Concentrate extends AbstractCard {
   public static final String ID = "Concentrate";
   private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Concentrate");

   public Concentrate() {
      super(
         "Concentrate",
         cardStrings.NAME,
         "green/skill/concentrate",
         0,
         cardStrings.DESCRIPTION,
         AbstractCard.CardType.SKILL,
         AbstractCard.CardColor.GREEN,
         AbstractCard.CardRarity.UNCOMMON,
         AbstractCard.CardTarget.SELF
      );
      this.baseMagicNumber = 3;
      this.magicNumber = this.baseMagicNumber;
   }

   @Override
   public void use(AbstractPlayer p, AbstractMonster m) {
      this.addToBot(new DiscardAction(p, p, this.magicNumber, false));
      this.addToBot(new GainEnergyAction(2));
   }

   @Override
   public void upgrade() {
      if (!this.upgraded) {
         this.upgradeName();
         this.upgradeMagicNumber(-1);
         this.initializeDescription();
      }
   }

   @Override
   public AbstractCard makeCopy() {
      return new Concentrate();
   }
}
