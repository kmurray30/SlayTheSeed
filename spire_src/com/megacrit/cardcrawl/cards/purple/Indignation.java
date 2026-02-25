package com.megacrit.cardcrawl.cards.purple;

import com.megacrit.cardcrawl.actions.watcher.IndignationAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class Indignation extends AbstractCard {
   public static final String ID = "Indignation";
   private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Indignation");

   public Indignation() {
      super(
         "Indignation",
         cardStrings.NAME,
         "purple/skill/indignation",
         1,
         cardStrings.DESCRIPTION,
         AbstractCard.CardType.SKILL,
         AbstractCard.CardColor.PURPLE,
         AbstractCard.CardRarity.UNCOMMON,
         AbstractCard.CardTarget.NONE
      );
      this.baseMagicNumber = 3;
      this.magicNumber = this.baseMagicNumber;
   }

   @Override
   public void use(AbstractPlayer p, AbstractMonster m) {
      this.addToBot(new IndignationAction(this.magicNumber));
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
      return new Indignation();
   }
}
