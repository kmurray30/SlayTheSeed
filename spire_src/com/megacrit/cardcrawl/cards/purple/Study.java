package com.megacrit.cardcrawl.cards.purple;

import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.tempCards.Insight;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.watcher.StudyPower;

public class Study extends AbstractCard {
   public static final String ID = "Study";
   private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Study");

   public Study() {
      super(
         "Study",
         cardStrings.NAME,
         "purple/power/study",
         2,
         cardStrings.DESCRIPTION,
         AbstractCard.CardType.POWER,
         AbstractCard.CardColor.PURPLE,
         AbstractCard.CardRarity.UNCOMMON,
         AbstractCard.CardTarget.SELF
      );
      this.baseMagicNumber = 1;
      this.magicNumber = this.baseMagicNumber;
      this.cardsToPreview = new Insight();
   }

   @Override
   public void use(AbstractPlayer p, AbstractMonster m) {
      this.addToBot(new ApplyPowerAction(p, p, new StudyPower(p, this.magicNumber), this.magicNumber));
   }

   @Override
   public AbstractCard makeCopy() {
      return new Study();
   }

   @Override
   public void upgrade() {
      if (!this.upgraded) {
         this.upgradeName();
         this.upgradeBaseCost(1);
      }
   }
}
