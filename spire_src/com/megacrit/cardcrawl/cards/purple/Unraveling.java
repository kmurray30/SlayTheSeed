package com.megacrit.cardcrawl.cards.purple;

import com.megacrit.cardcrawl.actions.watcher.UnravelingAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class Unraveling extends AbstractCard {
   public static final String ID = "Unraveling";
   private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Unraveling");

   public Unraveling() {
      super(
         "Unraveling",
         cardStrings.NAME,
         "purple/skill/unraveling",
         2,
         cardStrings.DESCRIPTION,
         AbstractCard.CardType.SKILL,
         AbstractCard.CardColor.PURPLE,
         AbstractCard.CardRarity.RARE,
         AbstractCard.CardTarget.NONE
      );
      this.exhaust = true;
   }

   @Override
   public void use(AbstractPlayer p, AbstractMonster m) {
      this.addToBot(new UnravelingAction());
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
      return new Unraveling();
   }
}
