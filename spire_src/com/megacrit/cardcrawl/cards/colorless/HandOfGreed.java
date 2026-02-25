package com.megacrit.cardcrawl.cards.colorless;

import com.megacrit.cardcrawl.actions.unique.GreedAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class HandOfGreed extends AbstractCard {
   public static final String ID = "HandOfGreed";
   private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("HandOfGreed");

   public HandOfGreed() {
      super(
         "HandOfGreed",
         cardStrings.NAME,
         "colorless/attack/hand_of_greed",
         2,
         cardStrings.DESCRIPTION,
         AbstractCard.CardType.ATTACK,
         AbstractCard.CardColor.COLORLESS,
         AbstractCard.CardRarity.RARE,
         AbstractCard.CardTarget.ENEMY
      );
      this.baseDamage = 20;
      this.baseMagicNumber = 20;
      this.magicNumber = this.baseMagicNumber;
   }

   @Override
   public void use(AbstractPlayer p, AbstractMonster m) {
      this.addToBot(new GreedAction(m, new DamageInfo(p, this.damage, this.damageTypeForTurn), this.magicNumber));
   }

   @Override
   public void upgrade() {
      if (!this.upgraded) {
         this.upgradeName();
         this.upgradeDamage(5);
         this.upgradeMagicNumber(5);
      }
   }

   @Override
   public AbstractCard makeCopy() {
      return new HandOfGreed();
   }
}
