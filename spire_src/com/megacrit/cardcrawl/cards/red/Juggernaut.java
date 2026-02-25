package com.megacrit.cardcrawl.cards.red;

import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.JuggernautPower;

public class Juggernaut extends AbstractCard {
   public static final String ID = "Juggernaut";
   private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Juggernaut");

   public Juggernaut() {
      super(
         "Juggernaut",
         cardStrings.NAME,
         "red/power/juggernaut",
         2,
         cardStrings.DESCRIPTION,
         AbstractCard.CardType.POWER,
         AbstractCard.CardColor.RED,
         AbstractCard.CardRarity.RARE,
         AbstractCard.CardTarget.SELF
      );
      this.baseMagicNumber = 5;
      this.magicNumber = this.baseMagicNumber;
   }

   @Override
   public void use(AbstractPlayer p, AbstractMonster m) {
      this.addToBot(new ApplyPowerAction(p, p, new JuggernautPower(p, this.magicNumber), this.magicNumber));
   }

   @Override
   public void upgrade() {
      if (!this.upgraded) {
         this.upgradeName();
         this.upgradeMagicNumber(2);
      }
   }

   @Override
   public AbstractCard makeCopy() {
      return new Juggernaut();
   }
}
