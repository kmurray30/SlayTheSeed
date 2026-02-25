package com.megacrit.cardcrawl.cards.green;

import com.megacrit.cardcrawl.actions.unique.ExpertiseAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class Expertise extends AbstractCard {
   public static final String ID = "Expertise";
   private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Expertise");

   public Expertise() {
      super(
         "Expertise",
         cardStrings.NAME,
         "green/skill/expertise",
         1,
         cardStrings.DESCRIPTION,
         AbstractCard.CardType.SKILL,
         AbstractCard.CardColor.GREEN,
         AbstractCard.CardRarity.UNCOMMON,
         AbstractCard.CardTarget.SELF
      );
      this.baseMagicNumber = 6;
      this.magicNumber = this.baseMagicNumber;
   }

   @Override
   public void use(AbstractPlayer p, AbstractMonster m) {
      this.addToBot(new ExpertiseAction(p, this.magicNumber));
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
      return new Expertise();
   }
}
