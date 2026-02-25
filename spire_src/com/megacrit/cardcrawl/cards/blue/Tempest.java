package com.megacrit.cardcrawl.cards.blue;

import com.megacrit.cardcrawl.actions.unique.TempestAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class Tempest extends AbstractCard {
   public static final String ID = "Tempest";
   private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Tempest");

   public Tempest() {
      super(
         "Tempest",
         cardStrings.NAME,
         "blue/skill/tempest",
         -1,
         cardStrings.DESCRIPTION,
         AbstractCard.CardType.SKILL,
         AbstractCard.CardColor.BLUE,
         AbstractCard.CardRarity.UNCOMMON,
         AbstractCard.CardTarget.SELF
      );
      this.showEvokeValue = true;
      this.showEvokeOrbCount = 3;
      this.exhaust = true;
   }

   @Override
   public void use(AbstractPlayer p, AbstractMonster m) {
      this.addToBot(new TempestAction(p, this.energyOnUse, this.upgraded, this.freeToPlayOnce));
   }

   @Override
   public void upgrade() {
      if (!this.upgraded) {
         this.upgradeName();
         this.rawDescription = cardStrings.UPGRADE_DESCRIPTION;
         this.initializeDescription();
      }
   }

   @Override
   public AbstractCard makeCopy() {
      return new Tempest();
   }
}
