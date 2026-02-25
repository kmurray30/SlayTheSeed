package com.megacrit.cardcrawl.cards.deprecated;

import com.megacrit.cardcrawl.actions.watcher.DivinePunishmentAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.tempCards.Smite;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class DEPRECATEDCleanseEvil extends AbstractCard {
   public static final String ID = "CleanseEvil";
   private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("CleanseEvil");

   public DEPRECATEDCleanseEvil() {
      super(
         "CleanseEvil",
         cardStrings.NAME,
         "purple/skill/cleanse_evil",
         -1,
         cardStrings.DESCRIPTION,
         AbstractCard.CardType.SKILL,
         AbstractCard.CardColor.PURPLE,
         AbstractCard.CardRarity.UNCOMMON,
         AbstractCard.CardTarget.SELF
      );
      this.cardsToPreview = new Smite();
   }

   @Override
   public void use(AbstractPlayer p, AbstractMonster m) {
      AbstractCard c = new Smite();
      if (this.upgraded) {
         c.upgrade();
      }

      this.addToBot(new DivinePunishmentAction(c, this.freeToPlayOnce, this.energyOnUse));
   }

   @Override
   public void upgrade() {
      if (!this.upgraded) {
         this.upgradeName();
         this.cardsToPreview.upgrade();
         this.rawDescription = cardStrings.UPGRADE_DESCRIPTION;
         this.initializeDescription();
      }
   }

   @Override
   public AbstractCard makeCopy() {
      return new DEPRECATEDCleanseEvil();
   }
}
