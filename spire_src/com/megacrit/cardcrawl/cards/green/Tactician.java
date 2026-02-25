package com.megacrit.cardcrawl.cards.green;

import com.megacrit.cardcrawl.actions.common.GainEnergyAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class Tactician extends AbstractCard {
   public static final String ID = "Tactician";
   private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Tactician");

   public Tactician() {
      super(
         "Tactician",
         cardStrings.NAME,
         "green/skill/tactician",
         -2,
         cardStrings.DESCRIPTION,
         AbstractCard.CardType.SKILL,
         AbstractCard.CardColor.GREEN,
         AbstractCard.CardRarity.UNCOMMON,
         AbstractCard.CardTarget.NONE
      );
      this.baseMagicNumber = 1;
      this.magicNumber = this.baseMagicNumber;
   }

   @Override
   public void use(AbstractPlayer p, AbstractMonster m) {
   }

   @Override
   public boolean canUse(AbstractPlayer p, AbstractMonster m) {
      this.cantUseMessage = cardStrings.EXTENDED_DESCRIPTION[0];
      return false;
   }

   @Override
   public void triggerOnManualDiscard() {
      this.addToTop(new GainEnergyAction(this.magicNumber));
   }

   @Override
   public AbstractCard makeCopy() {
      return new Tactician();
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
}
