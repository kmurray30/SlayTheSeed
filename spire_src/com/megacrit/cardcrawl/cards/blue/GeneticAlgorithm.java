package com.megacrit.cardcrawl.cards.blue;

import com.megacrit.cardcrawl.actions.common.GainBlockAction;
import com.megacrit.cardcrawl.actions.defect.IncreaseMiscAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class GeneticAlgorithm extends AbstractCard {
   public static final String ID = "Genetic Algorithm";
   private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Genetic Algorithm");

   public GeneticAlgorithm() {
      super(
         "Genetic Algorithm",
         cardStrings.NAME,
         "blue/skill/genetic_algorithm",
         1,
         cardStrings.DESCRIPTION,
         AbstractCard.CardType.SKILL,
         AbstractCard.CardColor.BLUE,
         AbstractCard.CardRarity.UNCOMMON,
         AbstractCard.CardTarget.SELF
      );
      this.misc = 1;
      this.baseMagicNumber = 2;
      this.magicNumber = this.baseMagicNumber;
      this.baseBlock = this.misc;
      this.exhaust = true;
   }

   @Override
   public void use(AbstractPlayer p, AbstractMonster m) {
      this.addToBot(new IncreaseMiscAction(this.uuid, this.misc, this.magicNumber));
      this.addToBot(new GainBlockAction(p, p, this.block));
   }

   @Override
   public void applyPowers() {
      this.baseBlock = this.misc;
      super.applyPowers();
      this.initializeDescription();
   }

   @Override
   public void upgrade() {
      if (!this.upgraded) {
         this.upgradeMagicNumber(1);
         this.upgradeName();
      }
   }

   @Override
   public AbstractCard makeCopy() {
      return new GeneticAlgorithm();
   }
}
