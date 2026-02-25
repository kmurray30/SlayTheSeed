package com.megacrit.cardcrawl.cards.purple;

import com.megacrit.cardcrawl.actions.common.GainBlockAction;
import com.megacrit.cardcrawl.actions.watcher.ChangeStanceAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class Vigilance extends AbstractCard {
   public static final String ID = "Vigilance";
   private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Vigilance");

   public Vigilance() {
      super(
         "Vigilance",
         cardStrings.NAME,
         "purple/skill/vigilance",
         2,
         cardStrings.DESCRIPTION,
         AbstractCard.CardType.SKILL,
         AbstractCard.CardColor.PURPLE,
         AbstractCard.CardRarity.BASIC,
         AbstractCard.CardTarget.SELF
      );
      this.baseBlock = 8;
      this.block = this.baseBlock;
   }

   @Override
   public void use(AbstractPlayer p, AbstractMonster m) {
      this.addToBot(new GainBlockAction(p, this.block));
      this.addToBot(new ChangeStanceAction("Calm"));
   }

   @Override
   public void upgrade() {
      if (!this.upgraded) {
         this.upgradeName();
         this.upgradeBlock(4);
      }
   }

   @Override
   public AbstractCard makeCopy() {
      return new Vigilance();
   }
}
