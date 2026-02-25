package com.megacrit.cardcrawl.cards.purple;

import com.megacrit.cardcrawl.actions.common.GainBlockAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class SpiritShield extends AbstractCard {
   public static final String ID = "SpiritShield";
   private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("SpiritShield");

   public SpiritShield() {
      super(
         "SpiritShield",
         cardStrings.NAME,
         "purple/skill/spirit_shield",
         2,
         cardStrings.DESCRIPTION,
         AbstractCard.CardType.SKILL,
         AbstractCard.CardColor.PURPLE,
         AbstractCard.CardRarity.RARE,
         AbstractCard.CardTarget.SELF
      );
      this.baseMagicNumber = 3;
      this.magicNumber = this.baseMagicNumber;
   }

   @Override
   public void use(AbstractPlayer p, AbstractMonster m) {
      this.applyPowers();
      this.addToBot(new GainBlockAction(p, p, this.block));
   }

   @Override
   public void applyPowers() {
      int count = 0;

      for (AbstractCard c : AbstractDungeon.player.hand.group) {
         if (c != this) {
            count++;
         }
      }

      this.baseBlock = count * this.magicNumber;
      super.applyPowers();
      this.rawDescription = cardStrings.DESCRIPTION + cardStrings.EXTENDED_DESCRIPTION[0];
      this.initializeDescription();
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
      return new SpiritShield();
   }
}
