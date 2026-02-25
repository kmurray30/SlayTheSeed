package com.megacrit.cardcrawl.cards.green;

import com.megacrit.cardcrawl.actions.common.DiscardAction;
import com.megacrit.cardcrawl.actions.common.GainBlockAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class Survivor extends AbstractCard {
   public static final String ID = "Survivor";
   private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Survivor");

   public Survivor() {
      super(
         "Survivor",
         cardStrings.NAME,
         "green/skill/survivor",
         1,
         cardStrings.DESCRIPTION,
         AbstractCard.CardType.SKILL,
         AbstractCard.CardColor.GREEN,
         AbstractCard.CardRarity.BASIC,
         AbstractCard.CardTarget.SELF
      );
      this.baseBlock = 8;
   }

   @Override
   public void use(AbstractPlayer p, AbstractMonster m) {
      this.addToBot(new GainBlockAction(p, p, this.block));
      this.addToBot(new DiscardAction(p, p, 1, false));
   }

   @Override
   public void upgrade() {
      if (!this.upgraded) {
         this.upgradeName();
         this.upgradeBlock(3);
      }
   }

   @Override
   public AbstractCard makeCopy() {
      return new Survivor();
   }
}
