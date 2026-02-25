package com.megacrit.cardcrawl.cards.red;

import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.EvolvePower;

public class Evolve extends AbstractCard {
   public static final String ID = "Evolve";
   private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Evolve");

   public Evolve() {
      super(
         "Evolve",
         cardStrings.NAME,
         "red/power/evolve",
         1,
         cardStrings.DESCRIPTION,
         AbstractCard.CardType.POWER,
         AbstractCard.CardColor.RED,
         AbstractCard.CardRarity.UNCOMMON,
         AbstractCard.CardTarget.SELF
      );
      this.baseMagicNumber = 1;
      this.magicNumber = this.baseMagicNumber;
   }

   @Override
   public void use(AbstractPlayer p, AbstractMonster m) {
      this.addToBot(new ApplyPowerAction(p, p, new EvolvePower(p, this.magicNumber), this.magicNumber));
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
      return new Evolve();
   }
}
