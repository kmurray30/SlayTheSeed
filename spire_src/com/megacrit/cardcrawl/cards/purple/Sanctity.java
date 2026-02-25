package com.megacrit.cardcrawl.cards.purple;

import com.megacrit.cardcrawl.actions.common.GainBlockAction;
import com.megacrit.cardcrawl.actions.watcher.SanctityAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class Sanctity extends AbstractCard {
   public static final String ID = "Sanctity";
   private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Sanctity");

   public Sanctity() {
      super(
         "Sanctity",
         cardStrings.NAME,
         "purple/skill/sanctity",
         1,
         cardStrings.DESCRIPTION,
         AbstractCard.CardType.SKILL,
         AbstractCard.CardColor.PURPLE,
         AbstractCard.CardRarity.UNCOMMON,
         AbstractCard.CardTarget.SELF
      );
      this.baseBlock = 6;
      this.baseMagicNumber = 2;
      this.magicNumber = this.baseMagicNumber;
   }

   @Override
   public void use(AbstractPlayer p, AbstractMonster m) {
      this.addToBot(new GainBlockAction(p, this.block));
      this.addToBot(new SanctityAction(this.magicNumber));
   }

   @Override
   public void triggerOnGlowCheck() {
      if (!AbstractDungeon.actionManager.cardsPlayedThisCombat.isEmpty()
         && AbstractDungeon.actionManager.cardsPlayedThisCombat.get(AbstractDungeon.actionManager.cardsPlayedThisCombat.size() - 1).type
            == AbstractCard.CardType.SKILL) {
         this.glowColor = AbstractCard.GOLD_BORDER_GLOW_COLOR.cpy();
      } else {
         this.glowColor = AbstractCard.BLUE_BORDER_GLOW_COLOR.cpy();
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
      return new Sanctity();
   }
}
