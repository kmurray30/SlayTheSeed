package com.megacrit.cardcrawl.cards.green;

import com.megacrit.cardcrawl.actions.common.DrawCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class Reflex extends AbstractCard {
   public static final String ID = "Reflex";
   private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Reflex");

   public Reflex() {
      super(
         "Reflex",
         cardStrings.NAME,
         "green/skill/reflex",
         -2,
         cardStrings.DESCRIPTION,
         AbstractCard.CardType.SKILL,
         AbstractCard.CardColor.GREEN,
         AbstractCard.CardRarity.UNCOMMON,
         AbstractCard.CardTarget.NONE
      );
      this.baseMagicNumber = 2;
      this.magicNumber = this.baseMagicNumber;
   }

   @Override
   public void use(AbstractPlayer p, AbstractMonster m) {
      this.addToBot(new DrawCardAction(p, this.magicNumber));
   }

   @Override
   public boolean canUse(AbstractPlayer p, AbstractMonster m) {
      this.cantUseMessage = cardStrings.EXTENDED_DESCRIPTION[0];
      return false;
   }

   @Override
   public void triggerOnManualDiscard() {
      this.addToBot(new DrawCardAction(AbstractDungeon.player, this.magicNumber));
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
      return new Reflex();
   }
}
