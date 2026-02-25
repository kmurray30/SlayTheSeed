package com.megacrit.cardcrawl.cards.red;

import com.megacrit.cardcrawl.actions.unique.SpotWeaknessAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class SpotWeakness extends AbstractCard {
   public static final String ID = "Spot Weakness";
   private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Spot Weakness");

   public SpotWeakness() {
      super(
         "Spot Weakness",
         cardStrings.NAME,
         "red/skill/spot_weakness",
         1,
         cardStrings.DESCRIPTION,
         AbstractCard.CardType.SKILL,
         AbstractCard.CardColor.RED,
         AbstractCard.CardRarity.UNCOMMON,
         AbstractCard.CardTarget.SELF_AND_ENEMY
      );
      this.baseMagicNumber = 3;
      this.magicNumber = this.baseMagicNumber;
   }

   @Override
   public void use(AbstractPlayer p, AbstractMonster m) {
      this.addToBot(new SpotWeaknessAction(this.magicNumber, m));
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
      return new SpotWeakness();
   }
}
