package com.megacrit.cardcrawl.cards.green;

import com.megacrit.cardcrawl.actions.common.GainBlockAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class Defend_Green extends AbstractCard {
   public static final String ID = "Defend_G";
   private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Defend_G");

   public Defend_Green() {
      super(
         "Defend_G",
         cardStrings.NAME,
         "green/skill/defend",
         1,
         cardStrings.DESCRIPTION,
         AbstractCard.CardType.SKILL,
         AbstractCard.CardColor.GREEN,
         AbstractCard.CardRarity.BASIC,
         AbstractCard.CardTarget.SELF
      );
      this.baseBlock = 5;
      this.tags.add(AbstractCard.CardTags.STARTER_DEFEND);
   }

   @Override
   public void use(AbstractPlayer p, AbstractMonster m) {
      if (Settings.isDebug) {
         this.addToBot(new GainBlockAction(p, p, 50));
      } else {
         this.addToBot(new GainBlockAction(p, p, this.block));
      }
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
      return new Defend_Green();
   }
}
