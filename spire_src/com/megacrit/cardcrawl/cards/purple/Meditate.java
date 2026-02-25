package com.megacrit.cardcrawl.cards.purple;

import com.megacrit.cardcrawl.actions.watcher.ChangeStanceAction;
import com.megacrit.cardcrawl.actions.watcher.MeditateAction;
import com.megacrit.cardcrawl.actions.watcher.PressEndTurnButtonAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class Meditate extends AbstractCard {
   public static final String ID = "Meditate";
   private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Meditate");

   public Meditate() {
      super(
         "Meditate",
         cardStrings.NAME,
         "purple/skill/meditate",
         1,
         cardStrings.DESCRIPTION,
         AbstractCard.CardType.SKILL,
         AbstractCard.CardColor.PURPLE,
         AbstractCard.CardRarity.UNCOMMON,
         AbstractCard.CardTarget.NONE
      );
      this.baseMagicNumber = 1;
      this.magicNumber = this.baseMagicNumber;
   }

   @Override
   public void use(AbstractPlayer p, AbstractMonster m) {
      this.addToBot(new MeditateAction(this.magicNumber));
      this.addToBot(new ChangeStanceAction("Calm"));
      this.addToBot(new PressEndTurnButtonAction());
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
      return new Meditate();
   }
}
