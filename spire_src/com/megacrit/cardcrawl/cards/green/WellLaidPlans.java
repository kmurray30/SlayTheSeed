package com.megacrit.cardcrawl.cards.green;

import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.RetainCardPower;

public class WellLaidPlans extends AbstractCard {
   public static final String ID = "Well Laid Plans";
   private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Well Laid Plans");

   public WellLaidPlans() {
      super(
         "Well Laid Plans",
         cardStrings.NAME,
         "green/power/well_laid_plans",
         1,
         cardStrings.DESCRIPTION,
         AbstractCard.CardType.POWER,
         AbstractCard.CardColor.GREEN,
         AbstractCard.CardRarity.UNCOMMON,
         AbstractCard.CardTarget.NONE
      );
      this.baseMagicNumber = 1;
      this.magicNumber = 1;
   }

   @Override
   public void use(AbstractPlayer p, AbstractMonster m) {
      this.addToBot(new ApplyPowerAction(p, p, new RetainCardPower(p, this.magicNumber), this.magicNumber));
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

   @Override
   public AbstractCard makeCopy() {
      return new WellLaidPlans();
   }
}
