package com.megacrit.cardcrawl.cards.blue;

import com.megacrit.cardcrawl.actions.defect.NewRipAndTearAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class RipAndTear extends AbstractCard {
   public static final String ID = "Rip and Tear";
   private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Rip and Tear");

   public RipAndTear() {
      super(
         "Rip and Tear",
         cardStrings.NAME,
         "blue/attack/rip_and_tear",
         1,
         cardStrings.DESCRIPTION,
         AbstractCard.CardType.ATTACK,
         AbstractCard.CardColor.BLUE,
         AbstractCard.CardRarity.UNCOMMON,
         AbstractCard.CardTarget.ALL_ENEMY
      );
      this.baseDamage = 7;
      this.baseMagicNumber = 2;
      this.magicNumber = this.baseMagicNumber;
   }

   @Override
   public void use(AbstractPlayer p, AbstractMonster m) {
      for (int i = 0; i < this.magicNumber; i++) {
         this.addToBot(new NewRipAndTearAction(this));
      }
   }

   @Override
   public void upgrade() {
      if (!this.upgraded) {
         this.upgradeName();
         this.upgradeDamage(2);
      }
   }

   @Override
   public AbstractCard makeCopy() {
      return new RipAndTear();
   }
}
