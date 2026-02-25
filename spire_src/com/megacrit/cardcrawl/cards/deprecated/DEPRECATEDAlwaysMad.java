package com.megacrit.cardcrawl.cards.deprecated;

import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.deprecated.DEPRECATEDAlwaysMadPower;

public class DEPRECATEDAlwaysMad extends AbstractCard {
   public static final String ID = "AlwaysMad";
   private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("AlwaysMad");

   public DEPRECATEDAlwaysMad() {
      super(
         "AlwaysMad",
         cardStrings.NAME,
         null,
         2,
         cardStrings.DESCRIPTION,
         AbstractCard.CardType.POWER,
         AbstractCard.CardColor.PURPLE,
         AbstractCard.CardRarity.RARE,
         AbstractCard.CardTarget.SELF
      );
   }

   @Override
   public void use(AbstractPlayer p, AbstractMonster m) {
      this.addToBot(new ApplyPowerAction(p, p, new DEPRECATEDAlwaysMadPower(p)));
   }

   @Override
   public void upgrade() {
      if (!this.upgraded) {
         this.upgradeName();
         this.upgradeBaseCost(1);
      }
   }

   @Override
   public AbstractCard makeCopy() {
      return new DEPRECATEDAlwaysMad();
   }
}
