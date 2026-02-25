package com.megacrit.cardcrawl.cards.purple;

import com.megacrit.cardcrawl.actions.common.ExhaustSpecificCardAction;
import com.megacrit.cardcrawl.actions.common.MakeTempCardInHandAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.tempCards.Miracle;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class DeusExMachina extends AbstractCard {
   public static final String ID = "DeusExMachina";
   private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("DeusExMachina");

   public DeusExMachina() {
      super(
         "DeusExMachina",
         cardStrings.NAME,
         "purple/skill/deus_ex_machina",
         -2,
         cardStrings.DESCRIPTION,
         AbstractCard.CardType.SKILL,
         AbstractCard.CardColor.PURPLE,
         AbstractCard.CardRarity.RARE,
         AbstractCard.CardTarget.SELF
      );
      this.exhaust = true;
      this.baseMagicNumber = 2;
      this.magicNumber = this.baseMagicNumber;
      this.cardsToPreview = new Miracle();
   }

   @Override
   public void triggerWhenDrawn() {
      this.addToTop(new MakeTempCardInHandAction(new Miracle(), this.magicNumber));
      this.addToTop(new ExhaustSpecificCardAction(this, AbstractDungeon.player.hand));
   }

   @Override
   public void use(AbstractPlayer p, AbstractMonster m) {
   }

   @Override
   public boolean canUse(AbstractPlayer p, AbstractMonster m) {
      return false;
   }

   @Override
   public void upgrade() {
      if (!this.upgraded) {
         this.upgradeName();
         this.upgradeMagicNumber(1);
      }
   }

   @Override
   public AbstractCard makeCopy() {
      return new DeusExMachina();
   }
}
