package com.megacrit.cardcrawl.cards.blue;

import com.megacrit.cardcrawl.actions.common.GainBlockAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class Stack extends AbstractCard {
   public static final String ID = "Stack";
   private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Stack");

   public Stack() {
      super(
         "Stack",
         cardStrings.NAME,
         "blue/skill/stack",
         1,
         cardStrings.DESCRIPTION,
         AbstractCard.CardType.SKILL,
         AbstractCard.CardColor.BLUE,
         AbstractCard.CardRarity.COMMON,
         AbstractCard.CardTarget.SELF
      );
      this.baseBlock = 0;
   }

   @Override
   public void use(AbstractPlayer p, AbstractMonster m) {
      this.addToBot(new GainBlockAction(p, p, this.block));
      if (!this.upgraded) {
         this.rawDescription = cardStrings.DESCRIPTION;
      } else {
         this.rawDescription = cardStrings.UPGRADE_DESCRIPTION;
      }

      this.initializeDescription();
   }

   @Override
   public void applyPowers() {
      this.baseBlock = AbstractDungeon.player.discardPile.size();
      if (this.upgraded) {
         this.baseBlock += 3;
      }

      super.applyPowers();
      if (!this.upgraded) {
         this.rawDescription = cardStrings.DESCRIPTION;
      } else {
         this.rawDescription = cardStrings.UPGRADE_DESCRIPTION;
      }

      this.rawDescription = this.rawDescription + cardStrings.EXTENDED_DESCRIPTION[0];
      this.initializeDescription();
   }

   @Override
   public void upgrade() {
      if (!this.upgraded) {
         this.upgradeName();
         this.upgradeBlock(3);
         this.rawDescription = cardStrings.UPGRADE_DESCRIPTION;
         this.initializeDescription();
      }
   }

   @Override
   public AbstractCard makeCopy() {
      return new Stack();
   }
}
