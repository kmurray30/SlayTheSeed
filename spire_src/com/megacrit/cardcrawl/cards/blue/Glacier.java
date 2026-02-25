package com.megacrit.cardcrawl.cards.blue;

import com.megacrit.cardcrawl.actions.common.GainBlockAction;
import com.megacrit.cardcrawl.actions.defect.ChannelAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.orbs.Frost;

public class Glacier extends AbstractCard {
   public static final String ID = "Glacier";
   private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Glacier");

   public Glacier() {
      super(
         "Glacier",
         cardStrings.NAME,
         "blue/skill/glacier",
         2,
         cardStrings.DESCRIPTION,
         AbstractCard.CardType.SKILL,
         AbstractCard.CardColor.BLUE,
         AbstractCard.CardRarity.UNCOMMON,
         AbstractCard.CardTarget.SELF
      );
      this.showEvokeValue = true;
      this.showEvokeOrbCount = 2;
      this.baseMagicNumber = 2;
      this.magicNumber = this.baseMagicNumber;
      this.baseBlock = 7;
   }

   @Override
   public void use(AbstractPlayer p, AbstractMonster m) {
      this.addToBot(new GainBlockAction(p, p, this.block));

      for (int i = 0; i < this.magicNumber; i++) {
         this.addToBot(new ChannelAction(new Frost()));
      }
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
      return new Glacier();
   }
}
